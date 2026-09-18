// --- CARNI-FLOW ADMIN CONTROL PANEL ---

// 1. Default catalog snapshot, used only the very first time the Admin Panel
//    is opened (no 'carni_inventory' record yet in browser storage)
const DEFAULT_INVENTORY = [
    { id: 1, name: 'Pork Liempo Cut', category: 'Pork Belly • Batch Verified', tier: 'fresh-chilled', price: 250.00, unit: 'kg', stock: 42, threshold: 15 },
    { id: 2, name: 'Pork Chop Cut', category: 'Pork Loin • Batch Verified', tier: 'fresh-chilled', price: 220.00, unit: 'kg', stock: 12, threshold: 15 },
    { id: 3, name: 'Chicken Breast Fillet', category: 'Poultry • Lean Cut', tier: 'fresh-chilled', price: 210.00, unit: 'kg', stock: 30, threshold: 10 },
    { id: 4, name: 'Beef Bulalo Shank', category: 'Beef Cuts • Bone-in Marrow', tier: 'deep-freeze', price: 380.00, unit: 'kg', stock: 8, threshold: 10 },
    { id: 5, name: 'Tender Juicy Hotdog', category: '1kg Classic Jumbo Sealed', tier: 'processed', price: 200.00, unit: 'pack', stock: 25, threshold: 8 },
    { id: 6, name: 'Young Pork Tocino', category: 'Sweet Cured Pork Pack', tier: 'processed', price: 75.00, unit: 'pack', stock: 0, threshold: 8 }
];

let inventory = [];
let orders = [];

// 2. Load inventory from storage, seeding defaults on first run
function loadInventory() {
    const stored = JSON.parse(localStorage.getItem('carni_inventory'));
    inventory = stored || DEFAULT_INVENTORY;
    saveInventory();
}

function saveInventory() {
    localStorage.setItem('carni_inventory', JSON.stringify(inventory));
}

// 3. Load the order log written by the storefront's checkout (main.js)
function loadOrders() {
    orders = JSON.parse(localStorage.getItem('carni_orders')) || [];
}

// 4. Work out a stock item's status against its low-stock threshold
function getStockStatus(item) {
    if (item.stock <= 0) return 'out-stock';
    if (item.stock <= item.threshold) return 'low-stock';
    return 'in-stock';
}

function getStockLabel(status) {
    if (status === 'out-stock') return 'Out of Stock';
    if (status === 'low-stock') return 'Low Stock';
    return 'In Stock';
}

// 5. Render the top stat cards (today's revenue, orders today, low stock, catalog size)
function renderStats() {
    const today = new Date().toDateString();

    const ordersToday = orders.filter(o => new Date(o.placedAt).toDateString() === today);
    const revenueToday = ordersToday.reduce((sum, o) => {
        const numeric = parseFloat(o.total.replace(/[^0-9.]/g, '')) || 0;
        return sum + numeric;
    }, 0);

    const lowStockItems = inventory.filter(item => getStockStatus(item) !== 'in-stock');

    document.getElementById('stat-revenue').textContent = `₱${revenueToday.toFixed(2)}`;
    document.getElementById('stat-orders').textContent = ordersToday.length;
    document.getElementById('stat-lowstock').textContent = lowStockItems.length;
    document.getElementById('stat-catalog').textContent = inventory.length;

    renderLowStockBanner(lowStockItems);
}

// 6. Render (or hide) the low-stock alert banner
function renderLowStockBanner(lowStockItems) {
    const banner = document.getElementById('low-stock-banner');
    const list = document.getElementById('low-stock-list');

    if (lowStockItems.length === 0) {
        banner.classList.add('hidden');
        return;
    }

    banner.classList.remove('hidden');
    list.innerHTML = lowStockItems.map(item => {
        const label = item.stock <= 0 ? 'Out of Stock' : `${item.stock} ${item.unit} left`;
        return `<span class="low-stock-chip">❄️ ${item.name} — ${label}</span>`;
    }).join('');
}

// 7. Render the inventory management table
function renderInventoryTable() {
    const body = document.getElementById('inventory-table-body');
    body.innerHTML = '';

    inventory.forEach(item => {
        const status = getStockStatus(item);
        const row = document.createElement('tr');
        row.innerHTML = `
      <td>
        <span class="cell-product-name">${item.name}</span><br>
        <span class="cell-product-cat">${item.category}</span>
      </td>
      <td>₱${item.price.toFixed(2)} / ${item.unit}</td>
      <td><span class="stock-pill ${status}">${getStockLabel(status)}</span></td>
      <td>
        <div class="stock-adjust">
          <button class="btn-qty" type="button" onclick="adjustStock(${item.id}, -1)">-</button>
          <span class="stock-qty-value">${item.stock} ${item.unit}</span>
          <button class="btn-qty" type="button" onclick="adjustStock(${item.id}, 1)">+</button>
        </div>
      </td>
      <td><button class="btn-restock" type="button" onclick="restockItem(${item.id})">+10 Restock</button></td>
    `;
        body.appendChild(row);
    });
}

// 8. Adjust stock by +/- 1 unit (e.g. correcting a manual count)
function adjustStock(id, change) {
    const item = inventory.find(i => i.id === id);
    if (!item) return;
    item.stock = Math.max(0, item.stock + change);
    saveInventory();
    renderInventoryTable();
    renderStats();
}

// 9. Simulate receiving a fresh batch delivery (+10 units at once)
function restockItem(id) {
    const item = inventory.find(i => i.id === id);
    if (!item) return;
    item.stock += 10;
    saveInventory();
    renderInventoryTable();
    renderStats();
}

// 10. Render the guest order log placed from order.html
function renderOrdersTable() {
    const body = document.getElementById('orders-table-body');
    const emptyMsg = document.getElementById('orders-empty-msg');

    body.innerHTML = '';

    if (orders.length === 0) {
        emptyMsg.classList.remove('hidden');
        return;
    }

    emptyMsg.classList.add('hidden');

    orders.forEach(order => {
        const itemsText = order.items.map(i => `${i.qty}× ${i.name}`).join(', ');
        const placed = new Date(order.placedAt);
        const row = document.createElement('tr');
        row.innerHTML = `
      <td><span class="ref-pill">#${order.ref}</span></td>
      <td>${placed.toLocaleDateString()} <br><span class="cell-product-cat">${placed.toLocaleTimeString()}</span></td>
      <td>${order.name}<br><span class="cell-product-cat">${order.phone}</span></td>
      <td class="order-items-list">${itemsText}</td>
      <td>${order.fulfillment}</td>
      <td>${order.payment}</td>
      <td><strong>${order.total}</strong></td>
    `;
        body.appendChild(row);
    });
}

// 11. Switch between the Inventory and Orders tabs
function switchTab(tabName) {
    document.querySelectorAll('.admin-tab').forEach(tab => tab.classList.remove('active'));
    document.querySelectorAll('.admin-tab-panel').forEach(panel => panel.classList.remove('active'));

    document.getElementById(`tab-btn-${tabName}`).classList.add('active');
    document.getElementById(`tab-panel-${tabName}`).classList.add('active');
}

// 12. Reset demo data (clears simulated orders + restores default stock)
function resetDemoData() {
    if (!confirm('Reset all Admin Panel demo data? This clears the simulated order log and restores default stock levels.')) {
        return;
    }
    localStorage.removeItem('carni_orders');
    localStorage.removeItem('carni_inventory');
    loadInventory();
    loadOrders();
    renderStats();
    renderInventoryTable();
    renderOrdersTable();
}

// 13. Live clock in the header, just for that "control room" feel
function tickClock() {
    const now = new Date();
    document.getElementById('admin-clock-time').textContent = now.toLocaleTimeString();
    document.getElementById('admin-clock-date').textContent = now.toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

// Initialize the Admin Panel on page load
document.addEventListener('DOMContentLoaded', () => {
    loadInventory();
    loadOrders();
    renderStats();
    renderInventoryTable();
    renderOrdersTable();
    tickClock();
    setInterval(tickClock, 1000);
});
