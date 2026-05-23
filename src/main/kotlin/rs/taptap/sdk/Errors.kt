package rs.taptap.sdk

import com.connectrpc.Code
import com.connectrpc.ResponseMessage
import java.util.UUID

/**
 * Returns the Connect code on a Failure, or null on Success. Useful
 * for chained `when (resp.code()) { ... }` checks.
 */
public fun ResponseMessage<*>.code(): Code? = when (this) {
    is ResponseMessage.Success -> null
    is ResponseMessage.Failure -> cause.code
}

public fun ResponseMessage<*>.isNotFound(): Boolean = code() == Code.NOT_FOUND
public fun ResponseMessage<*>.isAlreadyExists(): Boolean = code() == Code.ALREADY_EXISTS
public fun ResponseMessage<*>.isInvalidArgument(): Boolean = code() == Code.INVALID_ARGUMENT
public fun ResponseMessage<*>.isPermissionDenied(): Boolean = code() == Code.PERMISSION_DENIED
public fun ResponseMessage<*>.isUnauthenticated(): Boolean = code() == Code.UNAUTHENTICATED

/** `RESOURCE_EXHAUSTED` — SDK retry budget exhausted. */
public fun ResponseMessage<*>.isRateLimited(): Boolean = code() == Code.RESOURCE_EXHAUSTED

public fun ResponseMessage<*>.isFailedPrecondition(): Boolean = code() == Code.FAILED_PRECONDITION

/**
 * Generate a fresh UUID v4 suitable for use as an `idempotency_key`
 * on state-changing requests. Persist client-side before sending the
 * request so a crash-restart can resend the same key and reach the
 * same result.
 */
public fun newIdempotencyKey(): String = UUID.randomUUID().toString()
