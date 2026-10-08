/**
 * 认证域：登录 / 注册 / 找回密码申请 / 修改密码 / 登出 / token 过期处理 / 拉取 me / 换头像
 *
 * 从 App.vue 抽出（原 1618-1893 行）。
 *
 * 依赖注入说明（auth 是「叶子域」，几乎不依赖别的业务域，但依赖路由/会话/全局提示）：
 * - api / setToken / userStore：纯工具与 store，直接 import，不注入。
 * - session / route / navigate / goLogin：会话与路由职责，由 App.vue 注入。
 * - rememberUser / refreshForSession / getLoadCart：会话刷新与购物车回退，跨域，注入。
 *   getLoadCart 是读取器而非 loadCart 本身 —— loadCart 由 useCart 提供，而 useCart 装配在useAuth 之后，
 *   直接传会在装配实参里立即求值 → TDZ 白屏。
 * - notice / error / fail / showAlert：全局提示（条/弹窗），注入。
 * - closeAccountMenu：关掉头像下拉，改密弹窗拉起前要先收菜单，注入。
 *
 * ⚠️ forcedChange（强制改密）与 must-change-password 事件监听是同一条链路，
 *    不可拆散：后端 MustChangePasswordFilter 拦下任何调用 → 广播事件 → 拉起不可关闭的改密态。
 */
import { reactive, ref } from 'vue';
import { api, setToken } from '../api/client';
import { useUserStore } from '../stores/user';

export function useAuth(deps) {
  const {
    getSession, getRoute, navigate, goLogin, rememberUser, refreshForSession, getLoadCart,
    notice, error, fail, showAlert, closeAccountMenu,
  } = deps;

  const userStore = useUserStore();

  const authOpen = ref(false);
  const authTab = ref('login');
  const authSubmitting = ref(false);
  const loginForm = reactive({ username: '', password: '', remember: true });
  const registerForm = reactive({ username: '', password: '', confirmPassword: '', nickname: '', phone: '', email: '', agree: false });
  const authErrors = reactive({ username: '', password: '', confirmPassword: '', phone: '', email: '', agree: '' });
  const changeForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const changeErrors = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const changeSubmitting = ref(false);
  // forced=true 表示管理员刚重置过密码：弹窗不可关闭，改完才能继续用
  const forcedChange = ref(false);
  const resetForm = reactive({ username: '', contact: '' });
  const resetErrors = reactive({ username: '', contact: '' });
  const resetSubmitting = ref(false);

  function openAuth(tab) {
    authTab.value = tab === 'register' ? 'register' : 'login';
    resetAuthErrors();
    authOpen.value = true;
  }

  function closeAuth() {
    authOpen.value = false;
    loginForm.username = '';
    loginForm.password = '';
    loginForm.remember = true;
    registerForm.username = '';
    registerForm.password = '';
    registerForm.confirmPassword = '';
    registerForm.nickname = '';
    registerForm.phone = '';
    registerForm.email = '';
    registerForm.agree = false;
    changeForm.currentPassword = '';
    changeForm.newPassword = '';
    changeForm.confirmPassword = '';
    resetForm.username = '';
    resetForm.contact = '';
    forcedChange.value = false;
    resetAuthErrors();
  }

  function switchAuth(tab) {
    authTab.value = tab;
    resetAuthErrors();
  }

  function resetAuthErrors() {
    authErrors.username = '';
    authErrors.password = '';
    authErrors.confirmPassword = '';
    authErrors.phone = '';
    authErrors.email = '';
    authErrors.agree = '';
    changeErrors.currentPassword = '';
    changeErrors.newPassword = '';
    changeErrors.confirmPassword = '';
    resetErrors.username = '';
    resetErrors.contact = '';
    error.value = '';
  }

  // 后端部分错误文案仍是英文，这里统一映射为中文（新后端若已返回中文则原样透传）
  function authErrorText(msg) {
    if (!msg) return msg;
    const map = {
      'username or password is incorrect': '用户名或密码错误',
      'username already exists': '用户名已存在',
      'phone already exists': '手机号已被注册',
      'email already exists': '邮箱已被注册',
    };
    const lower = String(msg).toLowerCase();
    for (const k in map) { if (lower.includes(k)) return map[k]; }
    return msg;
  }

  async function submitLogin() {
    resetAuthErrors();
    if (!loginForm.username.trim()) authErrors.username = '请输入用户名';
    if (!loginForm.password) authErrors.password = '请输入密码';
    if (authErrors.username || authErrors.password) return { ok: false };
    authSubmitting.value = true;
    try {
      const data = await api.post('/auth/login', {
        username: loginForm.username.trim(),
        password: loginForm.password,
        // 勾了「记住我」→ 后端签发 7 天有效期的 token，前端把它存 localStorage（不勾则是 24 小时 + sessionStorage）
        remember: !!loginForm.remember,
      });
      setToken(data.token, !!loginForm.remember);
      rememberUser(data.user);
      await refreshForSession();
      resetAuthErrors();
      // 登录逻辑集中在这里（单一真相源），但「登录后去哪 / 是否强制改密」交给 LoginPage 决定（modal 已拆成独立路由页）。
      if (data.user.mustChangePassword) {
        // 管理员重置过密码：由 LoginPage 切到「修改密码」态（不可跳过），这里只返回标记
        return { ok: true, mustChange: true, user: data.user };
      }
      showAlert({ type: 'success', title: '登录成功', message: `欢迎回来，${data.user.nickname || data.user.username}` });
      return { ok: true, mustChange: false, user: data.user };
    } catch (err) {
      fail(authErrorText(err.message) || '登录失败，请检查用户名或密码');
      return { ok: false };
    } finally {
      authSubmitting.value = false;
    }
  }

  function validateRegisterForm() {
    const e = {};
    if (!registerForm.username.trim()) e.username = '请输入用户名';
    else if (registerForm.username.trim().length < 3) e.username = '用户名至少 3 个字符';
    if (!registerForm.password) e.password = '请输入密码';
    else if (registerForm.password.length < 6) e.password = '密码至少 6 位';
    if (registerForm.confirmPassword !== registerForm.password) e.confirmPassword = '两次输入的密码不一致';
    if (!registerForm.phone.trim()) e.phone = '请输入手机号';
    else if (!/^1[3-9]\d{9}$/.test(registerForm.phone.trim())) e.phone = '手机号格式不正确';
    if (registerForm.email.trim() && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(registerForm.email.trim())) e.email = '邮箱格式不正确';
    if (!registerForm.agree) e.agree = '请先同意用户协议';
    return e;
  }

  async function submitRegister() {
    resetAuthErrors();
    Object.assign(authErrors, validateRegisterForm());
    if (authErrors.username || authErrors.password || authErrors.confirmPassword || authErrors.phone || authErrors.email || authErrors.agree) return { ok: false };
    authSubmitting.value = true;
    try {
      const data = await api.post('/auth/register', {
        username: registerForm.username.trim(),
        password: registerForm.password,
        nickname: registerForm.nickname.trim() || registerForm.username.trim(),
        phone: registerForm.phone.trim(),
        email: registerForm.email.trim(),
      });
      setToken(data.token);
      rememberUser(data.user);
      await refreshForSession();
      resetAuthErrors();
      showAlert({ type: 'success', title: '注册成功', message: '欢迎加入！新人券已自动发放到你的账户 🎁' });
      return { ok: true };
    } catch (err) {
      const msg = authErrorText(err.message) || '注册失败，请稍后重试';
      if (msg.includes('用户名')) authErrors.username = msg;
      else if (msg.includes('手机')) authErrors.phone = msg;
      else if (msg.includes('邮箱')) authErrors.email = msg;
      fail(msg);
      return { ok: false };
    } finally {
      authSubmitting.value = false;
    }
  }

  // 忘记密码：不走自助重置（没有邮件/短信通道），改为打开「找回申请」表单
  function forgotPassword() {
    authTab.value = 'reset';
    resetForm.username = loginForm.username.trim();
    resetForm.contact = '';
    resetAuthErrors();
    authOpen.value = true;
  }

  function openChangePassword(forced = false) {
    closeAccountMenu();
    authTab.value = 'change';
    forcedChange.value = !!forced;
    resetAuthErrors();
    authOpen.value = true;
  }

  async function submitPasswordReset() {
    resetAuthErrors();
    if (!resetForm.username.trim()) resetErrors.username = '请输入账号';
    if (!resetForm.contact.trim()) resetErrors.contact = '请留下联系电话，否则客服无法联系你';
    else if (resetForm.contact.trim().length < 5) resetErrors.contact = '联系电话至少 5 个字符';
    if (resetErrors.username || resetErrors.contact) return { ok: false };
    resetSubmitting.value = true;
    try {
      const data = await api.post('/auth/password-reset-request', {
        username: resetForm.username.trim(),
        contact: resetForm.contact.trim(),
      });
      // 后端对「账号存在/不存在」返回同一句文案（防账号枚举），前端原样展示
      showAlert({ type: 'success', title: '申请已提交', message: data?.message || '客服会在 1 个工作日内联系你。' });
      return { ok: true };
    } catch (err) {
      fail(err?.message || '提交失败，请稍后重试');
      return { ok: false };
    } finally {
      resetSubmitting.value = false;
    }
  }

  function validateChangeForm() {
    const e = {};
    if (!changeForm.currentPassword) e.currentPassword = '请输入原密码';
    if (!changeForm.newPassword) e.newPassword = '请输入新密码';
    else if (changeForm.newPassword.length < 6) e.newPassword = '新密码至少 6 位';
    if (changeForm.confirmPassword !== changeForm.newPassword) e.confirmPassword = '两次输入的新密码不一致';
    if (changeForm.newPassword && changeForm.currentPassword && changeForm.newPassword === changeForm.currentPassword) e.newPassword = '新密码不能与原密码相同';
    return e;
  }

  async function submitChangePassword() {
    resetAuthErrors();
    Object.assign(changeErrors, validateChangeForm());
    if (changeErrors.currentPassword || changeErrors.newPassword || changeErrors.confirmPassword) return { ok: false };
    changeSubmitting.value = true;
    try {
      await api.post('/auth/change-password', {
        currentPassword: changeForm.currentPassword,
        newPassword: changeForm.newPassword,
        confirmPassword: changeForm.confirmPassword,
      });
      // 后端已清掉 must_change_password，重新拉一次 me 让本地 session 同步（强制改密的用户至此恢复可用）
      await loadMe();
      showAlert({ type: 'success', title: '修改成功', message: '密码已更新，下次登录请使用新密码。' });
      return { ok: true };
    } catch (err) {
      // 后端业务错误（原密码不正确 / 新密码与原密码相同 / 两次不一致）透传到对应字段
      const msg = err.message || '修改失败，请稍后重试';
      if (msg.includes('原密码')) changeErrors.currentPassword = msg;
      else if (msg.includes('不一致')) changeErrors.confirmPassword = msg;
      else if (msg.includes('相同')) changeErrors.newPassword = msg;
      else error.value = msg;
      fail(msg);
      return { ok: false };
    } finally {
      changeSubmitting.value = false;
    }
  }

  function logout() {
    setToken('');
    rememberUser(null);
    navigate('shop');
    notice.value = '已退出登录';
    getLoadCart()(); // 游客态：清掉服务端 cart 视图，回到本地车状态
  }

  // token 过期/失效：后端返回 401 时由 api 客户端广播，这里优雅回到未登录态（不卡死界面）
  function handleAuthExpired() {
    setToken('');
    rememberUser(null);
    const r = typeof getRoute === 'function' ? getRoute() : null;
    if (r?.name !== 'shop') navigate('shop');
    notice.value = '登录已过期，请重新登录';
    getLoadCart()();
  }

  // 挂全局监听：token 过期 + 待强制改密（后端 MustChangePasswordFilter 拦下任何调用时广播）
  function registerAuthListeners() {
    if (typeof window === 'undefined') return;
    window.addEventListener('auth-expired', handleAuthExpired);
    // 后端也会拦「待强制改密」的账号（40302，见 MustChangePasswordFilter）：任何调用被拦都把
    // 不可关闭的改密弹窗拉起来，避免出现「页面能点、接口全 403」这种说不清的状态。
    window.addEventListener('must-change-password', () => {
      // 登录态下被后端拦下（账号待强制改密）：跳到独立登录页的改密态（modal 已拆成 /login 路由页）
      const s = typeof getSession === 'function' ? getSession() : null;
      const r = typeof getRoute === 'function' ? getRoute() : null;
      if (s?.user && r?.name !== 'login') goLogin({ tab: 'change', forced: true });
    });
  }

  async function loadMe() {
    const s = typeof getSession === 'function' ? getSession() : null;
    if (!s?.user) return;
    const user = await api.get('/auth/me');
    rememberUser(user);
    userStore.setAuth(localStorage.getItem('token') || '', user);
    // 刷新页面时若仍处于「待强制改密」，重新跳到独立登录页的改密态
    const r = typeof getRoute === 'function' ? getRoute() : null;
    if (user.mustChangePassword && r?.name !== 'login') goLogin({ tab: 'change', forced: true });
  }

  async function onAvatarPick(e) {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    if (file.size > 10 * 1024 * 1024) {
      notice.value = '头像图片大小不能超过 10MB，请压缩后重试';
      e.target.value = '';
      return;
    }
    try {
      const fd = new FormData();
      fd.append('file', file);
      fd.append('type', 'avatar');
      const data = await api.post('/files/upload', fd);
      await api.put('/auth/me', { avatarUrl: data.url });
      await loadMe();
      notice.value = '头像已更新';
    } catch (err) {
      // 上传失败用轻提示而不是 fail() 弹窗（2026-10-09 用户要求）：
      // 弹窗要手动点掉，换头像失败这种小事打断感太强，toast 足够。
      notice.value = err?.message || '头像上传失败，请重试';
    } finally {
      e.target.value = '';
    }
  }

  return {
    authOpen, authTab, authSubmitting,
    loginForm, registerForm, authErrors,
    changeForm, changeErrors, changeSubmitting, forcedChange,
    resetForm, resetErrors, resetSubmitting,
    openAuth, closeAuth, switchAuth, resetAuthErrors, authErrorText,
    submitLogin, validateRegisterForm, submitRegister,
    forgotPassword, openChangePassword, submitPasswordReset,
    validateChangeForm, submitChangePassword,
    logout, handleAuthExpired, registerAuthListeners, loadMe, onAvatarPick,
  };
}
