# Update Log

## 2026-10-03

- Added admin controls to mark guest and registered-account orders as paid or unpaid. Payment status is now separate from the order total and is saved through the app.
- Expanded inventory adjustments to support +/-1, retain +/-10, and accept custom positive/negative quantities. Kilogram adjustments support up to two decimal places; stock cannot be reduced below zero.
- Widened the admin content area to 1360px without changing the storefront or header width.
- Confirmed the add-product image field accepts a URL or path only. File upload is not implemented and is deferred for later.
- Reviewed how the storefront catalog reads product and stock data from PostgreSQL and documented the existing setup/run flow in chat.
