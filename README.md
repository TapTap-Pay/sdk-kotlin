# TapTap-Pay Kotlin SDK

[![License](https://img.shields.io/badge/license-Apache_2.0-blue.svg)](LICENSE)

The official Kotlin (JVM) SDK for the [TapTap-Pay](https://usetaptap.com) API.

Wraps [Connect-Kotlin](https://github.com/connectrpc/connect-kotlin)
clients generated from the upstream `programmatic/*` proto surface
with API-key authentication, transient-error retries with exponential
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
import common.v1.Finance
import programmatic.payment_links.v1.PaymentLinks
import rs.taptap.sdk.TapTap
import rs.taptap.sdk.TapTapOptions

suspend fun main() {
    val client = TapTap(
        TapTapOptions(apiKey = System.getenv("TAPTAP_SECRET")),
    )

    val resp = client.paymentLinks.createPaymentLink(
        PaymentLinks.CreatePaymentLinkRequest.newBuilder()
            .setTitle("Premium plan")
            .setAmount(
                Finance.Money.newBuilder()
                    .setAmountMinor(2999)
                    .setCurrency("EUR")
                    .build(),
            )
            .setTargetWalletId(System.getenv("TAPTAP_WALLET_ID"))
            .build(),
        emptyMap(),
    )

    when (resp) {
        is ResponseMessage.Success -> println("link id: ${resp.message.link.id}")
        is ResponseMessage.Failure -> error("create failed: ${resp.cause.code}")
    }
}
```

## Authentication

API keys are minted in the [dashboard](https://app.usetaptap.com). Sandbox
keys are prefixed `sk_test_`, live keys `sk_live_`. The SDK sends them
as `Authorization: Bearer <key>` on every request via an OkHttp
interceptor.

## Configuration

```kotlin
val client = TapTap(
    TapTapOptions(
        apiKey = "sk_live_...",
        baseUrl = "https://api.usetaptap.com", // optional
        maxRetries = 3,                    // default 3
        retryBaseDelayMs = 500,            // default 500
        userAgent = "my-app/1.4.0",        // optional, appended to SDK UA
        httpClient = customOkHttp,         // optional; SDK adds auth + UA on top
    )
)
```

## Idempotency

Every state-changing RPC accepts an `idempotency_key` field on its
request message. Send the same key to safely retry a write — the API
dedupes and returns the original result.

```kotlin
import rs.taptap.sdk.newIdempotencyKey

val key = newIdempotencyKey() // UUID v4
client.payments.requestPayment(
    Payments.RequestPaymentRequest.newBuilder()
        .setIdempotencyKey(key)
        // ...
        .build(),
    emptyMap(),
)
```

The SDK's retry layer re-sends the same request on every attempt, so
the key is preserved naturally across automatic retries.

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
    resp is ResponseMessage.Success -> println(resp.message.link.id)
}
```

## Pagination

List endpoints return one page at a time. Use the SDK's `Flow` helper:

```kotlin
import com.connectrpc.ResponseMessage
import rs.taptap.sdk.items
import rs.taptap.sdk.iter
import kotlinx.coroutines.flow.collect

items(
    iter { page ->
        val resp = client.paymentLinks.listPaymentLinks(
            PaymentLinks.ListPaymentLinksRequest.newBuilder()
                .setPagination(
                    Response.PaginationRequestData.newBuilder()
                        .setPage(page).setPageSize(100).build(),
                )
                .build(),
            emptyMap(),
        )
        when (resp) {
            is ResponseMessage.Success -> resp.message.linksList to resp.message.meta
            is ResponseMessage.Failure -> throw resp.cause
        }
    }
).collect { link -> println(link.id) }
```

## Versioning

Releases follow the upstream [TapTap-Pay API](https://github.com/TapTap-Pay/api)
tag exactly — `0.0.40` of the API ships as `0.0.40` of every SDK.
Generated code is regenerated and pushed on each API release; the
hand-written ergonomics layer is left untouched.

## Local development

Requires JDK 17+ and Gradle 8+ (or use the wrapper once it's
committed). Build with:

```bash
gradle build
```

## Contributing

The generated code in `gen/` is overwritten by CI on every release.
Don't hand-edit it — change the source `.proto` in the
[`api`](https://github.com/TapTap-Pay/api) repo instead.

Hand-written ergonomics (`src/main/kotlin/rs/taptap/sdk/`) is fair
game for PRs.

## License

Apache 2.0 — see [LICENSE](LICENSE).
