# Update Log

## 2026-10-03

- Added admin controls to mark guest and registered-account orders as paid or unpaid. Payment status is now separate from the order total and is saved through the app.
- Expanded inventory adjustments to support +/-1, retain +/-10, and accept custom positive/negative quantities. Kilogram adjustments support up to two decimal places; stock cannot be reduced below zero.
- Widened the admin content area to 1360px without changing the storefront or header width.
- Added product image uploads through Cloudinary from the add-product form; manual image URLs remain supported.
- Reviewed how the storefront catalog reads product and stock data from PostgreSQL and documented the existing setup/run flow in chat.
- Added live inventory search by product name, category, or temperature tier in the admin dashboard.
- Cloudinary uploads are signed by the backend; credentials are read from the `CLOUDINARY_URL` environment variable and are not exposed to browser code.
