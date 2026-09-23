const API_BASE = import.meta.env.VITE_API_BASE || '/api';
const TOKEN_KEY = 'supermarket_token';

function readStoredToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '';
  } catch (e) {
    return '';
  }
}

let token = readStoredToken();

/**
 * 存 / 清 token。
 *
 * @param nextToken 新 token；传空串表示登出（两个 storage 都清）
 * @param remember  勾了「记住我」→ 存 localStorage（关浏览器仍免登录）；
 *                  不勾 → 存 sessionStorage（关浏览器即失效）。两者互斥，写入时会清掉另一个。
 *
 * ⚠️ 这与后端的 token 有效期是**两件事**，合起来才是完整的「记住我」：
 *    勾选 → 后端签 7 天有效（app.jwt.remember-expiration-seconds）+ 前端存 localStorage；
 *    不勾 → 后端签 24 小时 + 前端存 sessionStorage。
 */
export function setToken(nextToken, remember = true) {
  token = nextToken || '';
  try {
    if (!token) {
      localStorage.removeItem(TOKEN_KEY);
      sessionStorage.removeItem(TOKEN_KEY);
      return;
    }
    const keep = remember ? localStorage : sessionStorage;
    const drop = remember ? sessionStorage : localStorage;
    keep.setItem(TOKEN_KEY, token);
    drop.removeItem(TOKEN_KEY);
  } catch (e) {
    // 隐私模式 / 禁用 storage 时不阻断登录：token 仍在内存里，本次会话可用
  }

}

/** 登录态是否长期保存（= token 在 localStorage 里）—— 用来决定用户信息存哪。 */
export function isRemembered() {
  try {
    return !!localStorage.getItem(TOKEN_KEY);
  } catch (e) {
    return false;
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
