# TapTap-Pay Kotlin SDK

[![License](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](LICENSE)

The official Kotlin (JVM) SDK for the [TapTap-Pay](https://usetaptap.com) API.

Wraps [Connect-Kotlin](https://github.com/connectrpc/connect-kotlin)
clients generated from the upstream programmatic proto surface (the
`taptap.<context>.programmatic.v1` packages) with API-key authentication, transient-error retries with exponential
backoff + jitter, idiomatic Kotlin error guards, and a
[Flow](https://kotlinlang.org/docs/flow.html)-based pagination helper.

Requires JDK 17+.

## Install

The SDK is published to GitHub Packages. Add the repository + a GitHub
PAT with `read:packages` to your Gradle build:

```kotlin
// build.gradle.kts
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/TapTap-Pay/sdk-kotlin")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as String?
            password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.token") as String?
        }
    }
}

dependencies {
    implementation("rs.taptap:sdk:0.0.40")
}
```

## Quick start

```kotlin
import com.connectrpc.ResponseMessage
import rs.taptap.sdk.TapTap
import rs.taptap.sdk.TapTapOptions
import taptap.common.v1.MoneyOuterClass
import taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass

suspend fun main() {
    val client = TapTap(
        TapTapOptions(apiKey = System.getenv("TAPTAP_API_KEY")),
    )

    val resp = client.paymentLinks.createPaymentLink(
        PaymentLinkServiceOuterClass.CreatePaymentLinkRequest.newBuilder()
            .setVendorId(System.getenv("TAPTAP_VENDOR_ID"))
            .setTitle("Premium plan")
            .setAmount(
                MoneyOuterClass.Money.newBuilder()
                    .setCurrency("EUR")
                    .setAmountMinor(2999L)
                    .build(),
            )
            .setUsage(
                PaymentLinkServiceOuterClass.PaymentLinkUsage.newBuilder()
                    .setUnlimited(true)
                    .build(),
            )
            .build(),
        emptyMap(),
    )

    when (resp) {
        is ResponseMessage.Success -> println("link id: ${resp.message.paymentLink.id}")
        is ResponseMessage.Failure -> error("create failed: ${resp.cause.code}")
    }
}
```

## Services

| Client property | Service | Messages in |
|---|---|---|
| `banking` | `BankingService` | `taptap.banking.programmatic.v1.BankingServiceOuterClass` |
| `customers` | `CustomerService` | `taptap.payment.programmatic.v1.CustomerServiceOuterClass` |
| `inboundDocuments` | `InboundDocumentService` | `taptap.invoicing.programmatic.v1.InboundDocumentServiceOuterClass` |
| `invoices` | `InvoiceService` | `taptap.invoicing.programmatic.v1.InvoiceServiceOuterClass` |
| `paymentLinks` | `PaymentLinkService` | `taptap.payment.programmatic.v1.PaymentLinkServiceOuterClass` |
| `payments` | `PaymentService` | `taptap.payment.programmatic.v1.PaymentServiceOuterClass` |
| `refunds` | `RefundService` | `taptap.payment.programmatic.v1.RefundServiceOuterClass` |
| `subvendors` | `SubvendorService` | `taptap.marketplace.programmatic.v1.SubvendorServiceOuterClass` |
| `transactions` | `TransactionService` | `taptap.payment.programmatic.v1.TransactionServiceOuterClass` |
| `wallets` | `WalletService` | `taptap.payment.programmatic.v1.WalletServiceOuterClass` |
| `webhooks` | `WebhookService` | `taptap.developer.programmatic.v1.WebhookServiceOuterClass` |

Shared types live in `taptap.common.v1`: `MoneyOuterClass.Money`,
`Page.PageRequest` / `Page.PageResponse` and `Error.ErrorInfo`.

## Authentication

API keys are minted in the [dashboard](https://app.usetaptap.com), in your
vendor's settings under API keys. A key looks like `taptap_live_` followed
by a lookup part, a dot and a secret part. The SDK sends it as
`Authorization: Bearer <key>` on every request via an OkHttp
interceptor.

## Vendor id

A key belongs to one vendor, and every request names that vendor in its
`vendorId` field (`ven_` followed by a UUID). The SDK does not fill it
in: set it on each request. Naming a vendor the key does not belong to
is answered like a vendor that does not exist (`NOT_FOUND`, code
`VENDOR_NOT_FOUND`).

## Configuration

```kotlin
val client = TapTap(
    TapTapOptions(
        apiKey = "taptap_live_...",
        baseUrl = "https://api.usetaptap.com", // optional
        maxRetries = 3,                    // default 3
        retryBaseDelayMs = 500,            // default 500
        userAgent = "my-app/1.4.0",        // optional, appended to SDK UA
        httpClient = customOkHttp,         // optional; SDK adds auth + UA on top
    )
)
```

## Idempotency

State-changing RPCs accept an `idempotency_key` field on their request
message (`RefundPaymentRequest` calls it `request_key`). Send the same
key to safely retry a write — the API dedupes and returns the original
result.

```kotlin
import rs.taptap.sdk.newIdempotencyKey

val key = newIdempotencyKey() // UUID v4
client.payments.createPayment(
    PaymentServiceOuterClass.CreatePaymentRequest.newBuilder()
        .setVendorId(vendorId)
        .setIdempotencyKey(key)
        // ...
        .build(),
    emptyMap(),
)
```

The SDK's retry layer re-sends the same request on every attempt, so
the key is preserved naturally across automatic retries. Reusing a key
for a request with different content is refused with `ABORTED` and the
code `IDEMPOTENCY_KEY_CONFLICT` (`resp.isAborted()`).

## Retries

Transient errors (`UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`)
are retried up to `maxRetries` times with exponential backoff and
full jitter, transparently to the caller. All other Connect codes
surface on the first attempt. Streaming RPCs are not retried —
Connect bidi streams aren't safely replayable without
application-level checkpoints.

## Errors

```kotlin
import rs.taptap.sdk.isNotFound
import rs.taptap.sdk.isRateLimited
import rs.taptap.sdk.isFailedPrecondition

val resp = client.paymentLinks.getPaymentLink(req, emptyMap())
when {
    resp.isNotFound() -> {}
    resp.isRateLimited() -> {}
    resp.isFailedPrecondition() -> {}
    resp is ResponseMessage.Failure -> throw resp.cause
    resp is ResponseMessage.Success -> println(resp.message.paymentLink.id)
}
```

Every refusal also carries a `taptap.common.v1.ErrorInfo` detail: a
stable machine code, the parameters around it, and an English debug
message. Branch on the code, never on the message:

```kotlin
import rs.taptap.sdk.errorCode
import rs.taptap.sdk.errorInfo

resp.errorInfo()?.let { info -> println("${info.code} ${info.paramsMap}") }
if (resp.errorCode() == "REFUND_EXCEEDS_REMAINDER") {
    // offer a smaller refund
}
```

## Pagination

List endpoints return one page at a time and a cursor for the next. Use
the SDK's `Flow` helper. The lambda receives the cursor (empty for the
first page) and returns the items and the next cursor:

```kotlin
import com.connectrpc.ResponseMessage
import rs.taptap.sdk.items
import rs.taptap.sdk.iter

items(
    iter { pageToken ->
        val resp = client.paymentLinks.listPaymentLinks(
            PaymentLinkServiceOuterClass.ListPaymentLinksRequest.newBuilder()
                .setVendorId(vendorId)
                .setPageSize(100)
                .setPageToken(pageToken)
                .build(),
            emptyMap(),
        )
        when (resp) {
            is ResponseMessage.Success -> resp.message.paymentLinksList to resp.message.nextPageToken
            is ResponseMessage.Failure -> throw resp.cause
        }
    }
).collect { link -> println(link.id) }
```

The cursor is spelled three ways across the API, and the lambda is where
you map it:

| Lists | Request | Response |
|---|---|---|
| Most (`listPayments`, `listPaymentLinks`, `listRefunds`, `listCustomers`, `listUnappliedReceipts`, `listWalletTransactions`, `listBankTransfers`, `listSubscriptions`, `listDeliveries`) | `setPageToken` | `nextPageToken` |
| `listInvoices`, `listInboundDocuments` | `setCursor` | `nextCursor` |
| `listSubvendors` | `setPage(Page.PageRequest...)` | `page.nextPageToken` |

`listWallets` returns every wallet in one response.

## Versioning

This artifact has its own semver versions. After each release of the
[TapTap-Pay monorepo](https://github.com/TapTap-Pay/monorepo), `gen/` is
regenerated from its programmatic protos and pushed here, which cuts the
next SDK release.

## Local development

Requires JDK 17+ and Gradle 8+ (or use the wrapper once it's
committed). Build with:

```bash
gradle build
```

## Contributing

The generated code in `gen/` is overwritten by CI on every release.
Don't hand-edit it — change the source `.proto` under
`proto/taptap/*/programmatic/` in the
[`monorepo`](https://github.com/TapTap-Pay/monorepo) instead.

Hand-written ergonomics (`src/main/kotlin/rs/taptap/sdk/`) is fair
game for PRs.

## License

Apache 2.0 — see [LICENSE](LICENSE).
