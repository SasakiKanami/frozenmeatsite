# Update Log

## 2026-10-03

- Added admin controls to mark guest and registered-account orders as paid or unpaid. Payment status is now separate from the order total and is saved through the app.
- Expanded inventory adjustments to support +/-1, retain +/-10, and accept custom positive/negative quantities. Kilogram adjustments support up to two decimal places; stock cannot be reduced below zero.
- Widened the admin content area to 1360px without changing the storefront or header width.
- Added product image uploads through Cloudinary from the add-product form; manual image URLs remain supported.
- Reviewed how the storefront catalog reads product and stock data from PostgreSQL and documented the existing setup/run flow in chat.
- Added live inventory search by product name, category, or temperature tier in the admin dashboard.
- Cloudinary uploads are signed by the backend; credentials are read from the `CLOUDINARY_URL` environment variable and are not exposed to browser code.
- Updated the admin Revenue Today metric to include only paid, non-cancelled orders; Orders Today still counts every non-cancelled order.
- Checkout now records the quantity deducted from each FIFO inventory batch in the existing `batch_deductions` audit table.
- Added optional batch expiration dates to product intake and excluded expired batch stock from the storefront availability view and checkout FIFO deductions. Added `migration_003_batch_expiration.sql` for existing databases.
- Added server-backed login sessions, BCrypt password storage with successful-login upgrade for legacy plaintext passwords, CSRF protection for browser writes, role-gated admin pages/APIs, and an account-scoped pending-orders page showing fulfillment and payment states.
- Added admin-only storefront visibility controls. Hiding a product removes it from the customer catalog and prevents checkout while preserving its product and inventory records; existing databases need `migration_004_product_storefront_visibility.sql`.
- Replaced the temporary inventory +/- controls with a product batch page showing the image, price, stock status, combined unexpired stock, and batches in FIFO order. Admins can receive a new quantity as a distinct batch and edit recorded batch expiry dates; new batches require an expiry date.
- Added a supplier field to initial product/batch registration and the session additions table; supplier names are saved on the initial batch.
- Added one-time, 30-minute password reset links; configure `MAIL_ENABLED=true`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, and `APP_PUBLIC_BASE_URL` as environment variables to enable SMTP delivery. Without SMTP, reset requests still return the same generic response and the server logs that delivery is not configured.
- Added customer profile management and checkout prefill, a configurable `DELIVERY_FEE` (defaults to `0.00`), walk-in POS orders, pending/completed/cancelled order actions, unpaid cancellation stock restoration from FIFO audit rows, and audited soft-archiving of completed orders. Existing databases need `migration_005_account_orders_checkout.sql`; do not rerun destructive `schema.sql`.
- New online and walk-in orders start as pending. GCash remains an offline payment preference; staff still record payment manually.
