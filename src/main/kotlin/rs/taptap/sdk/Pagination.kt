package rs.taptap.sdk

import com.connectrpc.ResponseMessage
import common.v1.Response
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One page of items plus the server's pagination metadata. */
public data class Page<T>(
    val items: List<T>,
    val meta: Response.PaginatedResponseMeta?,
)

/**
 * Walks every page of a List endpoint as a `Flow<Page<T>>`. The fetch
 * lambda receives a 1-indexed page number and returns items + meta.
 * Errors from the fetch lambda surface as Flow exceptions.
 *
 * Example:
 * ```kotlin
 * iter { page ->
 *   val resp = client.paymentLinks.listPaymentLinks(
 *     PaymentLinks.ListPaymentLinksRequest.newBuilder()
 *       .setPagination(
 *         Response.PaginationRequestData.newBuilder().setPage(page).setPageSize(100).build()
 *       ).build(),
 *     emptyMap(),
 *   )
 *   when (resp) {
 *     is ResponseMessage.Success -> resp.message.linksList to resp.message.meta
 *     is ResponseMessage.Failure -> throw resp.cause
 *   }
 * }.collect { page -> page.items.forEach { println(it.id) } }
 * ```
 */
public fun <T> iter(
    fetch: suspend (page: Int) -> Pair<List<T>, Response.PaginatedResponseMeta?>,
): Flow<Page<T>> = flow {
    var page = 1
    while (true) {
        val (items, meta) = fetch(page)
        emit(Page(items, meta))
        if (meta == null || page >= meta.totalPages) return@flow
        page++
    }
}

/**
 * Flattens a paged Flow to one item at a time. Useful when you don't
 * need the per-page meta:
 * ```kotlin
 * items(iter(fetch)).collect { link -> println(link.id) }
 * ```
 */
public fun <T> items(pages: Flow<Page<T>>): Flow<T> = flow {
    pages.collect { page -> page.items.forEach { emit(it) } }
}
