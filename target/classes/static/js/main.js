// --- CARNI-FLOW GUEST CART SYSTEM ---

// 1. Load cart from browser storage (no account needed)
let cart = JSON.parse(localStorage.getItem('carni_guest_cart')) || [];

// 2. Add an item to the guest cart
function addToCart(name, price, productId, unit) {
    const existingItem = cart.find(item => item.name === name);

    if (existingItem) {
        existingItem.qty += 1;
    } else {
        cart.push({ name: name, price: price, productId: productId, unit: unit, qty: 1 });
    }

    saveCart();
    updateCartBadge();
    alert(`${name} added to your order!`);
}

function getBadgeClass(temperatureTier) {
    if (temperatureTier === 'Fresh Chilled') return 'badge-fresh-chilled';
    if (temperatureTier === 'Deep Freeze') return 'badge-deep-freeze';
    return 'badge-processed';
}

function getBadgeLabel(temperatureTier) {
    if (temperatureTier === 'Fresh Chilled') return '❄️ Fresh Chilled';
    if (temperatureTier === 'Deep Freeze') return '❅ Deep Freeze (-18°C)';
    return '📦 Processed Pack';
}

async function loadCatalog() {
    const productGrid = document.getElementById('product-grid');
    if (!productGrid) return;

    try {
        const response = await fetch('/api/products');
        if (!response.ok) throw new Error(`Catalog request failed: ${response.status}`);

        const products = await response.json();
        productGrid.innerHTML = '';

        products.forEach(product => {
            const card = document.createElement('article');
            card.className = 'product-card';

            const badge = document.createElement('div');
            badge.className = `badge ${getBadgeClass(product.temperatureTier)}`;
            badge.textContent = getBadgeLabel(product.temperatureTier);
            card.appendChild(badge);

            const name = document.createElement('h3');
            name.className = 'product-name';
            name.textContent = product.name;
            card.appendChild(name);

            const category = document.createElement('p');
            category.className = 'product-category';
            category.textContent = `${product.category} • ${product.inStockQty} ${product.unit} available`;
            card.appendChild(category);

            if (product.imageUrl) {
                const image = document.createElement('img');
                image.src = product.imageUrl;
                image.alt = product.name;
                card.appendChild(image);
            }

            const priceContainer = document.createElement('div');
            priceContainer.className = 'price-container';
            priceContainer.innerHTML = '<svg class="price-icon" viewBox="0 0 24 24"><path d="M12 2v20M2 12h20M4.93 4.93l14.14 14.14M4.93 19.07L19.07 4.93" /></svg>';

            const price = document.createElement('p');
            price.className = 'product-price';
            price.textContent = `₱${Number(product.pricePerUnit).toFixed(2)} / ${product.unit}`;
            priceContainer.appendChild(price);
            card.appendChild(priceContainer);

            const button = document.createElement('button');
            button.className = 'btn-card';
            button.type = 'button';
            button.textContent = product.inStockQty > 0 ? '+ Add to Order' : 'Out of Stock';
            button.disabled = product.inStockQty <= 0;
            button.addEventListener('click', () => addToCart(product.name, Number(product.pricePerUnit), product.productId, product.unit));
            card.appendChild(button);

            productGrid.appendChild(card);
        });
    } catch (error) {
        productGrid.innerHTML = '<p class="section-desc">The catalog is temporarily unavailable. Please try again.</p>';
        console.error('Unable to load catalog:', error);
    }
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
        badge.textContent = totalCount > 9 ? '9+' : totalCount;
    }
}

// 5. Render cart items on order.html
function renderOrderSummary() {
    const summaryContainer = document.getElementById('cart-items-container');
    const totalPriceElem = document.getElementById('cart-total-price');

    if (!summaryContainer || !totalPriceElem) return;

    summaryContainer.innerHTML = '';

    if (cart.length === 0) {
        summaryContainer.innerHTML = '<p class="empty-cart-msg">Your basket is empty. Browse the catalog to add stuff to your basket!</p>';
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
    loadCatalog();
});