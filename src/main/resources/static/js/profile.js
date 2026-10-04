function setProfileMessage(message, isError = false) {
    const element = document.getElementById('profile-message');
    element.textContent = message;
    element.classList.toggle('error', isError);
}

function fillProfile(profile) {
    document.getElementById('profile-name').value = profile.fullName || '';
    document.getElementById('profile-email').value = profile.email || '';
    document.getElementById('profile-phone').value = profile.phone || '';
    document.getElementById('profile-address').value = profile.addressLine || '';
    document.getElementById('profile-city').value = profile.city || '';
    document.getElementById('profile-landmark').value = profile.landmark || '';
}

document.addEventListener('DOMContentLoaded', async () => {
    try {
        const response = await fetch('/api/account/profile');
        if (response.status === 401 || response.status === 403) {
            window.location.replace('../login/login.html');
            return;
        }
        if (!response.ok) throw new Error(`Profile request failed: ${response.status}`);
        fillProfile(await response.json());
    } catch (error) {
        setProfileMessage('Unable to load your profile. Please refresh and try again.', true);
        console.error('Unable to load customer profile:', error);
    }

    document.getElementById('profile-form').addEventListener('submit', async event => {
        event.preventDefault();
        const button = event.currentTarget.querySelector('button[type="submit"]');
        button.disabled = true;
        try {
            const response = await fetch('/api/account/profile', {
                method: 'PUT',
                headers: csrfHeaders({ 'Content-Type': 'application/json' }),
                body: JSON.stringify({
                    fullName: document.getElementById('profile-name').value.trim(),
                    phone: document.getElementById('profile-phone').value.trim(),
                    addressLine: document.getElementById('profile-address').value.trim(),
                    city: document.getElementById('profile-city').value.trim(),
                    landmark: document.getElementById('profile-landmark').value.trim()
                })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.error || `Profile save failed: ${response.status}`);
            fillProfile(result);
            setProfileMessage('Profile saved.');
        } catch (error) {
            setProfileMessage(error.message || 'Unable to save your profile.', true);
            console.error('Unable to save customer profile:', error);
        } finally {
            button.disabled = false;
        }
    });
});
