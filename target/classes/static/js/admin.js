let inventoryProducts = [];

function updateAdminClock() {
    const now = new Date();
    const time = now.toLocaleTimeString('en-US', {
        hour12: true,
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
    const date = now.toLocaleDateString('en-US', {
        weekday: 'long',
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });

    const timeElement = document.getElementById('admin-clock-time');
    const dateElement = document.getElementById('admin-clock-date');
    if (timeElement) timeElement.textContent = time;
    if (dateElement) dateElement.textContent = date;
}

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
}

function getStockStatus(quantity, reorderLevel) {
    if (quantity <= 0) return { text: 'OUT OF STOCK', className: 'out-stock' };
    if (quantity <= reorderLevel) return { text: 'LOW STOCK', className: 'low-stock' };
    return { text: 'IN STOCK', className: 'in-stock' };
}

function renderInventory(products) {
    const tableBody = document.getElementById('inventory-table-body');
    if (!tableBody) return;

    tableBody.innerHTML = '';
    let lowStockCount = 0;
    const lowStockProducts = [];
    const searchTerm = document.getElementById('inventory-search')?.value.trim().toLocaleLowerCase() || '';
    const visibleProducts = products.filter(product =>
        [product.name, product.category, product.temperatureTier]
            .some(value => String(value ?? '').toLocaleLowerCase().includes(searchTerm))
    );

    products.forEach(product => {
        const quantity = Number(product.inStockQty);
        const reorderLevel = Number(product.reorderLevel ?? 5);
        const status = getStockStatus(quantity, reorderLevel);
        if (status.className !== 'in-stock') {
            lowStockCount += 1;
            lowStockProducts.push(`${product.name} (${quantity} ${product.unit} left)`);
        }
    });

    visibleProducts.forEach(product => {
        const quantity = Number(product.inStockQty);
        const status = getStockStatus(quantity, Number(product.reorderLevel ?? 5));
        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="cell-product-name">${escapeHtml(product.name)}</td>
            <td>₱${Number(product.pricePerUnit).toFixed(2)} / ${product.unit}</td>
            <td><span class="stock-pill ${status.className}">${status.text}</span></td>
            <td class="stock-qty-value">${quantity} ${product.unit}</td>
            <td>
                <div class="stock-adjust">
                    <button class="btn-restock" type="button" data-stock-adjustment="-1" data-product-id="${escapeHtml(product.productId)}" aria-label="Remove 1 ${escapeHtml(product.unit)} of ${escapeHtml(product.name)}">-1</button>
                    <button class="btn-restock" type="button" data-stock-adjustment="1" data-product-id="${escapeHtml(product.productId)}" aria-label="Add 1 ${escapeHtml(product.unit)} of ${escapeHtml(product.name)}">+1</button>
                </div>
            </td>
            <td>
                <div class="stock-adjust">
                    <button class="btn-restock" type="button" data-stock-adjustment="-10" data-product-id="${escapeHtml(product.productId)}" aria-label="Remove 10 ${escapeHtml(product.unit)} of ${escapeHtml(product.name)}">-10</button>
                    <button class="btn-restock" type="button" data-stock-adjustment="10" data-product-id="${escapeHtml(product.productId)}" aria-label="Add 10 ${escapeHtml(product.unit)} of ${escapeHtml(product.name)}">+10</button>
                </div>
            </td>
            <td>
                <div class="stock-adjust stock-custom-adjust">
                    <input class="stock-custom-input" type="number" min="${product.unit === 'kg' ? '0.01' : '1'}" step="${product.unit === 'kg' ? '0.01' : '1'}" required data-custom-stock-amount="${escapeHtml(product.productId)}" aria-label="Custom stock amount for ${escapeHtml(product.name)}">
                    <button class="btn-restock" type="button" data-custom-stock-adjustment="add" data-product-id="${escapeHtml(product.productId)}" aria-label="Add custom amount of ${escapeHtml(product.name)}">Add</button>
                    <button class="btn-restock" type="button" data-custom-stock-adjustment="remove" data-product-id="${escapeHtml(product.productId)}" aria-label="Remove custom amount of ${escapeHtml(product.name)}">Remove</button>
                </div>
            </td>
        `;
        tableBody.appendChild(row);
    });

    if (visibleProducts.length === 0) {
        const row = document.createElement('tr');
        const message = document.createElement('td');
        message.colSpan = 7;
        message.className = 'inventory-no-results';
        message.textContent = searchTerm
            ? `No inventory items match "${document.getElementById('inventory-search').value.trim()}".`
            : 'No inventory items found.';
        row.appendChild(message);
        tableBody.appendChild(row);
    }

    document.getElementById('stat-catalog').textContent = products.length;
    document.getElementById('stat-lowstock').textContent = lowStockCount;

    const lowStockBanner = document.getElementById('low-stock-banner');
    const lowStockList = document.getElementById('low-stock-list');
    if (lowStockBanner && lowStockList) {
        lowStockBanner.classList.toggle('hidden', lowStockProducts.length === 0);
        lowStockList.textContent = lowStockProducts.join(' | ');
    }
}

async function loadInventory() {
    const tableBody = document.getElementById('inventory-table-body');
    try {
        const response = await fetch('/api/products');
        if (!response.ok) throw new Error(`Inventory request failed: ${response.status}`);
        inventoryProducts = await response.json();
        renderInventory(inventoryProducts);
    } catch (error) {
        if (tableBody) {
            tableBody.innerHTML = '<tr><td colspan="7">Inventory is temporarily unavailable.</td></tr>';
        }
        console.error('Unable to load admin inventory:', error);
    }
}

function setStockAdjustmentMessage(message, isError = false) {
    const messageElement = document.getElementById('stock-adjustment-message');
    if (!messageElement) return;
    messageElement.textContent = message;
    messageElement.classList.toggle('error', isError);
}

async function adjustStock(productId, adjustment, button) {
    const row = button.closest('tr');
    const rowButtons = row?.querySelectorAll('[data-stock-adjustment], [data-custom-stock-adjustment]') ?? [];
    rowButtons.forEach(rowButton => { rowButton.disabled = true; });
    setStockAdjustmentMessage('');

    try {
        const response = await fetch(`/api/products/${encodeURIComponent(productId)}/stock-adjustments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ adjustment })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Stock adjustment failed: ${response.status}`);

        await loadInventory();
        setStockAdjustmentMessage(`Stock updated by ${adjustment > 0 ? '+' : ''}${adjustment}.`);
    } catch (error) {
        setStockAdjustmentMessage(error.message || 'Unable to update stock.', true);
        console.error('Unable to adjust admin stock:', error);
    } finally {
        rowButtons.forEach(rowButton => { rowButton.disabled = false; });
    }
}

function adjustCustomStock(button) {
    const input = button.closest('tr')?.querySelector('[data-custom-stock-amount]');
    if (!input || !input.reportValidity()) return;

    const amount = Number(input.value);
    if (!Number.isFinite(amount) || amount <= 0) {
        input.setCustomValidity('Enter an amount greater than zero.');
        input.reportValidity();
        input.setCustomValidity('');
        return;
    }

    const adjustment = button.dataset.customStockAdjustment === 'remove' ? -amount : amount;
    adjustStock(Number(button.dataset.productId), adjustment, button);
}

// ---- Orders (GET /api/orders) ----
// createdAt arrives as an ISO string (or [y,m,d,h,mi,s] array); both are read as local time.
function parseCreatedAt(createdAt) {
    if (Array.isArray(createdAt)) {
        const [year, month, day, hour = 0, minute = 0, second = 0] = createdAt;
        return new Date(year, month - 1, day, hour, minute, second);
    }
    return createdAt ? new Date(createdAt) : null;
}

function formatPlaced(createdAt) {
    const date = parseCreatedAt(createdAt);
    if (!date || isNaN(date)) return '—';
    return date.toLocaleString('en-US', { month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit' });
}

function buildOrderRow(order) {
    const items = (order.items || []).map(item =>
        `${escapeHtml(item.productName)} × ${Number(item.quantity)}${item.unit ? ' ' + escapeHtml(item.unit) : ''}`
    ).join('<br>');

    const customerLines = [escapeHtml(order.customerContact)];
    if (order.customerEmail) customerLines.push(escapeHtml(order.customerEmail));
    if (order.accountUsername) customerLines.push(`Account: ${escapeHtml(order.accountUsername)}`);

    let fulfillment = `<strong>${escapeHtml(order.fulfillmentMethod)}</strong>`;
    if (order.deliveryAddress) fulfillment += `<div class="order-items-list">${escapeHtml(order.deliveryAddress)}</div>`;
    if (order.deliveryNotes) fulfillment += `<div class="order-items-list">Note: ${escapeHtml(order.deliveryNotes)}</div>`;

    const originalPaymentStatus = String(order.paymentStatus || 'unpaid');
    const paymentStatus = originalPaymentStatus.toLowerCase();
    const legacyStatusOption = ['paid', 'unpaid'].includes(paymentStatus)
        ? ''
        : `<option value="${escapeHtml(paymentStatus)}" selected disabled>${escapeHtml(order.paymentStatus)}</option>`;
    const row = document.createElement('tr');
    row.innerHTML = `
        <td class="ref-pill">${escapeHtml(order.referenceId)}</td>
        <td>${formatPlaced(order.createdAt)}</td>
        <td><div class="cell-product-name">${escapeHtml(order.customerName)}</div><div class="order-items-list">${customerLines.join('<br>')}</div></td>
        <td class="order-items-list">${items || '—'}</td>
        <td>${fulfillment}</td>
        <td>${escapeHtml(order.paymentMethod)}</td>
        <td>
            <select class="payment-status-select" data-payment-status data-order-id="${escapeHtml(order.id)}" data-current-status="${escapeHtml(paymentStatus)}" aria-label="Payment status for ${escapeHtml(order.referenceId)}">
                ${legacyStatusOption}
                <option value="unpaid" ${paymentStatus === 'unpaid' ? 'selected' : ''}>Unpaid</option>
                <option value="paid" ${paymentStatus === 'paid' ? 'selected' : ''}>Paid</option>
            </select>
            <div class="payment-status-message" role="status" aria-live="polite"></div>
        </td>
        <td class="stock-qty-value">₱${Number(order.totalAmount).toFixed(2)}</td>
    `;
    return row;
}

async function updatePaymentStatus(select) {
    const previousStatus = select.dataset.currentStatus;
    const message = select.closest('td').querySelector('.payment-status-message');
    select.disabled = true;
    message.textContent = '';
    message.classList.remove('error');

    try {
        const response = await fetch(`/api/orders/${encodeURIComponent(select.dataset.orderId)}/payment-status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ paymentStatus: select.value })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Payment status update failed: ${response.status}`);
        select.dataset.currentStatus = select.value;
        message.textContent = 'Saved';
    } catch (error) {
        select.value = previousStatus;
        message.textContent = error.message || 'Unable to update payment status.';
        message.classList.add('error');
        console.error('Unable to update order payment status:', error);
    } finally {
        select.disabled = false;
    }
}

function fillOrderTable(bodyId, emptyMessageId, orders) {
    const tableBody = document.getElementById(bodyId);
    const emptyMessage = document.getElementById(emptyMessageId);
    if (!tableBody) return;

    tableBody.innerHTML = '';
    orders.forEach(order => tableBody.appendChild(buildOrderRow(order)));
    if (emptyMessage) emptyMessage.classList.toggle('hidden', orders.length > 0);
}

function renderOrders(orders) {
    // Guest checkout = no account id on the order; registered accounts carry their user id.
    fillOrderTable('orders-table-body', 'orders-empty-msg', orders.filter(order => order.customerUserId == null));
    fillOrderTable('account-orders-table-body', 'account-orders-empty-msg', orders.filter(order => order.customerUserId != null));

    const today = new Date().toDateString();
    const todaysOrders = orders.filter(order => {
        const placed = parseCreatedAt(order.createdAt);
        return placed && placed.toDateString() === today && order.orderStatus !== 'cancelled';
    });
    const revenue = todaysOrders.reduce((sum, order) => sum + Number(order.totalAmount), 0);
    document.getElementById('stat-orders').textContent = todaysOrders.length;
    document.getElementById('stat-revenue').textContent = `₱${revenue.toFixed(2)}`;
}

async function loadOrders() {
    try {
        const response = await fetch('/api/orders');
        if (!response.ok) throw new Error(`Orders request failed: ${response.status}`);
        renderOrders(await response.json());
    } catch (error) {
        ['orders-table-body', 'account-orders-table-body'].forEach(id => {
            const tableBody = document.getElementById(id);
            if (tableBody) tableBody.innerHTML = '<tr><td colspan="7">Orders are temporarily unavailable.</td></tr>';
        });
        console.error('Unable to load admin orders:', error);
    }
}

function switchTab(tabName) {
    document.querySelectorAll('.admin-tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.admin-tab-panel').forEach(panel => panel.classList.remove('active'));

    document.getElementById(`tab-btn-${tabName}`)?.classList.add('active');
    document.getElementById(`tab-panel-${tabName}`)?.classList.add('active');
}

function resetDemoData() {
    loadInventory();
    loadOrders();
}

document.addEventListener('DOMContentLoaded', () => {
    updateAdminClock();
    setInterval(updateAdminClock, 1000);
    document.getElementById('inventory-table-body')?.addEventListener('click', event => {
        const button = event.target.closest('[data-stock-adjustment], [data-custom-stock-adjustment]');
        if (!button) return;
        if (button.hasAttribute('data-custom-stock-adjustment')) {
            adjustCustomStock(button);
        } else {
            adjustStock(Number(button.dataset.productId), Number(button.dataset.stockAdjustment), button);
        }
    });
    document.getElementById('inventory-search')?.addEventListener('input', () => {
        renderInventory(inventoryProducts);
    });
    ['orders-table-body', 'account-orders-table-body'].forEach(bodyId => {
        document.getElementById(bodyId)?.addEventListener('change', event => {
            const select = event.target.closest('[data-payment-status]');
            if (select) updatePaymentStatus(select);
        });
    });
    loadInventory();
    loadOrders();
    // Pick up new orders (and the stock they deduct) without a manual page reload.
    setInterval(() => { loadInventory(); loadOrders(); }, 15000);
});
