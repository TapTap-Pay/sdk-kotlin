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

// Environment URLs. CI rewrites these from secrets at release time.
public const val PROD_BASE_URL: String = "https://api.taptap.rs"
public const val SANDBOX_BASE_URL: String = "https://api.usetaptap.dev"

/**
 * Configures a [TapTap] client. `apiKey` is required; everything
 * else has sensible defaults.
 *
 * @property apiKey Secret API key.
 * @property mode "production" (default) or "sandbox". Ignored when baseUrl is set.
 * @property baseUrl Explicit override — ignores mode when set.
 * @property maxRetries Cap automatic retries on transient errors.
 * @property retryBaseDelayMs Initial backoff between retries (ms).
 * @property userAgent Appended to the SDK's own User-Agent header.
 * @property httpClient Custom OkHttpClient.
 */
public data class TapTapOptions(
    val apiKey: String,
    val mode: String = "production",
    val baseUrl: String = if (mode == "sandbox") SANDBOX_BASE_URL else PROD_BASE_URL,
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
