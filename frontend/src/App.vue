<template>
  <main class="app-shell">
    <div class="header-utility">
      <div class="header-utility-inner">
        <div class="u-left">
          <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5" rx="1"/><line x1="12" y1="22" x2="12" y2="7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/></svg>
          <span>新人首单立减 <b>¥20</b>，再送 3 张满减券</span>
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
        <div class="brand">
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
          <template v-if="session.user">
            <button class="cart-pill" @click="navigate('cart')">
              <svg class="icon i-cart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.4 11.2a1.8 1.8 0 0 0 1.8 1.4h8.5a1.8 1.8 0 0 0 1.8-1.4L21.5 7H6"/></svg>
              购物车
              <span v-if="cartBadgeCount" class="cart-badge">{{ cartBadgeCount > 99 ? '99+' : cartBadgeCount }}</span>
            </button>
            <div class="account-user">
              <img v-if="session.user.avatarUrl" :src="session.user.avatarUrl" class="avatar-img avatar-clickable" alt="头像" title="点击更换头像" @click="avatarInput?.click()" />
              <span v-else class="avatar-img avatar-default avatar-clickable" title="点击更换头像" @click="avatarInput?.click()">{{ (session.user.nickname || session.user.username || '?').charAt(0) }}</span>
              <div class="account-meta">
                <span>{{ session.user.nickname || session.user.username }}</span>
                <small>{{ formatRole(session.user.role) }}</small>
              </div>
              <input ref="avatarInput" type="file" accept="image/*" hidden @change="onAvatarPick" />
            </div>
            <template v-if="!isAdmin">
              <div class="wallet-box">
                <span>账户余额</span>
                <strong>{{ money(wallet.balance) }}</strong>
              </div>
              <button class="ghost recharge-entry" @click="navigate('recharge')">去充值</button>
            </template>
            <button class="ghost" @click="logout">退出</button>
          </template>
          <template v-else>
            <div class="auth-guest">
              <button class="ghost" @click="openAuth('login')">登录</button>
              <button class="btn-solid" @click="openAuth('register')">注册</button>
            </div>
          </template>
        </section>
      </div>

      <div class="header-nav-band">
        <nav class="header-nav">
          <button class="allcat" @click="navigate('shop')"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>全部商品分类</button>
          <button :class="{ active: view === 'shop' }" @click="navigate('shop')">首页</button>
          <button v-if="!isAdmin" :class="{ active: view === 'orders' }" @click="navigate('orders')">我的订单</button>
          <button v-if="!isAdmin" :class="{ active: view === 'coupons' }" @click="navigate('coupons')">优惠券</button>
          <button v-if="!isAdmin" :class="{ active: view === 'addresses' }" @click="navigate('addresses')">收货地址</button>
          <button v-if="!isAdmin" :class="{ active: view === 'recharge' }" @click="navigate('recharge')">账户充值</button>
          <button v-if="isAdmin" :class="{ active: view === 'admin' }" @click="navigate('admin')">后台管理</button>
        </nav>
      </div>
    </header>

    <section class="content" :class="{ 'content-wide': view === 'admin' }">
      <header class="topbar" v-if="view !== 'product'">
        <div>
          <p class="eyebrow">{{ currentTitle.eyebrow }}</p>
          <h1>{{ currentTitle.title }}</h1>
        </div>
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
        </div>
      </div>
      <div class="footer-copy">© 2026 Supermarket Mall 超市购物系统 · 新鲜好物，一站购齐</div>
    </footer>

    <div v-if="confirmDialog.open" class="modal-mask" @click.self="resolveConfirm(false)">
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

    <div v-if="alertDialog.open" class="modal-mask" @click.self="closeAlert">
      <div class="modal alert-modal" :class="`alert-${alertDialog.type}`" role="dialog" aria-modal="true">
        <div class="alert-badge">{{ alertDialog.type === 'error' ? '!' : (alertDialog.type === 'success' ? '✓' : 'i') }}</div>
        <h3>{{ alertDialog.title }}</h3>
        <p class="modal-message">{{ alertDialog.message }}</p>
        <div class="modal-actions alert-actions">
          <button @click="closeAlert">我知道了</button>
        </div>
      </div>
    </div>

    <!-- 登录 / 注册 合一弹窗 -->
    <div v-if="authOpen" class="modal-mask" @click.self="closeAuth">
      <div class="modal auth-modal" role="dialog" aria-modal="true">
        <button class="modal-close" @click="closeAuth" aria-label="关闭">×</button>
        <div class="auth-tabs">
          <button :class="{ active: authTab === 'login' }" type="button" @click="switchAuth('login')">登录</button>
          <button :class="{ active: authTab === 'register' }" type="button" @click="switchAuth('register')">注册</button>
        </div>

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

        <form v-else class="auth-form" @submit.prevent="submitRegister">
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
            我已阅读并同意 <span class="auth-link" @click="forgotPassword">《用户协议》</span>
            <small v-if="authErrors.agree" class="field-hint warn">{{ authErrors.agree }}</small>
          </label>
          <button class="auth-submit" type="submit" :disabled="authSubmitting">{{ authSubmitting ? '注册中…' : '注册并领取新人券' }}</button>
          <p class="auth-tip">注册即自动发放新人券 🎁</p>
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
import { money, initials, formatRole, formatOrderStatus, formatPaymentStatus, formatRefundStatus, refundStatusTag, formatCouponStatus, formatDate, formatProductStatus, orderStatusTag, formatUnit, resolveUnit, discountSave, discountRate, itemOriginalSave } from './utils/format';
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
const error = ref('');
const avatarInput = ref(null);
const categories = ref([]);
const products = reactive({ items: [], page: 1, size: 12, total: 0 });
const adminProducts = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminOrders = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminOrderStats = ref([]);
const adminUsers = reactive({ items: [], page: 1, size: 10, total: 0 });
const cart = reactive({ items: [], selectedCount: 0, selectedAmount: 0 });
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
const productChartEl = ref(null);
const orderChartEl = ref(null);
let productChart = null;
let orderChart = null;


const session = reactive({
  user: JSON.parse(localStorage.getItem('supermarket_user') || 'null'),

});
const authOpen = ref(false);
const authTab = ref('login');
const authSubmitting = ref(false);
const loginForm = reactive({ username: '', password: '', remember: true });
const registerForm = reactive({ username: '', password: '', confirmPassword: '', nickname: '', phone: '', email: '', agree: false });
const authErrors = reactive({});
const filters = reactive({ categoryId: '', keyword: '', minPrice: '', maxPrice: '', brand: '', sort: '' });
const addressForm = reactive({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: true });
const productForm = reactive({ categoryId: '', sku: '', name: '', subtitle: '', description: '', price: 0, originalPrice: '', stock: 0, unit: 'piece', customUnit: '', brand: '', isHot: false, isNew: false, tags: '', images: [], skus: [], attributes: [] });
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
  cart: { eyebrow: '购物车', title: '购物车结算' },
  checkout: { eyebrow: '订单', title: '确认订单' },
  orders: { eyebrow: '订单', title: '我的订单' },
  coupons: { eyebrow: '优惠', title: '优惠券' },
  addresses: { eyebrow: '地址', title: '收货地址' },
  recharge: { eyebrow: '钱包', title: '账户充值' },
  admin: { eyebrow: '后台', title: '后台管理' },

}[view.value] || { eyebrow: '商品', title: '商品选购' }));

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

const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) || null);

const balanceSufficient = computed(() => Number(wallet.balance || 0) >= orderPayPreview.value);








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

const ROUTE_VIEWS = ['shop', 'product', 'cart', 'checkout', 'orders', 'coupons', 'addresses', 'recharge', 'admin'];
const ADMIN_MENU_KEYS = ['dashboard', 'orders', 'refunds', 'stock', 'products', 'categories', 'coupons', 'activities', 'users'];

function ensureAllowedView() {
  const allowed = isAdmin.value
    ? ['shop', 'admin', 'product', 'orderDetail'].includes(view.value)
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
function shipStatusOf(order) {
  const map = {
    PENDING_PAYMENT: { label: '待付款', cls: 'warn' },
    PAID: { label: '待发货', cls: 'amber' },
    SHIPPED: { label: '已发货', cls: 'info' },
    COMPLETED: { label: '已完成', cls: 'ok' },
    CANCELED: { label: '已取消', cls: 'muted' },
    CLOSED: { label: '已关闭', cls: 'muted' },
  };
  return map[order.status] || { label: formatOrderStatus(order.status), cls: 'muted' };

}

function categoryName(categoryId) {
  if (!categoryId) return '未分类';
  return categories.value.find((category) => category.id === categoryId)?.name || '未分类';

}


// 把后端存储的 unit（可能是代码、历史中文字面或自定义字面）还原为表单的 { unit, customUnit }



// 单个购物车项的「原价→现价」省了多少（已乘数量）；不足优惠时返回 0

function productChartOption() {
  const items = [...(adminChartProducts.value || [])].sort((a, b) => Number(b.stock || 0) - Number(a.stock || 0)).slice(0, 8);
  return {
    title: { text: '商品库存排行', left: 8, top: 4, textStyle: { fontSize: 15, color: '#1f2933' } },
    tooltip: { trigger: 'axis' },
    grid: { left: 24, right: 18, top: 48, bottom: 72, containLabel: true },
    xAxis: { type: 'category', data: items.map((item) => item.name), axisLabel: { rotate: 28 } },
    yAxis: { type: 'value', name: '库存' },
    series: [{ name: '库存', type: 'bar', data: items.map((item) => Number(item.stock || 0)), itemStyle: { color: '#fa8d1f', borderRadius: [4, 4, 0, 0] } }],
  };

}

function orderChartOption() {
  const counts = {};
  (adminOrderStats.value || []).forEach((order) => {
    const name = formatOrderStatus(order.status);
    counts[name] = (counts[name] || 0) + 1;
  });
  const data = Object.entries(counts).map(([name, value]) => ({ name, value }));
  return {
    title: { text: '订单状态分布', left: 8, top: 4, textStyle: { fontSize: 15, color: '#1f2933' } },
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, left: 'center' },
    color: ['#fa8d1f', '#ffb15b', '#ff6b35', '#ffd08a', '#d6502b', '#f5a03a'],
    series: [{
      name: '订单',
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '48%'],
      avoidLabelOverlap: true,
      data: data.length ? data : [{ name: '暂无订单', value: 1, itemStyle: { color: '#cbd5df' } }],
    }],
  };

}

async function renderAdminCharts() {
  if (!isAdmin.value || view.value !== 'admin' || adminMenu.value !== 'dashboard') {
    disposeCharts();
    return;
  }
  await nextTick();
  if (productChartEl.value) {
    if (productChart) productChart.dispose();
    productChart = echarts.init(productChartEl.value);
    productChart.setOption(productChartOption(), true);
  }
  if (orderChartEl.value) {
    if (orderChart) orderChart.dispose();
    orderChart = echarts.init(orderChartEl.value);
    orderChart.setOption(orderChartOption(), true);
  }

}

function resizeCharts() {
  productChart?.resize();
  orderChart?.resize();

}

function disposeCharts() {
  productChart?.dispose();
  orderChart?.dispose();
  productChart = null;
  orderChart = null;

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
    showAlert({ type: 'success', title: '登录成功', message: `欢迎回来，${data.user.nickname || data.user.username}` });
  } catch (err) {
    fail(err.message || '登录失败，请检查用户名或密码');
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
  } catch (err) {
    const msg = err.message || '注册失败，请稍后重试';
    if (msg.includes('用户名')) authErrors.username = msg;
    else if (msg.includes('手机')) authErrors.phone = msg;
    else if (msg.includes('邮箱')) authErrors.email = msg;
    fail(msg);
  } finally {
    authSubmitting.value = false;
  }
}

function forgotPassword() {
  showAlert({ type: 'info', title: '忘记密码', message: '可通过绑定手机号重置密码，或联系客服协助（功能开发中）。' });
}

function logout() {
  setToken('');
  rememberUser(null);
  navigate('shop');
  notice.value = '已退出登录';
  disposeCharts();

}

// token 过期/失效：后端返回 401 时由 api 客户端广播，这里优雅回到未登录态（不卡死界面）
function handleAuthExpired() {
  setToken('');
  rememberUser(null);
  if (route.name !== 'shop') navigate('shop');
  notice.value = '登录已过期，请重新登录';
}
if (typeof window !== 'undefined') {
  window.addEventListener('auth-expired', handleAuthExpired);
}

async function loadMe() {
  if (!session.user) return;
  const user = await api.get('/auth/me');
  rememberUser(user);
  userStore.setAuth(localStorage.getItem('token') || '', user);

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
  if (!session.user) { fail('请先登录后再加入购物车'); return; }
  const stock = Number(product.stock || 0);
  if (stock <= 0) { fail(`${product.name || '该商品'} 已售罄，暂时无法加入购物车`); return; }
  const existing = (cart.items || []).find((i) => i.productId === product.id);
  const currentQty = existing ? Number(existing.quantity || 0) : 0;
  if (currentQty + 1 > stock) {
    fail(`库存不足：${product.name || '该商品'} 仅剩 ${stock} 件，购物车中已有 ${currentQty} 件`, '库存不足');
    return;
  }
  await run(async () => {
    await api.post('/cart/items', { productId: product.id, quantity: 1 });
    await loadCart();
    navigate('cart');
  }, '已加入购物车');

}

async function loadCart() {
  if (!session.user || isAdmin.value) return;
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
// 修复：加入库存上限校验，超出库存时回滚到后端真实数量，避免「库存 5 却显示 6」的假象。
function stepQty(item, delta) {
  const max = Number(item.stock || 0);
  const next = Math.max(1, Math.min(Number(item.quantity) || 1, Math.max(max, 1)) + delta);
  item.quantity = next;
  onQtyInput(item);
}

function onQtyChange(item) {
  const max = Number(item.stock || 0);
  let q = Number(item.quantity) || 1;
  if (q < 1) q = 1;
  if (max > 0 && q > max) q = max;
  item.quantity = q;
  onQtyInput(item);
}

async function onQtyInput(item) {
  clearTimeout(cartSyncTimers[item.id]);
  cartSyncTimers[item.id] = setTimeout(async () => {
    const max = Number(item.stock || 0);
    const qty = Number(item.quantity) || 1;
    if (max > 0 && qty > max) {
      fail(`库存不足：仅剩 ${max} 件`, '库存不足');
      await loadCart(); // 回滚到后端真实数量
      return;
    }
    try {
      await api.put(`/cart/items/${item.id}`, { quantity: qty, selected: item.selected !== false });
      await loadUsableCoupons();
    } catch (e) {
      // 后端可能因库存不足等拒绝，回滚到真实数量并提示，避免界面与库存不一致
      fail(e?.message || '更新数量失败，已恢复', '库存不足');
      await loadCart();
    }
  }, 400);
}

async function removeCartItem(id) {
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
  if (!selectedAddressId.value) { fail('请先保存或选择收货地址'); return; }
  const itemIds = (cart.items || []).map((item) => item.id);
  if (!itemIds.length) { fail('购物车为空，请先添加商品'); return; }
  paying.value = true;
  try {
    // 模拟支付网关受理：先展示加载态，使模拟支付更逼真
    await new Promise((r) => setTimeout(r, 700));
    await run(async () => {
      const body = { addressId: selectedAddressId.value, cartItemIds: itemIds, remark: '前端下单' };
      if (selectedUserCouponId.value) body.userCouponId = selectedUserCouponId.value;
      const order = await api.post('/orders', body);
      // 模拟支付：创建后用钱包余额完成支付
      await api.post(`/orders/${order.id}/pay`);
      selectedUserCouponId.value = '';
      userOptedOutCoupon.value = false;
      await loadCart();
      await loadOrders();
      await loadWallet();
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
    await Promise.all([loadOrders(), loadWallet(), loadMe()]);
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
  if (!session.user) { fail('请先登录后再加入购物车'); return; }
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

// 仪表盘「订单状态分布」需要全量订单，与分页后的表格数据解耦，避免只统计当前页
async function loadAdminOrderStats() {
  try {
    const data = await api.get('/admin/orders?page=1&size=200');
    adminOrderStats.value = data.items || [];
  } catch (e) {
    adminOrderStats.value = [];
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
    loadAdminOrderStats(),
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
  await Promise.all([loadWallet(), loadCart(), loadOrders(), loadAddresses()]);

}

watch(view, async (next) => {
  ensureAllowedView();
  if (next === 'cart') await loadCart();
  if (next === 'checkout') { await loadWallet(); await loadAddresses(); await loadMyCoupons(); await loadUsableCoupons(); }
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
  window.addEventListener('resize', resizeCharts);
  window.addEventListener('beforeunload', reportDwell);
  document.addEventListener('visibilitychange', () => { if (document.hidden) reportDwell(); });
  await run(async () => {
    await loadCategories();
    await loadProducts();
    await loadHomeChannels();
    if (session.user) await refreshForSession();
    syncRoute();
  });

});

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts);
  window.removeEventListener('beforeunload', reportDwell);
  document.removeEventListener('visibilitychange', reportDwell);
  disposeCharts();

});
const adminCtx = { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStats, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, disposeCharts, error, fail, filters, loadAdminChartProducts, loadAdminCoupons, loadAdminOrderStats, loadAdminOrders, loadAdminProducts, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openOrderDetail, orderChart, orderChartEl, orderChartOption, orderDetail, orders, productChart, productChartEl, productChartOption, productForm, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, renderAdminCharts, run, safeParseSpec, session, showAlert, stockAlerts };

const appCtx = { ADMIN_MENU_KEYS, ROUTE_VIEWS, activeActivities, addDetailToCart, addToCart, addressForm, addresses, adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminCtx, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStats, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, api, applyFilters, askConfirm, authErrors, authOpen, authSubmitting, authTab, autoSelectCoupon, avatarInput, backFromProduct, backToShop, balanceSufficient, buildQrSvg, buyDetailNow, cancelOrder, cancelRechargeOrder, cart, cartLocalTotal, cartOriginalSave, cartSyncTimers, categories, categoryName, changeDetailQty, chooseCategory, chooseNoCoupon, clearCart, clearRechargeTimer, closeAlert, closeAuth, closeOrderDetail, closeRechargeModal, computed, confirmDialog, confirmReceipt, confirmRecharge, couponEligible, couponShortfall, coupons, createOrder, currentGalleryImage, currentImageIndex, currentTitle, detailQuantity, discountRate, discountSave, disposeCharts, dwellEnterTs, dwellProductId, dwellRankProducts, dwellSource, echarts, ensureAllowedView, error, fail, filters, forgotPassword, formatCountdown, formatCouponStatus, formatDate, formatOrderStatus, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, galleryImages, goCheckout, guessProducts, handleAuthExpired, handleRechargeExpired, hotProducts, initials, isAdmin, itemOriginalSave, loadAddresses, loadAdminChartProducts, loadAdminCoupons, loadAdminOrderStats, loadAdminOrders, loadAdminProducts, loadAdminUsers, loadCart, loadCategories, loadCoupons, loadDwellRank, loadGuess, loadHomeChannels, loadHot, loadMe, loadMyCoupons, loadNew, loadOrders, loadProducts, loadRefundOrders, loadReviewedFlags, loadStockAlerts, loadUsableCoupons, loadWallet, loginForm, logout, methodLabel, money, myCoupons, newProducts, nextTick, notice, onAvatarPick, onBeforeUnmount, onCustomAmountInput, onMounted, onQtyChange, onQtyInput, openAuth, openOrderDetail, openProductDetail, openRefundForm, openReviewForm, orderChart, orderChartEl, orderChartOption, orderDetail, orderPayPreview, orderStatusTag, orders, payOrder, payRechargeOrder, paying, productChart, productChartEl, productChartOption, productDetail, productForm, products, provide, qrSvg, reactive, receiveCoupon, recharge, rechargePresets, ref, refreshAdminData, refreshForSession, refundForm, refundJumpPage, refundOrders, refundStatusFilter, refundStatusTag, registerForm, relatedProducts, rememberUser, removeCartItem, renderAdminCharts, reportDwell, resetAuthErrors, resetFilters, resetRecharge, resizeCharts, resolveConfirm, resolveUnit, reviewForm, reviewedMap, run, safeParseSpec, saveAddress, selectCoupon, selectRechargePreset, selectedAddress, selectedAddressId, selectedCoupon, selectedSku, selectedSpec, selectedSpecText, selectedUserCouponId, session, setToken, shipStatusOf, showAlert, specDimensions, startCountdown, stepQty, stockAlerts, submitLogin, submitRefund, submitRegister, submitReview, switchAuth, usableCoupons, useAddress, userOptedOutCoupon, validateRegisterForm, view, wallet, watch };
provide('appCtx', appCtx);
</script>
