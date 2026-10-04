// --- CARNI-FLOW GUEST CART SYSTEM ---

// 1. Load cart from browser storage (no account needed)
let cart = JSON.parse(localStorage.getItem('carni_guest_cart')) || [];
let quantityProduct = null;
let savedDeliveryFee = 0;
let savedCustomerProfile = null;

function escapeHtml(value) {
    return String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
}

// Ask the server for the current session; browser storage is not trusted for identity.
async function updateAuthLink() {
    const link = document.getElementById('auth-link');
    const ordersLink = document.getElementById('my-orders-link');
    const profileLink = document.getElementById('profile-link');
    if (!link) return;

    try {
        const response = await fetch('/api/auth/me');
        if (!response.ok) throw new Error(`Session check failed: ${response.status}`);
        const session = await response.json();
        if (!session.authenticated) return;

        if (session.role === 'customer') {
            if (ordersLink) ordersLink.classList.remove('hidden');
            if (profileLink) profileLink.classList.remove('hidden');
        }
    } catch (error) {
        console.error('Unable to check signed-in customer session:', error);
        return;
    }

    link.textContent = 'Logout';
    link.href = '#';
    link.addEventListener('click', async event => {
        event.preventDefault();
        link.setAttribute('aria-disabled', 'true');
        try {
            const response = await fetch('/api/auth/logout', {
                method: 'POST',
                headers: csrfHeaders()
            });
            if (!response.ok) throw new Error(`Sign out failed: ${response.status}`);
            window.location.reload();
        } catch (error) {
            link.removeAttribute('aria-disabled');
            alert(error.message || 'Unable to sign out.');
        }
    });
}

// The backend reports shortages by product ID; show the item name instead.
function friendlyCheckoutError(message) {
    const text = message || 'We could not place your order. Please try again.';
    const match = /product ID: (\d+)/.exec(text);
    const item = match ? cart.find(entry => entry.productId === Number(match[1])) : null;
    return item ? `Not enough stock for ${item.name}. Please lower the quantity and try again.` : text;
}

// 2. Add an item to the guest cart
function addToCart(name, price, productId, unit) {
    addProductQuantity({ name, price, productId, unit, inStockQty: Number.MAX_SAFE_INTEGER }, 1);
}

function addProductQuantity(product, quantity) {
    const existingItem = cart.find(item => item.productId === product.productId);
    const currentQuantity = existingItem ? existingItem.qty : 0;
    if (currentQuantity + quantity > Number(product.inStockQty)) {
        alert(`Only ${product.inStockQty} ${product.unit} available for ${product.name}.`);
        return false;
    }

    if (existingItem) {
        existingItem.qty += quantity;
        existingItem.maxQty = Number(product.inStockQty);
    } else {
        cart.push({ name: product.name, price: Number(product.price ?? product.pricePerUnit), productId: product.productId, unit: product.unit, qty: quantity, maxQty: Number(product.inStockQty) });
    }

    saveCart();
    updateCartBadge();
    alert(`${quantity} ${product.unit} of ${product.name} added to your order!`);
    return true;
}

function openQuantityDialog(product) {
    quantityProduct = product;
    const dialog = document.getElementById('quantity-dialog');
    const input = document.getElementById('quantity-input');
    document.getElementById('quantity-product-name').textContent = product.name;
    document.getElementById('quantity-stock-message').textContent = `${product.inStockQty} ${product.unit} available`;
    input.max = Math.floor(Number(product.inStockQty));
    input.value = '1';
    dialog.showModal();
    input.focus();
}

function closeQuantityDialog() {
    document.getElementById('quantity-dialog').close();
    quantityProduct = null;
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

            const actions = document.createElement('div');
            actions.className = 'product-actions';

            const addOneButton = document.createElement('button');
            addOneButton.className = 'btn-card';
            addOneButton.type = 'button';
            addOneButton.textContent = product.inStockQty > 0 ? 'Add one' : 'Out of Stock';
            addOneButton.disabled = product.inStockQty <= 0;
            addOneButton.addEventListener('click', () => addProductQuantity(product, 1));
            actions.appendChild(addOneButton);

            const addMultipleButton = document.createElement('button');
            addMultipleButton.className = 'btn-card btn-card-secondary';
            addMultipleButton.type = 'button';
            addMultipleButton.textContent = 'Add multiple';
            addMultipleButton.disabled = product.inStockQty <= 0;
            addMultipleButton.addEventListener('click', () => openQuantityDialog(product));
            actions.appendChild(addMultipleButton);
            card.appendChild(actions);

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
    const deliverySelected = document.getElementById('fulfillmentType')?.value === 'Same-Day Delivery';
    const fee = deliverySelected ? savedDeliveryFee : 0;
    const feePreview = document.getElementById('delivery-fee-preview');
    if (feePreview) {
        feePreview.textContent = deliverySelected
            ? `Delivery fee: ₱${fee.toFixed(2)}`
            : `Local delivery fee: ₱${savedDeliveryFee.toFixed(2)} (charged only for delivery)`;
    }

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
        <span class="summary-item-name">${escapeHtml(item.name)}</span>
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

    totalPriceElem.textContent = `₱${(total + fee).toFixed(2)}`;
}

// 6. Change quantity (+ / -) in order.html
function changeQty(index, change) {
    const maxQty = Number(cart[index].maxQty);
    if (change > 0 && Number.isFinite(maxQty) && cart[index].qty + change > maxQty) {
        alert(`Only ${maxQty} ${cart[index].unit} available for ${cart[index].name}.`);
        return;
    }
    cart[index].qty += change;
    if (cart[index].qty <= 0) {
        cart.splice(index, 1);
    }
    saveCart();
    renderOrderSummary();
    updateCartBadge();
}

// 7. Handle Guest Form Submission
async function handleGuestCheckout(event) {
    event.preventDefault();

    if (cart.length === 0) {
        alert('Please add at least one meat item to your basket before checking out.');
        return;
    }

    if (cart.some(item => !Number.isInteger(item.productId) || item.productId <= 0)) {
        alert('Your basket has an older item format. Return to the catalog, remove those items, and add them again.');
        return;
    }

    const guestName = document.getElementById('guestName').value.trim();
    const guestPhone = document.getElementById('guestPhone').value.trim();
    const guestEmail = document.getElementById('guestEmail').value.trim();
    const fulfillmentType = document.getElementById('fulfillmentType').value;
    const paymentMethod = document.getElementById('paymentMethod').value;
    const deliveryAddress = document.getElementById('deliveryAddress').value.trim();
    const deliveryNotes = document.getElementById('deliveryNotes').value.trim();
    const submitButton = event.currentTarget.querySelector('button[type="submit"]');
    submitButton.disabled = true;

    try {
        const response = await fetch('/api/orders', {
            method: 'POST',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({
                order: {
                    customerName: guestName,
                    customerContact: guestPhone,
                    customerEmail: guestEmail,
                    fulfillmentMethod: fulfillmentType,
                    deliveryAddress: deliveryAddress,
                    deliveryNotes: deliveryNotes,
                    paymentMethod: paymentMethod,
                },
                items: cart.map(item => ({
                    productId: item.productId,
                    quantity: item.qty
                }))
            })
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(friendlyCheckoutError(result.error));

        const checkoutCard = document.getElementById('checkout-card');
        const confirmationCard = document.getElementById('confirmation-card');
        if (!checkoutCard || !confirmationCard) throw new Error('Order was placed, but the confirmation could not be displayed.');

        document.getElementById('conf-ref').textContent = result.referenceId;
        document.getElementById('conf-name').textContent = guestName;
        document.getElementById('conf-phone').textContent = guestPhone;
        document.getElementById('conf-fulfillment').textContent = fulfillmentType.toUpperCase();
        document.getElementById('conf-payment').textContent = paymentMethod.toUpperCase();
        document.getElementById('conf-delivery-fee').textContent = `₱${Number(result.deliveryFee || 0).toFixed(2)}`;
        document.getElementById('conf-total').textContent = `₱${Number(result.totalAmount).toFixed(2)}`;

        checkoutCard.classList.add('hidden');
        confirmationCard.classList.remove('hidden');

        cart = [];
        saveCart();
        updateCartBadge();
    } catch (error) {
        alert(error.message);
    } finally {
        submitButton.disabled = false;
    }
}

function updateDeliveryFields() {
    const deliverySelected = document.getElementById('fulfillmentType')?.value === 'Same-Day Delivery';
    const addressGroup = document.getElementById('delivery-address-group');
    const notesGroup = document.getElementById('delivery-notes-group');
    const addressInput = document.getElementById('deliveryAddress');

    if (!addressGroup || !notesGroup || !addressInput) return;
    addressGroup.hidden = !deliverySelected;
    notesGroup.hidden = !deliverySelected;
    addressInput.required = deliverySelected;
    if (deliverySelected && savedCustomerProfile) {
        if (!addressInput.value) {
            addressInput.value = [savedCustomerProfile.addressLine, savedCustomerProfile.city]
                .filter(Boolean).join(', ');
        }
        const notesInput = document.getElementById('deliveryNotes');
        if (!notesInput.value) notesInput.value = savedCustomerProfile.landmark || '';
    }
    renderOrderSummary();
}

async function loadCheckoutSettings() {
    try {
        const response = await fetch('/api/checkout-settings');
        if (!response.ok) throw new Error(`Checkout settings request failed: ${response.status}`);
        const settings = await response.json();
        savedDeliveryFee = Number(settings.deliveryFee || 0);
        renderOrderSummary();
    } catch (error) {
        console.error('Unable to load checkout settings:', error);
    }
}

async function loadSavedCustomerProfile() {
    if (!document.getElementById('guestName')) return;
    try {
        const response = await fetch('/api/account/profile');
        if (response.status === 401 || response.status === 403) return;
        if (!response.ok) throw new Error(`Profile request failed: ${response.status}`);
        savedCustomerProfile = await response.json();
        document.getElementById('guestName').value = savedCustomerProfile.fullName || '';
        document.getElementById('guestPhone').value = savedCustomerProfile.phone || '';
        document.getElementById('guestEmail').value = savedCustomerProfile.email || '';
        updateDeliveryFields();
    } catch (error) {
        console.error('Unable to prefill saved checkout details:', error);
    }
}

// Initialize badge count when page loads
document.addEventListener('DOMContentLoaded', () => {
    updateCartBadge();
    updateAuthLink();
    renderOrderSummary();
    loadCatalog();
    loadCheckoutSettings();
    loadSavedCustomerProfile();
    document.getElementById('fulfillmentType')?.addEventListener('change', updateDeliveryFields);
    updateDeliveryFields();
    document.getElementById('quantity-dialog-close')?.addEventListener('click', closeQuantityDialog);
    document.getElementById('quantity-form')?.addEventListener('submit', event => {
        event.preventDefault();
        const quantity = Number(document.getElementById('quantity-input').value);
        if (quantityProduct && Number.isInteger(quantity) && quantity > 0 && addProductQuantity(quantityProduct, quantity)) {
            closeQuantityDialog();
        }
    });
});