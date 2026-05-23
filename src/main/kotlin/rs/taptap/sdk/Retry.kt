package rs.taptap.sdk

import com.connectrpc.Code
import com.connectrpc.Headers
import com.connectrpc.MethodSpec
import com.connectrpc.ProtocolClientInterface
import com.connectrpc.ResponseMessage
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.random.Random

/**
 * Wraps a `ProtocolClientInterface` so unary calls automatically
 * retry on transient errors with exponential backoff + full jitter.
 *
 * Retried Connect codes: `Unavailable`, `DeadlineExceeded`,
 * `ResourceExhausted`. All other failures surface to the caller on
 * the first attempt. Streaming methods are passed through unretried
 * via Kotlin's `by` delegation — Connect bidi streams aren't safely
 * replayable without application-level checkpoints.
 *
 * Idempotency: state-changing TapTap-Pay RPCs take `idempotency_key`
 * as a message field. The same request object is sent on every
 * attempt, so the server dedupes naturally as long as the caller set
 * the key.
 */
internal class RetryingProtocolClient(
    private val delegate: ProtocolClientInterface,
    private val maxRetries: Int,
    private val baseDelayMs: Long,
) : ProtocolClientInterface by delegate {

    override suspend fun <Input : Any, Output : Any> unary(
        request: Input,
        headers: Headers,
        methodSpec: MethodSpec<Input, Output>,
    ): ResponseMessage<Output> {
        var lastFailure: ResponseMessage.Failure<Output>? = null
        for (attempt in 0..maxRetries) {
            when (val resp = delegate.unary(request, headers, methodSpec)) {
                is ResponseMessage.Success -> return resp
                is ResponseMessage.Failure -> {
                    if (!isRetryable(resp.cause.code)) return resp
                    lastFailure = resp
                    if (attempt == maxRetries) break
                    delay(backoffMs(baseDelayMs, attempt))
                }
            }
        }
        return lastFailure!!
    }

    private fun isRetryable(code: Code): Boolean =
        code == Code.UNAVAILABLE ||
            code == Code.DEADLINE_EXCEEDED ||
            code == Code.RESOURCE_EXHAUSTED

    private fun backoffMs(base: Long, attempt: Int): Long {
        val max = (base * 2.0.pow(attempt)).toLong()
        return Random.nextLong(0, max.coerceAtLeast(1))
    }
}
