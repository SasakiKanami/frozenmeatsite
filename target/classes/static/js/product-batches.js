function escapeBatchText(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
}

function formatBatchDate(value) {
    if (!value) return 'Not recorded';
    const date = Array.isArray(value)
        ? new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0)
        : new Date(value);
    return Number.isNaN(date.getTime())
        ? 'Not recorded'
        : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function formatExpiryDate(value) {
    if (!value) return 'Not recorded';
    const date = new Date(`${value}T00:00:00`);
    return Number.isNaN(date.getTime())
        ? 'Not recorded'
        : date.toLocaleDateString(undefined, { dateStyle: 'medium' });
}

function getProductStockStatus(quantity, reorderLevel) {
    if (quantity <= 0) return { text: 'OUT OF STOCK', className: 'out-stock' };
    if (quantity <= reorderLevel) return { text: 'LOW STOCK', className: 'low-stock' };
    return { text: 'IN STOCK', className: 'in-stock' };
}

function getBatchStatus(batch) {
    if (batch.deleted) return { text: 'ARCHIVED', className: 'out-stock' };
    const now = new Date();
    const today = [
        now.getFullYear(),
        String(now.getMonth() + 1).padStart(2, '0'),
        String(now.getDate()).padStart(2, '0')
    ].join('-');
    if (batch.expirationDate && batch.expirationDate < today) {
        return { text: 'EXPIRED', className: 'out-stock' };
    }
    if (Number(batch.remainingQty) <= 0) return { text: 'DEPLETED', className: 'out-stock' };
    if (!batch.expirationDate) return { text: 'NO EXPIRY RECORDED', className: 'low-stock' };
    return { text: 'AVAILABLE', className: 'in-stock' };
}

function renderProductDetails(product) {
    const summary = document.getElementById('product-summary');
    const stock = Number(product.inStockQty);
    const status = getProductStockStatus(stock, Number(product.reorderLevel ?? 5));
    const image = product.imageUrl
        ? `<img class="product-summary-image" src="${escapeBatchText(product.imageUrl)}" alt="${escapeBatchText(product.name)}">`
        : '<div class="product-summary-image product-summary-image-empty">No image</div>';

    summary.innerHTML = `
        <div class="product-summary-layout">
            ${image}
            <div class="product-summary-details">
                <p class="admin-eyebrow">Product inventory</p>
                <h1 class="product-summary-name">${escapeBatchText(product.name)}</h1>
                <p class="product-summary-category">SKU: <strong>${escapeBatchText(product.sku)}</strong></p>
                <p class="product-summary-category">${escapeBatchText(product.category)} · ${escapeBatchText(product.temperatureTier)}</p>
                <div class="product-summary-stats">
                    <div><span>Price per unit</span><strong>₱${Number(product.pricePerUnit).toFixed(2)} / ${escapeBatchText(product.unit)}</strong></div>
                    <div><span>Combined available stock</span><strong>${stock} ${escapeBatchText(product.unit)}</strong></div>
                    <div><span>Stock status</span><strong><span class="stock-pill ${status.className}">${status.text}</span></strong></div>
                    <div><span>Customer storefront</span><strong>${product.visible ? 'Shown' : 'Hidden'}</strong></div>
                </div>
            </div>
        </div>
    `;
    document.getElementById('product-sku-value').value = product.sku;
    document.getElementById('product-alias-list').innerHTML = product.aliases.length
        ? product.aliases.map(alias => `<span class="product-alias-chip">${escapeBatchText(alias)}</span>`).join('')
        : '<span class="admin-panel-sub">No alternate names added.</span>';
    document.title = `${product.name} Batches | J&R Frozen Goods`;

    const quantityInput = document.getElementById('batch-quantity');
    quantityInput.step = product.unit === 'kg' ? '0.01' : '1';
    quantityInput.min = product.unit === 'kg' ? '0.01' : '1';
    quantityInput.setAttribute('aria-label', `Quantity received in ${product.unit}`);

    const now = new Date();
    document.getElementById('batch-expiration').min = [
        now.getFullYear(),
        String(now.getMonth() + 1).padStart(2, '0'),
        String(now.getDate()).padStart(2, '0')
    ].join('-');
}

function renderBatches(batches, unit) {
    const tableBody = document.getElementById('batch-table-body');
    tableBody.replaceChildren();
    if (!batches.length) {
        tableBody.innerHTML = '<tr><td colspan="8">No batches have been recorded for this product.</td></tr>';
        return;
    }

    batches.forEach((batch, index) => {
        const status = getBatchStatus(batch);
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${index + 1}</td>
            <td class="cell-product-name">${escapeBatchText(batch.batchNumber)}</td>
            <td>${escapeBatchText(batch.supplierName || '—')}</td>
            <td>${formatBatchDate(batch.arrivalDate)}</td>
            <td>
                <div class="batch-expiry-edit">
                    <input class="batch-expiry-input" type="date" value="${escapeBatchText(batch.expirationDate || '')}" required data-batch-expiration="${batch.id}" aria-label="Expiry date for batch ${escapeBatchText(batch.batchNumber)}">
                    <button class="btn-restock" type="button" data-save-expiration="${batch.id}" data-batch-id="${batch.id}">Save</button>
                    <span class="batch-expiry-message" role="status" aria-live="polite"></span>
                </div>
            </td>
            <td>${Number(batch.initialQty)} ${escapeBatchText(unit)}</td>
            <td class="stock-qty-value">${Number(batch.remainingQty)} ${escapeBatchText(unit)}</td>
            <td><span class="stock-pill ${status.className}">${status.text}</span></td>
        `;
        tableBody.appendChild(row);
    });
}

function setBatchPageMessage(message, isError = false) {
    const element = document.getElementById('batch-page-message');
    element.textContent = message;
    element.classList.toggle('error', isError);
}

async function loadProductBatches() {
    const productId = new URLSearchParams(window.location.search).get('id');
    if (!productId || !/^\d+$/.test(productId)) {
        setBatchPageMessage('A valid product was not specified.', true);
        document.getElementById('product-summary').textContent = 'Product not found.';
        document.getElementById('batch-table-body').innerHTML = '<tr><td colspan="8">Product not found.</td></tr>';
        document.getElementById('receive-batch-form').hidden = true;
        return null;
    }

    try {
        const response = await fetch(`/api/admin/products/${encodeURIComponent(productId)}`);
        if (response.status === 401 || response.status === 403) {
            window.location.replace('../login/login.html');
            return null;
        }
        const product = await response.json();
        if (!response.ok) throw new Error(product.error || `Product request failed: ${response.status}`);
        renderProductDetails(product);
        renderBatches(product.batches || [], product.unit);
        return productId;
    } catch (error) {
        setBatchPageMessage(error.message || 'Unable to load product batches.', true);
        document.getElementById('product-summary').textContent = 'Product details are temporarily unavailable.';
        document.getElementById('batch-table-body').innerHTML = '<tr><td colspan="8">Unable to load batches.</td></tr>';
        console.error('Unable to load product batches:', error);
        return null;
    }
}

document.addEventListener('DOMContentLoaded', async () => {
    const productId = await loadProductBatches();
    if (!productId) return;

    document.getElementById('product-sku-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const response = await fetch(`/api/admin/products/${encodeURIComponent(productId)}/sku`, {
                method: 'PUT',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ sku: document.getElementById('product-sku-value').value })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `SKU update failed: ${response.status}`);
            await loadProductBatches();
            setBatchPageMessage(result.message);
        } catch (error) {
            setBatchPageMessage(error.message || 'Unable to update the product SKU.', true);
            console.error('Unable to update product SKU:', error);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('product-alias-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const response = await fetch(`/api/admin/products/${encodeURIComponent(productId)}/aliases`, {
                method: 'POST',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ alias: document.getElementById('product-alias-value').value })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Alternate name update failed: ${response.status}`);
            form.reset();
            await loadProductBatches();
            setBatchPageMessage(result.message);
        } catch (error) {
            setBatchPageMessage(error.message || 'Unable to add the alternate name.', true);
            console.error('Unable to add product alias:', error);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('batch-table-body').addEventListener('click', async event => {
        const button = event.target.closest('[data-save-expiration]');
        if (!button) return;
        const row = button.closest('tr');
        const input = row.querySelector('[data-batch-expiration]');
        const message = row.querySelector('.batch-expiry-message');
        if (!input.reportValidity()) return;

        button.disabled = true;
        message.textContent = '';
        try {
            const response = await fetch(
                `/api/admin/products/${encodeURIComponent(productId)}/batches/${encodeURIComponent(button.dataset.batchId)}/expiration`,
                {
                    method: 'PUT',
                    headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                    body: JSON.stringify({ expirationDate: input.value })
                }
            );
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Expiry update failed: ${response.status}`);
            await loadProductBatches();
            setBatchPageMessage(result.message);
        } catch (error) {
            message.textContent = error.message || 'Unable to update expiry date.';
            console.error('Unable to update batch expiry:', error);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('receive-batch-form').addEventListener('submit', async event => {
        event.preventDefault();
        const form = event.currentTarget;
        const button = document.getElementById('receive-batch-button');
        const formData = new FormData(form);
        button.disabled = true;
        setBatchPageMessage('');

        try {
            const response = await fetch(`/api/admin/products/${encodeURIComponent(productId)}/batches`, {
                method: 'POST',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    batchNumber: formData.get('batchNumber'),
                    supplierName: formData.get('supplierName'),
                    quantity: Number(formData.get('quantity')),
                    expirationDate: formData.get('expirationDate')
                })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Batch receipt failed: ${response.status}`);

            form.reset();
            await loadProductBatches();
            setBatchPageMessage(result.message);
        } catch (error) {
            setBatchPageMessage(error.message || 'Unable to receive the new batch.', true);
            console.error('Unable to receive product batch:', error);
        } finally {
            button.disabled = false;
        }
    });
});
