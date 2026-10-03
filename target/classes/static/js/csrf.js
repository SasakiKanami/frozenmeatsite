function csrfHeaders(headers = {}) {
    const cookie = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    if (!cookie) {
        throw new Error('Security token is missing. Refresh the page and try again.');
    }
    return {
        ...headers,
        'X-XSRF-TOKEN': decodeURIComponent(cookie[1])
    };
}
