function escapeOrderText(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
}

function formatOrderDate(value) {
    const date = Array.isArray(value)
        ? new Date(value[0], value[1] - 1, value[2], value[3] || 0, value[4] || 0, value[5] || 0)
        : new Date(value);
    return Number.isNaN(date.getTime())
        ? '—'
        : date.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

function renderCustomerOrders(orders) {
    const tableBody = document.getElementById('customer-orders-body');
    const message = document.getElementById('orders-message');
    tableBody.replaceChildren();

    if (!orders.length) {
        message.textContent = 'You have no orders yet.';
        return;
    }

    message.textContent = `${orders.length} ${orders.length === 1 ? 'order' : 'orders'} in your history.`;
    orders.forEach(order => {
        const row = document.createElement('tr');
        const items = (order.items || []).map(item =>
            `${escapeOrderText(item.productName)} × ${Number(item.quantity)}${item.unit ? ` ${escapeOrderText(item.unit)}` : ''}`
        ).join('<br>');
        row.innerHTML = `
            <td class="ref-pill">${escapeOrderText(order.referenceId)}</td>
            <td>${formatOrderDate(order.createdAt)}</td>
            <td class="order-items-list">${items || '—'}</td>
            <td><strong>${escapeOrderText(order.fulfillmentMethod)}</strong>${order.deliveryAddress ? `<div class="order-items-list">${escapeOrderText(order.deliveryAddress)}</div>` : ''}</td>
            <td>${escapeOrderText(order.orderStatus)}</td>
            <td>${escapeOrderText(order.paymentMethod)}<div class="order-items-list">${escapeOrderText(order.paymentStatus)}</div></td>
            <td class="stock-qty-value">₱${Number(order.totalAmount).toFixed(2)}</td>
        `;
        tableBody.appendChild(row);
    });
}

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await fetch('/api/account/orders');
        if (response.status === 401 || response.status === 403) {
            window.location.replace('../login/login.html');
            return;
        }
        if (!response.ok) throw new Error(`Order request failed: ${response.status}`);
        renderCustomerOrders(await response.json());
    } catch (error) {
        document.getElementById('orders-message').textContent = 'Unable to load your orders. Please refresh and try again.';
        console.error('Unable to load customer pending orders:', error);
    }
});
