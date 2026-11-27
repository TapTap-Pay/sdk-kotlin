package rs.taptap.sdk

import com.connectrpc.ResponseMessage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import taptap.common.v1.Error.ErrorInfo
import taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass.GetPaymentLinkRequest
import taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass.ListPaymentLinksRequest
import taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass.ListPaymentLinksResponse
import taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass.PaymentLink
import taptap.payment.programmatic.v1.WalletServiceOuterClass.ListWalletsRequest
import taptap.payment.programmatic.v1.WalletServiceOuterClass.ListWalletsResponse
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val API_KEY = "taptap_live_000000000000.test"
private const val VENDOR_ID = "ven_00000000-0000-0000-0000-000000000001"
private const val PAYMENT_LINKS = "/taptap.payment.programmatic.v1.PaymentLinkService"
private const val WALLETS = "/taptap.payment.programmatic.v1.WalletService"

/** A stand-in for the API on a loopback port. */
class TapTapTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val authorization = CopyOnWriteArrayList<String?>()

    private fun client(): TapTap {
        server.start()
        return TapTap(
            TapTapOptions(
                apiKey = API_KEY,
                baseUrl = "http://127.0.0.1:${server.address.port}",
                retryBaseDelayMs = 1,
            ),
        )
    }

    private fun handle(path: String, reply: (HttpExchange, ByteArray) -> Unit) {
        server.createContext(path) { exchange ->
            authorization += exchange.requestHeaders.getFirst("Authorization")
            val body = exchange.requestBody.readBytes()
            exchange.use { reply(it, body) }
        }
    }

    private fun HttpExchange.send(status: Int, contentType: String, body: ByteArray) {
        responseHeaders.add("Content-Type", contentType)
        sendResponseHeaders(status, body.size.toLong())
        responseBody.write(body)
    }

    @AfterTest
    fun stop() = server.stop(0)

    @Test
    fun errorInfoDecodesTheRefusalDetail() = runBlocking {
        handle("$PAYMENT_LINKS/GetPaymentLink") { exchange, body ->
            val request = GetPaymentLinkRequest.parseFrom(body)
            val detail = ErrorInfo.newBuilder()
                .setCode("PAYMENT_LINK_NOT_FOUND")
                .putParams("payment_link_id", request.paymentLinkId)
                .setMessage("payment link: not found")
                .build()
            val value = Base64.getEncoder().withoutPadding().encodeToString(detail.toByteArray())
            exchange.send(
                404,
                "application/json",
                """{"code":"not_found","message":"payment link: not found","details":[{"type":"taptap.common.v1.ErrorInfo","value":"$value"}]}"""
                    .toByteArray(),
            )
        }

        val resp = client().paymentLinks.getPaymentLink(
            GetPaymentLinkRequest.newBuilder()
                .setVendorId(VENDOR_ID)
                .setPaymentLinkId("plink_missing")
                .build(),
            emptyMap(),
        )

        assertTrue(resp.isNotFound())
        assertEquals("PAYMENT_LINK_NOT_FOUND", resp.errorInfo()?.code)
        assertEquals(mapOf("payment_link_id" to "plink_missing"), resp.errorInfo()?.paramsMap)
        assertEquals("PAYMENT_LINK_NOT_FOUND", resp.errorCode())
        assertEquals(listOf<String?>("Bearer $API_KEY"), authorization.toList(), "one call: NOT_FOUND is not retried")
    }

    @Test
    fun errorInfoIsNullWithoutADetail() = runBlocking {
        handle("$WALLETS/ListWallets") { exchange, _ ->
            exchange.send(403, "application/json", """{"code":"permission_denied","message":"no"}""".toByteArray())
        }

        val resp = client().wallets.listWallets(
            ListWalletsRequest.newBuilder().setVendorId(VENDOR_ID).build(),
            emptyMap(),
        )

        assertTrue(resp.isPermissionDenied())
        assertFalse(resp.isAborted())
        assertNull(resp.errorInfo())
        assertNull(resp.errorCode())
    }

    @Test
    fun iterFollowsNextPageTokenUntilEmpty() = runBlocking {
        val pageTokens = CopyOnWriteArrayList<String>()
        handle("$PAYMENT_LINKS/ListPaymentLinks") { exchange, body ->
            val request = ListPaymentLinksRequest.parseFrom(body)
            pageTokens += request.pageToken
            val page = ListPaymentLinksResponse.newBuilder()
            if (request.pageToken.isEmpty()) {
                page.addPaymentLinks(PaymentLink.newBuilder().setId("plink_1"))
                page.addPaymentLinks(PaymentLink.newBuilder().setId("plink_2"))
                page.setNextPageToken("page-2")
            } else {
                page.addPaymentLinks(PaymentLink.newBuilder().setId("plink_3"))
            }
            exchange.send(200, "application/proto", page.build().toByteArray())
        }
        val client = client()

        val links = items(
            iter { pageToken ->
                val resp = client.paymentLinks.listPaymentLinks(
                    ListPaymentLinksRequest.newBuilder()
                        .setVendorId(VENDOR_ID)
                        .setPageToken(pageToken)
                        .build(),
                    emptyMap(),
                )
                when (resp) {
                    is ResponseMessage.Success -> resp.message.paymentLinksList to resp.message.nextPageToken
                    is ResponseMessage.Failure -> throw resp.cause
                }
            },
        ).toList()

        assertEquals(listOf("plink_1", "plink_2", "plink_3"), links.map { it.id })
        assertEquals(listOf("", "page-2"), pageTokens.toList())
    }

    @Test
    fun unavailableIsRetried() = runBlocking {
        var attempts = 0
        handle("$WALLETS/ListWallets") { exchange, _ ->
            attempts++
            if (attempts < 3) {
                exchange.send(503, "application/json", """{"code":"unavailable","message":"down"}""".toByteArray())
            } else {
                exchange.send(200, "application/proto", ListWalletsResponse.getDefaultInstance().toByteArray())
            }
        }

        val resp = client().wallets.listWallets(
            ListWalletsRequest.newBuilder().setVendorId(VENDOR_ID).build(),
            emptyMap(),
        )

        assertTrue(resp is ResponseMessage.Success)
        assertEquals(3, attempts)
    }
}
