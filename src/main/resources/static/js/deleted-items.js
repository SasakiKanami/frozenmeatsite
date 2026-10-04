function escapeDeletedItemText(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
}

function formatProductDeletedAt(value) {
    if (!value) return 'Not recorded';
    const date = Array.isArray(value)
        ? new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0)
        : new Date(value);
    return Number.isNaN(date.getTime())
        ? 'Not recorded'
        : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function setDeletedItemsMessage(message, isError = false) {
    const element = document.getElementById('deleted-items-message');
    element.textContent = message;
    element.classList.toggle('error', isError);
}

function renderDeletedItems(products) {
    const tableBody = document.getElementById('deleted-items-table-body');
    const emptyMessage = document.getElementById('deleted-items-empty');
    tableBody.replaceChildren();
    emptyMessage.classList.toggle('hidden', products.length > 0);

    products.forEach(product => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeDeletedItemText(product.sku)}</td>
            <td class="cell-product-name">${escapeDeletedItemText(product.name)}</td>
            <td>${escapeDeletedItemText(product.category)}</td>
            <td>₱${Number(product.pricePerUnit).toFixed(2)} / ${escapeDeletedItemText(product.unit)}</td>
            <td>${formatProductDeletedAt(product.deletedAt)}</td>
            <td>
                <div class="trash-actions">
                    <button class="btn-restock" type="button" data-deleted-product-action="restore" data-product-id="${escapeDeletedItemText(product.productId)}">Restore</button>
                    <button class="trash-action-button" type="button" data-deleted-product-action="permanent" data-product-id="${escapeDeletedItemText(product.productId)}">Permanently Delete</button>
                </div>
            </td>
        `;
        tableBody.appendChild(row);
    });
}

async function loadDeletedItems() {
    const tableBody = document.getElementById('deleted-items-table-body');
    try {
        const response = await fetch('/api/admin/deleted-products');
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Deleted items request failed: ${response.status}`);
        renderDeletedItems(result);
    } catch (error) {
        tableBody.innerHTML = '<tr><td colspan="5">Deleted items are temporarily unavailable.</td></tr>';
        setDeletedItemsMessage(error.message || 'Unable to load deleted items.', true);
        console.error('Unable to load deleted items:', error);
    }
}

async function updateDeletedProduct(button) {
    const action = button.dataset.deletedProductAction;
    const productId = button.dataset.productId;
    const isPermanent = action === 'permanent';
    if (isPermanent && !window.confirm(
        'Permanently delete this product from the catalog? Past order details and its inventory batch/audit history will be kept. This cannot be undone.'
    )) {
        return;
    }

    button.disabled = true;
    setDeletedItemsMessage('');
    try {
        const response = await fetch(
            `/api/admin/products/${encodeURIComponent(productId)}${isPermanent ? '/permanent' : '/restore'}`,
            {
                method: isPermanent ? 'DELETE' : 'POST',
                headers: csrfHeaders()
            }
        );
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Product action failed: ${response.status}`);
        setDeletedItemsMessage(result.message);
        await loadDeletedItems();
    } catch (error) {
        setDeletedItemsMessage(error.message || 'Unable to update the deleted product.', true);
        console.error('Unable to update deleted product:', error);
    } finally {
        button.disabled = false;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    const tableBody = document.getElementById('deleted-items-table-body');
    tableBody.addEventListener('click', event => {
        const target = event.target;
        if (!(target instanceof Element)) return;
        const button = target.closest('[data-deleted-product-action]');
        if (button) updateDeletedProduct(button);
    });
    loadDeletedItems();
});
