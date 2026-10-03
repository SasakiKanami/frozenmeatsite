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
    const submitButton = e.currentTarget.querySelector('button[type="submit"]');

    if (!identifier || !password) {
        showAlert('Please enter your email/username and password.');
        return;
    }

    submitButton.disabled = true;

    try {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({ username: identifier, password: password })
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(result.error || 'Incorrect email/username or password.');
        }

        localStorage.removeItem('carni_session');
        sessionStorage.removeItem('carni_session');
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
            headers: csrfHeaders({ 'Content-Type': 'application/json' }),
            body: JSON.stringify({
                fullName: name,
                username: email, // the users table requires a unique username; the signup email doubles as it
                password: password,
                email: email
            })
        });
        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
            throw new Error(result.error || 'Unable to create your account.');
        }

        localStorage.removeItem('carni_session');
        sessionStorage.removeItem('carni_session');

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
        showAlert('Password reset is not available yet. Please contact an administrator for help.');
    });
});
