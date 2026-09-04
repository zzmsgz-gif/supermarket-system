const API_BASE = import.meta.env.VITE_API_BASE || '/api';

let token = localStorage.getItem('supermarket_token') || '';

export function setToken(nextToken) {
  token = nextToken || '';
  if (token) {
    localStorage.setItem('supermarket_token', token);
  } else {
    localStorage.removeItem('supermarket_token');
  }

}

export function getToken() {
  return token;

}

async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  const isFormData = options.body instanceof FormData;
  if (!isFormData && options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
    body: isFormData ? options.body : (options.body === undefined ? undefined : JSON.stringify(options.body)),
  });

  let payload = null;
  const text = await response.text();
  if (text) {
    payload = JSON.parse(text);
  }

  if (!response.ok || (payload && payload.code !== 0)) {
    const message = payload?.message || `HTTP ${response.status}`;
    const authFailed = response.status === 401 && !!token;
    if (authFailed) {
      setToken('');
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('auth-expired'));
      }
    }
    const err = new Error(message);
    if (authFailed) err.authExpired = true;
    throw err;
  }

  return payload?.data ?? null;

}

export const api = {
  get: (path) => request(path),
  post: (path, body) => request(path, { method: 'POST', body }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  patch: (path, body) => request(path, { method: 'PATCH', body }),
  delete: (path) => request(path, { method: 'DELETE' }),

};
