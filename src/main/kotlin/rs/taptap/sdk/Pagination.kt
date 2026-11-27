package rs.taptap.sdk

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * One page of items plus the cursor the server returned for the page
 * after it (empty on the last page).
 */
public data class Page<T>(
    val items: List<T>,
    val nextPageToken: String,
)

/**
 * Walks every page of a List endpoint as a `Flow<Page<T>>`. The fetch
 * lambda receives the cursor of the page to fetch (empty for the first
 * page) and returns the items plus the cursor of the next page, empty
 * when there are no more. Errors from the fetch lambda surface as Flow
 * exceptions.
 *
 * The cursor is spelled differently across the List endpoints, which is
 * why the lambda does the mapping: most requests take `pageToken` and
 * answer `nextPageToken`; `listInvoices` and `listInboundDocuments`
 * take `cursor` and answer `nextCursor`; `listSubvendors` takes a
 * `taptap.common.v1.Page.PageRequest` and answers `page.nextPageToken`.
 *
 * Example:
 * ```kotlin
 * iter { pageToken ->
 *   val resp = client.paymentLinks.listPaymentLinks(
 *     PaymentLinkServiceOuterClass.ListPaymentLinksRequest.newBuilder()
 *       .setVendorId(vendorId)
 *       .setPageSize(100)
 *       .setPageToken(pageToken)
 *       .build(),
 *     emptyMap(),
 *   )
 *   when (resp) {
 *     is ResponseMessage.Success -> resp.message.paymentLinksList to resp.message.nextPageToken
 *     is ResponseMessage.Failure -> throw resp.cause
 *   }
 * }.collect { page -> page.items.forEach { println(it.id) } }
 * ```
 */
public fun <T> iter(
    fetch: suspend (pageToken: String) -> Pair<List<T>, String>,
): Flow<Page<T>> = flow {
    var pageToken = ""
    while (true) {
        val (items, next) = fetch(pageToken)
        emit(Page(items, next))
        if (next.isEmpty()) return@flow
        pageToken = next
    }
}

/**
 * Flattens a paged Flow to one item at a time. Useful when you don't
 * need the per-page cursor:
 * ```kotlin
 * items(iter(fetch)).collect { link -> println(link.id) }
 * ```
 */
public fun <T> items(pages: Flow<Page<T>>): Flow<T> = flow {
    pages.collect { page -> page.items.forEach { emit(it) } }
}
