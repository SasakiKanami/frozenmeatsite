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
            <td class="cell-product-name">${product.name}</td>
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

function switchTab(tabName) {
    document.querySelectorAll('.admin-tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.admin-tab-panel').forEach(panel => panel.classList.remove('active'));

    document.getElementById(`tab-btn-${tabName}`)?.classList.add('active');
    document.getElementById(`tab-panel-${tabName}`)?.classList.add('active');
}

function resetDemoData() {
    loadInventory();
}

document.addEventListener('DOMContentLoaded', () => {
    updateAdminClock();
    setInterval(updateAdminClock, 1000);
    loadInventory();
});
