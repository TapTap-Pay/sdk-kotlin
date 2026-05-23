package rs.taptap.sdk

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import okhttp3.OkHttpClient
import programmatic.invoices.v1.InvoicesServiceClient
import programmatic.payins.v1.PayInsServiceClient
import programmatic.payment_links.v1.PaymentLinksServiceClient
import programmatic.payments.v1.PaymentsServiceClient
import programmatic.payouts.v1.PayOutsServiceClient
import programmatic.refunds.v1.RefundsServiceClient
import programmatic.transactions.v1.TransactionsServiceClient
import programmatic.transfers.v1.TransfersServiceClient
import programmatic.wallets.v1.WalletsServiceClient
import programmatic.webhooks.v1.WebhooksServiceClient

/** Production TapTap-Pay API endpoint. */
public const val DEFAULT_BASE_URL: String = "https://api.taptap.rs"

/**
 * Configures a [TapTap] client. `apiKey` is required; everything
 * else has sensible defaults.
 *
 * @property apiKey Secret API key (`sk_test_…` or `sk_live_…`).
 * @property baseUrl Override the API base URL.
 * @property maxRetries Cap automatic retries on transient errors
 *   (Unavailable, DeadlineExceeded, ResourceExhausted, network
 *   failures).
 * @property retryBaseDelayMs Initial backoff between retries (ms).
 * @property userAgent Appended to the SDK's own User-Agent header.
 *   Use this to identify your integration in support requests.
 * @property httpClient Custom OkHttpClient. Pass your own to share
 *   connection pools or plug in observability. The SDK adds auth +
 *   user-agent interceptors on top.
 */
public data class TapTapOptions(
    val apiKey: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val maxRetries: Int = 3,
    val retryBaseDelayMs: Long = 500,
    val userAgent: String? = null,
    val httpClient: OkHttpClient? = null,
)

/**
 * Entry point for the TapTap-Pay SDK. Construct once and reuse — the
 * per-service sub-clients are safe for concurrent use.
 *
 * Example:
 * ```kotlin
 * val client = TapTap(TapTapOptions(apiKey = System.getenv("TAPTAP_SECRET")))
 * val resp = client.paymentLinks.createPaymentLink(
 *     PaymentLinks.CreatePaymentLinkRequest.newBuilder()
 *         .setTitle("Premium plan")
 *         .setAmount(Finance.Money.newBuilder().setAmountMinor(2999).setCurrency("EUR").build())
 *         .setTargetWalletId(walletId)
 *         .build(),
 *     emptyMap(),
 * )
 * ```
 */
public class TapTap(opts: TapTapOptions) {
    public val invoices: InvoicesServiceClient
    public val payIns: PayInsServiceClient
    public val paymentLinks: PaymentLinksServiceClient
    public val payments: PaymentsServiceClient
    public val payOuts: PayOutsServiceClient
    public val refunds: RefundsServiceClient
    public val transactions: TransactionsServiceClient
    public val transfers: TransfersServiceClient
    public val wallets: WalletsServiceClient
    public val webhooks: WebhooksServiceClient

    init {
        require(opts.apiKey.isNotBlank()) { "TapTap: apiKey is required" }

        val http = (opts.httpClient ?: OkHttpClient())
            .newBuilder()
            .addInterceptor(UserAgentInterceptor(opts.userAgent))
            .addInterceptor(AuthInterceptor(opts.apiKey))
            .build()

        val raw = ProtocolClient(
            httpClient = ConnectOkHttpClient(http),
            config = ProtocolClientConfig(
                host = opts.baseUrl,
                serializationStrategy = GoogleJavaProtobufStrategy(),
            ),
        )

        val protocol = RetryingProtocolClient(raw, opts.maxRetries, opts.retryBaseDelayMs)

        invoices = InvoicesServiceClient(protocol)
        payIns = PayInsServiceClient(protocol)
        paymentLinks = PaymentLinksServiceClient(protocol)
        payments = PaymentsServiceClient(protocol)
        payOuts = PayOutsServiceClient(protocol)
        refunds = RefundsServiceClient(protocol)
        transactions = TransactionsServiceClient(protocol)
        transfers = TransfersServiceClient(protocol)
        wallets = WalletsServiceClient(protocol)
        webhooks = WebhooksServiceClient(protocol)
    }
}
