package rs.taptap.sdk

import com.connectrpc.ProtocolClientConfig
import com.connectrpc.extensions.GoogleJavaProtobufStrategy
import com.connectrpc.impl.ProtocolClient
import com.connectrpc.okhttp.ConnectOkHttpClient
import okhttp3.OkHttpClient
import taptap.banking.programmatic.v1.BankingServiceClient
import taptap.developer.programmatic.v1.WebhookServiceClient
import taptap.invoicing.programmatic.v1.InboundDocumentServiceClient
import taptap.invoicing.programmatic.v1.InvoiceServiceClient
import taptap.marketplace.programmatic.v1.SubvendorServiceClient
import taptap.payment.programmatic.v1.CustomerServiceClient
import taptap.payment.programmatic.v1.PaymentLinkServiceClient
import taptap.payment.programmatic.v1.PaymentServiceClient
import taptap.payment.programmatic.v1.RefundServiceClient
import taptap.payment.programmatic.v1.TransactionServiceClient
import taptap.payment.programmatic.v1.WalletServiceClient

// Environment URLs. CI rewrites these from secrets at release time.
public const val PROD_BASE_URL: String = "https://api.usetaptap.com/"
public const val SANDBOX_BASE_URL: String = "https://api.usetaptap.dev/"

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
 * Every request names the vendor the API key belongs to in `vendorId`
 * (`ven_` followed by a UUID). The SDK does not fill it in.
 *
 * Example:
 * ```kotlin
 * val client = TapTap(TapTapOptions(apiKey = System.getenv("TAPTAP_API_KEY")))
 * val resp = client.paymentLinks.createPaymentLink(
 *     PaymentLinkServiceOuterClass.CreatePaymentLinkRequest.newBuilder()
 *         .setVendorId(System.getenv("TAPTAP_VENDOR_ID"))
 *         .setTitle("Premium plan")
 *         .setAmount(MoneyOuterClass.Money.newBuilder().setCurrency("EUR").setAmountMinor(2999L).build())
 *         .setUsage(PaymentLinkServiceOuterClass.PaymentLinkUsage.newBuilder().setUnlimited(true).build())
 *         .build(),
 *     emptyMap(),
 * )
 * ```
 */
public class TapTap(opts: TapTapOptions) {
    public val banking: BankingServiceClient
    public val customers: CustomerServiceClient
    public val inboundDocuments: InboundDocumentServiceClient
    public val invoices: InvoiceServiceClient
    public val paymentLinks: PaymentLinkServiceClient
    public val payments: PaymentServiceClient
    public val refunds: RefundServiceClient
    public val subvendors: SubvendorServiceClient
    public val transactions: TransactionServiceClient
    public val wallets: WalletServiceClient
    public val webhooks: WebhookServiceClient

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

        banking = BankingServiceClient(protocol)
        customers = CustomerServiceClient(protocol)
        inboundDocuments = InboundDocumentServiceClient(protocol)
        invoices = InvoiceServiceClient(protocol)
        paymentLinks = PaymentLinkServiceClient(protocol)
        payments = PaymentServiceClient(protocol)
        refunds = RefundServiceClient(protocol)
        subvendors = SubvendorServiceClient(protocol)
        transactions = TransactionServiceClient(protocol)
        wallets = WalletServiceClient(protocol)
        webhooks = WebhookServiceClient(protocol)
    }
}
