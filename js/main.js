// --- CARNI-FLOW GUEST CART SYSTEM ---

// 1. Load cart from browser storage (no account needed)
let cart = JSON.parse(localStorage.getItem('carni_guest_cart')) || [];

// 2. Add an item to the guest cart
function addToCart(name, price) {
    const existingItem = cart.find(item => item.name === name);

    if (existingItem) {
        existingItem.qty += 1;
    } else {
        cart.push({ name: name, price: price, qty: 1 });
    }

    saveCart();
    updateCartBadge();
    alert(`${name} added to your order!`);
}

// 3. Save cart to localStorage
function saveCart() {
    localStorage.setItem('carni_guest_cart', JSON.stringify(cart));
}

// 4. Update the navbar cart counter badge
function updateCartBadge() {
    const badge = document.getElementById('cart-count');
    if (badge) {
        const totalCount = cart.reduce((sum, item) => sum + item.qty, 0);
        badge.textContent = totalCount;
    }
}

// 5. Render cart items on order.html
function renderOrderSummary() {
    const summaryContainer = document.getElementById('cart-items-container');
    const totalPriceElem = document.getElementById('cart-total-price');

    if (!summaryContainer || !totalPriceElem) return;

    summaryContainer.innerHTML = '';

    if (cart.length === 0) {
        summaryContainer.innerHTML = '<p class="empty-cart-msg">Your basket is empty. Browse the catalog to add fresh meat!</p>';
        totalPriceElem.textContent = '₱0.00';
        return;
    }

    let total = 0;

    cart.forEach((item, index) => {
        const itemSubtotal = item.price * item.qty;
        total += itemSubtotal;

        const row = document.createElement('div');
        row.className = 'summary-row';
        row.innerHTML = `
      <div class="summary-item-info">
        <span class="summary-item-name">${item.name}</span>
        <span class="summary-item-rate">₱${item.price.toFixed(2)} each</span>
      </div>
      <div class="summary-qty-controls">
        <button class="btn-qty" type="button" onclick="changeQty(${index}, -1)">-</button>
        <span class="summary-qty">${item.qty}</span>
        <button class="btn-qty" type="button" onclick="changeQty(${index}, 1)">+</button>
      </div>
      <span class="summary-subtotal">₱${itemSubtotal.toFixed(2)}</span>
    `;
        summaryContainer.appendChild(row);
    });

    totalPriceElem.textContent = `₱${total.toFixed(2)}`;
}

// 6. Change quantity (+ / -) in order.html
function changeQty(index, change) {
    cart[index].qty += change;
    if (cart[index].qty <= 0) {
        cart.splice(index, 1);
    }
    saveCart();
    renderOrderSummary();
    updateCartBadge();
}

// 7. Handle Guest Form Submission
function handleGuestCheckout(event) {
    event.preventDefault();

    if (cart.length === 0) {
        alert('Please add at least one meat item to your basket before checking out.');
        return;
    }

    // Generate a random Order Reference Number (e.g., CF-9281)
    const orderRef = 'CF-' + Math.floor(1000 + Math.random() * 9000);

    const guestName = document.getElementById('guestName').value;
    const guestPhone = document.getElementById('guestPhone').value;
    const fulfillmentType = document.getElementById('fulfillmentType').value;
    const paymentMethod = document.getElementById('paymentMethod').value;

    // Display receipt confirmation card
    const checkoutCard = document.getElementById('checkout-card');
    const confirmationCard = document.getElementById('confirmation-card');

    if (checkoutCard && confirmationCard) {
        document.getElementById('conf-ref').textContent = orderRef;
        document.getElementById('conf-name').textContent = guestName;
        document.getElementById('conf-phone').textContent = guestPhone;
        document.getElementById('conf-fulfillment').textContent = fulfillmentType.toUpperCase();
        document.getElementById('conf-payment').textContent = paymentMethod.toUpperCase();
        document.getElementById('conf-total').textContent = document.getElementById('cart-total-price').textContent;

        checkoutCard.classList.add('hidden');
        confirmationCard.classList.remove('hidden');

        // Clear the cart after placing order
        cart = [];
        saveCart();
        updateCartBadge();
    }
}

// Initialize badge count when page loads
document.addEventListener('DOMContentLoaded', () => {
    updateCartBadge();
    renderOrderSummary();
});