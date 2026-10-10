<template>
  <div class="login-page">
    <div class="login-logo" role="button" tabindex="0" title="回到首页" aria-label="回到首页"
         @click="appCtx.navigate('shop')" @keydown.enter.prevent="appCtx.navigate('shop')">
      <img src="/logo-full.svg" alt="超市购物系统" />
    </div>
    <div class="modal auth-modal" role="dialog" aria-modal="true">
      <button v-if="!forcedChange" class="modal-close" type="button" @click="back" aria-label="关闭">×</button>

      <div v-if="tab === 'login' || tab === 'register'" class="auth-tabs">
        <button :class="{ active: tab === 'login' }" type="button" @click="tab = 'login'">登录</button>
        <button :class="{ active: tab === 'register' }" type="button" @click="tab = 'register'">注册</button>
      </div>
      <div v-else-if="tab === 'change'" class="auth-change-title">修改密码</div>
      <div v-else class="auth-change-title">找回密码</div>

      <div v-if="appCtx.error.value" class="auth-error-banner" role="alert">⚠ {{ appCtx.error.value }}</div>

      <form v-if="tab === 'login'" class="auth-form" @submit.prevent="handleLogin">
        <label class="auth-field">
          <span>用户名</span>
          <input v-model="appCtx.loginForm.username" placeholder="请输入用户名" autocomplete="username" />
          <small v-if="appCtx.authErrors.username" class="field-hint warn">{{ appCtx.authErrors.username }}</small>
        </label>
        <label class="auth-field">
          <span>密码</span>
          <input v-model="appCtx.loginForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
          <small v-if="appCtx.authErrors.password" class="field-hint warn">{{ appCtx.authErrors.password }}</small>
        </label>
        <label class="auth-remember">
          <input type="checkbox" v-model="appCtx.loginForm.remember" /> 记住我（7 天免登录）
          <span class="auth-forgot" @click="toReset">忘记密码？</span>
        </label>
        <button class="auth-submit" type="submit" :disabled="appCtx.authSubmitting.value">{{ appCtx.authSubmitting.value ? '登录中…' : '登录' }}</button>
      </form>

      <form v-else-if="tab === 'register'" class="auth-form" @submit.prevent="handleRegister">
        <label class="auth-field">
          <span>用户名 <i class="req">*</i></span>
          <input v-model="appCtx.registerForm.username" placeholder="3-50 个字符" autocomplete="username" />
          <small v-if="appCtx.authErrors.username" class="field-hint warn">{{ appCtx.authErrors.username }}</small>
        </label>
        <label class="auth-field">
          <span>密码 <i class="req">*</i></span>
          <input v-model="appCtx.registerForm.password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
          <small v-if="appCtx.authErrors.password" class="field-hint warn">{{ appCtx.authErrors.password }}</small>
        </label>
        <label class="auth-field">
          <span>确认密码 <i class="req">*</i></span>
          <input v-model="appCtx.registerForm.confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" />
          <small v-if="appCtx.authErrors.confirmPassword" class="field-hint warn">{{ appCtx.authErrors.confirmPassword }}</small>
        </label>
        <label class="auth-field">
          <span>昵称</span>
          <input v-model="appCtx.registerForm.nickname" placeholder="不填则默认等于用户名" autocomplete="nickname" />
        </label>
        <label class="auth-field">
          <span>手机号 <i class="req">*</i></span>
          <input v-model="appCtx.registerForm.phone" placeholder="用于收货通知" autocomplete="tel" />
          <small v-if="appCtx.authErrors.phone" class="field-hint warn">{{ appCtx.authErrors.phone }}</small>
        </label>
        <label class="auth-field">
          <span>邮箱</span>
          <input v-model="appCtx.registerForm.email" placeholder="选填" autocomplete="email" />
          <small v-if="appCtx.authErrors.email" class="field-hint warn">{{ appCtx.authErrors.email }}</small>
        </label>
        <label class="auth-agree">
          <input type="checkbox" v-model="appCtx.registerForm.agree" />
          我已阅读并同意 <span class="auth-link" @click="appCtx.openLegal('TERMS')">《用户协议》</span>
          与 <span class="auth-link" @click="appCtx.openLegal('PRIVACY')">《隐私政策》</span>
          <small v-if="appCtx.authErrors.agree" class="field-hint warn">{{ appCtx.authErrors.agree }}</small>
        </label>
        <button class="auth-submit" type="submit" :disabled="appCtx.authSubmitting.value">{{ appCtx.authSubmitting.value ? '注册中…' : '注册并领取新人券' }}</button>
        <p class="auth-tip">注册即自动发放新人券 🎁</p>
      </form>

      <form v-else-if="tab === 'change'" class="auth-form" @submit.prevent="handleChange">
        <p v-if="forcedChange" class="auth-tip warn-tip">
          管理员已为你重置密码，请先用临时密码设置新密码，改完即可正常使用。
        </p>
        <label class="auth-field">
          <span>{{ forcedChange ? '临时密码' : '原密码' }}</span>
          <input v-model="appCtx.changeForm.currentPassword" type="password" placeholder="请输入当前密码" autocomplete="current-password" />
          <small v-if="appCtx.changeErrors.currentPassword" class="field-hint warn">{{ appCtx.changeErrors.currentPassword }}</small>
        </label>
        <label class="auth-field">
          <span>新密码</span>
          <input v-model="appCtx.changeForm.newPassword" type="password" placeholder="6-50 位字符" autocomplete="new-password" />
          <small v-if="appCtx.changeErrors.newPassword" class="field-hint warn">{{ appCtx.changeErrors.newPassword }}</small>
        </label>
        <label class="auth-field">
          <span>确认新密码</span>
          <input v-model="appCtx.changeForm.confirmPassword" type="password" placeholder="再次输入新密码" autocomplete="new-password" />
          <small v-if="appCtx.changeErrors.confirmPassword" class="field-hint warn">{{ appCtx.changeErrors.confirmPassword }}</small>
        </label>
        <button class="auth-submit" type="submit" :disabled="appCtx.changeSubmitting.value">{{ appCtx.changeSubmitting.value ? '提交中…' : '确认修改' }}</button>
      </form>

      <form v-else class="auth-form" @submit.prevent="handleReset">
        <p class="auth-tip">
          出于安全考虑，找回密码需要人工核对身份。提交申请后，客服会在 1 个工作日内
          通过你留下的联系方式告知临时密码，首次登录后需立即修改。
        </p>
        <label class="auth-field">
          <span>账号 <i class="req">*</i></span>
          <input v-model="appCtx.resetForm.username" placeholder="请输入用户名" autocomplete="username" />
          <small v-if="appCtx.resetErrors.username" class="field-hint warn">{{ appCtx.resetErrors.username }}</small>
        </label>
        <label class="auth-field">
          <span>联系电话 <i class="req">*</i></span>
          <input v-model="appCtx.resetForm.contact" placeholder="便于客服核对身份后联系你" autocomplete="tel" />
          <small v-if="appCtx.resetErrors.contact" class="field-hint warn">{{ appCtx.resetErrors.contact }}</small>
        </label>
        <button class="auth-submit" type="submit" :disabled="appCtx.resetSubmitting.value">{{ appCtx.resetSubmitting.value ? '提交中…' : '提交找回申请' }}</button>
        <p class="auth-tip"><span class="auth-link" @click="toLogin">返回登录</span></p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { inject, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { takePendingAction } from '../composables/pendingAction.js';

// 登录逻辑（token / 记住我 / 拉全量会话）仍集中在 App.vue 的 submit* 函数里（单一真相源），
// 这里只负责渲染独立全屏表单，并在登录成功后决定「去哪」。
const appCtx = inject('appCtx');
const route = useRoute();
const router = useRouter();

const VALID_TABS = ['login', 'register', 'change', 'reset'];
const qTab = route.query.tab;
const tab = ref(VALID_TABS.includes(qTab) ? qTab : 'login');
// forced=1：管理员重置过密码，登录后强制改密（不可关闭、不可跳走）
const forcedChange = ref(route.query.forced === '1' || route.query.forced === 1);

function back() {
  if (forcedChange.value) return;
  const r = route.query.redirect ? String(route.query.redirect) : 'shop';
  appCtx.navigate(r);
}

// 若本页是从别的页面「新标签页」打开的（window.opener 存在），登录成功后刷新来源页 + 关闭本标签，
// 让原页面回到已登录态；返回 false 表示不是新标签打开，走原同标签逻辑。
function closeAndRefreshOpener() {
  if (!window.opener) return false;
  try {
    syncCredentialsToOpener();
    window.opener.location.reload();
  } catch (e) { /* 跨域或被拦截时忽略，交给下面同标签兜底 */ }
  window.close();
  // 兜底：若浏览器拦截了 window.close()（极少数情况），本标签原地跳回首页，避免卡在登录表单
  appCtx.navigate('shop');
  return true;
}

/**
 * 把本标签的登录凭据写进 opener 的 sessionStorage。
 *
 * <p>不勾「记住我」时 token 存在 sessionStorage，而 **sessionStorage 按「标签页 + 源」隔离** ——
 * 新标签页里存的，opener 读不到。结果就是：游客点「立即购买」→ 新标签页登录 →
 * 回到原页面**仍是未登录**（勾「记住我」时存 localStorage 跨标签页共享，所以看不出问题）。
 *
 * <p>同源标签页之间允许直接写对方的 storage，所以直接搬过去即可。
 * 保持 sessionStorage 语义（关浏览器即失效），而不是改成 localStorage
 * —— 那是「记住我」才该有的行为。
 */
function syncCredentialsToOpener() {
  if (!window.opener) return;
  try {
    const token = sessionStorage.getItem('supermarket_token');
    const user = sessionStorage.getItem('supermarket_user');
    if (token) window.opener.sessionStorage.setItem('supermarket_token', token);
    if (user) window.opener.sessionStorage.setItem('supermarket_user', user);
  } catch (e) { /* 跨域时忽略 */ }
}

/**
 * 从 opener 那里取「待续做动作」—— 它存在 **opener 的 sessionStorage** 里，
 * 本标签读不到（同上，按标签页隔离）。取出即从 opener 侧删除，避免刷新后重复触发。
 */
function takeOpenerPendingAction() {
  if (!window.opener) return null;
  try {
    const raw = window.opener.sessionStorage.getItem('supermarket_pending_action');
    if (!raw) return null;
    window.opener.sessionStorage.removeItem('supermarket_pending_action');
    return JSON.parse(raw);
  } catch (e) {
    return null;
  }
}

/** 关掉本登录标签（被浏览器拦截时兜底回首页，避免卡在登录表单） */
function closeSelfTab() {
  window.close();
  setTimeout(() => {
    // window.close() 被拦截说明不是脚本开的标签，只能原地跳走
    if (window.closed) return;
    appCtx.navigate('shop');
  }, 300);
}

// 登录/注册成功后的去向：优先消费「拦截时存下的动作」（去结算 / 立即购买 / 收藏），否则回 redirect 或首页
function resumeAfterAuth() {
  // 1) 新标签页 + 原页面有「待续做动作」（去结算/立即购买/收藏）
  //    pendingAction 也存在 sessionStorage，同样按标签页隔离 —— 动作留在 opener 那边，
  //    必须从 opener 读出来。读到就**在本标签完成回跳**，同时把登录态搬给 opener 并刷新它。
  const openerAction = takeOpenerPendingAction();
  if (openerAction) {
    if (applyPendingAction(openerAction)) { syncCredentialsToOpener(); closeSelfTab(); return; }
  }
  // 2) 常规新标签页入口（页头登录/注册）：刷新来源页并关掉登录标签，原页面回到已登录态
  if (closeAndRefreshOpener()) return;
  // 3) 同标签登录：动作就在本页 sessionStorage
  const action = takePendingAction();
  if (action && applyPendingAction(action)) return;
  appCtx.navigate(route.query.redirect ? String(route.query.redirect) : 'shop');
}

/** 按 action 类型执行回跳；返回 false 表示类型不认识，交给调用方走兜底 */
function applyPendingAction(action) {
  if (!action) return false;
  if (action.type === 'checkout') { appCtx.navigate(action.redirect || 'checkout'); return true; }
  if (action.type === 'quickBuy') { appCtx.consumePendingQuickBuy(action); return true; }
  if (action.type === 'favorite') {
    appCtx.toggleFavorite(action.product);
    // 收藏拦截来自商品详情等页：redirect 只记了路由名（缺 :id），用 history back 回到来前那一页最稳
    if (window.history.length > 1) router.back();
    else appCtx.navigate(route.query.redirect ? String(route.query.redirect) : 'shop');
    return true;
  }
  return false;
}

async function handleLogin() {
  const res = await appCtx.submitLogin();
  if (!res || !res.ok) return;
  if (res.mustChange) { forcedChange.value = true; tab.value = 'change'; return; }
  resumeAfterAuth();
}

async function handleRegister() {
  const res = await appCtx.submitRegister();
  if (res && res.ok) resumeAfterAuth();
}

async function handleChange() {
  const res = await appCtx.submitChangePassword();
  if (res && res.ok) {
    // 新标签页入口（页头改密）优先：刷新来源页并关掉本标签
    if (closeAndRefreshOpener()) return;
    // 改密成功回到来前那一页（商品详情 / 首页等）；forced 场景没有良好落点时回首页
    if (window.history.length > 1) router.back();
    else appCtx.navigate('shop');
  }
}

async function handleReset() {
  const res = await appCtx.submitPasswordReset();
  if (res && res.ok) tab.value = 'login';
}

function toReset() {
  tab.value = 'reset';
  if (appCtx.loginForm) appCtx.resetForm.username = (appCtx.loginForm.username || '').trim();
}

function toLogin() { tab.value = 'login'; }
</script>

<style scoped>
/* 整屏生鲜大图 + 右侧白卡（委托模式：表单逻辑仍在 App.vue，见 script 注释）。
   卡片用 margin:auto 垂直居中——内容比视口高（注册表单）时 auto 归零不裁顶，比 align-items:center 稳。 */
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  padding: 40px 7vw 40px 32px;
  background:
    linear-gradient(100deg, rgba(6, 46, 32, 0.12) 0%, transparent 48%, rgba(6, 46, 32, 0.30) 100%),
    url('/auth-bg.jpg') center / cover no-repeat #eef7f2;
}

/* 原弹窗靠 .modal-mask 定位、关闭按钮 absolute 锚定到遮罩；独立页里卡片自己要 position:relative。
   margin:auto 居中 + margin-right 偏右：右侧只留 3vw，其余空间都给左边 → 卡片整体靠右。 */
.login-page .modal {
  position: relative;
  margin: auto;
  margin-right: 3vw;
  box-shadow: 0 30px 70px rgba(0, 60, 40, 0.35);
}

/* 左上角 logo 浮标（白底胶囊保证在任意照片上都清晰；点击回首页） */
.login-logo {
  position: absolute;
  top: 26px;
  left: 7vw;
  display: flex;
  align-items: center;
  background: rgba(255, 255, 255, 0.92);
  padding: 9px 15px;
  border-radius: 14px;
  box-shadow: 0 10px 26px rgba(0, 50, 32, 0.18);
  cursor: pointer;
}
.login-logo img { height: 38px; display: block; }
.login-logo:active { transform: translateY(1px); }

@media (max-width: 900px) {
  .login-page { padding: 24px 16px 48px; }
  .login-brand { display: none; }
  .login-page .modal { margin: auto; }
}
</style>
