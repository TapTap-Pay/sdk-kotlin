# Changelog

All notable changes to the TapTap-Pay Kotlin SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
`gen/` is regenerated from the
[TapTap-Pay monorepo](https://github.com/TapTap-Pay/monorepo) after each of
its releases.

## [Unreleased]

### Changed (breaking)

- The SDK now targets the contract the monorepo generates: proto and Java
  packages `taptap.<context>.programmatic.v1`.
- `TapTap` properties follow the new services: `banking`, `customers`,
  `inboundDocuments`, `invoices`, `paymentLinks`, `payments`, `refunds`,
  `subvendors`, `transactions`, `wallets`, `webhooks`. `payIns`, `payOuts`
  and `transfers` are gone; payouts are read through `banking`.
- Every request names the vendor in `vendorId`.
- Pagination is cursor based: the `iter` lambda takes a page token and
  returns the next one, and `Page` carries `nextPageToken` instead of
  `meta`.
- `protobuf-java` is 4.36.2, the version the regenerated messages need.

### Added

- `errorInfo()` and `errorCode()` decode the `taptap.common.v1.ErrorInfo`
  detail attached to every refusal.
- `isAborted()`, for an idempotency key reused with different content.
