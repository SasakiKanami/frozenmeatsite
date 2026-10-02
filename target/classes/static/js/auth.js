// --- CARNI-FLOW ACCOUNT SYSTEM ---
// CHANGED (see reply for full list): this file originally ran entirely on
// localStorage as a front-end-only placeholder. It now calls the real
// /api/auth/signup and /api/auth/login endpoints from AuthController.java,
// matching the field names Spring expects:
//   - signup body binds directly to the User entity, so keys must be
//     fullName / username / passwordHash / email (not "name" / "password").
//   - login body is read as a Map<String,String> of username / password.
// The backend's `username` column is what login actually matches against,
// and this form only collects an email, so the email the person signs up
// with is reused as their username behind the scenes. That's why the
// Sign In field is now labelled "Email or Username" — it also has to
// accept non-email usernames like the seeded admin/cashier accounts.

// 1. Tab switching between Sign In / Create Account
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

// 2. Alert banner helpers
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

// 3. Password show/hide toggles
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

// 4. Redirect a signed-in user to the right side of the site for their role
function redirectForRole(role) {
    if (role === 'admin' || role === 'cashier') {
        window.location.href = '../admin_side/admin.html';
    } else {
        window.location.href = '../customer_side/index.html';
    }
}

// 5. Handle Sign In -> POST /api/auth/login
async function handleLogin(e) {
    e.preventDefault();
    clearAlert();

    const identifier = document.getElementById('login-email').value.trim();
    const password = document.getElementById('login-password').value;
    const remember = document.getElementById('remember-me').checked;
    const submitButton = e.currentTarget.querySelector('button[type="submit"]');

    if (!identifier || !password) {
        showAlert('Please enter your email/username and password.');
        return;
    }

    submitButton.disabled = true;

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: identifier, password: password })
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(result.error || 'Incorrect email/username or password.');
        }

        const session = { username: identifier, role: result.role, userId: result.userId, since: Date.now() };
        if (remember) {
            localStorage.setItem('carni_session', JSON.stringify(session));
        } else {
            sessionStorage.setItem('carni_session', JSON.stringify(session));
        }

        showAlert('Signed in! Redirecting…', 'success');
        setTimeout(() => redirectForRole(result.role), 700);
    } catch (error) {
        showAlert(error.message);
    } finally {
        submitButton.disabled = false;
    }
}

// 6. Handle Create Account -> POST /api/auth/signup
async function handleSignup(e) {
    e.preventDefault();
    clearAlert();

    const name = document.getElementById('signup-name').value.trim();
    const email = document.getElementById('signup-email').value.trim().toLowerCase();
    const password = document.getElementById('signup-password').value;
    const confirm = document.getElementById('signup-confirm').value;
    const agreed = document.getElementById('agree-terms').checked;
    const submitButton = e.currentTarget.querySelector('button[type="submit"]');

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

    submitButton.disabled = true;

    try {
        const response = await fetch('/api/auth/signup', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                fullName: name,
                username: email, // the users table requires a unique username; the signup email doubles as it
                passwordHash: password, // AuthController stores/compares this field as-is (see note in reply)
                email: email
            })
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(result.error || 'Unable to create your account.');
        }

        const session = { username: email, role: 'customer', userId: result.userId, since: Date.now() };
        localStorage.setItem('carni_session', JSON.stringify(session));

        showAlert('Account created! Redirecting…', 'success');
        setTimeout(() => redirectForRole('customer'), 700);
    } catch (error) {
        showAlert(error.message);
    } finally {
        submitButton.disabled = false;
    }
}

// 7. Wire everything up
document.addEventListener('DOMContentLoaded', () => {
    initPasswordToggles();

    document.getElementById('login-form').addEventListener('submit', handleLogin);
    document.getElementById('signup-form').addEventListener('submit', handleSignup);

    document.getElementById('forgot-link').addEventListener('click', (e) => {
        e.preventDefault();
        showAlert('Password reset isn\'t wired up yet — ask an admin to reset it for you directly in the database for now.');
    });
});
