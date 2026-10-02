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

    products.forEach(product => {
        const quantity = Number(product.inStockQty);
        const reorderLevel = Number(product.reorderLevel ?? 5);
        const status = getStockStatus(quantity, reorderLevel);
        if (status.className !== 'in-stock') {
            lowStockCount += 1;
            lowStockProducts.push(`${product.name} (${quantity} ${product.unit} left)`);
        }

        const row = document.createElement('tr');
        row.innerHTML = `
            <td class="cell-product-name">${escapeHtml(product.name)}</td>
            <td>₱${Number(product.pricePerUnit).toFixed(2)} / ${product.unit}</td>
            <td><span class="stock-pill ${status.className}">${status.text}</span></td>
            <td class="stock-qty-value">${quantity} ${product.unit}</td>
            <td>Product ID ${product.productId}</td>
        `;
        tableBody.appendChild(row);
    });

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
        renderInventory(await response.json());
    } catch (error) {
        if (tableBody) {
            tableBody.innerHTML = '<tr><td colspan="5">Inventory is temporarily unavailable.</td></tr>';
        }
        console.error('Unable to load admin inventory:', error);
    }
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

    const row = document.createElement('tr');
    row.innerHTML = `
        <td class="ref-pill">${escapeHtml(order.referenceId)}</td>
        <td>${formatPlaced(order.createdAt)}</td>
        <td><div class="cell-product-name">${escapeHtml(order.customerName)}</div><div class="order-items-list">${customerLines.join('<br>')}</div></td>
        <td class="order-items-list">${items || '—'}</td>
        <td>${fulfillment}</td>
        <td>${escapeHtml(order.paymentMethod)}<div class="order-items-list">Status: ${escapeHtml(order.paymentStatus)}</div></td>
        <td class="stock-qty-value">₱${Number(order.totalAmount).toFixed(2)}</td>
    `;
    return row;
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
    loadInventory();
    loadOrders();
    // Pick up new orders (and the stock they deduct) without a manual page reload.
    setInterval(() => { loadInventory(); loadOrders(); }, 15000);
});
