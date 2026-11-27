package rs.taptap.sdk

import com.connectrpc.Code
import com.connectrpc.ConnectException
import com.connectrpc.ResponseMessage
import java.util.UUID
import taptap.common.v1.Error.ErrorInfo

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
 * `ABORTED` — returned with code `IDEMPOTENCY_KEY_CONFLICT` when an
 * idempotency key is reused for a request whose content differs.
 */
public fun ResponseMessage<*>.isAborted(): Boolean = code() == Code.ABORTED

/**
 * Returns the `taptap.common.v1.ErrorInfo` detail the API attaches to
 * every refusal: a stable machine `code` (e.g. `PAYMENT_LINK_NOT_FOUND`),
 * the `paramsMap` around it, and an English debug `message`. Branch on
 * the code, never on the message. Null when the exception carries no
 * such detail (e.g. a transport failure).
 */
public fun ConnectException.errorInfo(): ErrorInfo? =
    unpackedDetails(ErrorInfo::class).firstOrNull()

/** The [ErrorInfo] detail of a Failure, or null on Success or without one. */
public fun ResponseMessage<*>.errorInfo(): ErrorInfo? = when (this) {
    is ResponseMessage.Success -> null
    is ResponseMessage.Failure -> cause.errorInfo()
}

/** The stable machine code of a refusal, or null without one. */
public fun ResponseMessage<*>.errorCode(): String? = errorInfo()?.code

/**
 * Generate a fresh UUID v4 suitable for use as an `idempotency_key`
 * on state-changing requests. Persist client-side before sending the
 * request so a crash-restart can resend the same key and reach the
 * same result.
 */
public fun newIdempotencyKey(): String = UUID.randomUUID().toString()
