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
  try {
    if (text) {
      payload = JSON.parse(text);
    }
  } catch (e) {
    // 响应体不是合法 JSON（典型如 nginx 限流返回的 429 HTML 页、网关错误页）。
    // 绝不能让原始 SyntaxError 冒泡——否则 request() 直接崩，调用方连可读错误都拿不到，
    // 表现为「接口失效 / 整个页面卡死」。这里吞掉解析错误，交给下面的错误分支走兜底文案。
    payload = null;
  }

  if (!response.ok || (payload && payload.code !== 0)) {
    // 限流：nginx 返回 429 时响应体是 HTML，payload 为 null。给出专门文案，
    // 并透出 Retry-After（秒），让调用方可以优雅退避而不是反复打。
    if (response.status === 429) {
      const retryAfter = response.headers.get('Retry-After');
      const message = retryAfter
        ? `请求过于频繁，请 ${retryAfter} 秒后再试`
        : '请求过于频繁，请稍后再试';
      const err = new Error(message);
      err.status = 429;
      err.code = 429;
      err.retryAfter = retryAfter ? Number(retryAfter) : null;
      throw err;
    }

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
