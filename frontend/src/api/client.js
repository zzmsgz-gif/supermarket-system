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
    // 40302 = 后端拦下了「管理员重置过密码、还没改」的账号（见 MustChangePasswordFilter）。
    // 前端本来就会弹不可关闭的改密弹窗，这里再补一次事件，保证任何调用路径被拦都能拉起来。
    const mustChange = payload?.code === 40302;
    if (mustChange && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('must-change-password'));
    }
    const err = new Error(message);
    if (authFailed) err.authExpired = true;
    if (mustChange) err.mustChangePassword = true;
    err.code = payload?.code;
    err.status = response.status;
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
