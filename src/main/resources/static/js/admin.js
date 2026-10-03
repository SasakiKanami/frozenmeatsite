let inventoryProducts = [];
let posCart = [];

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

async function toggleProductVisibility(button) {
    const productId = button.dataset.productId;
    const visible = button.dataset.productVisibility === 'show';
    button.disabled = true;
    setStockAdjustmentMessage('');

    try {
        const response = await fetch(`/api/products/${encodeURIComponent(productId)}/visibility`, {
            method: 'PUT',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ visible })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Visibility update failed: ${response.status}`);

        await loadInventory();
        setStockAdjustmentMessage(result.message);
    } catch (error) {
        setStockAdjustmentMessage(error.message || 'Unable to update storefront visibility.', true);
        console.error('Unable to update product storefront visibility:', error);
    } finally {
        button.disabled = false;
    }
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
            <td class="cell-product-name">
                <a class="inventory-product-link" href="product_batches.html?id=${encodeURIComponent(product.productId)}">${escapeHtml(product.name)}</a>
                <a class="inventory-manage-link" href="product_batches.html?id=${encodeURIComponent(product.productId)}">Manage batches</a>
            </td>
            <td>₱${Number(product.pricePerUnit).toFixed(2)} / ${product.unit}</td>
            <td><span class="stock-pill ${status.className}">${status.text}</span></td>
            <td class="stock-qty-value">${quantity} ${product.unit}</td>
            <td>
                <button class="btn-restock" type="button" data-product-visibility="${product.visible ? 'hide' : 'show'}" data-product-id="${escapeHtml(product.productId)}">
                    ${product.visible ? 'Hide item' : 'Show item'}
                </button>
            </td>
        `;
        tableBody.appendChild(row);
    });

    if (visibleProducts.length === 0) {
        const row = document.createElement('tr');
        const message = document.createElement('td');
        message.colSpan = 5;
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
        const response = await fetch('/api/admin/products');
        if (!response.ok) throw new Error(`Inventory request failed: ${response.status}`);
        inventoryProducts = await response.json();
        renderInventory(inventoryProducts);
        renderPosProductOptions();
    } catch (error) {
        if (tableBody) {
            tableBody.innerHTML = '<tr><td colspan="5">Inventory is temporarily unavailable.</td></tr>';
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
    const orderStatus = String(order.orderStatus || 'pending').toLowerCase();
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
        <td>${escapeHtml(order.paymentMethod)}${order.orderSource === 'walk_in' ? '<div class="order-items-list">Walk-in POS</div>' : ''}</td>
        <td>
            <select class="payment-status-select" data-payment-status data-order-id="${escapeHtml(order.id)}" data-current-status="${escapeHtml(paymentStatus)}" aria-label="Payment status for ${escapeHtml(order.referenceId)}" ${orderStatus === 'cancelled' ? 'disabled' : ''}>
                ${legacyStatusOption}
                <option value="unpaid" ${paymentStatus === 'unpaid' ? 'selected' : ''}>Unpaid</option>
                <option value="paid" ${paymentStatus === 'paid' ? 'selected' : ''}>Paid</option>
            </select>
            <div class="payment-status-message" role="status" aria-live="polite"></div>
        </td>
        <td class="stock-qty-value">₱${Number(order.totalAmount).toFixed(2)}</td>
        <td>
            <strong>${escapeHtml(orderStatus)}</strong>
            <div class="order-action-buttons">
                ${orderStatus === 'pending' ? `<button type="button" data-order-action="complete" data-order-id="${escapeHtml(order.id)}">Complete</button><button type="button" data-order-action="cancel" data-order-id="${escapeHtml(order.id)}">Cancel</button>` : ''}
                ${orderStatus === 'completed' ? `<button type="button" data-order-action="archive" data-order-id="${escapeHtml(order.id)}">Archive</button>` : ''}
            </div>
            <div class="order-action-message" role="status" aria-live="polite"></div>
        </td>
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
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
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

async function updateOrderAction(button) {
    const action = button.dataset.orderAction;
    const orderId = button.dataset.orderId;
    if (action === 'archive' && !window.confirm('Archive this completed order? Its records will be retained for audit.')) return;
    const cell = button.closest('td');
    const message = cell.querySelector('.order-action-message');
    button.disabled = true;
    message.textContent = '';
    message.classList.remove('error');
    try {
        const response = await fetch(`/api/orders/${encodeURIComponent(orderId)}/${action === 'archive' ? 'archive' : 'status'}`, {
            method: 'PUT',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: action === 'archive' ? '{}' : JSON.stringify({ orderStatus: action === 'complete' ? 'completed' : 'cancelled' })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `Order update failed: ${response.status}`);
        await loadOrders();
        if (action === 'cancel') await loadInventory();
        setStockAdjustmentMessage(result.message);
    } catch (error) {
        message.textContent = error.message || 'Unable to update the order.';
        message.classList.add('error');
        console.error('Unable to update order:', error);
    } finally {
        button.disabled = false;
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
    const revenue = todaysOrders
        .filter(order => order.paymentStatus === 'paid')
        .reduce((sum, order) => sum + Number(order.totalAmount), 0);
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
            if (tableBody) tableBody.innerHTML = '<tr><td colspan="9">Orders are temporarily unavailable.</td></tr>';
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

function renderPosProductOptions() {
    const select = document.getElementById('pos-product');
    if (!select) return;
    const selectedId = select.value;
    select.replaceChildren();
    inventoryProducts.forEach(product => {
        const option = document.createElement('option');
        option.value = product.productId;
        option.textContent = `${product.name} — ₱${Number(product.pricePerUnit).toFixed(2)} / ${product.unit} (${product.inStockQty} available)`;
        option.disabled = Number(product.inStockQty) <= 0;
        select.appendChild(option);
    });
    if (inventoryProducts.some(product => String(product.productId) === selectedId)) select.value = selectedId;
}

function renderPosCart() {
    const body = document.getElementById('pos-cart-body');
    if (!body) return;
    body.replaceChildren();
    posCart.forEach((item, index) => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${escapeHtml(item.name)}</td>
            <td>${item.quantity} ${escapeHtml(item.unit)}</td>
            <td>₱${item.price.toFixed(2)}</td>
            <td>₱${(item.quantity * item.price).toFixed(2)}</td>
            <td><button type="button" data-pos-remove="${index}">Remove</button></td>
        `;
        body.appendChild(row);
    });
}

function setPosMessage(message, isError = false) {
    const element = document.getElementById('pos-message');
    if (!element) return;
    element.textContent = message;
    element.classList.toggle('error', isError);
}

function addPosItem() {
    const product = inventoryProducts.find(item =>
        String(item.productId) === document.getElementById('pos-product').value);
    const quantity = Number(document.getElementById('pos-quantity').value);
    const fractionalCents = Number.isFinite(quantity) ? Math.abs(quantity * 100 - Math.round(quantity * 100)) : 0;
    if (!product || !Number.isFinite(quantity) || quantity <= 0 || fractionalCents > 0.0000001) {
        setPosMessage('Choose a product and enter a positive quantity with up to two decimal places.', true);
        return;
    }
    const current = posCart.find(item => item.productId === product.productId);
    if ((current ? current.quantity : 0) + quantity > Number(product.inStockQty)) {
        setPosMessage(`Only ${product.inStockQty} ${product.unit} are currently available for ${product.name}.`, true);
        return;
    }
    if (current) current.quantity += quantity;
    else posCart.push({
        productId: product.productId,
        name: product.name,
        unit: product.unit,
        price: Number(product.pricePerUnit),
        quantity
    });
    setPosMessage('');
    renderPosCart();
}

async function submitPosOrder(event) {
    event.preventDefault();
    if (posCart.length === 0) {
        setPosMessage('Add at least one product before creating an order.', true);
        return;
    }
    const form = event.currentTarget;
    const submitButton = form.querySelector('button[type="submit"]');
    submitButton.disabled = true;
    setPosMessage('');
    try {
        const response = await fetch('/api/admin/orders', {
            method: 'POST',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({
                order: {
                    customerName: document.getElementById('pos-customer-name').value.trim(),
                    customerContact: document.getElementById('pos-customer-contact').value.trim(),
                    paymentMethod: document.getElementById('pos-payment-method').value
                },
                items: posCart.map(item => ({ productId: item.productId, quantity: item.quantity }))
            })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.error || `POS order failed: ${response.status}`);
        posCart = [];
        document.getElementById('pos-customer-name').value = 'Walk-in Customer';
        document.getElementById('pos-customer-contact').value = '';
        renderPosCart();
        setPosMessage(`Walk-in order ${result.referenceId} created for ₱${Number(result.totalAmount).toFixed(2)}. Mark its payment status in the order list after collecting payment.`);
        await Promise.all([loadOrders(), loadInventory()]);
    } catch (error) {
        setPosMessage(error.message || 'Unable to create the walk-in order.', true);
        console.error('Unable to create walk-in order:', error);
    } finally {
        submitButton.disabled = false;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    updateAdminClock();
    setInterval(updateAdminClock, 1000);
    document.getElementById('inventory-table-body')?.addEventListener('click', event => {
        const button = event.target.closest('[data-product-visibility]');
        if (!button) return;
        toggleProductVisibility(button);
    });
    document.getElementById('inventory-search')?.addEventListener('input', () => {
        renderInventory(inventoryProducts);
    });
    ['orders-table-body', 'account-orders-table-body'].forEach(bodyId => {
        const body = document.getElementById(bodyId);
        body?.addEventListener('change', event => {
            const select = event.target.closest('[data-payment-status]');
            if (select) updatePaymentStatus(select);
        });
        body?.addEventListener('click', event => {
            const button = event.target.closest('[data-order-action]');
            if (button) updateOrderAction(button);
        });
    });
    document.getElementById('pos-add-item')?.addEventListener('click', addPosItem);
    document.getElementById('pos-cart-body')?.addEventListener('click', event => {
        const button = event.target.closest('[data-pos-remove]');
        if (!button) return;
        posCart.splice(Number(button.dataset.posRemove), 1);
        renderPosCart();
    });
    document.getElementById('pos-form')?.addEventListener('submit', submitPosOrder);
    loadInventory();
    loadOrders();
    // Pick up new orders (and the stock they deduct) without a manual page reload.
    setInterval(() => { loadInventory(); loadOrders(); }, 15000);
});
