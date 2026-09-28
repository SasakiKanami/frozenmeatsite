// --- CARNI-FLOW ACCOUNT SYSTEM (front-end demo) ---
// NOTE: This site currently has no backend/server (see main.js and admin.js,
// which both run entirely on localStorage). This file follows that same
// pattern so the Login page works today. When a real backend/API exists,
// swap the two TODO-marked sections below for real fetch() calls to your
// auth endpoints — the form markup and validation can stay as-is.

// 1. Load the "user directory" from browser storage
function loadUsers() {
    return JSON.parse(localStorage.getItem('carni_users')) || [];
}

function saveUsers(users) {
    localStorage.setItem('carni_users', JSON.stringify(users));
}

// 2. Tiny non-cryptographic hash so we at least avoid storing plain-text
//    passwords in localStorage. Replace with real server-side hashing
//    (bcrypt/argon2) once there's a backend.
function hashPassword(password) {
    let hash = 0;
    for (let i = 0; i < password.length; i++) {
        hash = (hash << 5) - hash + password.charCodeAt(i);
        hash |= 0;
    }
    return 'h_' + hash.toString(36) + '_' + password.length;
}

// 3. Tab switching between Sign In / Create Account
function switchAuthTab(tab) {
    const loginTab = document.getElementById('tab-login');
    const signupTab = document.getElementById('tab-signup');
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');

    const isLogin = tab === 'login';

    loginTab.classList.toggle('active', isLogin);
    signupTab.classList.toggle('active', !isLogin);
    loginTab.setAttribute('aria-selected', isLogin);
    signupTab.setAttribute('aria-selected', !isLogin);

    loginForm.classList.toggle('hidden', !isLogin);
    signupForm.classList.toggle('hidden', isLogin);

    clearAlert();
}

// 4. Alert banner helpers
function showAlert(message, type) {
    const alertBox = document.getElementById('auth-alert');
    alertBox.textContent = message;
    alertBox.className = 'auth-alert' + (type === 'success' ? ' success' : '');
    alertBox.classList.remove('hidden');
}

function clearAlert() {
    const alertBox = document.getElementById('auth-alert');
    alertBox.classList.add('hidden');
}

// 5. Password show/hide toggles
function initPasswordToggles() {
    document.querySelectorAll('.pw-toggle').forEach(function (btn) {
        btn.addEventListener('click', function () {
            const input = document.getElementById(btn.dataset.target);
            const isHidden = input.type === 'password';
            input.type = isHidden ? 'text' : 'password';
            btn.setAttribute('aria-label', isHidden ? 'Hide password' : 'Show password');
        });
    });
}

// 6. Handle Sign In
function handleLogin(e) {
    e.preventDefault();
    clearAlert();

    const email = document.getElementById('login-email').value.trim().toLowerCase();
    const password = document.getElementById('login-password').value;
    const remember = document.getElementById('remember-me').checked;

    if (!email || !password) {
        showAlert('Please enter your email and password.');
        return;
    }

    // TODO (backend): replace with e.g.
    // const res = await fetch('/api/login', { method: 'POST', body: JSON.stringify({ email, password }) });
    const users = loadUsers();
    const user = users.find(function (u) { return u.email === email; });

    if (!user || user.passwordHash !== hashPassword(password)) {
        showAlert('Incorrect email or password.');
        return;
    }

    const session = { name: user.name, email: user.email, since: Date.now() };
    if (remember) {
        localStorage.setItem('carni_session', JSON.stringify(session));
    } else {
        sessionStorage.setItem('carni_session', JSON.stringify(session));
    }

    showAlert('Signed in! Redirecting…', 'success');
    setTimeout(function () {
        window.location.href = 'index.html';
    }, 700);
}

// 7. Handle Create Account
function handleSignup(e) {
    e.preventDefault();
    clearAlert();

    const name = document.getElementById('signup-name').value.trim();
    const email = document.getElementById('signup-email').value.trim().toLowerCase();
    const password = document.getElementById('signup-password').value;
    const confirm = document.getElementById('signup-confirm').value;
    const agreed = document.getElementById('agree-terms').checked;

    if (!name || !email || !password || !confirm) {
        showAlert('Please fill in every field.');
        return;
    }
    if (password.length < 8) {
        showAlert('Password must be at least 8 characters.');
        return;
    }
    if (password !== confirm) {
        showAlert('Passwords do not match.');
        return;
    }
    if (!agreed) {
        showAlert('Please agree to the storage & order terms.');
        return;
    }

    // TODO (backend): replace with e.g.
    // const res = await fetch('/api/signup', { method: 'POST', body: JSON.stringify({ name, email, password }) });
    const users = loadUsers();
    if (users.some(function (u) { return u.email === email; })) {
        showAlert('An account with that email already exists.');
        return;
    }

    users.push({ name: name, email: email, passwordHash: hashPassword(password), createdAt: Date.now() });
    saveUsers(users);

    const session = { name: name, email: email, since: Date.now() };
    localStorage.setItem('carni_session', JSON.stringify(session));

    showAlert('Account created! Redirecting…', 'success');
    setTimeout(function () {
        window.location.href = 'index.html';
    }, 700);
}

// 8. Wire everything up
document.addEventListener('DOMContentLoaded', function () {
    initPasswordToggles();

    document.getElementById('login-form').addEventListener('submit', handleLogin);
    document.getElementById('signup-form').addEventListener('submit', handleSignup);

    document.getElementById('forgot-link').addEventListener('click', function (e) {
        e.preventDefault();
        showAlert('Password reset isn\'t wired up yet — this needs a backend email service.');
    });
});