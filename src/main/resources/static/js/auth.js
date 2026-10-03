// 1. Tab switching between Sign In / Create Account
function switchAuthTab(tab) {
    const loginTab = document.getElementById('tab-login');
    const signupTab = document.getElementById('tab-signup');
    const loginForm = document.getElementById('login-form');
    const signupForm = document.getElementById('signup-form');
    const resetPanel = document.getElementById('password-reset-panel');
    const tabs = document.querySelector('.auth-tabs');

    const isLogin = tab === 'login';

    tabs.classList.remove('hidden');
    resetPanel.classList.add('hidden');
    loginTab.classList.toggle('active', isLogin);
    signupTab.classList.toggle('active', !isLogin);
    loginTab.setAttribute('aria-selected', isLogin);
    signupTab.setAttribute('aria-selected', !isLogin);

    loginForm.classList.toggle('hidden', !isLogin);
    signupForm.classList.toggle('hidden', isLogin);
    document.querySelector('.guest-note').classList.remove('hidden');

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

function showPasswordResetPanel(completion) {
    document.querySelector('.auth-tabs').classList.add('hidden');
    document.getElementById('login-form').classList.add('hidden');
    document.getElementById('signup-form').classList.add('hidden');
    document.getElementById('password-reset-panel').classList.remove('hidden');
    document.getElementById('password-reset-request-form').classList.toggle('hidden', completion);
    document.getElementById('password-reset-complete-form').classList.toggle('hidden', !completion);
    document.querySelector('.guest-note').classList.add('hidden');
    clearAlert();
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
        showPasswordResetPanel(false);
    });
    document.getElementById('back-to-login').addEventListener('click', () => switchAuthTab('login'));

    document.getElementById('password-reset-request-form').addEventListener('submit', async event => {
        event.preventDefault();
        clearAlert();
        const button = event.currentTarget.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const response = await fetch('/api/auth/password-reset-requests', {
                method: 'POST',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({ email: document.getElementById('reset-email').value.trim() })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Reset request failed: ${response.status}`);
            showAlert(result.message || 'If an account exists for that email, password reset instructions will be sent.', 'success');
        } catch (error) {
            showAlert(error.message || 'Unable to request a password reset.');
            console.error('Unable to request password reset:', error);
        } finally {
            button.disabled = false;
        }
    });

    document.getElementById('password-reset-complete-form').addEventListener('submit', async event => {
        event.preventDefault();
        clearAlert();
        const password = document.getElementById('reset-password').value;
        const confirmation = document.getElementById('reset-password-confirm').value;
        if (password.length < 8 || password.length > 72) {
            showAlert('Password must be between 8 and 72 characters.');
            return;
        }
        if (password !== confirmation) {
            showAlert('Passwords do not match.');
            return;
        }
        const button = event.currentTarget.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const response = await fetch('/api/auth/password-resets', {
                method: 'POST',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    token: new URLSearchParams(window.location.search).get('resetToken'),
                    password
                })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Password reset failed: ${response.status}`);
            window.history.replaceState({}, document.title, window.location.pathname);
            switchAuthTab('login');
            showAlert(result.message || 'Password updated. You can now sign in.', 'success');
        } catch (error) {
            showAlert(error.message || 'Unable to reset your password.');
            console.error('Unable to reset password:', error);
        } finally {
            button.disabled = false;
        }
    });

    const resetToken = new URLSearchParams(window.location.search).get('resetToken');
    if (resetToken) showPasswordResetPanel(true);
});
