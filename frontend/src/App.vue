<template>
  <main class="app-shell">
    <div class="header-utility">
      <div class="header-utility-inner">
        <div class="u-left">
          <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5" rx="1"/><line x1="12" y1="22" x2="12" y2="7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/></svg>
          <Transition name="notice-fade" mode="out-in">
            <span :key="rotatingNotice">{{ rotatingNotice }}</span>
          </Transition>
        </div>
        <div class="u-right">
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="14" height="12" rx="1"/><polygon points="15 8 19 8 22 11 22 16 15 16"/><circle cx="6" cy="18.5" r="2"/><circle cx="18" cy="18.5" r="2"/></svg>满 ¥99 免运费</span>
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-3.6 8-10V5l-8-3-8 3v7c0 6.4 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>生鲜坏果包赔</span>
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>21:00 前下单 · 次日达</span>
        </div>
      </div>
    </div>

    <header class="site-header">
      <div class="header-inner">
        <!-- 导航条已取消：Logo 承担「回到首页」 -->
        <div class="brand brand-link" role="button" tabindex="0" title="回到首页" aria-label="回到首页"
             @click="navigate('shop')" @keydown.enter.prevent="navigate('shop')">
          <span class="brand-mark">S</span>
          <div>
            <strong>超市购物系统</strong>
            <small>Supermarket Mall</small>
          </div>
        </div>

        <div class="search-area">
          <form class="header-search" @submit.prevent="goSearch">
            <svg class="icon i-search" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
            <input v-model="headerKeyword" type="search" placeholder="搜索牛奶、面包、五常大米、抽纸…" aria-label="搜索商品" />
            <button type="submit" aria-label="搜索"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg></button>
          </form>
          <div class="hot-words">
            <span class="hw-tag">热搜</span>
            <a @click.prevent="quickSearch('牛奶')">纯牛奶</a>
            <a @click.prevent="quickSearch('大米')">五常大米</a>
            <a @click.prevent="quickSearch('食用油')">食用油</a>
            <a @click.prevent="quickSearch('抽纸')">抽纸</a>
            <a @click.prevent="quickSearch('鸡蛋')">鸡蛋</a>
          </div>
        </div>

        <section class="account-panel">
          <button v-if="!isAdmin" class="cart-pill" @click="navigate('cart')" aria-label="购物车">
            <svg class="icon i-cart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.4 11.2a1.8 1.8 0 0 0 1.8 1.4h8.5a1.8 1.8 0 0 0 1.8-1.4L21.5 7H6"/></svg>
            购物车
            <span v-if="cartBadgeCount" class="cart-badge">{{ cartBadgeCount > 99 ? '99+' : cartBadgeCount }}</span>
          </button>
          <template v-if="session.user">
            <div class="account-menu-wrap">
              <div
                class="account-user"
                role="button"
                tabindex="0"
                aria-haspopup="menu"
                :aria-expanded="accountMenuOpen ? 'true' : 'false'"
                @click="toggleAccountMenu"
                @keydown.enter.prevent="toggleAccountMenu"
                @keydown.space.prevent="toggleAccountMenu"
              >
                <span class="account-avatar-wrap">
                  <img v-if="session.user.avatarUrl" :src="session.user.avatarUrl" class="avatar-img avatar-clickable" alt="头像" title="点击更换头像" @error="imgFallback($event, session.user.nickname || session.user.username)" @click.stop="avatarInput?.click()" />
                  <span v-else class="avatar-img avatar-default avatar-clickable" title="点击更换头像" @click.stop="avatarInput?.click()">{{ (session.user.nickname || session.user.username || '?').charAt(0) }}</span>
                  <!-- 导航条取消后，未读提醒收在头像上：不展开下拉也能看见 -->
                  <span v-if="!isAdmin && accountDotTitle" class="account-dot" :title="accountDotTitle"></span>
                </span>
                <div class="account-meta">
                  <span>{{ session.user.nickname || session.user.username }}</span>
                  <small>{{ formatRole(session.user.role) }}</small>
                </div>
                <span class="account-caret" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg></span>
              </div>
              <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPick" />

              <div v-if="accountMenuOpen" class="account-menu" role="menu">
                <!-- 导航条取消后，管理员的「后台管理」入口挪进下拉 -->
                <div v-if="isAdmin" class="account-menu-list account-menu-lead">
                  <button role="menuitem" @click="navigate('admin')">后台管理</button>
                </div>
                <button v-if="!isAdmin" class="account-menu-assets" role="menuitem" @click="navigate('points')" :title="'等级：' + tierNameFor(session.user.memberLevel)">
                  <span class="ama-balance"><small>余额</small><strong>{{ money(wallet.balance) }}</strong></span>
                  <span class="ama-tier">{{ tierNameFor(session.user.memberLevel) }} · {{ session.user.points || 0 }} 积分</span>
                </button>
                <div v-if="!isAdmin" class="account-menu-list">
                  <button role="menuitem" @click="navigate('orders')">我的订单</button>
                  <button role="menuitem" @click="navigate('coupons')">优惠券</button>
                  <button role="menuitem" @click="navigate('addresses')">收货地址</button>
                  <button role="menuitem" @click="navigate('points')">我的积分</button>
                  <button role="menuitem" @click="navigate('favorites')">我的收藏<span v-if="alertUnread" class="nav-badge">{{ alertUnread > 99 ? '99+' : alertUnread }}</span></button>
                  <button role="menuitem" @click="navigate('messages')">消息<span v-if="messageUnread" class="nav-badge">{{ messageUnread > 99 ? '99+' : messageUnread }}</span></button>
                  <button role="menuitem" @click="navigate('recharge')">账户充值</button>
                </div>
                <div class="account-menu-list account-menu-tail">
                  <button role="menuitem" @click="openChangePassword">修改密码</button>
                  <button role="menuitem" class="menu-danger" @click="logout">退出登录</button>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="auth-guest">
              <button class="ghost" @click="openAuth('login')">登录</button>
              <button class="btn-solid" @click="openAuth('register')">注册</button>
            </div>
          </template>
        </section>
      </div>

    </header>

    <section class="content" :class="{ 'content-wide': view === 'admin' }">
      <header class="topbar" v-if="view !== 'product' && view !== 'shop'">
        <h1>{{ currentTitle.title }}</h1>
        <button v-if="['orders', 'coupons', 'points', 'favorites', 'messages'].includes(view)" class="ghost" @click="refreshCurrentPage">刷新</button>
      </header>

      <router-view v-if="route.name !== 'admin'" />

      <AdminPanel v-else :view="view" :categories="categories" :admin-ctx="adminCtx" />
    </section>

    <footer class="site-footer">
      <div class="footer-promise">
        <div class="footer-promise-inner">
          <div class="promise-item">
            <span class="promise-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-3.6 8-10V5l-8-3-8 3v7c0 6.4 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg></span>
            <div><strong>正品保障</strong><small>品牌直供 · 假一赔十</small></div>
          </div>
          <div class="promise-item">
            <span class="promise-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M14 18V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v11a1 1 0 0 0 1 1h2"/><path d="M15 18H9"/><path d="M19 18h2a1 1 0 0 0 1-1v-3.65a1 1 0 0 0-.22-.62l-3.48-4.35A1 1 0 0 0 17.52 8H14"/><circle cx="17" cy="18" r="2"/><circle cx="7" cy="18" r="2"/></svg></span>
            <div><strong>极速配送</strong><small>冷链到家 · 次日必达</small></div>
          </div>
          <div class="promise-item">
            <span class="promise-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/></svg></span>
            <div><strong>7天无理由退换</strong><small>生鲜坏品先行赔付</small></div>
          </div>
          <div class="promise-item">
            <span class="promise-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-5Z"/><path d="M21 14h-3a2 2 0 0 0-2 2v3a2 2 0 0 0 2 2h1a2 2 0 0 0 2-2v-5Z"/><path d="M3 14v-3a9 9 0 0 1 18 0v3"/></svg></span>
            <div><strong>售后无忧</strong><small>7×24 小时在线客服</small></div>
          </div>
        </div>
      </div>
      <div class="footer-inner">
        <div class="footer-brand">
          <div class="footer-logo">
            <span class="brand-mark">S</span>
            <span>超市购物系统</span>
          </div>
          <p>Supermarket Mall · 让每一次下单都简单可靠。产地直采、冷链到家，把新鲜交还给每一个清晨。</p>
          <form class="footer-sub" novalidate @submit.prevent="footerSubscribe">
            <input v-model="subEmail" type="email" placeholder="输入邮箱，订阅促销情报" aria-label="订阅邮箱" />
            <button type="button" @click="footerSubscribe">订阅</button>
          </form>
          <p v-if="subMsg" class="footer-sub-msg">{{ subMsg }}</p>
        </div>
        <div class="footer-cols">
          <div class="footer-col">
            <h5>购物指南</h5>
            <a @click="navigate('shop')">首页商品</a>
            <a @click="navigate('cart')">购物车</a>
            <a @click="navigate('orders')">我的订单</a>
            <a @click="navigate('coupons')">优惠券</a>
          </div>
          <div class="footer-col">
            <h5>配送方式</h5>
            <a>上门自提</a><a>极速达</a><a>配送范围</a><a>运费标准</a>
          </div>
          <div class="footer-col">
            <h5>支付方式</h5>
            <a>在线支付</a><a>微信支付</a><a>货到付款</a><a>发票说明</a>
          </div>
          <div class="footer-col">
            <h5>售后服务</h5>
            <a>售后政策</a><a>退款说明</a><a>取消订单</a><a>投诉建议</a>
          </div>
          <div class="footer-col">
            <h5>法律条款</h5>
            <a @click="openLegal('TERMS')">用户协议</a><a @click="openLegal('PRIVACY')">隐私政策</a>
          </div>
        </div>
      </div>
      <div class="footer-copy">© 2026 Supermarket Mall 超市购物系统 · 新鲜好物，一站购齐</div>
    </footer>

    <div v-if="confirmDialog.open" class="modal-mask modal-mask--float" @click.self="resolveConfirm(false)">
      <div class="modal" role="dialog" aria-modal="true">
        <h3>{{ confirmDialog.title }}</h3>
        <p class="modal-message">{{ confirmDialog.message }}</p>
        <div v-if="confirmDialog.details?.length" class="modal-detail">
          <div v-for="detail in confirmDialog.details" :key="detail.label">
            <span>{{ detail.label }}</span>
            <strong>{{ detail.value }}</strong>
          </div>
        </div>
        <div class="modal-actions">
          <button class="ghost" @click="resolveConfirm(false)">再想想</button>
          <button :class="{ danger: confirmDialog.danger }" @click="resolveConfirm(true)">{{ confirmDialog.confirmText }}</button>
        </div>
      </div>
    </div>

    <div v-if="alertDialog.open" class="modal-mask modal-mask--float" @click.self="closeAlert">
      <div class="modal alert-modal" :class="`alert-${alertDialog.type}`" role="dialog" aria-modal="true">
        <div class="alert-badge">{{ alertDialog.type === 'error' ? '!' : (alertDialog.type === 'success' ? '✓' : 'i') }}</div>
        <h3>{{ alertDialog.title }}</h3>
        <p class="modal-message">{{ alertDialog.message }}</p>
        <div class="modal-actions alert-actions">
          <button @click="closeAlert">我知道了</button>
        </div>
      </div>
    </div>

    <!-- 全局成功提示（run(action, message) 的消息）：3 秒自动消失 -->
    <Transition name="notice-fade">
      <div v-if="notice" class="app-toast" role="status">{{ notice }}</div>
    </Transition>

    <!-- 登录 / 注册 合一弹窗 -->
    <div v-if="authOpen" class="modal-mask" @click.self="!forcedChange && closeAuth()">
      <div class="modal auth-modal" role="dialog" aria-modal="true">
        <button v-if="!forcedChange" class="modal-close" @click="closeAuth" aria-label="关闭">×</button>
        <div v-if="authTab === 'login' || authTab === 'register'" class="auth-tabs">
          <button :class="{ active: authTab === 'login' }" type="button" @click="switchAuth('login')">登录</button>
          <button :class="{ active: authTab === 'register' }" type="button" @click="switchAuth('register')">注册</button>
        </div>
        <div v-else-if="authTab === 'change'" class="auth-change-title">修改密码</div>
        <div v-else class="auth-change-title">找回密码</div>

        <div v-if="error && authOpen" class="auth-error-banner" role="alert">⚠ {{ error }}</div>

        <form v-if="authTab === 'login'" class="auth-form" @submit.prevent="submitLogin">
          <label class="auth-field">
            <span>用户名</span>
            <input v-model="loginForm.username" placeholder="请输入用户名" autocomplete="username" />
            <small v-if="authErrors.username" class="field-hint warn">{{ authErrors.username }}</small>
          </label>
          <label class="auth-field">
            <span>密码</span>
            <input v-model="loginForm.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
            <small v-if="authErrors.password" class="field-hint warn">{{ authErrors.password }}</small>
          </label>
          <label class="auth-remember">
            <input type="checkbox" v-model="loginForm.remember" /> 记住我
            <span class="auth-forgot" @click="forgotPassword">忘记密码？</span>
          </label>
          <button class="auth-submit" type="submit" :disabled="authSubmitting">{{ authSubmitting ? '登录中…' : '登录' }}</button>
        </form>

        <form v-else-if="authTab === 'register'" class="auth-form" @submit.prevent="submitRegister">
          <label class="auth-field">
            <span>用户名 <i class="req">*</i></span>
            <input v-model="registerForm.username" placeholder="3-50 个字符" autocomplete="username" />
            <small v-if="authErrors.username" class="field-hint warn">{{ authErrors.username }}</small>
          </label>
          <label class="auth-field">
            <span>密码 <i class="req">*</i></span>
            <input v-model="registerForm.password" type="password" placeholder="至少 6 位" autocomplete="new-password" />
            <small v-if="authErrors.password" class="field-hint warn">{{ authErrors.password }}</small>
          </label>
          <label class="auth-field">
            <span>确认密码 <i class="req">*</i></span>
            <input v-model="registerForm.confirmPassword" type="password" placeholder="再次输入密码" autocomplete="new-password" />
            <small v-if="authErrors.confirmPassword" class="field-hint warn">{{ authErrors.confirmPassword }}</small>
          </label>
          <label class="auth-field">
            <span>昵称</span>
            <input v-model="registerForm.nickname" placeholder="不填则默认等于用户名" autocomplete="nickname" />
          </label>
          <label class="auth-field">
            <span>手机号 <i class="req">*</i></span>
            <input v-model="registerForm.phone" placeholder="用于收货通知" autocomplete="tel" />
            <small v-if="authErrors.phone" class="field-hint warn">{{ authErrors.phone }}</small>
          </label>
          <label class="auth-field">
            <span>邮箱</span>
            <input v-model="registerForm.email" placeholder="选填" autocomplete="email" />
            <small v-if="authErrors.email" class="field-hint warn">{{ authErrors.email }}</small>
          </label>
          <label class="auth-agree">
            <input type="checkbox" v-model="registerForm.agree" />
            我已阅读并同意 <span class="auth-link" @click="openLegal('TERMS')">《用户协议》</span>
            与 <span class="auth-link" @click="openLegal('PRIVACY')">《隐私政策》</span>
            <small v-if="authErrors.agree" class="field-hint warn">{{ authErrors.agree }}</small>
          </label>
          <button class="auth-submit" type="submit" :disabled="authSubmitting">{{ authSubmitting ? '注册中…' : '注册并领取新人券' }}</button>
          <p class="auth-tip">注册即自动发放新人券 🎁</p>
        </form>

        <form v-else-if="authTab === 'change'" class="auth-form" @submit.prevent="submitChangePassword">
          <p v-if="forcedChange" class="auth-tip warn-tip">
            管理员已为你重置密码，请先用临时密码设置新密码，改完即可正常使用。
          </p>
          <label class="auth-field">
            <span>{{ forcedChange ? '临时密码' : '原密码' }}</span>
            <input v-model="changeForm.currentPassword" type="password" placeholder="请输入当前密码" autocomplete="current-password" />
            <small v-if="changeErrors.currentPassword" class="field-hint warn">{{ changeErrors.currentPassword }}</small>
          </label>
          <label class="auth-field">
            <span>新密码</span>
            <input v-model="changeForm.newPassword" type="password" placeholder="6-50 位字符" autocomplete="new-password" />
            <small v-if="changeErrors.newPassword" class="field-hint warn">{{ changeErrors.newPassword }}</small>
          </label>
          <label class="auth-field">
            <span>确认新密码</span>
            <input v-model="changeForm.confirmPassword" type="password" placeholder="再次输入新密码" autocomplete="new-password" />
            <small v-if="changeErrors.confirmPassword" class="field-hint warn">{{ changeErrors.confirmPassword }}</small>
          </label>
          <button class="auth-submit" type="submit" :disabled="changeSubmitting">{{ changeSubmitting ? '提交中…' : '确认修改' }}</button>
        </form>

        <!-- 忘记密码：本项目没有邮件/短信通道，不做自助重置，只受理申请由客服核对身份后发临时密码 -->
        <form v-else class="auth-form" @submit.prevent="submitPasswordReset">
          <p class="auth-tip">
            出于安全考虑，找回密码需要人工核对身份。提交申请后，客服会在 1 个工作日内
            通过你留下的联系方式告知临时密码，首次登录后需立即修改。
          </p>
          <label class="auth-field">
            <span>账号 <i class="req">*</i></span>
            <input v-model="resetForm.username" placeholder="请输入用户名" autocomplete="username" />
            <small v-if="resetErrors.username" class="field-hint warn">{{ resetErrors.username }}</small>
          </label>
          <label class="auth-field">
            <span>联系电话 <i class="req">*</i></span>
            <input v-model="resetForm.contact" placeholder="便于客服核对身份后联系你" autocomplete="tel" />
            <small v-if="resetErrors.contact" class="field-hint warn">{{ resetErrors.contact }}</small>
          </label>
          <button class="auth-submit" type="submit" :disabled="resetSubmitting">{{ resetSubmitting ? '提交中…' : '提交找回申请' }}</button>
          <p class="auth-tip"><span class="auth-link" @click="switchAuth('login')">返回登录</span></p>
        </form>
      </div>
    </div>
  </main>
</template>































<script setup>
import * as echarts from 'echarts';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, provide } from 'vue';
import { api, setToken } from './api/client';
import ImageUpload from './components/ImageUpload.vue';
import ProductCard from './components/ProductCard.vue';
import StarRating from './components/StarRating.vue';
import CouponCard from './components/CouponCard.vue';
import AddressCard from './components/AddressCard.vue';
import { money, initials, formatRole, orderStatusLabel, fulfillmentLabel, formatPaymentStatus, formatRefundStatus, refundStatusTag, formatCouponStatus, formatDate, formatProductStatus, orderStatusTag, formatUnit, resolveUnit, discountSave, discountRate, itemOriginalSave, imgFallback } from './utils/format';
import AdminPanel from './components/AdminPanel.vue';
import { useRoute, useRouter } from 'vue-router';
import { useCartStore } from './stores/cart';
import { useUserStore } from './stores/user';

const view = ref('shop');
const router = useRouter();
const route = useRoute();
const cartStore = useCartStore();
const userStore = useUserStore();

// 头部搜索：跳转到商城并把关键词写入路由 query，ShopPage 挂载/监听后应用到筛选
const headerKeyword = ref('');
function goSearch() {
  const kw = (headerKeyword.value || '').trim();
  router.push({ name: 'shop', query: kw ? { kw } : {} });
}

// 头部热词：一键填充并搜索
function quickSearch(kw) {
  headerKeyword.value = kw;
  goSearch();
}

// 页脚订阅
const subEmail = ref('');
const subMsg = ref('');
function footerSubscribe() {
  const v = (subEmail.value || '').trim();
  const ok = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
  subMsg.value = ok ? '订阅成功，优惠情报将第一时间送达' : '请输入有效的邮箱地址';
  if (ok) subEmail.value = '';
}

function navigate(name, params) {
  closeAccountMenu();
  router.push(params ? { name, params } : { name });
}

// 路由 -> 视图镜像 + 深层链接数据恢复（替代原自制 hash 的 restoreRoute）。
// product/orderDetail 的数据加载保留在 openProductDetail/openOrderDetail 内，
// 这里统一在路由命中对应视图时触发，避免前进/后退/刷新丢失数据。
let productNavLock = false;
let orderNavLock = false;
async function syncRoute() {
  const name = route.name;
  view.value = name;
  ensureAllowedView();
  if (name === 'product' && route.params.id) {
    const id = Number(route.params.id);
    if (productNavLock) return;
    if (productDetail.data?.id === id) return;
    await openProductDetail({ id }, { fromHistory: true });
  } else if (name === 'orderDetail' && route.params.id) {
    const id = route.params.id;
    if (orderNavLock) return;
    if (orderDetail.data?.id === id) return;
    await openOrderDetail({ id });
  }
}
watch(() => route.fullPath, syncRoute);

const notice = ref('');

// 全局成功提示条（notice 由 run(action, message) 与各处写入）：3 秒自动消失
let flashTimer = null;
watch(notice, (msg) => {
  if (flashTimer) { clearTimeout(flashTimer); flashTimer = null; }
  if (msg) flashTimer = setTimeout(() => { notice.value = ''; flashTimer = null; }, 3200);
});
const error = ref('');
const avatarInput = ref(null);

// 右上角「我的」下拉：导航收敛后，个人中心的全部入口都收在这里。
// ⚠️ 宿主 .account-menu-wrap 必须 position:relative，否则 absolute 面板会挂到视口上（同 .nav-badge 的教训）。
const accountMenuOpen = ref(false);
function toggleAccountMenu() { accountMenuOpen.value = !accountMenuOpen.value; }
function closeAccountMenu() { accountMenuOpen.value = false; }
function onDocumentClick(event) { if (!event.target.closest('.account-menu-wrap')) closeAccountMenu(); }
function onDocumentKeydown(event) { if (event.key === 'Escape') closeAccountMenu(); }
const categories = ref([]);
const products = reactive({ items: [], page: 1, size: 12, total: 0 });
const adminProducts = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminOrders = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminStatsOverview = ref(null);
const adminUsers = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminAnnouncements = ref([]);
const announcementForm = reactive({ id: null, title: '', content: '', type: 'NOTICE', sortOrder: 0, enabled: true });
const announcementFormOpen = ref(false);
const adminBanners = ref([]);
const bannerForm = reactive({ id: null, imageUrl: '', linkProductId: null, sortOrder: 0, enabled: true });
const bannerFormOpen = ref(false);
const bannerUploading = ref(false);
const cart = reactive({ items: [], selectedCount: 0, selectedAmount: 0 });
// 商品星级聚合：{ productId: { avg, count } }，商品卡/详情展示平均星级
const ratingSummaryMap = ref({});

/* ===== 游客购物车：未登录先本地暂存，登录后并入服务端购物车 ===== */
const GUEST_CART_KEY = 'supermarket_guest_cart';
const guestCartRows = ref([]); // [{ productId, quantity, skuSpec }]
const guestProductCache = new Map(); // productId -> 商品快照（渲染本地车用）
const pendingCheckout = ref(false); // 游客点结算 → 登录完成后继续去结算

function readGuestCart() {
  try { const raw = localStorage.getItem(GUEST_CART_KEY); return raw ? JSON.parse(raw) : []; } catch { return []; }
}
function writeGuestCart() {
  localStorage.setItem(GUEST_CART_KEY, JSON.stringify(guestCartRows.value));
}
function persistGuestFromItems() {
  guestCartRows.value = (cart.items || []).map((i) => ({ productId: i.productId, quantity: Number(i.quantity) || 1, skuSpec: i.skuSpec || '' }));
  writeGuestCart();
}
function recomputeCartTotals() {
  const selected = (cart.items || []).filter((i) => i.selected !== false);
  cart.selectedCount = selected.reduce((s, i) => s + Number(i.quantity || 0), 0);
  cart.selectedAmount = +selected
    .reduce((s, i) => s + Number(i.productPrice || 0) * Number(i.quantity || 0), 0)
    .toFixed(2);
  // 游客态没有后端购物车接口，这里按后端同口径本地预估活动优惠（门槛校验 + 取减免最大者）
  const act = estimateGuestActivity(selected);
  cart.activityDiscount = act.activityDiscount;
  cart.activityName = act.activityName;
}
// 与后端 evaluateBestActivity 同口径的本地预估：仅用于游客购物车展示
function estimateGuestActivity(selected) {
  if (!selected?.length || !activeActivities.value.length) return { activityDiscount: 0, activityName: null };
  const byProduct = new Map();
  for (const it of selected) {
    const pid = Number(it.productId);
    byProduct.set(pid, (byProduct.get(pid) || 0) + Number(it.productPrice || 0) * Number(it.quantity || 0));
  }
  let best = 0, bestName = null;
  for (const a of activeActivities.value) {
    const threshold = Number(a.threshold || 0);
    const discount = Number(a.discount || 0);
    if (!(threshold > 0) || !(discount > 0)) continue;
    const scope = String(a.scope || 'ALL');
    let qualifying = 0;
    for (const it of selected) {
      const pid = Number(it.productId);
      if (scope === 'ALL') qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
      else if (scope === 'PRODUCT' && Number(a.productId) === pid) qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
      else if (scope === 'CATEGORY' && a.categoryId != null && Number(a.categoryId) === Number(it.categoryId)) qualifying += Number(it.productPrice || 0) * Number(it.quantity || 0);
    }
    if (qualifying < threshold) continue;
    const off = (a.type === 'DISCOUNT' && discount > 0 && discount < 1)
      ? +(qualifying * (1 - discount)).toFixed(2)
      : discount;
    if (off > best) { best = off; bestName = a.name || null; }
  }
  return { activityDiscount: best, activityName: bestName };
}
// 用本地行 + 商品快照重建与后端一致形状的 cart（字段名对齐 CartItemResponse），页面无需分叉
async function refreshGuestCartView() {
  const rows = readGuestCart();
  if (!rows.length) { cart.items = []; cart.selectedCount = 0; cart.selectedAmount = 0; return; }
  const items = [];
  for (const row of rows) {
    let p = guestProductCache.get(row.productId);
    if (!p || (p.price == null && p.productPrice == null)) {
      try { p = await api.get(`/products/${row.productId}`); guestProductCache.set(row.productId, p); } catch { /* 商品可能已下架 */ }
    }
    if (!p) continue;
    const price = Number(p.price ?? p.productPrice ?? 0);
    const orig = Number(p.originalPrice ?? p.productOriginalPrice ?? price);
    const stock = Number(p.stock ?? 0);
    const qty = Math.max(1, Math.min(Number(row.quantity) || 1, Math.max(stock, 1)));
    items.push({
      id: 'g' + row.productId + '|' + (row.skuSpec || ''),
      productId: p.id ?? row.productId,
      categoryId: p.categoryId ?? null,
      productName: p.name || '商品',
      productCoverUrl: p.coverUrl || '',
      skuSpec: row.skuSpec || '',
      productPrice: price,
      productOriginalPrice: orig,
      stock,
      unit: p.unit || '',
      quantity: qty,
      selected: true,
      subtotalAmount: +(price * qty).toFixed(2),
    });
    if (qty !== (Number(row.quantity) || 1)) row.quantity = qty;
  }
  if (JSON.stringify(rows) !== JSON.stringify(guestCartRows.value)) { guestCartRows.value = rows; writeGuestCart(); }
  cart.items = items;
  recomputeCartTotals();
}
async function guestAdd(product, quantity = 1, skuSpec = '') {
  const q = Math.max(1, Number(quantity) || 1);
  const stock = Number(product.stock ?? product.stockQuantity ?? 0);
  const rows = readGuestCart();
  const idx = rows.findIndex((r) => r.productId === product.id && (r.skuSpec || '') === (skuSpec || ''));
  const cur = idx >= 0 ? Number(rows[idx].quantity) || 0 : 0;
  if (stock > 0 && cur + q > stock) { fail(`库存不足：仅剩 ${stock} 件，购物车中已有 ${cur} 件`, '库存不足'); return false; }
  if (idx >= 0) rows[idx].quantity = cur + q; else rows.push({ productId: product.id, quantity: q, skuSpec: skuSpec || '' });
  guestCartRows.value = rows;
  writeGuestCart();
  if (product?.id) guestProductCache.set(product.id, product);
  await refreshGuestCartView();
  return true;
}
function guestRemoveItem(id) {
  cart.items = (cart.items || []).filter((i) => i.id !== id);
  persistGuestFromItems();
  recomputeCartTotals();
}
function guestClear() {
  cart.items = [];
  cart.selectedCount = 0;
  cart.selectedAmount = 0;
  guestCartRows.value = [];
  localStorage.removeItem(GUEST_CART_KEY);
}
async function mergeGuestCartToServer() {
  const rows = readGuestCart();
  if (!rows.length || !session.user) return;
  let added = 0;
  let merged = 0;
  for (const row of rows) {
    try {
      await api.post('/cart/items', { productId: row.productId, quantity: Math.max(1, Number(row.quantity) || 1), skuSpec: row.skuSpec || undefined });
      added += Math.max(1, Number(row.quantity) || 1);
      merged += 1;
    } catch (e) {
      if (e?.authExpired) throw e;
      fail(e?.message || '部分本地商品未能并入购物车');
    }
  }
  guestCartRows.value = [];
  localStorage.removeItem(GUEST_CART_KEY);
  guestProductCache.clear();
  if (added > 0) {
    notice.value = `已将本地购物车 ${merged} 种 / ${added} 件商品并入你的账户`;
    await loadCart();
  }
}
// 活动规则缓存拉取（凑单进度条用；公开接口，游客也可调）
async function ensureActiveActivities() {
  if (activeActivities.value.length) return;
  try { activeActivities.value = (await api.get('/activities/active')) || []; } catch { /* 非关键 */ }
}
// 活动口号：通栏横幅/商品角标共用
function activitySlogan(a) {
  if (!a) return '';
  const t = Number(a.threshold || 0);
  return a.type === 'DISCOUNT'
    ? `满${t}打${Math.round(Number(a.discount) * 10)}折`
    : `满${t}减${Number(a.discount)}`;
}
// 全站通栏首推：按「满额时的实际减免」取力度最大者
// 顶部公告带单条文案：活动名里已含「满/折/减」语义时只显名字，避免与口号重复
function activityNoticeText(a) {
  const name = String(a.name || '').trim();
  const slogan = activitySlogan(a);
  if (/满|折|减/.test(name)) return name;
  return name ? `${name} · ${slogan}` : slogan;
}
// 公告带轮播：多个营销活动 + 新人福利轮流展示（4s 一换）
const noticeIndex = ref(0);
let noticeTimer = null;
const noticeList = computed(() => {
  const items = (activeActivities.value || [])
    .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0)
    .map((a) => `限时活动 ${activityNoticeText(a)}`);
  items.push('新人首单立减 ¥20，再送 3 张满减券');
  return items;
});
const rotatingNotice = computed(() => noticeList.value[noticeIndex.value % noticeList.value.length] || '');
// 到达门槛后能减多少 —— 与后端 evaluateBestActivity「取减免最大者」同一口径
function activityOffset(a) {
  return a.type === 'DISCOUNT'
    ? Number(a.threshold) * (1 - Number(a.discount))
    : Number(a.discount);
}

// 商品是否命中该活动（ALL / CATEGORY / PRODUCT 三种范围）
function activityMatches(a, product) {
  const scope = String(a.scope || 'ALL');
  if (scope === 'PRODUCT') return Number(a.productId) === Number(product.id);
  if (scope === 'CATEGORY') return a.categoryId != null && Number(a.categoryId) === Number(product.categoryId);
  return true;
}

const topActivity = computed(() => [...(activeActivities.value || [])]
  .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0)
  .sort((x, y) => activityOffset(y) - activityOffset(x))[0] || null);
// 商品卡角标：只在活动**限定了分类或单品**（scope ≠ ALL）时才显示，且取命中的活动里「最省」的那个。
//
// 为什么排除全场活动：全场活动对每一张商品卡都是同一句话，一屏重复几十次没有任何信息量，
// 反而把真正有区分度的「分类专属 / 单品专属」活动淹没了；全场活动交给页头公告条统一宣传。
// 为什么取「最省」而不是「第一个命中的」：角标必须与结算实际生效的那条一致，
// 否则会出现「卡上写满200打8折、实付按满200减50」，被当成欺骗。
function productActivityTag(product) {
  const hits = (activeActivities.value || [])
    .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0)
    .filter((a) => String(a.scope || 'ALL') !== 'ALL')
    .filter((a) => activityMatches(a, product));
  if (!hits.length) return '';
  return activitySlogan([...hits].sort((x, y) => activityOffset(y) - activityOffset(x))[0]);
}
// 凑单进度条：与后端同口径——只有达到门槛的活动才生效，实际生效取「减免金额最大者」，
// 未达门槛时宣传的也是「达到门槛后减免最大」的同一活动，保证达标前后文案一致。
const cartActivityProgress = computed(() => {
  const amount = cartLocalTotal.value;
  const rules = (activeActivities.value || [])
    .filter((a) => Number(a.threshold || 0) > 0 && Number(a.discount || 0) > 0 && String(a.scope || 'ALL') === 'ALL');
  if (!rules.length || amount <= 0) return null;
  const discountOf = (a, base) => {
    if (base < Number(a.threshold)) return 0;
    if (a.type === 'DISCOUNT') {
      const rate = Number(a.discount);
      return (rate > 0 && rate < 1) ? Math.round(base * (1 - rate) * 100) / 100 : 0;
    }
    return Number(a.discount);
  };
  const benefitOf = (a) => a.type === 'DISCOUNT'
    ? `打 ${Math.round(Number(a.discount) * 10)} 折`
    : `立减 ¥${Number(a.discount)}`;
  // 已达门槛的活动里取减免最大者（与后端 evaluateBestActivity 一致）
  const bestApplied = rules
    .map((rule) => ({ rule, off: discountOf(rule, amount) }))
    .sort((x, y) => y.off - x.off)[0];
  // 尚未达标的活动里，按「到达门槛后能减多少」取最优者，作为凑单目标
  const bestUpcoming = rules
    .filter((a) => amount < Number(a.threshold))
    .map((rule) => ({ rule, off: discountOf(rule, Number(rule.threshold)) }))
    .sort((x, y) => y.off - x.off || Number(x.rule.threshold) - Number(y.rule.threshold))[0];
  // 若继续凑单能换到更大优惠，优先展示凑单提示（当前已享优惠仍由合计区展示）
  if (bestUpcoming && bestUpcoming.off > (bestApplied ? bestApplied.off : 0)) {
    const target = bestUpcoming.rule;
    return {
      benefit: benefitOf(target),
      gap: Math.max(Number(target.threshold) - amount, 0),
      threshold: Number(target.threshold),
      amount,
      percent: Math.min(100, Math.round((amount / Number(target.threshold)) * 100)),
      reachedTop: false,
    };
  }
  if (!bestApplied) return null;
  return {
    benefit: benefitOf(bestApplied.rule),
    gap: 0,
    threshold: Number(bestApplied.rule.threshold),
    amount,
    percent: 100,
    reachedTop: true,
  };
});
const orders = reactive({ items: [] });
const addresses = ref([]);
const selectedAddressId = ref(null);
const coupons = ref([]);
const myCoupons = ref([]);
const usableCoupons = ref([]);
const selectedUserCouponId = ref('');
const userOptedOutCoupon = ref(false);
const paying = ref(false);
const cartSyncTimers = {};
const reviewedMap = reactive({});
const refundOrders = reactive({ items: [], page: 1, size: 10, total: 0 });
const stockAlerts = ref([]);
const adminCoupons = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminMenu = ref('dashboard');
const refundForm = reactive({ orderId: null, reason: '' });
const reviewForm = reactive({ orderId: null, rating: 5, content: '', images: [] });
const confirmDialog = reactive({
  open: false,
  title: '',
  message: '',
  details: [],
  confirmText: '确认',
  danger: false,
  resolver: null,
});
// 失败/提示弹窗：操作未通过时弹出，避免仅顶部状态栏提示被忽略
const alertDialog = reactive({
  open: false,
  title: '提示',
  message: '',
  type: 'error',
  timer: null,
  resolver: null,
});
const productDetail = reactive({ data: null, reviews: [], loading: false });
const orderDetail = reactive({ data: null, loading: false, error: '' });
const detailQuantity = ref(1);
const currentImageIndex = ref(0);
const wallet = reactive({ balance: 0, recentTransactions: [] });

function safeParseSpec(str) {
  try { return JSON.parse(str || '{}') || {}; } catch (e) { return {}; }
}

const galleryImages = computed(() => {
  const d = productDetail.data;
  if (!d) return [];
  const imgs = (d.images || []).map((it) => it.url).filter(Boolean);
  if (d.coverUrl && !imgs.includes(d.coverUrl)) imgs.unshift(d.coverUrl);
  return imgs;
});
const currentGalleryImage = computed(() => galleryImages.value[currentImageIndex.value] || '');

const specDimensions = computed(() => {
  const skus = productDetail.data?.skus || [];
  const dims = {};
  for (const sku of skus) {
    const spec = safeParseSpec(sku.specJson);
    for (const key of Object.keys(spec)) {
      if (!dims[key]) dims[key] = [];
      if (!dims[key].includes(spec[key])) dims[key].push(spec[key]);
    }
  }
  return dims;
});

const selectedSku = computed(() => {
  const skus = productDetail.data?.skus || [];
  if (!Object.keys(selectedSpec).length) return null;
  return skus.find((sku) => {
    const spec = safeParseSpec(sku.specJson);
    return Object.keys(spec).every((k) => String(spec[k]) === String(selectedSpec[k]));
  }) || null;
});

const selectedSpecText = computed(() =>
  Object.keys(selectedSpec).sort().map((k) => `${k}:${selectedSpec[k]}`).join(' '));
const recharge = reactive({
  step: 'form', // form | paying | success | expired
  amount: 100,
  method: 'ALIPAY', // ALIPAY | WECHAT
  customAmount: '',
  order: null,
  countdown: 0,
  timer: null,
  paying: false,
});
const rechargePresets = [50, 100, 200, 500];
const trendChartEl = ref(null);
const salesChartEl = ref(null);
let trendChart = null;
let salesChart = null;


const session = reactive({
  user: JSON.parse(localStorage.getItem('supermarket_user') || 'null'),

});
const authOpen = ref(false);
const authTab = ref('login');
const authSubmitting = ref(false);
const loginForm = reactive({ username: '', password: '', remember: true });
const registerForm = reactive({ username: '', password: '', confirmPassword: '', nickname: '', phone: '', email: '', agree: false });
const authErrors = reactive({});
const changeForm = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const changeErrors = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' });
const changeSubmitting = ref(false);
// 强制改密：管理员重置过密码，登录后必须先改密。此时弹窗不可关闭（关闭按钮/遮罩点击都禁掉）。
const forcedChange = ref(false);
// 找回密码申请（忘记密码）：只提交申请，由客服核对身份后发临时密码
const resetForm = reactive({ username: '', contact: '' });
const resetErrors = reactive({ username: '', contact: '' });
const resetSubmitting = ref(false);
const filters = reactive({ categoryId: '', keyword: '', minPrice: '', maxPrice: '', brand: '', sort: '' });
const addressForm = reactive({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: true });
const productForm = reactive({ categoryId: '', sku: '', name: '', subtitle: '', description: '', price: 0, originalPrice: '', memberPrice: '', stock: 0, unit: 'piece', customUnit: '', brand: '', isHot: false, isNew: false, tags: '', images: [], skus: [], attributes: [] });
const hotProducts = ref([]);
const newProducts = ref([]);
const relatedProducts = ref([]);
const guessProducts = ref([]);
const dwellRankProducts = ref([]);
const activeActivities = ref([]);
const dwellEnterTs = ref(0);
const dwellProductId = ref(null);
const dwellSource = ref('detail');
const selectedSpec = reactive({});
// 打开编辑表单时记录当时的库存，仅当管理员改动库存字段时才随表单提交，
// 避免把打开表单瞬间可能已过期的库存值覆盖真实库存。

// 计价单位选项：与后端 formatUnit 映射保持一致；custom 表示管理员自定义单位

// 管理后台商品列表：查询条件与分页状态
const adminProductKeyword = ref('');
const adminProductStatus = ref('');
const adminChartProducts = ref([]);
const adminJumpPage = ref(1);

// 管理后台订单列表：查询条件与分页状态
const adminOrderKeyword = ref('');
const adminOrderStatus = ref('');
const adminOrderJumpPage = ref(1);

// 管理后台退款（售后）列表：筛选与分页状态
const refundStatusFilter = ref('APPLYING');
const refundJumpPage = ref(1);

// 管理后台用户列表：查询条件与分页状态
const adminUserKeyword = ref('');
const adminUserRole = ref('');
const adminUserStatus = ref('');
const adminUserJumpPage = ref(1);

// 管理后台优惠券列表：查询条件与分页状态
const adminCouponKeyword = ref('');
const adminCouponJumpPage = ref(1);

// 分类管理：基于全量分类（商品表单也要用），前端做筛选 + 分页

// 库存预警：基于全量预警（数量少），前端做筛选 + 分页







const isAdmin = computed(() => session.user?.role === 'ADMIN');


const currentTitle = computed(() => ({
  shop: { eyebrow: '商品', title: isAdmin.value ? '上架商品查看' : '商品选购' },
  product: { eyebrow: '商品详情', title: productDetail.data?.name || '商品详情' },
  cart: { eyebrow: '购物车', title: '购物车' },
  checkout: { eyebrow: '订单', title: '确认订单' },
  orders: { eyebrow: '订单', title: '我的订单' },
  orderDetail: { eyebrow: '订单', title: '订单详情' },
  coupons: { eyebrow: '优惠', title: '优惠券' },
  addresses: { eyebrow: '地址', title: '收货地址' },
  recharge: { eyebrow: '钱包', title: '账户充值' },
  points: { eyebrow: '会员', title: '我的积分' },
  favorites: { eyebrow: '收藏', title: '我的收藏' },
  messages: { eyebrow: '通知', title: '消息中心' },
  terms: { eyebrow: '条款', title: '用户协议' },
  privacy: { eyebrow: '条款', title: '隐私政策' },
  admin: { eyebrow: '后台', title: '后台管理' },

}[view.value] || { eyebrow: '商品', title: '商品选购' }));

// 二级页页头刷新：订单/优惠券页把刷新动作收进页头，避免卡片里再放一个孤立按钮
async function refreshCurrentPage() {
  if (view.value === 'orders') await loadOrders();
  else if (view.value === 'coupons') await loadCoupons();
  else if (view.value === 'points') { await loadMemberProfile(); await loadMemberLedger(1); }
  else if (view.value === 'favorites') { await Promise.all([loadFavorites(), loadPriceAlerts(), loadAlertUnread()]); }
  else if (view.value === 'messages') { await Promise.all([loadMessages(), loadMessageUnread()]); }
}

const selectedCoupon = computed(() => usableCoupons.value.find((coupon) => coupon.id === selectedUserCouponId.value) || null);

// 购物车实时合计：只累加「已勾选」商品（与后端 selectedAmount 口径一致）
const cartLocalTotal = computed(() => (cart.items || [])
  .filter((item) => item.selected !== false)
  .reduce((sum, item) => sum + Number(item.productPrice || 0) * Number(item.quantity || 0), 0));

// 划线价（原价）相对现价的优惠合计：仅统计已勾选商品，纯展示用、不计入应付
const cartOriginalSave = computed(() => (cart.items || [])
  .filter((item) => item.selected !== false)
  .reduce((sum, item) => sum + itemOriginalSave(item), 0));

// 顶栏购物车角标：购物车内商品总件数（不区分是否勾选）
const cartBadgeCount = computed(() => (cart.items || [])
  .reduce((sum, item) => sum + Number(item.quantity || 0), 0));

const orderPayPreview = computed(() => {
  const total = cartLocalTotal.value;
  let pay = total;
  if (selectedCoupon.value) pay = Math.max(pay - Number(selectedCoupon.value.discountAmount || 0), 0);
  if (Number(cart.activityDiscount) > 0) pay = Math.max(pay - Number(cart.activityDiscount || 0), 0);
  return pay;
});

// 已勾选件数（结算明细展示用）
const cartSelectedQty = computed(() => (cart.items || [])
  .filter((item) => item.selected !== false)
  .reduce((sum, item) => sum + Number(item.quantity || 0), 0));

// 实际抵扣口径的共省金额：活动优惠 + 优惠券（划线价已体现在现价里，单列展示不计入）
const cartTotalSaved = computed(() =>
  Number(cart.activityDiscount || 0)
  + (selectedCoupon.value ? Number(selectedCoupon.value.discountAmount || 0) : 0));

const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) || null);

// ---------------- 会员积分体系（前端状态 + 结算预览） ----------------
const memberProfile = reactive({
  points: 0, memberLevel: 0, levelName: '', totalSpent: 0,
  discountRate: 1, nextLevelThreshold: null, nextLevelName: '', progressToNext: 0, maxRedeemRatio: 0.5,
});
const memberLedger = reactive({ items: [], total: 0, page: 1, size: 10, loading: false });
const memberLevels = ref([]);
const usePoints = ref(false);
const pointsToUse = ref(0);

// 等级档位兜底（与后端 MemberService 常量一致）：/member/levels 未就绪时也不漏算会员折扣
const TIER_NAMES_FALLBACK = ['普通会员', '银卡会员', '金卡会员', '钻石会员'];
const TIER_RATES_FALLBACK = [1, 0.98, 0.95, 0.90];

function tierRateForLevel(level) {
  const lv = Number(level) || 0;
  const t = memberLevels.value.find((x) => x.level === lv);
  if (t) return Number(t.rate);
  if (Number(memberProfile.memberLevel) === lv && memberProfile.discountRate != null) {
    return Number(memberProfile.discountRate);
  }
  return TIER_RATES_FALLBACK[lv] != null ? TIER_RATES_FALLBACK[lv] : 1;
}
function tierNameFor(level) {
  const lv = Number(level) || 0;
  const t = memberLevels.value.find((x) => x.level === lv);
  if (t) return t.name;
  return TIER_NAMES_FALLBACK[lv] || '普通会员';
}
function round2(n) { return Math.round((Number(n) || 0) * 100) / 100; }

async function loadMemberLevels() {
  // 该接口需要登录：未登录时会 401（静默忽略，结算时由 tierRateForLevel 兜底）
  try { memberLevels.value = (await api.get('/member/levels')) || []; } catch (e) { /* ignore */ }
}
async function loadMemberProfile() {
  if (!session.user || isAdmin.value) return;
  try { Object.assign(memberProfile, (await api.get('/member/profile')) || {}); } catch (e) { /* ignore */ }
}
async function loadMemberLedger(page = 1) {
  if (!session.user || isAdmin.value) return;
  memberLedger.loading = true;
  try {
    const data = await api.get(`/member/ledger?page=${page}&size=${memberLedger.size}`);
    if (data) {
      memberLedger.items = data.items || [];
      memberLedger.total = data.total || 0;
      memberLedger.page = page;
    }
  } catch (e) { /* ignore */ } finally { memberLedger.loading = false; }
}

// 结算预览：会员折扣 + 积分抵现（与后端 OrderService 口径一致；当前目录无会员价，cartLocalTotal 即后端 totalAmount）
const memberPreview = computed(() => {
  const amountAfterPromo = Number(orderPayPreview.value || 0);
  const level = session.user?.memberLevel || 0;
  const rate = tierRateForLevel(level);
  const memberDiscount = rate < 1 ? round2(amountAfterPromo * (1 - rate)) : 0;
  const payBeforePoints = Math.max(amountAfterPromo - memberDiscount, 0);
  const maxRedeemValue = round2(payBeforePoints * 0.5);
  const maxRedeemPoints = Math.floor(maxRedeemValue * 100);
  const userPoints = Number(session.user?.points || 0);
  let pointsUsed = 0;
  if (usePoints.value && userPoints > 0 && maxRedeemPoints > 0) {
    const want = Number(pointsToUse.value) > 0 ? Number(pointsToUse.value) : userPoints;
    pointsUsed = Math.max(0, Math.min(userPoints, want, maxRedeemPoints));
  }
  const pointsValue = round2(pointsUsed / 100);
  // 运费不参与任何折扣（券/活动/会员/积分都只作用于商品小计），最后加上去 ——
  // 与后端 OrderService 同口径，保证「商品小计 + 运费 − 券 − 活动 − 会员 − 积分 = 应付」成立
  const freight = expressFreight.value;
  const finalPay = Math.max(round2(payBeforePoints - pointsValue), 0) + freight;
  return { amountAfterPromo, memberDiscount, payBeforePoints, maxRedeemValue, maxRedeemPoints, userPoints, pointsUsed, pointsValue, freight, finalPay };
});

const balanceSufficient = computed(() => Number(wallet.balance || 0) >= memberPreview.value.finalPay);

// 勾选「使用积分抵扣」时默认填入本次可用最大积分，保证输入框数值与实际抵扣一致
watch(usePoints, (on) => {
  if (on) {
    pointsToUse.value = Math.min(memberPreview.value.userPoints, memberPreview.value.maxRedeemPoints);
  } else {
    pointsToUse.value = 0;
  }
});








/* ---------------- 收藏 + 降价提醒 ---------------- */
const favoriteIds = ref([]);
const favorites = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
const priceAlerts = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
const alertUnread = ref(0);
const pendingFavorite = ref(null);

function isFavorite(productId) {
  return favoriteIds.value.includes(Number(productId));
}

function setFavoriteLocal(productId, on) {
  const id = Number(productId);
  const next = new Set(favoriteIds.value.map(Number));
  if (on) next.add(id);
  else next.delete(id);
  favoriteIds.value = [...next];
}

async function loadFavoriteIds() {
  if (!session.user || isAdmin.value) { favoriteIds.value = []; return; }
  try { favoriteIds.value = ((await api.get('/favorites/ids')) || []).map(Number); } catch (e) { /* ignore */ }
}

async function loadFavorites(reset = true) {
  if (!session.user || isAdmin.value) { favorites.items = []; favorites.total = 0; return; }
  const nextPage = reset ? 1 : favorites.page + 1;
  favorites.loading = true;
  try {
    const data = await api.get(`/favorites?page=${nextPage}&size=${favorites.size}`);
    const items = data?.items || [];
    favorites.items = reset ? items : [...favorites.items, ...items];
    favorites.total = data?.total || 0;
    favorites.page = nextPage;
  } catch (e) { /* ignore */ } finally { favorites.loading = false; }
}

async function loadPriceAlerts(reset = true) {
  if (!session.user || isAdmin.value) { priceAlerts.items = []; priceAlerts.total = 0; return; }
  const nextPage = reset ? 1 : priceAlerts.page + 1;
  priceAlerts.loading = true;
  try {
    const data = await api.get(`/price-alerts?page=${nextPage}&size=${priceAlerts.size}`);
    const items = data?.items || [];
    priceAlerts.items = reset ? items : [...priceAlerts.items, ...items];
    priceAlerts.total = data?.total || 0;
    priceAlerts.page = nextPage;
  } catch (e) { /* ignore */ } finally { priceAlerts.loading = false; }
}

async function loadAlertUnread() {
  if (!session.user || isAdmin.value) { alertUnread.value = 0; return; }
  try {
    const data = await api.get('/price-alerts/unread-count');
    alertUnread.value = Number(data?.count || 0);
  } catch (e) { /* ignore */ }
}

async function markAlertsRead() {
  if (!session.user || isAdmin.value || alertUnread.value <= 0) return;
  try {
    await api.post('/price-alerts/read');
    alertUnread.value = 0;
    priceAlerts.items = priceAlerts.items.map((item) => ({ ...item, isRead: true }));
    notice.value = '降价提醒已全部标记为已读';
  } catch (err) {
    fail(err?.message || '操作失败，请稍后重试');
  }
}

// 收藏/取消收藏：先乐观更新心形状态，失败再回滚；未登录则引导登录并在登录后自动补做
async function toggleFavorite(product) {
  const id = Number(product?.id);
  if (!id) return;
  if (!session.user) {
    pendingFavorite.value = product;
    notice.value = '登录后即可收藏，降价时会提醒你';
    openAuth('login');
    return;
  }
  const wasFav = isFavorite(id);
  setFavoriteLocal(id, !wasFav);
  try {
    if (wasFav) await api.delete(`/favorites/${id}`);
    else await api.post(`/favorites/${id}`);
    notice.value = wasFav ? '已取消收藏' : '已收藏，降价后会在「我的收藏」提醒你';
    await loadAlertUnread();
    if (view.value === 'favorites') await Promise.all([loadFavorites(), loadPriceAlerts()]);
  } catch (err) {
    setFavoriteLocal(id, wasFav);
    fail(err?.message || '操作失败，请稍后重试');
  }
}

/* ---------------- 履约：同城即时配送 / 快递配送 / 门店自提 ---------------- */
// 三种互斥的交付形态，与后端 OrderEntity.FULFILLMENT_* 一一对应。
// 改造前的旧值 DELIVERY 已迁移为 INSTANT，这里不再产生该值。
const stores = ref([]);
const deliverySlots = ref([]);
const fulfillment = reactive({ type: 'INSTANT', storeId: null, slot: '' });

// ⚠️ 必须与后端 OrderService.EXPRESS_FREE_THRESHOLD / EXPRESS_FREIGHT 保持一致
const EXPRESS_FREE_THRESHOLD = 99;
const EXPRESS_FREIGHT = 8;

const isPickup = computed(() => fulfillment.type === 'PICKUP');
const isExpress = computed(() => fulfillment.type === 'EXPRESS');
// 快递运费：按**商品小计**判门槛（不含运费本身），即时配送与门店自提恒为 0
const expressFreight = computed(() => {
  if (!isExpress.value) return 0;
  return cartLocalTotal.value >= EXPRESS_FREE_THRESHOLD ? 0 : EXPRESS_FREIGHT;
});
// 生效门店：显式选过就用选的，否则回落到第一家营业门店（与 activeStoreId 保持一致）
const selectedStore = computed(() => {
  const id = Number(fulfillment.storeId) || (stores.value.length ? Number(stores.value[0].id) : 0);
  return stores.value.find((s) => Number(s.id) === id) || null;
});
// 自提门店的默认选择：进结算页时若没选过，自动选中第一家营业门店
const activeStoreId = computed(() => {
  if (fulfillment.storeId) return Number(fulfillment.storeId);
  return stores.value.length ? Number(stores.value[0].id) : null;
});

async function loadStores() {
  try { stores.value = (await api.get('/stores')) || []; } catch (e) { /* ignore */ }
}

async function loadDeliverySlots() {
  try { deliverySlots.value = (await api.get('/delivery-slots')) || []; } catch (e) { /* ignore */ }
}

// ===== 限时秒杀 =====
const flashSales = ref([]);
// 用本地时间戳做倒计时基准：服务端只给「剩余秒数」，前端换算成本地绝对时刻后自行走秒，
// 这样不会因为请求延迟或前后端时钟不一致而出现倒计时跳变。
const nowTick = ref(Date.now());
let flashTickTimer = null;

async function loadFlashSales() {
  try {
    const list = (await api.get('/flash-sales')) || [];
    const now = Date.now();
    flashSales.value = list.map((f) => ({
      ...f,
      // RUNNING 时 targetAt 是结束时刻，UPCOMING 时是开始时刻
      targetAt: now + Number(f.countdownSeconds || 0) * 1000
    }));
  } catch (e) { /* 秒杀是营销位，拉不到就不展示，不打扰用户 */ }
}

const runningFlashSales = computed(() => flashSales.value.filter((f) => f.state === 'RUNNING'));

function flashRemaining(sale) {
  if (!sale || !sale.targetAt) return 0;
  return Math.max(0, Math.floor((sale.targetAt - nowTick.value) / 1000));
}

// 秒杀档期可能跨天，MM:SS 不够用，这里给到「天/时/分/秒」
function formatDuration(seconds) {
  const s = Math.max(0, Math.floor(seconds || 0));
  const days = Math.floor(s / 86400);
  const hh = String(Math.floor((s % 86400) / 3600)).padStart(2, '0');
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return days > 0 ? `${days}天 ${hh}:${mm}:${ss}` : `${hh}:${mm}:${ss}`;
}

function startFlashTick() {
  if (flashTickTimer) return;
  flashTickTimer = setInterval(() => { nowTick.value = Date.now(); }, 1000);
}

// ===== 秒杀限购：把「还能买几件」提前暴露出来，而不是等用户点结算才报错 =====

/** 该商品此刻进行中的秒杀（同一商品同时只会有一个进行中的场次） */
function flashSaleOfProduct(productId) {
  const id = Number(productId);
  return flashSales.value.find((f) => f.state === 'RUNNING' && Number(f.productId) === id) || null;
}

/**
 * 该商品「我还能买几件」—— 直接读后端算好的 myRemainingQuota（= 每人限购 − 我的已购，
 * 已把未付款订单占用的名额算进去），不在前端重算一遍，避免两处口径漂移。
 * 返回 null 表示不受限购约束（游客未登录，或该场次不限购）。
 */
function flashLimitOfProduct(productId) {
  const sale = flashSaleOfProduct(productId);
  if (!sale || sale.myRemainingQuota === null || sale.myRemainingQuota === undefined) return null;
  return Number(sale.myRemainingQuota);
}

/**
 * 购物车里某行最多能加到几件 = min(库存, 秒杀还能买几件)。
 *
 * 注意 myRemainingQuota 约束的已经是「购物车件数 + 已购件数」的总和，所以直接当上限用即可，
 * 不要再减一次购物车里已有的数量，否则会少给一件，用户会觉得"明明说还能买却加不上"。
 * 结果至少为 1：即使用户已经超了（比如后台调小了限购），也要让他能把数量改小或删除。
 */
function cartQtyMax(item) {
  const stock = Number(item?.stock || 0);
  const byStock = stock > 0 ? stock : 1;
  const byFlash = flashLimitOfProduct(item?.productId);
  return byFlash === null ? byStock : Math.max(Math.min(byStock, byFlash), 1);
}

/** 该行是否因秒杀限购到顶 —— 用于把 + 置灰并在原地说明原因 */
function cartQtyCapped(item) {
  const limit = flashLimitOfProduct(item?.productId);
  return limit !== null && Number(item?.quantity || 0) >= limit;
}

/**
 * 因限购被挡时的统一文案（措辞与后端 409 对齐，用户在前端预检和后端拒绝时看到的是同一句话）。
 *
 * left = 后端算的「还能买几件」：它只扣了订单已占用的，没有扣购物车里已有的件数，
 * 所以说「购物车里最多放几件」而不是「你还能买几件」—— 否则与用户眼前看到的数量对不上，
 * 会被当成系统算错。
 */
function flashLimitMessage(productId, left) {
  const sale = flashSaleOfProduct(productId);
  const limit = Number(sale?.perUserLimit || 0);
  const cap = `「${sale?.name || '该秒杀商品'}」每人限购 ${limit} 件`;
  if (left <= 0) return cap + '，你已经买满了';
  const bought = limit - Number(left);
  return cap + `，购物车里最多放 ${left} 件` + (bought > 0 ? `（已下单占用 ${bought} 件）` : '');
}

// ===== 协议 / 隐私正文（后台可编辑，按 key 拉取） =====
const legalDocs = reactive({ data: {}, loading: false });

async function loadLegalDoc(docKey) {
  const key = String(docKey || '').toUpperCase();
  if (!key) return null;
  legalDocs.loading = true;
  try {
    legalDocs.data[key] = await api.get(`/legal-docs/${key}`);
    return legalDocs.data[key];
  } catch (e) {
    return legalDocs.data[key] || null;
  } finally {
    legalDocs.loading = false;
  }
}

// 注册弹窗里点《用户协议》《隐私政策》：新标签页打开，避免关掉弹窗丢失已填内容
function openLegal(docKey) {
  window.open(docKey === 'PRIVACY' ? '/privacy' : '/terms', '_blank', 'noopener');
}

// 三种履约互斥：切到自提要清掉时段，切到快递也要清时段（快递的时效由第三方决定，不自选时段）
function selectFulfillment(type) {
  const next = ['PICKUP', 'EXPRESS'].includes(type) ? type : 'INSTANT';
  fulfillment.type = next;
  if (next !== 'INSTANT') fulfillment.slot = '';
  if (next === 'PICKUP' && !fulfillment.storeId && stores.value.length) {
    fulfillment.storeId = Number(stores.value[0].id);
  }
}

function selectStore(id) {
  fulfillment.storeId = id ? Number(id) : null;
}

function resetFulfillment() {
  fulfillment.type = 'INSTANT';
  fulfillment.slot = '';
}

/* ---------------- 消息中心 ---------------- */
const messages = reactive({ items: [], total: 0, page: 1, size: 12, loading: false });
const messageUnread = ref(0);
const messageTypeFilter = ref('');

// 头像上的未读红点：导航条取消后，「消息」与「收藏降价提醒」两个提醒都收在这一颗点上。
// 返回空串即不显示，所以模板里直接用它当 v-if 条件。
const accountDotTitle = computed(() => {
  const parts = [];
  if (messageUnread.value) parts.push(`${messageUnread.value} 条未读消息`);
  if (alertUnread.value) parts.push(`${alertUnread.value} 条降价提醒`);
  return parts.join(' · ');
});

async function loadMessages(reset = true) {
  if (!session.user || isAdmin.value) { messages.items = []; messages.total = 0; return; }
  const nextPage = reset ? 1 : messages.page + 1;
  messages.loading = true;
  try {
    const query = messageTypeFilter.value ? `&type=${messageTypeFilter.value}` : '';
    const data = await api.get(`/messages?page=${nextPage}&size=${messages.size}${query}`);
    const items = data?.items || [];
    messages.items = reset ? items : [...messages.items, ...items];
    messages.total = data?.total || 0;
    messages.page = nextPage;
  } catch (e) { /* ignore */ } finally { messages.loading = false; }
}

async function loadMessageUnread() {
  if (!session.user || isAdmin.value) { messageUnread.value = 0; return; }
  try {
    const data = await api.get('/messages/unread-count');
    messageUnread.value = Number(data?.count || 0);
  } catch (e) { /* ignore */ }
}

function changeMessageFilter(type) {
  messageTypeFilter.value = type || '';
  loadMessages();
}

async function markMessagesRead() {
  if (!session.user || isAdmin.value || messageUnread.value <= 0) return;
  try {
    await api.post('/messages/read');
    messageUnread.value = 0;
    messages.items = messages.items.map((item) => ({ ...item, isRead: true }));
    notice.value = '消息已全部标记为已读';
  } catch (err) {
    fail(err?.message || '操作失败，请稍后重试');
  }
}

// 点开消息：先置为已读，再按 linkView 跳到对应页面
async function openMessage(message) {
  if (!message) return;
  if (!message.isRead) {
    try {
      await api.post(`/messages/${message.id}/read`);
      message.isRead = true;
      messageUnread.value = Math.max(0, messageUnread.value - 1);
    } catch (e) { /* 已读失败不阻断跳转 */ }
  }
  const target = message.linkView;
  if (target === 'orderDetail' && message.linkRef) navigate('orderDetail', { id: Number(message.linkRef) });
  else if (target === 'coupons') navigate('coupons');
  else if (target === 'points') navigate('points');
  else if (target === 'favorites') navigate('favorites');
  else if (target === 'orders') navigate('orders');
  else if (target === 'shop') navigate('shop');
}

function rememberUser(user) {
  session.user = user;
  if (user) {
    localStorage.setItem('supermarket_user', JSON.stringify(user));
    wallet.balance = Number(user.balance || 0);
  } else {
    localStorage.removeItem('supermarket_user');
    wallet.balance = 0;
    wallet.recentTransactions = [];
  }
  ensureAllowedView();

}

const ROUTE_VIEWS = ['shop', 'product', 'cart', 'checkout', 'orders', 'coupons', 'addresses', 'recharge', 'points', 'favorites', 'messages', 'terms', 'privacy', 'admin'];
const ADMIN_MENU_KEYS = ['dashboard', 'insights', 'orders', 'refunds', 'stock', 'products', 'categories', 'coupons', 'activities', 'flashSales', 'notices', 'stores', 'banners', 'users', 'passwordResets'];

function ensureAllowedView() {
  const allowed = isAdmin.value
    ? ['shop', 'admin', 'product', 'orderDetail', 'terms', 'privacy'].includes(view.value)
    : view.value !== 'admin';
  if (allowed) return;
  router.replace({ name: 'shop' });
  view.value = 'shop';
}

async function run(action, message) {
  error.value = '';
  notice.value = '';
  try {
    const result = await action();
    if (message) notice.value = message;
    return result;
  } catch (err) {
    if (err.authExpired) {
      notice.value = err.message || '登录已过期，请重新登录';
    } else {
      fail(err.message || '操作失败，请稍后重试');
    }
    throw err;
  }

}











function askConfirm(options) {
  return new Promise((resolve) => {
    Object.assign(confirmDialog, {
      open: true,
      title: '',
      message: '',
      details: [],
      confirmText: '确认',
      danger: false,
      ...options,
      resolver: resolve,
    });
  });

}

function resolveConfirm(result) {
  const resolver = confirmDialog.resolver;
  confirmDialog.open = false;
  confirmDialog.resolver = null;
  if (resolver) resolver(result);

}

// 失败/提示弹窗：type 可为 error/info/success，3.2s 后自动关闭，也可点「我知道了」手动关闭
function showAlert(options) {
  if (alertDialog.timer) clearTimeout(alertDialog.timer);
  Object.assign(alertDialog, { open: true, title: '提示', message: '', type: 'error', ...options });
  alertDialog.timer = setTimeout(() => { alertDialog.open = false; }, 3200);
}

function closeAlert() {
  if (alertDialog.timer) clearTimeout(alertDialog.timer);
  alertDialog.open = false;
}

// 操作未通过时统一弹出提示框（同时顶部状态栏也保留红字提示）
function fail(message, title) {
  error.value = message;
  showAlert({ title: title || '操作失败', message, type: 'error' });
}


// 用户订单的「发货进度」状态：让“发没发货”一眼可辨
// 状态徽标。注意 SHIPPED 对自提单要显示「待取货」——自提单没有物流，
// 后台走的是「备货完成」，叫「已发货」会让用户以为有快递在路上。
function shipStatusOf(order) {
  const pickup = order?.fulfillmentType === 'PICKUP';
  const map = {
    PENDING_PAYMENT: { label: '待付款', cls: 'warn' },
    PAID: { label: pickup ? '备货中' : '待发货', cls: 'amber' },
    SHIPPED: { label: pickup ? '待取货' : '已发货', cls: 'info' },
    COMPLETED: { label: '已完成', cls: 'ok' },
    CANCELED: { label: '已取消', cls: 'muted' },
    CLOSED: { label: '已关闭', cls: 'muted' },
  };
  return map[order.status] || { label: orderStatusLabel(order), cls: 'muted' };
}

function categoryName(categoryId) {
  if (!categoryId) return '未分类';
  return categories.value.find((category) => category.id === categoryId)?.name || '未分类';

}


// 把后端存储的 unit（可能是代码、历史中文字面或自定义字面）还原为表单的 { unit, customUnit }



// 单个购物车项的「原价→现价」省了多少（已乘数量）；不足优惠时返回 0

// 近 7 天成交趋势：管理员判断生意涨跌的第一张图
function trendChartOption() {
  const trend = (adminStatsOverview.value && adminStatsOverview.value.salesTrend) || [];
  const days = trend.map((p) => `${String(p.date).slice(8)}日`);
  const sales = trend.map((p) => Number(p.salesAmount || 0));
  const counts = trend.map((p) => Number(p.orderCount || 0));
  return {
    title: { text: '近 7 天成交趋势', left: 8, top: 4, textStyle: { fontSize: 15, color: '#1f2933' } },
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, left: 'center' },
    grid: { left: 24, right: 18, top: 48, bottom: 72, containLabel: true },
    xAxis: { type: 'category', data: days, boundaryGap: false },
    yAxis: [
      { type: 'value', name: '成交额(¥)' },
      { type: 'value', name: '订单数', splitLine: { show: false } },
    ],
    series: [
      { name: '成交额', type: 'bar', data: sales, barMaxWidth: 26, itemStyle: { color: '#0aa870', borderRadius: [5, 5, 0, 0] } },
      { name: '订单数', type: 'line', smooth: true, data: counts, itemStyle: { color: '#ffa042' }, yAxisIndex: 1 },
    ],
  };

}

// 热销商品 TOP8：按销量排序，直接支撑备货与运营决策（取代无行动价值的"库存最多排行"）
function salesTopChartOption() {
  const items = [...(adminChartProducts.value || [])]
    .sort((a, b) => Number(b.sales || 0) - Number(a.sales || 0))
    .slice(0, 8);
  return {
    title: { text: '热销商品 TOP8（按销量）', left: 8, top: 4, textStyle: { fontSize: 15, color: '#1f2933' } },
    tooltip: { trigger: 'axis' },
    grid: { left: 24, right: 18, top: 48, bottom: 72, containLabel: true },
    xAxis: { type: 'category', data: items.map((item) => item.name), axisLabel: { rotate: 28 } },
    yAxis: { type: 'value', name: '销量' },
    series: [{ name: '销量', type: 'bar', data: items.map((item) => Number(item.sales || 0)), itemStyle: { color: '#12c48b', borderRadius: [4, 4, 0, 0] } }],
  };

}

async function renderAdminCharts() {
  if (!isAdmin.value || view.value !== 'admin' || adminMenu.value !== 'dashboard') {
    disposeCharts();
    return;
  }
  await nextTick();
  const trendHasData = ((adminStatsOverview.value && adminStatsOverview.value.salesTrend) || [])
    .some((p) => Number(p.orderCount || 0) > 0 || Number(p.salesAmount || 0) > 0);
  if (trendChartEl.value && trendHasData) {
    if (trendChart) trendChart.dispose();
    trendChart = echarts.init(trendChartEl.value);
    trendChart.setOption(trendChartOption(), true);
  }
  if (salesChartEl.value) {
    if (salesChart) salesChart.dispose();
    salesChart = echarts.init(salesChartEl.value);
    salesChart.setOption(salesTopChartOption(), true);
  }

}

function resizeCharts() {
  trendChart?.resize();
  salesChart?.resize();

}

function disposeCharts() {
  trendChart?.dispose();
  salesChart?.dispose();
  trendChart = null;
  salesChart = null;

}

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
  if (authErrors.username || authErrors.password) return;
  authSubmitting.value = true;
  try {
    const data = await api.post('/auth/login', { username: loginForm.username.trim(), password: loginForm.password });
    setToken(data.token);
    rememberUser(data.user);
    await refreshForSession();
    closeAuth();
    if (data.user.mustChangePassword) {
      // 管理员重置过密码：先强制改密，不出常规欢迎提示（改完 AuthService 会清标记）
      openChangePassword(true);
    } else {
      showAlert({ type: 'success', title: '登录成功', message: `欢迎回来，${data.user.nickname || data.user.username}` });
    }
    if (pendingCheckout.value) { pendingCheckout.value = false; navigate('checkout'); }
    // 未登录时点心形收藏 → 登录成功后自动补做
    if (pendingFavorite.value) {
      const pendingProduct = pendingFavorite.value;
      pendingFavorite.value = null;
      await toggleFavorite(pendingProduct);
    }
  } catch (err) {
    fail(authErrorText(err.message) || '登录失败，请检查用户名或密码');
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
  if (authErrors.username || authErrors.password || authErrors.confirmPassword || authErrors.phone || authErrors.email || authErrors.agree) return;
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
    closeAuth();
    showAlert({ type: 'success', title: '注册成功', message: '欢迎加入！新人券已自动发放到你的账户 🎁' });
    if (pendingCheckout.value) { pendingCheckout.value = false; navigate('checkout'); }
    // 未登录时点心形收藏 → 注册成功后自动补做
    if (pendingFavorite.value) {
      const pendingProduct = pendingFavorite.value;
      pendingFavorite.value = null;
      await toggleFavorite(pendingProduct);
    }
  } catch (err) {
    const msg = authErrorText(err.message) || '注册失败，请稍后重试';
    if (msg.includes('用户名')) authErrors.username = msg;
    else if (msg.includes('手机')) authErrors.phone = msg;
    else if (msg.includes('邮箱')) authErrors.email = msg;
    fail(msg);
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

// forced=true 表示管理员刚重置过密码：弹窗不可关闭，改完才能继续用
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
  if (resetErrors.username || resetErrors.contact) return;
  resetSubmitting.value = true;
  try {
    const data = await api.post('/auth/password-reset-request', {
      username: resetForm.username.trim(),
      contact: resetForm.contact.trim(),
    });
    closeAuth();
    // 后端对「账号存在/不存在」返回同一句文案（防账号枚举），前端原样展示
    showAlert({ type: 'success', title: '申请已提交', message: data?.message || '客服会在 1 个工作日内联系你。' });
  } catch (err) {
    fail(err?.message || '提交失败，请稍后重试');
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
  if (changeErrors.currentPassword || changeErrors.newPassword || changeErrors.confirmPassword) return;
  changeSubmitting.value = true;
  try {
    await api.post('/auth/change-password', {
      currentPassword: changeForm.currentPassword,
      newPassword: changeForm.newPassword,
      confirmPassword: changeForm.confirmPassword,
    });
    closeAuth();
    // 后端已清掉 must_change_password，重新拉一次 me 让本地 session 同步（强制改密的用户至此恢复可用）
    await loadMe();
    showAlert({ type: 'success', title: '修改成功', message: '密码已更新，下次登录请使用新密码。' });
  } catch (err) {
    // 后端业务错误（原密码不正确 / 新密码与原密码相同 / 两次不一致）透传到对应字段
    const msg = err.message || '修改失败，请稍后重试';
    if (msg.includes('原密码')) changeErrors.currentPassword = msg;
    else if (msg.includes('不一致')) changeErrors.confirmPassword = msg;
    else if (msg.includes('相同')) changeErrors.newPassword = msg;
    else error.value = msg;
    fail(msg);
  } finally {
    changeSubmitting.value = false;
  }
}

function logout() {
  setToken('');
  rememberUser(null);
  navigate('shop');
  notice.value = '已退出登录';
  disposeCharts();
  loadCart(); // 游客态：清掉服务端 cart 视图，回到本地车状态
}

// token 过期/失效：后端返回 401 时由 api 客户端广播，这里优雅回到未登录态（不卡死界面）
function handleAuthExpired() {
  setToken('');
  rememberUser(null);
  if (route.name !== 'shop') navigate('shop');
  notice.value = '登录已过期，请重新登录';
  loadCart();
}
if (typeof window !== 'undefined') {
  window.addEventListener('auth-expired', handleAuthExpired);
}

async function loadMe() {
  if (!session.user) return;
  const user = await api.get('/auth/me');
  rememberUser(user);
  userStore.setAuth(localStorage.getItem('token') || '', user);
  // 刷新页面时若仍处于「待强制改密」，重新把不可关闭的改密弹窗拉起来
  if (user.mustChangePassword && !authOpen.value) openChangePassword(true);
}

async function onAvatarPick(e) {
  const file = e.target.files && e.target.files[0];
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) {
    fail('头像图片大小不能超过 10MB，请压缩后重试');
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
    fail(err?.message || '头像上传失败');
  } finally {
    e.target.value = '';
  }
}

async function loadWallet() {
  if (!session.user || isAdmin.value) return;
  const data = await api.get('/wallet');
  Object.assign(wallet, data);
  rememberUser({ ...session.user, balance: data.balance });

}

/* ---------------- 充值（模拟支付宝 / 微信支付） ---------------- */

function methodLabel(method) {
  if (method === 'WECHAT') return '微信支付';
  return '支付宝';
}

function selectRechargePreset(preset) {
  recharge.amount = preset;
  recharge.customAmount = '';
}

function onCustomAmountInput() {
  const value = Number(recharge.customAmount);
  if (recharge.customAmount === '' || Number.isNaN(value)) {
    recharge.amount = 0;
    return;
  }
  recharge.amount = value;
}

function formatCountdown(seconds) {
  const s = Math.max(0, Math.floor(seconds));
  const mm = String(Math.floor(s / 60)).padStart(2, '0');
  const ss = String(s % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

/** 根据订单号生成一张装饰性「二维码」SVG（非真实二维码，仅用于模拟扫码页） */
function buildQrSvg(seed) {
  const size = 21;
  const cell = 8;
  let hash = 0;
  for (let i = 0; i < seed.length; i += 1) hash = (hash * 31 + seed.charCodeAt(i)) >>> 0;
  const rand = () => {
    hash = (hash * 1103515245 + 12345) >>> 0;
    return hash / 4294967296;
  };
  let rects = '';
  for (let y = 0; y < size; y += 1) {
    for (let x = 0; x < size; x += 1) {
      if (rand() > 0.5) {
        rects += `<rect x="${x * cell}" y="${y * cell}" width="${cell}" height="${cell}" />`;
      }
    }
  }
  // 三个定位角
  const finder = (ox, oy) => `<rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="#fff"/><rect x="${ox * cell}" y="${oy * cell}" width="${cell * 7}" height="${cell * 7}" fill="none" stroke="#1f2933" stroke-width="${cell}"/><rect x="${(ox + 2) * cell}" y="${(oy + 2) * cell}" width="${cell * 3}" height="${cell * 3}" fill="#1f2933"/>`;
  const svg = `<svg viewBox="0 0 ${size * cell} ${size * cell}" xmlns="http://www.w3.org/2000/svg" fill="#1f2933">${rects}${finder(0, 0)}${finder(size - 7, 0)}${finder(0, size - 7)}</svg>`;
  return svg;
}

const qrSvg = computed(() => buildQrSvg(recharge.order?.orderNo || 'RECHARGE'));

function startCountdown() {
  clearRechargeTimer();
  if (!recharge.order?.expireAt) return;
  const expireAt = recharge.order.expireAt;
  const tick = () => {
    const remain = Math.max(0, Math.floor((expireAt - Date.now()) / 1000));
    recharge.countdown = remain;
    if (remain <= 0) {
      clearRechargeTimer();
      handleRechargeExpired();
    }
  };
  tick();
  recharge.timer = setInterval(tick, 1000);
}

function clearRechargeTimer() {
  if (recharge.timer) {
    clearInterval(recharge.timer);
    recharge.timer = null;
  }
}

async function confirmRecharge() {
  const amount = Number(recharge.amount);
  if (!Number.isFinite(amount) || amount <= 0) {
    fail('请选择或输入大于 0 的充值金额');
    return;
  }
  const ok = await askConfirm({
    title: '确认创建充值订单',
    message: `将创建一笔 ${methodLabel(recharge.method)} 充值订单，金额 ${money(amount)}，订单 10 分钟内有效。`,
    confirmText: '创建订单',
  });
  if (!ok) return;
  await run(async () => {
    const order = await api.post('/wallet/recharge-orders', { amount, method: recharge.method });
    recharge.order = order;
    recharge.step = 'paying';
    recharge.paying = false;
    startCountdown();
    notice.value = '充值订单已创建，请在支付页完成付款';
  }, null);
}

async function payRechargeOrder() {
  if (!recharge.order) return;
  const ok = await askConfirm({
    title: '确认支付',
    message: `确认通过${methodLabel(recharge.method)}支付 ${money(recharge.order.amount)}？支付成功后金额即时到账。`,
    confirmText: '立即支付',
  });
  if (!ok) return;
  recharge.paying = true;
  try {
    await run(async () => {
      const paid = await api.post(`/wallet/recharge-orders/${recharge.order.id}/pay`);
      recharge.order = paid;
      await loadWallet();
      clearRechargeTimer();
      recharge.step = 'success';
    }, '充值成功，金额已到账');
  } finally {
    recharge.paying = false;
  }
}

async function cancelRechargeOrder() {
  if (!recharge.order) return;
  const ok = await askConfirm({
    title: '取消充值订单',
    message: '确定取消该充值订单吗？取消后需重新创建。',
    confirmText: '取消订单',
    danger: true,
  });
  if (!ok) return;
  await run(async () => {
    await api.post(`/wallet/recharge-orders/${recharge.order.id}/cancel`);
    clearRechargeTimer();
    resetRecharge();
    notice.value = '充值订单已取消';
  }, null);
}

async function handleRechargeExpired() {
  // 倒计时归零：尝试在服务端取消（已超时的订单也兼容处理），并提示用户
  if (recharge.order) {
    try {
      await api.post(`/wallet/recharge-orders/${recharge.order.id}/cancel`);
    } catch (e) {
      // 订单可能已被调度任务置为 EXPIRED，忽略
    }
  }
  recharge.step = 'expired';
}

function closeRechargeModal() {
  // 支付中不允许直接关闭，需走「取消订单」；成功/超时允许返回
  if (recharge.step === 'paying') {
    cancelRechargeOrder();
    return;
  }
  clearRechargeTimer();
  resetRecharge();
}

function resetRecharge() {
  clearRechargeTimer();
  recharge.step = 'form';
  recharge.order = null;
  recharge.countdown = 0;
  recharge.paying = false;
  recharge.customAmount = '';
  recharge.amount = 100;
  recharge.method = 'ALIPAY';
}

function backToShop() {
  clearRechargeTimer();
  resetRecharge();
  navigate('shop');
}

// 离开充值页时清理倒计时并重置，避免后台计时器泄漏或残留支付浮层
watch(view, (next) => {
  if (next !== 'recharge') {
    clearRechargeTimer();
    resetRecharge();
  }
});

async function loadCategories() {
  categories.value = await api.get('/categories');
  if (!productForm.categoryId && categories.value[0]) productForm.categoryId = categories.value[0].id;

}

async function loadProducts() {
  const params = new URLSearchParams({ page: '1', size: String(products.size) });
  if (filters.categoryId) params.set('categoryId', filters.categoryId);
  if (filters.keyword) params.set('keyword', filters.keyword);
  if (filters.minPrice !== '' && filters.minPrice != null) params.set('minPrice', String(filters.minPrice));
  if (filters.maxPrice !== '' && filters.maxPrice != null) params.set('maxPrice', String(filters.maxPrice));
  if (filters.brand) params.set('brand', filters.brand);
  if (filters.sort) params.set('sort', filters.sort);
  const data = await api.get(`/products?${params}`);
  Object.assign(products, data);
}

async function chooseCategory(categoryId) {
  filters.categoryId = categoryId;
  await loadProducts();

}

async function loadHot() {
  try { hotProducts.value = (await api.get('/products/hot?limit=8')) || []; } catch (e) { hotProducts.value = []; }
}

async function loadNew() {
  try { newProducts.value = (await api.get('/products/new?limit=8')) || []; } catch (e) { newProducts.value = []; }
}

async function loadHomeChannels() {
  await Promise.all([loadHot(), loadNew(), loadGuess(), loadDwellRank()]);
}

async function loadGuess() {
  try { guessProducts.value = (await api.get('/recommendations/guess?limit=8')) || []; } catch (e) { guessProducts.value = []; }
}

async function loadDwellRank() {
  try { dwellRankProducts.value = (await api.get('/dwell/rank?limit=8')) || []; } catch (e) { dwellRankProducts.value = []; }
}

function applyFilters() {
  loadProducts();
}

function resetFilters() {
  filters.minPrice = '';
  filters.maxPrice = '';
  filters.brand = '';
  filters.sort = '';
  loadProducts();
}

async function addToCart(product) {
  if (isAdmin.value) { fail('管理员只能查看上架商品，不能加入购物车'); return; }
  if (!session.user) {
    const ok = await guestAdd(product, 1);
    if (ok) navigate('cart');
    return;
  }
  const stock = Number(product.stock || 0);
  if (stock <= 0) { fail(`${product.name || '该商品'} 已售罄，暂时无法加入购物车`); return; }
  const existing = (cart.items || []).find((i) => i.productId === product.id);
  const currentQty = existing ? Number(existing.quantity || 0) : 0;
  if (currentQty + 1 > stock) {
    fail(`库存不足：${product.name || '该商品'} 仅剩 ${stock} 件，购物车中已有 ${currentQty} 件`, '库存不足');
    return;
  }
  // 秒杀每人限购：在这里就把原因说清楚，别让用户点完「加入购物车」才等后端 409 回来
  const flashLeft = flashLimitOfProduct(product.id);
  if (flashLeft !== null && currentQty + 1 > flashLeft) {
    fail(flashLimitMessage(product.id, flashLeft), '超出限购');
    return;
  }
  await run(async () => {
    await api.post('/cart/items', { productId: product.id, quantity: 1 });
    await loadCart();
    navigate('cart');
  }, '已加入购物车');

}

async function loadCart() {
  if (isAdmin.value) return;
  if (!session.user) { await refreshGuestCartView(); return; }
  const cartData = await api.get('/cart');
  Object.assign(cart, cartData);
  cartStore.setCart(cartData);
  await loadMyCoupons();
  await loadUsableCoupons();
  // 首次进入：未手动放弃用券且有可用券时，自动选用优惠力度最大的券
  userOptedOutCoupon.value = false;
  autoSelectCoupon();

}

async function loadMyCoupons() {
  if (!session.user || isAdmin.value) { myCoupons.value = []; return; }
  myCoupons.value = await api.get('/coupons/mine') || [];

}

async function loadUsableCoupons() {
  if (!session.user || isAdmin.value) {
    usableCoupons.value = [];
    selectedUserCouponId.value = '';
    return;
  }
  const amount = cartLocalTotal.value;
  usableCoupons.value = await api.get(`/coupons/usable?amount=${amount}`);
  // 当前选中的券若因金额变化而不再满足条件，自动取消；随后在仍满足条件、且用户未显式放弃时补选最优券
  if (selectedUserCouponId.value && !usableCoupons.value.some((coupon) => coupon.id === selectedUserCouponId.value)) {
    selectedUserCouponId.value = '';
  }
  autoSelectCoupon();

}

// 该券是否满足当前购物车金额门槛（可点击/可自动使用）
function couponEligible(coupon) {
  return usableCoupons.value.some((item) => item.id === coupon.id);

}

// 还差多少金额可用
function couponShortfall(coupon) {
  return Math.max(Number(coupon.thresholdAmount || 0) - cartLocalTotal.value, 0);

}

function autoSelectCoupon() {
  if (userOptedOutCoupon.value || selectedUserCouponId.value) return;
  const best = usableCoupons.value.slice()
    .sort((a, b) => Number(b.discountAmount || 0) - Number(a.discountAmount || 0))[0];
  if (best) selectedUserCouponId.value = best.id;

}

function selectCoupon(coupon) {
  if (!couponEligible(coupon)) return;
  selectedUserCouponId.value = coupon.id;
  userOptedOutCoupon.value = false;

}

function chooseNoCoupon() {
  selectedUserCouponId.value = '';
  userOptedOutCoupon.value = true;

}

// 数量改变：价格即时变化（v-model 已更新 item.quantity，计算属性即时重算），
// 同时防抖把新数量同步到后端，并在合适时机刷新可用券列表。
// 修复：加入库存与秒杀限购上限校验（cartQtyMax = min(库存, 还能买几件)）。
// 原实现是「先夹到 max 再加 delta」，到顶时反而会多给一件（5→6），这里改成「先加再夹」。
function stepQty(item, delta) {
  const max = cartQtyMax(item);
  const current = Math.max(1, Number(item.quantity) || 1);
  const next = Math.min(Math.max(current + delta, 1), Math.max(max, 1));
  if (next === current) return;   // 已到顶，不发这次必然被拒的请求
  item.quantity = next;
  onQtyInput(item);
}

function onQtyChange(item) {
  const max = cartQtyMax(item);
  const flashLimit = flashLimitOfProduct(item.productId);
  let q = Number(item.quantity) || 1;
  if (q < 1) q = 1;
  if (q > max) {
    q = max;
    // 因限购被夹下来时说一句，否则用户会以为「我输入的数字被吞了」
    if (flashLimit !== null && Number(item.quantity) > flashLimit) {
      const sale = flashSaleOfProduct(item.productId);
      notice.value = `「${sale?.name || '该秒杀商品'}」每人限购 ${flashLimit} 件，已为你调整为 ${max} 件`;
    }
  }
  item.quantity = q;
  onQtyInput(item);
}

async function onQtyInput(item) {
  clearTimeout(cartSyncTimers[item.id]);
  if (!session.user) {
    // 游客：本地车直接改本地存储并即时重算合计（无需网络防抖）
    persistGuestFromItems();
    recomputeCartTotals();
    return;
  }
  cartSyncTimers[item.id] = setTimeout(async () => {
    const max = Number(item.stock || 0);
    const qty = Number(item.quantity) || 1;
    if (max > 0 && qty > max) {
      fail(`库存不足：仅剩 ${max} 件`, '库存不足');
      await loadCart(); // 回滚到后端真实数量
      return;
    }
    try {
      // PUT 返回的就是更新后的完整购物车（含 activityDiscount），必须接住，
      // 否则改数量后活动优惠/应付合计停留在旧值，与结算页对不上
      const updated = await api.put(`/cart/items/${item.id}`, { quantity: qty, selected: item.selected !== false });
      if (updated) {
        Object.assign(cart, updated);
        cartStore.setCart(updated);
      }
      await loadUsableCoupons();
    } catch (e) {
      // 后端可能因库存不足、或超出秒杀每人限购而拒绝，回滚到真实数量并提示，
      // 避免界面与后端不一致。后端的 409 文案已是中文可读的，直接用。
      const msg = e?.message || '更新数量失败，已恢复';
      fail(msg, /限购|买满/.test(msg) ? '超出限购' : '库存不足');
      await loadCart();
    }
  }, 400);
}

async function removeCartItem(id) {
  if (!session.user) { guestRemoveItem(id); return; }
  await run(() => api.delete(`/cart/items/${id}`).then(loadCart), '购物车商品已删除');

}

async function clearCart() {
  const confirmed = await askConfirm({
    title: '清空购物车',
    message: '确定要清空购物车里的全部商品吗？此操作不可恢复。',
    confirmText: '清空',
    danger: true,
  });
  if (!confirmed) return;
  if (!session.user) { guestClear(); return; }
  const ids = (cart.items || []).map((item) => item.id);
  await run(async () => {
    await Promise.all(ids.map((id) => api.delete(`/cart/items/${id}`)));
    await loadCart();
  }, '购物车已清空');

}

async function loadAddresses() {
  if (!session.user || isAdmin.value) return;
  addresses.value = await api.get('/addresses');
  const defaultAddress = addresses.value.find((item) => item.isDefault) || addresses.value[0];
  selectedAddressId.value = defaultAddress?.id || null;

}

function useAddress(address) {
  selectedAddressId.value = address.id;
  Object.assign(addressForm, address);
  notice.value = '已选择该地址';

}

async function saveAddress() {
  await run(async () => {
    const saved = await api.post('/addresses', addressForm);
    selectedAddressId.value = saved.id;
    await loadAddresses();
  }, '地址已保存');

}

async function goCheckout() {
  if (!cart.items?.length) { fail('购物车为空，请先添加商品'); return; }
  if (!session.user) { pendingCheckout.value = true; openAuth('login'); return; }
  if (!addresses.value.length) await loadAddresses();
  if (!selectedAddressId.value && addresses.value.length) {
    selectedAddressId.value = (addresses.value.find((item) => item.isDefault) || addresses.value[0]).id;
  }
  if (!selectedAddressId.value) { fail('请先在「收货地址」中添加收货地址'); return; }
  await Promise.all([loadWallet(), loadMyCoupons(), loadUsableCoupons()]);
  navigate('checkout');

}

async function createOrder() {
  if (paying.value) return;
  // 履约三选一：自提要选门店；即时配送与快递配送都要收货地址
  if (isPickup.value) {
    if (!activeStoreId.value) { fail('请选择自提门店'); return; }
  } else if (!selectedAddressId.value) {
    fail('请先保存或选择收货地址');
    return;
  }
  const itemIds = (cart.items || []).map((item) => item.id);
  if (!itemIds.length) { fail('购物车为空，请先添加商品'); return; }
  paying.value = true;
  try {
    // 模拟支付网关受理：先展示加载态，使模拟支付更逼真
    await new Promise((r) => setTimeout(r, 700));
    await run(async () => {
      const body = { cartItemIds: itemIds, remark: '前端下单' };
      if (isPickup.value) {
        body.fulfillmentType = 'PICKUP';
        body.pickupStoreId = activeStoreId.value;
      } else {
        // 即时配送与快递配送都要地址；只有即时配送带自选时段（快递时效由第三方决定）
        body.fulfillmentType = isExpress.value ? 'EXPRESS' : 'INSTANT';
        body.addressId = selectedAddressId.value;
        if (!isExpress.value && fulfillment.slot) body.deliverySlot = fulfillment.slot;
      }
      if (selectedUserCouponId.value) body.userCouponId = selectedUserCouponId.value;
      if (usePoints.value && memberPreview.value.pointsUsed > 0) {
        body.usePoints = true;
        body.pointsToUse = memberPreview.value.pointsUsed;
      }
      const order = await api.post('/orders', body);
      // 模拟支付：创建后用钱包余额完成支付
      await api.post(`/orders/${order.id}/pay`);
      selectedUserCouponId.value = '';
      userOptedOutCoupon.value = false;
      usePoints.value = false;
      pointsToUse.value = 0;
      await loadCart();
      await loadOrders();
      await loadWallet();
      await loadMe();
      await loadMemberProfile();
      await loadMessageUnread();
      // 下单会占掉秒杀名额（未付款也占），必须重拉，否则「还能买几件」停留在旧值
      await loadFlashSales();
      navigate('orders');
      return order;
    }, '支付成功，订单已创建');
  } finally {
    paying.value = false;
  }
}

async function loadOrders() {
  if (!session.user || isAdmin.value) return;
  Object.assign(orders, await api.get('/orders?page=1&size=20'));
  await loadReviewedFlags();

}

async function loadReviewedFlags() {
  const completed = (orders.items || []).filter((order) => order.status === 'COMPLETED');
  await Promise.all(completed.map(async (order) => {
    if (reviewedMap[order.id] !== undefined) return;
    try {
      const reviews = await api.get(`/reviews/orders/${order.id}`);
      reviewedMap[order.id] = (reviews || []).length > 0;
    } catch {
      reviewedMap[order.id] = false;
    }
  }));

}

async function payOrder(id) {
  await run(async () => {
    await api.post(`/orders/${id}/pay`);
    await Promise.all([loadOrders(), loadWallet(), loadMe()]);
  }, '支付成功');

}

async function cancelOrder(id) {
  const order = (orders.items || []).find((item) => item.id === id);
  const confirmed = await askConfirm({
    title: '取消订单',
    message: '取消后订单不可恢复，已占用的库存会立即释放。',
    confirmText: '确认取消',
    danger: true,
    details: order ? [{ label: '订单号', value: order.orderNo }, { label: '金额', value: money(order.payAmount) }] : [],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/orders/${id}/cancel`);
    // 取消会回退秒杀名额与限购额度，重拉后购物车里的「还能买几件」才会立刻放开
    await Promise.all([loadOrders(), loadWallet(), loadMe(), loadFlashSales()]);
  }, '订单已取消');

}

async function confirmReceipt(id) {
  const confirmed = await askConfirm({
    title: '确认收货',
    message: '确认后订单交易完成，款项将结算给商家，之后如需退货只能走退款申请。',
    confirmText: '确认收货',
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/orders/${id}/confirm-receipt`);
    await loadOrders();
  }, '已确认收货，订单完成');

}

function openRefundForm(orderId) {
  refundForm.orderId = orderId;
  refundForm.reason = '';

}

async function submitRefund(id) {
  if (!refundForm.reason.trim()) { fail('请填写退款原因'); return; }
  const confirmed = await askConfirm({
    title: '提交退款申请',
    message: '提交后需要商家审核，审核通过后款项会退回你的钱包。',
    confirmText: '提交申请',
    danger: true,
    details: [{ label: '退款原因', value: refundForm.reason.trim() }],
  });
  if (!confirmed) return;
  await run(async () => {
    await api.post(`/orders/${id}/refund-apply`, { reason: refundForm.reason.trim() });
    refundForm.orderId = null;
    await loadOrders();
  }, '退款申请已提交，等待商家处理');

}

function openReviewForm(orderId) {
  reviewForm.orderId = orderId;
  reviewForm.rating = 5;
  reviewForm.content = '';

}

async function submitReview(id) {
  await run(async () => {
    await api.post(`/reviews/orders/${id}`, {
      rating: reviewForm.rating,
      content: reviewForm.content.trim(),
      imageUrls: reviewForm.images,
    });
    reviewedMap[id] = true;
    loadRatingSummary(); // 新评价改变平均分，刷新商品卡/详情的星级聚合
    reviewForm.orderId = null;
    reviewForm.images = [];
    await loadProducts();
    // 若用户正在查看该订单对应商品的详情页，立即刷新评价列表
    try {
      const order = await api.get(`/orders/${id}`).catch(() => null);
      const pid = order?.items?.[0]?.productId;
      if (pid && view.value === 'product' && productDetail.data && productDetail.data.id === pid) {
        const r = await api.get(`/reviews/products/${pid}?page=1&size=20`).catch(() => ({ items: [] }));
        productDetail.reviews = r?.items || [];
      }
    } catch (_) { /* 刷新评价失败不影响主流程 */ }
  }, '评价提交成功，感谢反馈');

}

async function openProductDetail(product, { fromHistory = false } = {}) {
  if (productNavLock) return;
  productNavLock = true;
  try {
    reportDwell(); // 离开上一个详情页，先上报停留时长
    productDetail.data = null;
    productDetail.reviews = [];
    relatedProducts.value = [];
    activeActivities.value = [];
    currentImageIndex.value = 0;
    for (const k in selectedSpec) delete selectedSpec[k];
    productDetail.loading = true;
    detailQuantity.value = 1;
    dwellProductId.value = product.id;
    dwellEnterTs.value = Date.now();
    dwellSource.value = 'detail';
    router.push({ name: 'product', params: { id: product.id } });
    try {
      const [detail, reviews, related, activities] = await Promise.all([
        api.get(isAdmin.value ? `/admin/products/${product.id}` : `/products/${product.id}`).catch(() => null),
        api.get(`/reviews/products/${product.id}?page=1&size=20`).catch(() => ({ items: [] })),
        api.get(`/products/related/${product.id}?limit=6`).catch(() => []),
        api.get(`/activities/active`).catch(() => []),
      ]);
      productDetail.data = detail;
      productDetail.reviews = reviews?.items || [];
      relatedProducts.value = related || [];
      activeActivities.value = activities || [];
    } catch (err) {
      fail(err?.message || '商品详情加载失败');
    } finally {
      productDetail.loading = false;
    }
  } finally {
    productNavLock = false;
  }
}

async function openOrderDetail(order) {
  if (orderNavLock) return;
  orderNavLock = true;
  try {
    orderDetail.data = null;
    orderDetail.error = '';
    orderDetail.loading = true;
    router.push({ name: 'orderDetail', params: { id: order.id } });
    try {
      const url = isAdmin.value ? `/admin/orders/${order.id}` : `/orders/${order.id}`;
      orderDetail.data = await api.get(url);
    } catch (err) {
      orderDetail.error = err?.message || '订单详情加载失败';
    } finally {
      orderDetail.loading = false;
    }
  } finally {
    orderNavLock = false;
  }
}

function closeOrderDetail() {
  navigate(isAdmin.value ? 'admin' : 'orders');
}

function reportDwell() {
  const pid = dwellProductId.value;
  const ts = dwellEnterTs.value;
  if (pid == null || !ts) return;
  const seconds = Math.round((Date.now() - ts) / 1000);
  dwellProductId.value = null;
  dwellEnterTs.value = 0;
  if (seconds < 3) return; // 太短忽略，避免误触
  api.post('/dwell', { productId: pid, seconds, source: dwellSource.value || 'detail' }).catch(() => {});
}

function backFromProduct() {
  reportDwell();
  // 站内导航进来的商品页，走路由回退避免重复堆积历史记录；直接打开链接/刷新则回首页
  if (window.history.length > 1) {
    router.back();
    return;
  }
  navigate('shop');
}

function changeDetailQty(delta) {
  const max = Number(productDetail.data?.stock || 0);
  const next = Number(detailQuantity.value || 1) + delta;
  detailQuantity.value = Math.min(Math.max(next, 1), Math.max(max, 1));

}

async function addDetailToCart() {
  if (isAdmin.value) { fail('管理员只能查看上架商品，不能加入购物车'); return; }
  if (!session.user) {
    const ok = await guestAdd(
      { id: productDetail.data?.id, name: productDetail.data?.name, stock: productDetail.data?.stock,
        price: productDetail.data?.price, originalPrice: productDetail.data?.originalPrice,
        coverUrl: productDetail.data?.coverUrl, unit: productDetail.data?.unit },
      detailQuantity.value || 1,
      selectedSpecText.value || ''
    );
    if (ok) navigate('cart');
    return;
  }
  const stock = Number(productDetail.data?.stock || 0);
  const qty = Number(detailQuantity.value || 1);
  if (stock <= 0) { fail(`${productDetail.data?.name || '该商品'} 已售罄，暂时无法加入购物车`); return; }
  if (qty > stock) { fail(`库存不足：仅剩 ${stock} 件，您选择了 ${qty} 件`, '库存不足'); return; }
  const specNote = selectedSpecText.value ? `（${selectedSpecText.value}）` : '';
  await run(async () => {
    reportDwell();
    await api.post('/cart/items', { productId: productDetail.data.id, quantity: detailQuantity.value, skuSpec: selectedSpecText.value || null });
    await loadCart();
    navigate('cart');
  }, `已加入购物车 ${detailQuantity.value} 件${specNote}`);

}

async function buyDetailNow() {
  await addDetailToCart();

}

async function loadCoupons() {
  if (!session.user || isAdmin.value) return;
  const [available, mine] = await Promise.all([
    api.get('/coupons/available'),
    api.get('/coupons/mine'),
  ]);
  coupons.value = available || [];
  myCoupons.value = mine || [];

}

async function receiveCoupon(couponId) {
  await run(async () => {
    await api.post(`/coupons/${couponId}/receive`);
    await loadCoupons();
  }, '优惠券领取成功');

}

async function loadAdminProducts() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(adminProducts.page),
    size: String(adminProducts.size),
  });
  if (adminProductKeyword.value.trim()) params.set('keyword', adminProductKeyword.value.trim());
  if (adminProductStatus.value) params.set('status', adminProductStatus.value);
  const data = await api.get(`/admin/products?${params}`);
  Object.assign(adminProducts, data);
  // 当前页被删空（如删掉最后一页最后一条）时，自动回退到上一页
  if (adminProducts.items.length === 0 && adminProducts.page > 1) {
    adminProducts.page -= 1;
    await loadAdminProducts();
    return;
  }
  adminJumpPage.value = adminProducts.page;
  await loadAdminChartProducts();
  await renderAdminCharts();
}

// 仪表盘「商品库存排行」需要全量商品，与分页后的表格数据解耦，避免只统计当前页
/* ===== 公告管理（后台） + 首页公告详情弹层 ===== */
async function loadAdminAnnouncements() {
  if (!isAdmin.value) return;
  try {
    adminAnnouncements.value = await api.get('/admin/announcements');
  } catch (e) {
    adminAnnouncements.value = [];
  }
}

function openAnnouncementForm(a) {
  Object.assign(announcementForm, a
    ? { id: a.id, title: a.title, content: a.content, type: a.type || 'NOTICE', sortOrder: a.sortOrder || 0, enabled: Number(a.enabled) === 1 }
    : { id: null, title: '', content: '', type: 'NOTICE', sortOrder: 0, enabled: true });
  announcementFormOpen.value = true;
}
function closeAnnouncementForm() {
  announcementFormOpen.value = false;
  announcementForm.id = null;
}

async function saveAnnouncement() {
  const form = announcementForm;
  if (!form.title.trim() || !form.content.trim()) { showAlert('标题和内容都要填写'); return; }
  await run(async () => {
    const payload = { title: form.title.trim(), content: form.content.trim(), type: form.type, sortOrder: Number(form.sortOrder) || 0, enabled: !!form.enabled };
    if (form.id) await api.put(`/admin/announcements/${form.id}`, payload);
    else await api.post('/admin/announcements', payload);
    closeAnnouncementForm();
    await loadAdminAnnouncements();
  }, '公告已保存');
}

async function toggleAnnouncement(a) {
  await run(async () => {
    await api.put(`/admin/announcements/${a.id}`, {
      title: a.title, content: a.content, type: a.type, sortOrder: a.sortOrder || 0,
      enabled: Number(a.enabled) !== 1,
    });
    await loadAdminAnnouncements();
  }, Number(a.enabled) === 1 ? '公告已停用' : '公告已启用');
}

async function deleteAnnouncement(a) {
  const ok = await askConfirm(`确定删除公告「${a.title}」？删除后前台立即消失。`);
  if (!ok) return;
  await run(async () => {
    await api.delete(`/admin/announcements/${a.id}`);
    await loadAdminAnnouncements();
  }, '公告已删除');
}

/* ===== 轮播位管理（后台） ===== */
async function loadAdminBanners() {
  if (!isAdmin.value) return;
  try {
    adminBanners.value = await api.get('/admin/banners');
  } catch (e) {
    adminBanners.value = [];
  }
}

function openBannerForm(b) {
  // 新建时默认排在最后（当前最大排序 + 1），避免多条都是 0 导致播放顺序随机
  const nextSort = (adminBanners.value || []).reduce((max, item) => Math.max(max, Number(item.sortOrder) || 0), 0) + 1;
  Object.assign(bannerForm, b
    ? { id: b.id, imageUrl: b.imageUrl || '', linkProductId: b.linkProductId || null, sortOrder: b.sortOrder || 0, enabled: Number(b.enabled) === 1 }
    : { id: null, imageUrl: '', linkProductId: null, sortOrder: nextSort, enabled: true });
  bannerFormOpen.value = true;
}
function closeBannerForm() {
  bannerFormOpen.value = false;
  bannerForm.id = null;
}

async function saveBanner() {
  const form = bannerForm;
  if (bannerUploading.value) { showAlert('图片还在上传中，请稍候 1-2 秒再保存'); return; }
  if (!form.imageUrl) { showAlert('请先上传轮播图片'); return; }
  await run(async () => {
    const payload = {
      imageUrl: form.imageUrl,
      linkProductId: form.linkProductId || null,
      sortOrder: Number(form.sortOrder) || 0,
      enabled: !!form.enabled,
    };
    if (form.id) await api.put(`/admin/banners/${form.id}`, payload);
    else await api.post('/admin/banners', payload);
    closeBannerForm();
    await loadAdminBanners();
  }, '轮播位已保存');
}

async function toggleBanner(b) {
  await run(async () => {
    await api.put(`/admin/banners/${b.id}`, {
      imageUrl: b.imageUrl,
      linkProductId: b.linkProductId,
      sortOrder: b.sortOrder || 0,
      enabled: Number(b.enabled) !== 1,
    });
    await loadAdminBanners();
  }, Number(b.enabled) === 1 ? '轮播位已停用' : '轮播位已启用');
}

async function deleteBanner(b) {
  const ok = await askConfirm('确定删除这个轮播位？删除后前台立即不再展示。');
  if (!ok) return;
  await run(async () => {
    await api.delete(`/admin/banners/${b.id}`);
    await loadAdminBanners();
  }, '轮播位已删除');
}

// 公开接口：全量商品星级聚合（有评价的商品才会出现）
async function loadRatingSummary() {
  try {
    const list = await api.get('/products/rating-summary');
    const map = {};
    for (const item of list || []) {
      map[item.productId] = { avg: Number(item.avgRating || 0), count: Number(item.reviewCount || 0) };
    }
    ratingSummaryMap.value = map;
  } catch (e) {
    ratingSummaryMap.value = {};
  }
}

async function loadAdminChartProducts() {
  try {
    const data = await api.get('/admin/products?page=1&size=100');
    adminChartProducts.value = data.items || [];
  } catch (e) {
    adminChartProducts.value = [];
  }
}












async function loadAdminOrders() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(adminOrders.page),
    size: String(adminOrders.size),
  });
  if (adminOrderKeyword.value.trim()) params.set('orderNo', adminOrderKeyword.value.trim());
  if (adminOrderStatus.value) params.set('status', adminOrderStatus.value);
  const data = await api.get(`/admin/orders?${params}`);
  Object.assign(adminOrders, data);
  // 当前页被删空（如取消最后一页最后一条）时，自动回退到上一页
  if (adminOrders.items.length === 0 && adminOrders.page > 1) {
    adminOrders.page -= 1;
    await loadAdminOrders();
    return;
  }
  adminOrderJumpPage.value = adminOrders.page;
}

// 仪表盘聚合统计：后端一次给全量口径（订单总数/成交额/今日/待办/状态分布），
// 不再拿分页数据凑 KPI（旧实现请求 size=200 被后端 400 拒绝，环图永远是"暂无订单"）
async function loadAdminStatsOverview() {
  if (!isAdmin.value) return;
  try {
    adminStatsOverview.value = await api.get('/admin/stats/overview');
  } catch (e) {
    adminStatsOverview.value = null;
  }
}








async function loadRefundOrders() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(refundOrders.page),
    size: String(refundOrders.size),
    refundStatus: refundStatusFilter.value,
  });
  const data = await api.get(`/admin/orders?${params}`);
  Object.assign(refundOrders, data);
  if (refundOrders.items.length === 0 && refundOrders.page > 1) {
    refundOrders.page -= 1;
    await loadRefundOrders();
    return;
  }
  refundJumpPage.value = refundOrders.page;
}








async function loadStockAlerts() {
  if (!isAdmin.value) return;
  stockAlerts.value = await api.get('/admin/stock-alerts');

}


async function loadAdminCoupons() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(adminCoupons.page),
    size: String(adminCoupons.size),
  });
  if (adminCouponKeyword.value.trim()) params.set('keyword', adminCouponKeyword.value.trim());
  const data = await api.get(`/admin/coupons?${params}`);
  Object.assign(adminCoupons, data);
  if (adminCoupons.items.length === 0 && adminCoupons.page > 1) {
    adminCoupons.page -= 1;
    await loadAdminCoupons();
    return;
  }
  adminCouponJumpPage.value = adminCoupons.page;
}





























async function loadAdminUsers() {
  if (!isAdmin.value) return;
  const params = new URLSearchParams({
    page: String(adminUsers.page),
    size: String(adminUsers.size),
  });
  if (adminUserKeyword.value.trim()) params.set('keyword', adminUserKeyword.value.trim());
  if (adminUserRole.value) params.set('role', adminUserRole.value);
  if (adminUserStatus.value !== '') params.set('status', String(adminUserStatus.value));
  const data = await api.get(`/admin/users?${params}`);
  Object.assign(adminUsers, data);
  if (adminUsers.items.length === 0 && adminUsers.page > 1) {
    adminUsers.page -= 1;
    await loadAdminUsers();
    return;
  }
  adminUserJumpPage.value = adminUsers.page;
}









async function refreshAdminData() {
  if (!isAdmin.value) return;
  await Promise.all([
    loadCategories(),
    loadProducts(),
    loadAdminProducts(),
    loadAdminOrders(),
    loadAdminStatsOverview(),
    loadAdminUsers(),
    loadRefundOrders(),
    loadStockAlerts(),
    loadAdminCoupons(),
  ]);
  await renderAdminCharts();

}




async function refreshForSession() {
  ensureAllowedView();
  if (isAdmin.value) {
    await refreshAdminData();
    return;
  }
  // loadMemberLevels 也要在这里补一次：/member/levels 需要登录，而 app 挂载时的调用发生在登录之前（401），
  // 不补的话「站内登录 → 结算」这条常见链路上 memberLevels 为空，会员折扣会被漏算。
  // loadFlashSales 同理，而且它还要拿到「我的已购/未付款占用」——那是带身份的，必须在登录后重拉。
  await Promise.all([loadWallet(), loadCart(), loadOrders(), loadAddresses(), loadMemberProfile(), loadMemberLevels(), loadFavoriteIds(), loadAlertUnread(), loadMessageUnread(), loadFlashSales()]);
  // 登录/注册成功后：把游客本地购物车并入服务端
  await mergeGuestCartToServer();

}

watch(view, async (next) => {
  ensureAllowedView();
  if (next === 'cart') { await loadCart(); await ensureActiveActivities(); }
  if (next === 'checkout') { await loadWallet(); await loadAddresses(); await loadMyCoupons(); await loadUsableCoupons(); usePoints.value = false; pointsToUse.value = 0; resetFulfillment(); await Promise.all([loadStores(), loadDeliverySlots()]); }
  if (next === 'points') { await loadMemberProfile(); await loadMemberLedger(); }
  if (next === 'favorites') { await loadFavorites(); await loadPriceAlerts(); await loadAlertUnread(); }
  if (next === 'messages') { await Promise.all([loadMessages(), loadMessageUnread()]); }
  if (next === 'orders') await loadOrders();
  if (next === 'coupons') await loadCoupons();
  if (next === 'addresses') await loadAddresses();
  if (next === 'recharge') await loadWallet();
  if (next === 'admin') await refreshAdminData();

});

watch(() => session.user?.role, () => {
  ensureAllowedView();

});

watch(() => [view.value, adminMenu.value, adminProducts.items, adminOrders.items], () => {
  renderAdminCharts();

}, { deep: true });


onMounted(async () => {
  noticeTimer = setInterval(() => { if (noticeList.value.length > 1) noticeIndex.value += 1; }, 4000);
  startFlashTick();
  window.addEventListener('resize', resizeCharts);
  window.addEventListener('beforeunload', reportDwell);
  document.addEventListener('click', onDocumentClick);
  document.addEventListener('keydown', onDocumentKeydown);
  document.addEventListener('visibilitychange', () => { if (document.hidden) reportDwell(); });
  await run(async () => {
    await loadCategories();
    await loadProducts();
    await loadRatingSummary();
    await loadHomeChannels();
    await loadMemberLevels();
    await loadFlashSales();
    if (session.user) await refreshForSession();
    else await refreshGuestCartView(); // 游客：水合本地购物车（角标/购物车页）
    await ensureActiveActivities();
    syncRoute();
  });

});

onBeforeUnmount(() => {
  clearInterval(noticeTimer);
  if (flashTickTimer) { clearInterval(flashTickTimer); flashTickTimer = null; }
  window.removeEventListener('resize', resizeCharts);
  window.removeEventListener('beforeunload', reportDwell);
  document.removeEventListener('visibilitychange', reportDwell);
  document.removeEventListener('click', onDocumentClick);
  document.removeEventListener('keydown', onDocumentKeydown);
  disposeCharts();

});
const adminCtx = { adminAnnouncements, adminBanners, adminChartProducts, announcementForm, bannerForm, bannerFormOpen, bannerUploading, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminAnnouncements, adminBanners, adminProducts, adminStatsOverview, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, disposeCharts, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminChartProducts, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openOrderDetail, orderDetail, orders, productForm, salesChart, salesChartEl, salesTopChartOption, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, renderAdminCharts, run, safeParseSpec, trendChart, trendChartEl, trendChartOption, saveAnnouncement, session, showAlert, stockAlerts, announcementFormOpen, closeAnnouncementForm, saveBanner, toggleBanner, deleteBanner, bannerForm, bannerFormOpen, openBannerForm, closeBannerForm, toggleAnnouncement, deleteAnnouncement };

const appCtx = { ADMIN_MENU_KEYS, ROUTE_VIEWS, activeActivities, adminBanners, bannerUploading, loadAdminBanners, bannerForm, bannerFormOpen, openBannerForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, addDetailToCart, addToCart, addressForm, addresses, adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminCtx, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, api, applyFilters, askConfirm, authErrors, authOpen, authSubmitting, authTab, autoSelectCoupon, avatarInput, backFromProduct, backToShop, balanceSufficient, buildQrSvg, buyDetailNow, cancelOrder, cancelRechargeOrder, cart, cartLocalTotal, cartOriginalSave, cartSelectedQty, cartSyncTimers, cartTotalSaved, cartActivityProgress, imgFallback, refreshCurrentPage, topActivity, activitySlogan, productActivityTag, ratingSummaryMap, adminAnnouncements, announcementForm, loadAdminAnnouncements, openAnnouncementForm, saveAnnouncement, toggleAnnouncement, deleteAnnouncement, categories, categoryName, changeDetailQty, chooseCategory, chooseNoCoupon, clearCart, clearRechargeTimer, closeAlert, closeAuth, closeOrderDetail, closeRechargeModal, computed, confirmDialog, confirmReceipt, confirmRecharge, couponEligible, couponShortfall, coupons, createOrder, currentGalleryImage, currentImageIndex, currentTitle, detailQuantity, discountRate, discountSave, disposeCharts, dwellEnterTs, dwellProductId, dwellRankProducts, dwellSource, echarts, ensureAllowedView, error, fail, filters, forgotPassword, formatCountdown, formatCouponStatus, formatDate, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, fulfillmentLabel, orderStatusLabel, galleryImages, goCheckout, guessProducts, handleAuthExpired, handleRechargeExpired, hotProducts, initials, isAdmin, itemOriginalSave, loadAddresses, loadAdminChartProducts, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCart, loadCategories, loadCoupons, loadDwellRank, loadGuess, loadHomeChannels, loadHot, loadMe, loadMyCoupons, loadNew, loadOrders, loadProducts, loadRefundOrders, loadReviewedFlags, loadStockAlerts, loadUsableCoupons, loadWallet, loginForm, logout, methodLabel, money, myCoupons, navigate, newProducts, nextTick, notice, onAvatarPick, onBeforeUnmount, onCustomAmountInput, onMounted, onQtyChange, onQtyInput, openAuth, openOrderDetail, openProductDetail, openRefundForm, openReviewForm, orderDetail, orderPayPreview, orderStatusTag, orders, payOrder, payRechargeOrder, paying, productDetail, productForm, products, provide, qrSvg, reactive, receiveCoupon, recharge, rechargePresets, ref, refreshAdminData, refreshForSession, refundForm, refundJumpPage, refundOrders, refundStatusFilter, refundStatusTag, registerForm, relatedProducts, rememberUser, removeCartItem, renderAdminCharts, trendChart, trendChartEl, trendChartOption, reportDwell, resetAuthErrors, resetFilters, resetRecharge, resizeCharts, resolveConfirm, resolveUnit, reviewForm, reviewedMap, run, safeParseSpec, saveAddress, selectCoupon, selectRechargePreset, selectedAddress, selectedAddressId, selectedCoupon, selectedSku, selectedSpec, selectedSpecText, selectedUserCouponId, session, setToken, shipStatusOf, showAlert, specDimensions, startCountdown, stepQty, stockAlerts, submitLogin, submitRefund, submitRegister, submitReview, switchAuth, usableCoupons, useAddress, userOptedOutCoupon, validateRegisterForm, memberProfile, memberLedger, memberLevels, usePoints, pointsToUse, tierRateForLevel, tierNameFor, memberPreview, loadMemberProfile, loadMemberLedger, loadMemberLevels, favoriteIds, favorites, priceAlerts, alertUnread, isFavorite, toggleFavorite, loadFavoriteIds, loadFavorites, loadPriceAlerts, loadAlertUnread, markAlertsRead, stores, deliverySlots, fulfillment, isPickup, isExpress, expressFreight, selectedStore, activeStoreId, loadStores, loadDeliverySlots, selectFulfillment, selectStore, resetFulfillment, messages, messageUnread, messageTypeFilter, loadMessages, loadMessageUnread, changeMessageFilter, markMessagesRead, openMessage, flashSales, runningFlashSales, loadFlashSales, flashRemaining, formatDuration, nowTick, legalDocs, loadLegalDoc, view, wallet, watch, cartQtyMax, cartQtyCapped, flashSaleOfProduct, flashLimitOfProduct, flashLimitMessage };
provide('appCtx', appCtx);
</script>
