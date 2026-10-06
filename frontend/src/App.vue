<template>
  <main class="app-shell">
    <div class="header-utility" v-if="view !== 'login'">
      <div class="header-utility-inner">
        <div class="u-left" v-if="noticeList.length">
          <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 12 20 22 4 22 4 12"/><rect x="2" y="7" width="20" height="5" rx="1"/><line x1="12" y1="22" x2="12" y2="7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7z"/><path d="M12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/></svg>
          <Transition name="notice-fade" mode="out-in">
            <span :key="rotatingNotice">{{ rotatingNotice }}</span>
          </Transition>
        </div>
        <div class="u-right">
          <!-- 二级页的「回首页」：放在利益条右侧、紧邻「满 ¥99 免运费」。
               做成有边框的小胶囊是刻意的 —— 这一颗必须一眼看出能点
               （纯文字 Logo 那种弱提示正是它存在的理由）。 -->
          <button v-if="view !== 'shop'" type="button" class="u-home" @click="navigate('shop')">
            <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m3 10.5 9-7.5 9 7.5"/><path d="M5.5 9.8V21h13V9.8"/></svg>回首页
          </button>
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="1" y="4" width="14" height="12" rx="1"/><polygon points="15 8 19 8 22 11 22 16 15 16"/><circle cx="6" cy="18.5" r="2"/><circle cx="18" cy="18.5" r="2"/></svg>满 ¥99 免运费</span>
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-3.6 8-10V5l-8-3-8 3v7c0 6.4 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>生鲜坏果包赔</span>
          <span class="u-item"><svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/></svg>21:00 前下单 · 次日达</span>
        </div>
      </div>
    </div>

    <header class="site-header" v-if="view !== 'login'">
      <div class="header-inner">
        <!-- 导航条已取消：Logo 承担「回到首页」 -->
        <div class="brand brand-link" role="button" tabindex="0" title="回到首页" aria-label="回到首页"
             @click="navigate('shop')" @keydown.enter.prevent="navigate('shop')">
          <img src="/logo.svg" class="brand-logo" alt="超市购物系统" />
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
          <div class="hot-words" v-if="hotSearches.length">
            <span class="hw-tag">热搜</span>
            <a v-for="word in hotSearches" :key="word.id" @click.prevent="quickSearch(word.keyword)">{{ word.label }}</a>
          </div>
        </div>

        <section class="account-panel">
          <button v-if="!isAdmin" ref="cartPillEl" class="cart-pill" @click="navigate('cart')" aria-label="购物车">
            <svg class="icon i-cart" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.4 11.2a1.8 1.8 0 0 0 1.8 1.4h8.5a1.8 1.8 0 0 0 1.8-1.4L21.5 7H6"/></svg>
            购物车
            <span v-if="cartBadgeCount" ref="cartBadgeEl" class="cart-badge">{{ cartBadgeCount > 99 ? '99+' : cartBadgeCount }}</span>
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
                <span class="account-avatar-wrap tier-ring" :class="'lv' + (session.user?.memberLevel || 0)" :title="'等级：' + tierNameFor(session.user?.memberLevel)">
                  <img v-if="session.user.avatarUrl" :src="session.user.avatarUrl" class="avatar-img avatar-clickable" alt="头像" title="点击更换头像" @error="imgFallback($event, session.user.nickname || session.user.username)" @click.stop="avatarInput?.click()" />
                  <span v-else class="avatar-img avatar-default avatar-clickable" title="点击更换头像" @click.stop="avatarInput?.click()">{{ (session.user.nickname || session.user.username || '?').charAt(0) }}</span>
                  <!-- 导航条取消后，未读提醒收在头像上：不展开下拉也能看见 -->
                  <span v-if="!isAdmin && accountDotTitle" class="account-dot" :title="accountDotTitle"></span>
                </span>
                <div class="account-meta">
                  <span>{{ session.user.nickname || session.user.username }}</span>
                  <!-- 这里显示的是「会员等级」而非账号角色：曾用 formatRole(USER)=「普通用户」，
                       导致银卡用户头像下也挂着「普通用户」被当成等级（角色≠等级） -->
                  <small>{{ isAdmin ? '管理员' : tierNameFor(session.user.memberLevel) }}</small>
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
                  <button role="menuitem" @click="goLogin({ tab: 'change', redirect: (route.name && route.name !== 'login') ? route.name : 'shop' })">修改密码</button>
                  <button role="menuitem" class="menu-danger" @click="logout">退出登录</button>
                </div>
              </div>
            </div>
          </template>
          <template v-else>
            <div class="auth-guest">
              <button class="ghost" @click="openAuthNewTab({ tab: 'login' })">登录</button>
              <button class="btn-solid" @click="openAuthNewTab({ tab: 'register' })">注册</button>
            </div>
          </template>
        </section>
      </div>

    </header>

    <section class="content" ref="contentEl" :class="{ 'content-wide': view === 'admin', 'content-flush': view === 'login' }">
      <!-- 管理员预览身份横幅：以某会员档位查看价格时始终可见，明确「仅展示、不影响真实账号」 -->
      <div v-if="isPreviewing && view !== 'login'" class="tier-preview-banner">
        <span class="tpb-dot" :class="'lv' + previewTier"></span>
        正在以 <strong>{{ previewTierName }}</strong> 身份预览价格（仅展示，不影响真实账号）
        <button class="tpb-exit" @click="clearPreviewTier()">退出预览</button>
      </div>
      <header class="topbar" v-if="view !== 'product' && view !== 'shop' && view !== 'login'">
        <h1>{{ currentTitle.title }}</h1>
        <button v-if="['orders', 'coupons', 'points', 'favorites', 'messages'].includes(view)" class="ghost" @click="refreshCurrentPage">刷新</button>
      </header>

      <router-view v-if="route.name !== 'admin'" />

      <AdminPanel v-else :view="view" :categories="categories" :admin-ctx="adminCtx" />
    </section>

    <!-- 管理员专属：以某会员身份预览价格（纯展示）。固定右下角，不在后台管理页显示以免遮挡。 -->
    <div v-if="isAdmin && view !== 'login' && view !== 'admin'" class="tier-preview-fab" :class="{ open: tierPreviewOpen }">
      <button class="tpf-toggle" @click="tierPreviewOpen = !tierPreviewOpen" :title="isPreviewing ? '当前预览：' + previewTierName : '以会员身份预览价格'">
        <span class="tpf-dot" :class="'lv' + (isPreviewing ? previewTier : (session.user?.memberLevel || 0))"></span>
        预览身份{{ isPreviewing ? '：' + previewTierName : '' }}
        <span class="tpf-caret" :class="{ up: tierPreviewOpen }">▾</span>
      </button>
      <div v-if="tierPreviewOpen" class="tpf-panel">
        <div class="tpf-title">以会员身份预览价格</div>
        <label class="tpf-row" v-for="(name, i) in TIER_NAMES_FALLBACK" :key="i">
          <input type="radio" name="tpf-tier" :value="i" :checked="previewTier === i" @change="setPreviewTier(i)" />
          <span class="tpf-name" :class="'lv' + i">{{ name }}</span>
          <small v-if="i > 0" class="tpf-rate">{{ (TIER_RATES_FALLBACK[i] * 10).toFixed(1) }}折</small>
          <small v-else class="tpf-rate">无折扣</small>
        </label>
        <button class="tpf-clear" @click="clearPreviewTier()">关闭预览（按真实身份）</button>
      </div>
    </div>

    <footer class="site-footer" v-if="view !== 'login'">
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
            <img src="/logo.svg" class="brand-logo footer-logo-img" alt="超市购物系统" />
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
    <Transition name="toast-slide">
      <div v-if="notice" class="app-toast" role="status">{{ notice }}</div>
    </Transition>

    <!-- 登录 / 注册 / 改密 / 找回 已拆成独立路由页 /login（见 pages/LoginPage.vue），此处不再渲染 modal -->
  </main>
</template>



<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch, provide } from 'vue';
import { api, setToken, isRemembered } from './api/client';
import { setPendingAction, takePendingAction, clearPendingAction } from './composables/pendingAction.js';
import { useStores } from './composables/useStores.js';
import { useLegalDoc } from './composables/useLegalDoc.js';
import { useMessages } from './composables/useMessages.js';
import { useAuth } from './composables/useAuth';
import { useRecharge } from './composables/useRecharge';
import { useActivity } from './composables/useActivity.js';
import { useFavorites } from './composables/useFavorites.js';
import { useFlashSale } from './composables/useFlashSale.js';
import { useChannels } from './composables/useChannels.js';
import { useShopFilters } from './composables/useShopFilters.js';
// QUICKBUY_ITEM_ID 随 useGuestCart 一起搬走了（立即购买虚拟项的 id），这里仍要用它过滤虚拟项
import { useGuestCart, QUICKBUY_ITEM_ID } from './composables/useGuestCart.js';
import { useCart } from './composables/useCart.js';
import { useCartTotals } from './composables/useCartTotals.js';
import { useCartUi } from './composables/useCartUi.js';
import { useAdminContent } from './composables/useAdminContent.js';
import { useMemberPoints } from './composables/useMemberPoints.js';
import { useOrders } from './composables/useOrders.js';
import { useQuickBuy } from './composables/useQuickBuy.js';
import { useCoupons } from './composables/useCoupons.js';
import { useHeaderSearch } from './composables/useHeaderSearch.js';
import { useCheckout } from './composables/useCheckout.js';
import { useAdminLoaders } from './composables/useAdminLoaders.js';
import { useProductDetail } from './composables/useProductDetail.js';
import ImageUpload from './components/ImageUpload.vue';
import ProductCard from './components/ProductCard.vue';
import StarRating from './components/StarRating.vue';
import CouponCard from './components/CouponCard.vue';
import AddressCard from './components/AddressCard.vue';
import { money, initials, formatRole, orderStatusLabel, fulfillmentLabel, formatPaymentStatus, formatRefundStatus, refundStatusTag, formatCouponStatus, formatDate, formatProductStatus, orderStatusTag, formatUnit, resolveUnit, round2, discountSave, discountRate, itemOriginalSave, imgFallback } from './utils/format';
// 后台面板较重且仅管理员进入，改为懒加载（首屏不进主包）
import { defineAsyncComponent } from 'vue'
const AdminPanel = defineAsyncComponent(() => import('./components/AdminPanel.vue'));
import { useRoute, useRouter } from 'vue-router';
import { useCartStore } from './stores/cart';
import { useUserStore } from './stores/user';

const view = ref('shop');
const router = useRouter();
const route = useRoute();
const cartStore = useCartStore();
const userStore = useUserStore();


function navigate(name, params) {
  closeAccountMenu();
  router.push(params ? { name, params } : { name });
}

// 跳到独立登录页 /login。query: tab(login/register/change/reset)、forced(强制改密)、redirect(登录后落点路由名)。
// 拦截式登录（去结算 / 立即购买 / 收藏）由调用方先 setPendingAction，这里只负责跳转。
function goLogin(query = {}) {
  const q = {};
  if (query.tab) q.tab = query.tab;
  if (query.forced) q.forced = '1';
  if (query.redirect) q.redirect = query.redirect;
  router.push({ path: '/login', query: q });
}

// 页头「登录 / 注册」入口：在新标签页打开 /login（用户点进去登录，原页面留在原地）。
// 注意：不能带 noopener，否则 window.opener 为 null，登录成功后无法刷新来源页并关闭本标签。
// 同为同源 SPA，无反向篡改风险。拦截式登录（去结算/立即购买/收藏）仍走 goLogin 同标签，不要动它。
function openAuthNewTab(query = {}) {
  const params = new URLSearchParams();
  if (query.tab) params.set('tab', query.tab);
  if (query.forced) params.set('forced', '1');
  if (query.redirect) params.set('redirect', query.redirect);
  const url = '/login' + (params.toString() ? '?' + params.toString() : '');
  window.open(url, '_blank');
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
  // 首页的筛选状态跟着 URL 走：刷新 / 分享链接 / 前进后退都靠这一步恢复。
  // 有了它，返回键才真正等于「取消上一次筛选」（返回楼层视图），而不是毫无反应。
  if (name === 'shop' && applyShopQueryFromRoute()) await loadProducts();
  // 后台模块同理：深链 /admin?tab=notices、刷新、返回键都靠这一步回填（含首次进入）
  if (name === 'admin') applyAdminQueryFromRoute();
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
// 商品列表请求中。存在的意义是把「加载中」和「真的没有结果」分开 ——
// 否则首屏/切分类那一下 items 为空，空态那句「没有符合条件的商品」会先闪一下。
const productsLoading = ref(false);
const adminProducts = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminOrders = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminStatsOverview = ref(null);
const adminUsers = reactive({ items: [], page: 1, size: 10, total: 0 });
const cart = reactive({ items: [], selectedCount: 0, selectedAmount: 0 });
// 商品星级聚合：{ productId: { avg, count } }，商品卡/详情展示平均星级
const ratingSummaryMap = ref({});

const orders = reactive({ items: [], total: 0, page: 1, size: 20, loading: false });
const addresses = ref([]);
const selectedAddressId = ref(null);
const coupons = ref([]);
const paying = ref(false);
const reviewedMap = reactive({});
const refundOrders = reactive({ items: [], page: 1, size: 10, total: 0 });
const stockAlerts = ref([]);
const adminCoupons = reactive({ items: [], page: 1, size: 10, total: 0 });
const adminMenu = ref('insights');
const refundForm = reactive({ orderId: null, reason: '' });
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
const orderDetail = reactive({ data: null, loading: false, error: '' });
const wallet = reactive({ balance: 0, recentTransactions: [] });

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

const session = reactive({
  // 登录态存在哪个 storage 由「记住我」决定（见 api/client.js）：勾了在 localStorage、没勾在 sessionStorage
  user: JSON.parse(localStorage.getItem('supermarket_user') || sessionStorage.getItem('supermarket_user') || 'null'),

});
const filters = reactive({ categoryId: '', keyword: '', minPrice: '', maxPrice: '', sort: '' });
const addressForm = reactive({ receiverName: '', receiverPhone: '', province: '', city: '', district: '', detailAddress: '', isDefault: true });
const productForm = reactive({ categoryId: '', sku: '', name: '', subtitle: '', description: '', price: 0, originalPrice: '', memberPrice: '', stock: 0, unit: 'piece', customUnit: '', brand: '', isHot: false, isNew: false, tags: '', images: [], skus: [], attributes: [] });
// 打开编辑表单时记录当时的库存，仅当管理员改动库存字段时才随表单提交，
// 避免把打开表单瞬间可能已过期的库存值覆盖真实库存。

// 计价单位选项：与后端 formatUnit 映射保持一致；custom 表示管理员自定义单位

// 管理后台商品列表：查询条件与分页状态
const adminProductKeyword = ref('');
const adminProductStatus = ref('');
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

// 管理员「以某会员身份预览价格」：仅 admin 可用，纯展示、不改真实账号、不影响下单。
// previewTier=null 表示不预览（按管理员自身身份）；0..6 表示以对应会员档位预览。
const previewTier = ref(null);
const tierPreviewOpen = ref(false);
const isPreviewing = computed(() => isAdmin.value && previewTier.value != null);
const previewTierName = computed(() => (isPreviewing.value ? tierNameFor(previewTier.value) : ''));
function setPreviewTier(level) {
  if (!isAdmin.value) return;
  previewTier.value = (level == null || level === '') ? null : Number(level);
}
function clearPreviewTier() { previewTier.value = null; }
function effectiveMemberLevel() {
  if (isPreviewing.value) return Number(previewTier.value);
  return Number(session.user?.memberLevel || 0);
}


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
  pay: { eyebrow: '收银台', title: '订单付款' },
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



const selectedAddress = computed(() => addresses.value.find((item) => item.id === selectedAddressId.value) || null);





function rememberUser(user) {
  session.user = user;
  // 用户信息跟着 token 走：勾了「记住我」→ localStorage（关浏览器仍在），否则 sessionStorage（关浏览器即清）
  const keep = isRemembered() ? localStorage : sessionStorage;
  const drop = isRemembered() ? sessionStorage : localStorage;
  if (user) {
    keep.setItem('supermarket_user', JSON.stringify(user));
    drop.removeItem('supermarket_user');
    wallet.balance = Number(user.balance || 0);
  } else {
    localStorage.removeItem('supermarket_user');
    sessionStorage.removeItem('supermarket_user');
    wallet.balance = 0;
    wallet.recentTransactions = [];
  }
  ensureAllowedView();

}

/* ---------------- 履约 / 协议 / 消息：已抽为 composable ---------------- */
// 装配点必须在 fail() 之后（消息的 markMessagesRead 要用），且被抽走的符号在本行之前
// 无任何同步求值（无 immediate watch / watchEffect），故不存在 TDZ 风险。
const { stores, deliverySlots, fulfillment, EXPRESS_FREE_THRESHOLD, EXPRESS_FREIGHT, isPickup, isExpress, expressFreight, selectedStore, activeStoreId, loadStores, loadDeliverySlots, selectFulfillment, selectStore, resetFulfillment } = useStores({ getCartLocalTotal: () => cartLocalTotal.value });

const { legalDocs, loadLegalDoc, openLegal } = useLegalDoc();

const { messages, messageUnread, messageTypeFilter, loadMessages, loadMessageUnread, changeMessageFilter, loadMessagesPage, markMessagesRead, openMessage } = useMessages({ getSession: () => session, isAdmin, navigate, notice, fail });

// 头像上的未读红点：导航条取消后，「消息」与「收藏降价提醒」两个提醒都收在这一颗点上。
// 返回空串即不显示，所以模板里直接用它当 v-if 条件。
// ⚠️ 它跨两个域（messageUnread 来自消息域、alertUnread 来自收藏域），故留在 App.vue 不搬，
//    位置必须在上面 useMessages 装配之后。
const accountDotTitle = computed(() => {
  const parts = [];
  if (messageUnread.value) parts.push(`${messageUnread.value} 条未读消息`);
  if (alertUnread.value) parts.push(`${alertUnread.value} 条降价提醒`);
  return parts.join(' · ');
});

/* ---------------- 认证 / 钱包充值：已抽为 composable ---------------- */
// 装配点必须在全部依赖之后：auth 需要 rememberUser/refreshForSession/loadCart/goLogin/
// closeAccountMenu，recharge 需要 askConfirm/run/wallet/recharge。
// 且被抽走的符号在本行之前无任何同步求值（无 immediate watch / watchEffect），无 TDZ 风险。
const { authOpen, authTab, authSubmitting, loginForm, registerForm, authErrors, changeForm, changeErrors, changeSubmitting, forcedChange, resetForm, resetErrors, resetSubmitting, openAuth, closeAuth, switchAuth, resetAuthErrors, authErrorText, submitLogin, validateRegisterForm, submitRegister, forgotPassword, openChangePassword, submitPasswordReset, validateChangeForm, submitChangePassword, logout, handleAuthExpired, registerAuthListeners, loadMe, onAvatarPick } = useAuth({ getSession: () => session, getRoute: () => route, navigate, goLogin, rememberUser, refreshForSession, getLoadCart: () => loadCart, notice, error, fail, showAlert, closeAccountMenu });

// 全局监听（token 过期 / 待强制改密）由 composable 统一注册，行为与拆分前一致
registerAuthListeners();

const { loadWallet, methodLabel, selectRechargePreset, onCustomAmountInput, formatCountdown, buildQrSvg, qrSvg, startCountdown, clearRechargeTimer, confirmRecharge, payRechargeOrder, cancelRechargeOrder, handleRechargeExpired, closeRechargeModal, resetRecharge } = useRecharge({ getSession: () => session, isAdmin, rememberUser, wallet, recharge, notice, fail, askConfirm, run });

/* ---------------- 活动 / 收藏 / 秒杀：已抽为 composable ---------------- */
// 装配点必须在 cartLocalTotal / session / isAdmin / route / goLogin / notice / fail 之后，
// 且被抽走的符号在本行之前无任何同步求值（无 immediate watch / watchEffect），无 TDZ 风险。
// ⚠️ activeActivities 虽然被游客购物车(estimateGuestActivity)与商品详情页读写，
//    但解构拿到的是**同一个 ref 对象**，跨域读写天然可用，无需额外注入。
const { activeActivities, noticeIndex, noticeList, rotatingNotice, topActivity, cartActivityProgress, ensureActiveActivities, activitySlogan, activityNoticeText, activityOffset, activityMatches, productActivityTag, startNoticeRotation, stopNoticeRotation } = useActivity({ getCartLocalTotal: () => cartLocalTotal.value });

const { favoriteIds, favorites, priceAlerts, alertUnread, pendingFavorite, isFavorite, setFavoriteLocal, loadFavoriteIds, loadFavorites, loadPriceAlerts, loadFavoritesPage, loadPriceAlertsPage, loadAlertUnread, markAlertsRead, toggleFavorite } = useFavorites({ getSession: () => session, isAdmin, getRoute: () => route, goLogin, notice, fail, getView: () => view.value });

const { flashSales, nowTick, runningFlashSales, FLASH_TICK_WINDOW_SECONDS, loadFlashSales, flashRemaining, flashDeadlineText, formatDuration, startFlashTick, stopFlashTick, flashSaleOfProduct, flashLimitOfProduct, cartQtyMax, cartQtyCapped, flashSplitNote, isFlashSplit, flashLimitMessage } = useFlashSale();

/* ---------------- 运营频道 / 筛选↔URL：已抽为 composable ---------------- */
// 装配点必须在 filters / products / productsLoading / adminMenu / ADMIN_MENU_KEYS 之后。
// scrollToResultsAfterLoad 变成 composable 私有标记，goSearch 改用 markScrollToResults() 置位。
const { hotProducts, newProducts, guessProducts, dwellRankProducts, CHANNEL_PAGE, channelPool, channelCursor, channelRefs, channelPage, loadChannel, rotateChannel, channelRotatable, loadHot, loadNew, loadGuess, loadDwellRank, loadHomeChannels } = useChannels();

const ROUTE_VIEWS = ['shop', 'product', 'cart', 'checkout', 'orders', 'coupons', 'addresses', 'recharge', 'points', 'favorites', 'messages', 'terms', 'privacy', 'admin'];
const ADMIN_MENU_KEYS = ['insights', 'orders', 'refunds', 'reviews', 'stock', 'products', 'categories', 'coupons', 'activities', 'flashSales', 'notices', 'hotSearches', 'memberDays', 'stores', 'banners', 'users', 'passwordResets'];

// ⚠️ 装配点必须在 ADMIN_MENU_KEYS 定义之后（要用它做 tab 白名单校验）——
// 放它前面会 TDZ: Cannot access 'ADMIN_MENU_KEYS' before initialization，整页白屏。
const { SHOP_FILTER_KEYS, ADMIN_TAB_DEFAULT, filterQueryFromFilters, sameShopQuery, syncShopQuery, applyShopQueryFromRoute, syncAdminQuery, applyAdminQueryFromRoute, scrollToResultsIfNeeded, loadProducts, fetchProducts, chooseCategory, applyFilters, resetFilters, markScrollToResults } = useShopFilters({ filters, products, productsLoading, adminMenu, adminMenuKeys: ADMIN_MENU_KEYS, getRoute: () => route, router });

/* ---------------- 游客购物车 / 加购微交互：已抽为 composable ---------------- */
// 装配点必须在 cart / activeActivities / cartBadgeCount / session 之后（都由 adminCtx 或本文件提供）。
// activeActivities 来自 useActivity（跨域共享同一个 ref 对象）。
// ⚠️ getLoadCart 是读取器而不是 loadCart 本身：loadCart 由下面的 useCart 提供，而 useCart 又要用本
//    composable 的 guestAdd 等 —— 直接传会形成循环依赖（双向都要对方的立即值）。改成读取器后
//    loadCart 只在 mergeGuestCartToServer 被调用（用户登录那一刻）才求值，循环自然解开。
const { guestCartRows, guestProductCache, pendingCheckout, pendingQuickBuy, quickBuy, readGuestCart, writeGuestCart, persistGuestFromItems, estimateGuestActivity, recomputeCartTotals, refreshGuestCartView, guestAdd, guestRemoveItem, guestClear, mergeGuestCartToServer } = useGuestCart({ cart, getSession: () => session, activeActivities, getLoadCart: () => loadCart, fail, notice });

// 顶栏购物车角标：购物车内商品总件数（不区分是否勾选）；立即购买的虚拟项不算进角标。
// ⚠️ 刻意留在 App.vue（不进任何 composable）：useCartUi 要 watch 它，useCartTotals 又在 useCartUi 之后，
//    搬进任一个都会形成循环依赖。它是纯计算、没有副作用，放这里最省事。
const cartBadgeCount = computed(() => (cart.items || [])
  .filter((item) => item.id !== QUICKBUY_ITEM_ID)
  .reduce((sum, item) => sum + Number(item.quantity || 0), 0));

const { contentEl, cartPillEl, cartBadgeEl, motionAllowed, bob, popCartBadge, flyToCart, onDocClickCapture, takeAddSource } = useCartUi({ cartBadgeCount, getView: () => view.value });
// 页头搜索 / 热搜词 / 页脚订阅。⚠️ 必须早于 useAdminContent —— 后者的实参里立即求值 loadHotSearches。
const { headerKeyword, hotSearches, subEmail, subMsg, goSearch, quickSearch, loadHotSearches, footerSubscribe } = useHeaderSearch({ api, router, route, sameShopQuery, scrollToResultsIfNeeded, markScrollToResults });

// 公告 / 热搜词 / 轮播位：后台三个配置型模块。
// ⚠️ 装配点必须在 isAdmin 之后（它是 ref，装配实参里立即求值）；api/run/showAlert/askConfirm/
// loadHotSearches 只在 composable 的函数体内被调（用户点击时才执行），只需顶层存在。
const { adminAnnouncements, announcementForm, announcementFormOpen, loadAdminAnnouncements, openAnnouncementForm, closeAnnouncementForm, saveAnnouncement, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch, adminBanners, bannerForm, bannerFormOpen, bannerUploading, loadAdminBanners, openBannerForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner } = useAdminContent({ api, isAdmin, run, showAlert, askConfirm, loadHotSearches });
// 商品详情：SKU 规格、图集、停留上报、评价提交。productNavLock 是 App.vue 的 let 变量
//（syncRoute 也要用）→ 留在原地，这里用读写器操作，避免两个来源各管一半。
const { productDetail, detailQuantity, currentImageIndex, reviewForm, relatedProducts, dwellEnterTs, dwellProductId, dwellSource, selectedSpec, safeParseSpec, selectedSkuImage, galleryImages, currentGalleryImage, specDimensions, selectedSku, selectedSpecText, selectedSkuPrice, selectedSkuOriginalPrice, effectiveDetailPrice, openReviewForm, submitReview, openProductDetail, refreshProductDetail, reportDwell, backFromProduct, changeDetailQty, loadRatingSummary } = useProductDetail({ api, run, fail, isAdmin, router, navigate, activeActivities, reviewedMap, loadProducts, ratingSummaryMap, isProductNavLocked: () => productNavLock, setProductNavLock: (v) => { productNavLock = v; }, getFlashLimitOfProduct: flashLimitOfProduct, getFlashSaleOfProduct: flashSaleOfProduct, getView: () => view.value });

// 购物车：增删改 + 可用券的数据源。
// 购物车：金额合计、增删改、可用券。⚠️ 装配点必须在 useGuestCart 之后（要用 guestAdd / guestRemoveItem 等）。
// takeAddSource / flyToCart 来自下面的 useCartUi，不能直接注入（会形成循环：useCartUi 又要
//    cartBadgeCount，而它是本 composable 的产物）→ 改成 onAddedFeedback 回调，由这里组装。
const { myCoupons, usableCoupons, selectedUserCouponId, userOptedOutCoupon, cartSyncTimers, addToCart, loadCart, loadMyCoupons, loadMoreMyCoupons, loadMyCouponsPage, loadUsableCoupons, couponEligible, couponShortfall, autoSelectCoupon, selectCoupon, chooseNoCoupon, stepQty, onQtyChange, onQtyInput, removeCartItem, clearCart } = useCart({ api, run, fail, askConfirm, session, isAdmin, cart, cartStore, setNotice: (v) => { notice.value = v; }, onAddedFeedback: (src, url) => flyToCart(takeAddSource(), url), guestAdd, guestRemoveItem, guestClear, persistGuestFromItems, recomputeCartTotals, refreshGuestCartView, getFlashLimitOfProduct: flashLimitOfProduct, getFlashSaleOfProduct: flashSaleOfProduct, getCartQtyMax: cartQtyMax });

// 购物车的纯金额计算层：只读状态，无副作用。⚠️ 必须在 useCart 之后（读它的 usableCoupons /
//    selectedUserCouponId）且在 useMemberPoints 之前（后者要 orderPayPreview）。
const { selectedCoupon, cartLocalTotal, cartOriginalSave, orderPayPreview, cartSelectedQty, cartTotalSaved, productBasePriceMap, catalogBasePrice } = useCartTotals({ cart, usableCoupons, selectedUserCouponId, getProducts: () => products.value });

// 会员积分体系：档位/资料/流水/结算预览。⚠️ 装配点必须在 orderPayPreview /
// effectiveMemberLevel / expressFreight / wallet 之后（它们是 ref，装配实参里立即求值）。
const { memberProfile, memberLedger, memberLevels, usePoints, pointsToUse, TIER_NAMES_FALLBACK, TIER_RATES_FALLBACK, tierRateForLevel, tierNameFor, productMemberView, memberUnitView, loadMemberLevels, loadMemberProfile, loadMemberLedger, memberPreview, balanceSufficient } = useMemberPoints({ api, session, isAdmin, wallet, effectiveMemberLevel, orderPayPreview, expressFreight });
// 订单：列表/支付/取消/收货/退款/详情。orderNavLock 与 orderDetail 被 syncRoute 共用 → 留在原地，
//    这里用读写器与注入的方式访问，保持单一真相源。
const { loadOrders, loadMoreOrders, loadOrdersPage, loadReviewedFlags, payOrder, reorder, cancelOrder, confirmReceipt, openRefundForm, submitRefund, openOrderDetail, closeOrderDetail } = useOrders({ api, run, fail, askConfirm, showAlert, money, session, isAdmin, router, navigate, orders, reviewedMap, refundForm, orderDetail, paying, isOrderNavLocked: () => orderNavLock, setOrderNavLock: (v) => { orderNavLock = v; }, loadWallet, loadMe, loadCart, loadFlashSales, loadProducts, refreshProductDetail });
// 立即购买：虚拟购物车项通道（不写购物车表）。依赖秒杀域与商品详情域的状态。
const { addDetailToCart, buildQuickBuyCartItem, buyDetailNow, enterQuickBuy, consumePendingQuickBuy } = useQuickBuy({ api, run, fail, session, isAdmin, router, navigate, notice, cart, quickBuy, pendingQuickBuy, productDetail, detailQuantity, selectedSpecText, effectiveDetailPrice, selectedSkuPrice, getFlashSaleOfProduct: flashSaleOfProduct, getFlashLimitOfProduct: flashLimitOfProduct, reportDwell, guestAdd, takeAddSource, flyToCart, loadCart, loadProducts, setPendingAction, takePendingAction, clearPendingAction, goLogin });
// 优惠券中心：可领列表 / 我的券 / 领取。与 useCart 的「可用券选择」是两件事（那边服务结算）。
const { loadCoupons, receiveCoupon } = useCoupons({ api, run, session, isAdmin, coupons, myCoupons });
// 后台各列表的数据加载。state 仍留在 App.vue —— 侧栏菜单要读它们的 total 算角标。
const { loadAdminProducts, loadAdminOrders, loadAdminStatsOverview, loadRefundOrders, loadStockAlerts, loadAdminCoupons, loadAdminUsers } = useAdminLoaders({ api, isAdmin, adminProducts, adminProductKeyword, adminProductStatus, adminJumpPage, adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage, adminStatsOverview, refundOrders, refundStatusFilter, refundJumpPage, stockAlerts, adminCoupons, adminCouponKeyword, adminCouponJumpPage, adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage });
// 结算下单：地址簿 + 去结算 + 提交订单（购物车 / 立即购买两条路径）。
const { loadAddresses, useAddress, saveAddress, goCheckout, createOrder } = useCheckout({ api, run, fail, session, isAdmin, router, navigate, route, notice, cart, paying, quickBuy, selectedUserCouponId, userOptedOutCoupon, addresses, selectedAddressId, addressForm, usePoints, pointsToUse, memberPreview, isPickup, activeStoreId, isExpress, fulfillment, QUICKBUY_ITEM_ID, loadWallet, loadMyCoupons, loadUsableCoupons, loadCart, loadOrders, loadFlashSales, loadProducts, refreshProductDetail, setPendingAction, goLogin });


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
    loadAdminHotSearches(),
  ]);
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
  // ⚠️ 必须带 loadMe()：头像环读的是 session.user.memberLevel，而它只来自登录那一刻的 /auth/me。
  // 若用户等级被服务端改了（下单升级 / 管理员 / SQL）却没重新登录，localStorage 里的旧值会让头像一直显示旧等级
  // （曾出现「库里已是银卡、头像却显示普通用户」）。每次进会话都重拉一次 /auth/me 才能对上。
  await Promise.all([loadWallet(), loadCart(), loadOrders(), loadAddresses(), loadMemberProfile(), loadMemberLevels(), loadFavoriteIds(), loadAlertUnread(), loadMessageUnread(), loadFlashSales(), loadMe()]);
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

// 离开结算页且仍处于「立即购买」会话：放弃本次快速结算，清掉虚拟项（购物车零残留）
watch(view, (nv, ov) => {
  if (ov === 'checkout' && nv !== 'checkout' && quickBuy.value) {
    quickBuy.value = null;
    cart.items = (cart.items || []).filter((i) => i.id !== QUICKBUY_ITEM_ID);
  }
});

watch(() => session.user?.role, () => {
  ensureAllowedView();

});


onMounted(async () => {
  startNoticeRotation();
  startFlashTick();
  window.addEventListener('beforeunload', reportDwell);
  document.addEventListener('click', onDocumentClick);
  document.addEventListener('click', onDocClickCapture, true);   // 捕获阶段：记录加购来源元素
  document.addEventListener('keydown', onDocumentKeydown);
  document.addEventListener('visibilitychange', () => { if (document.hidden) reportDwell(); });
  await run(async () => {
    await loadCategories();
    await loadHotSearches();     // 头部「热搜」词条（公开接口，游客可见）
    applyShopQueryFromRoute();   // 深链 /shop?category=3 要在首次 loadProducts 之前生效，否则先拉一遍全量再纠正
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
  stopNoticeRotation();
  stopFlashTick();
  window.removeEventListener('beforeunload', reportDwell);
  document.removeEventListener('visibilitychange', reportDwell);
  document.removeEventListener('click', onDocumentClick);
  document.removeEventListener('click', onDocClickCapture, true);
  document.removeEventListener('keydown', onDocumentKeydown);

});
const adminCtx = { adminAnnouncements, adminBanners, announcementForm, bannerForm, bannerFormOpen, bannerUploading, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminAnnouncements, adminBanners, adminProducts, adminStatsOverview, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openOrderDetail, orderDetail, orders, productForm, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, run, safeParseSpec, saveAnnouncement, session, showAlert, stockAlerts, announcementFormOpen, closeAnnouncementForm, saveBanner, toggleBanner, deleteBanner, bannerForm, bannerFormOpen, openBannerForm, closeBannerForm, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch };

const appCtx = { productsLoading, ADMIN_MENU_KEYS, ROUTE_VIEWS, activeActivities, adminBanners, bannerUploading, loadAdminBanners, bannerForm, bannerFormOpen, openBannerForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, addDetailToCart, addToCart, addressForm, addresses, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminCtx, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, api, applyFilters, askConfirm, authErrors, authOpen, authSubmitting, authTab, autoSelectCoupon, avatarInput, backFromProduct, backToShop, balanceSufficient, buildQrSvg, buyDetailNow, quickBuy, cancelOrder, reorder, cancelRechargeOrder, cart, cartLocalTotal, cartOriginalSave, cartSelectedQty, cartSyncTimers, cartTotalSaved, cartActivityProgress, imgFallback, refreshCurrentPage, topActivity, activitySlogan, productActivityTag, ratingSummaryMap, adminAnnouncements, announcementForm, loadAdminAnnouncements, openAnnouncementForm, saveAnnouncement, toggleAnnouncement, deleteAnnouncement, categories, categoryName, changeDetailQty, chooseCategory, chooseNoCoupon, clearCart, clearRechargeTimer, closeAlert, closeAuth, closeOrderDetail, closeRechargeModal, computed, confirmDialog, confirmReceipt, confirmRecharge, couponEligible, couponShortfall, coupons, createOrder, currentGalleryImage, currentImageIndex, currentTitle, detailQuantity, discountRate, discountSave, dwellEnterTs, dwellProductId, dwellRankProducts, dwellSource, ensureAllowedView, error, fail, filters, forgotPassword, formatCountdown, formatCouponStatus, formatDate, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, fulfillmentLabel, orderStatusLabel, galleryImages, goCheckout, guessProducts, handleAuthExpired, handleRechargeExpired, hotProducts, initials, isAdmin, channelRotatable, rotateChannel, itemOriginalSave, loadAddresses, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCart, loadCategories, loadCoupons, loadDwellRank, loadGuess, loadHomeChannels, loadHot, loadMe, loadMyCoupons, loadNew, loadOrders, loadProducts, loadRefundOrders, loadReviewedFlags, loadStockAlerts, loadUsableCoupons, loadWallet, loginForm, logout, methodLabel, money, myCoupons, navigate, newProducts, nextTick, notice, onAvatarPick, onBeforeUnmount, onCustomAmountInput, onMounted, onQtyChange, onQtyInput, openAuth, openOrderDetail, openProductDetail, openRefundForm, openReviewForm, orderDetail, orderPayPreview, orderStatusTag, orders, payOrder, payRechargeOrder, paying, productDetail, productForm, products, provide, qrSvg, reactive, receiveCoupon, recharge, rechargePresets, ref, refreshAdminData, refreshForSession, refundForm, refundJumpPage, refundOrders, refundStatusFilter, refundStatusTag, registerForm, relatedProducts, rememberUser, removeCartItem, reportDwell, resetAuthErrors, resetFilters, resetRecharge, resolveConfirm, resolveUnit, reviewForm, reviewedMap, run, safeParseSpec, saveAddress, selectCoupon, selectRechargePreset, selectedAddress, selectedAddressId, selectedCoupon, selectedSku, selectedSpec, selectedSpecText, selectedSkuPrice, selectedSkuOriginalPrice, effectiveDetailPrice, selectedUserCouponId, session, setToken, shipStatusOf, showAlert, specDimensions, startCountdown, stepQty, stockAlerts, submitLogin, submitRefund, submitRegister, submitReview, switchAuth, usableCoupons, useAddress, userOptedOutCoupon, validateRegisterForm, memberProfile, memberLedger, memberLevels, usePoints, pointsToUse, tierRateForLevel, tierNameFor, memberPreview, loadMemberProfile, loadMemberLedger, loadMemberLevels, favoriteIds, favorites, priceAlerts, alertUnread, isFavorite, toggleFavorite, loadFavoriteIds, loadFavorites, loadPriceAlerts, loadAlertUnread, markAlertsRead, stores, deliverySlots, fulfillment, isPickup, isExpress, expressFreight, selectedStore, activeStoreId, loadStores, loadDeliverySlots, selectFulfillment, selectStore, resetFulfillment, messages, messageUnread, messageTypeFilter, loadMessages, loadMessageUnread, changeMessageFilter, markMessagesRead, openMessage, flashSales, runningFlashSales, loadFlashSales, flashRemaining, flashDeadlineText, formatDuration, nowTick, legalDocs, loadLegalDoc, view, wallet, watch, cartQtyMax, cartQtyCapped, isFlashSplit, flashSplitNote, flashSaleOfProduct, flashLimitOfProduct, flashLimitMessage };
appCtx.orders = orders;
appCtx.loadOrders = loadOrders;
appCtx.loadMoreOrders = loadMoreOrders;
appCtx.loadOrdersPage = loadOrdersPage;
appCtx.loadMessages = loadMessages;
appCtx.loadMessagesPage = loadMessagesPage;
appCtx.loadFavoritesPage = loadFavoritesPage;
appCtx.loadPriceAlertsPage = loadPriceAlertsPage;
appCtx.loadMyCoupons = loadMyCoupons;
appCtx.loadMoreMyCoupons = loadMoreMyCoupons;
appCtx.loadMyCouponsPage = loadMyCouponsPage;
appCtx.productMemberView = productMemberView;
appCtx.memberUnitView = memberUnitView;
// 下单/支付/取消后只刷当前详情页的库存销量（列表由 loadProducts 负责）。
// ⚠️ 必须挂到 appCtx：PayPage / PayPage 模板里通过 appCtx 调它，漏挂会让
//    `appCtx.refreshProductDetail is not a function` 抛错 → 整个 withMinSpinner reject
//    → 支付成功却不跳转、还顺带吞掉转圈（2026-10-07 踩过，症状极具误导性）。
appCtx.refreshProductDetail = refreshProductDetail;
// 管理员「以某会员身份预览价格」：状态暴露给需要直接读取的面板（卡片/详情页走上面两个函数已自动生效）
appCtx.previewTier = previewTier;
appCtx.isPreviewing = isPreviewing;
appCtx.setPreviewTier = setPreviewTier;
appCtx.clearPreviewTier = clearPreviewTier;
appCtx.catalogBasePrice = catalogBasePrice;
// 以下显式挂到 appCtx，供独立登录页 LoginPage 委托调用（见 pages/LoginPage.vue）。
// 登录/注册的核心逻辑仍集中在这里（单一真相源），LoginPage 只负责渲染表单 + 登录后跳哪。
appCtx.openLegal = openLegal;
appCtx.navigate = navigate;
appCtx.session = session;
appCtx.showAlert = showAlert;
appCtx.error = error;
appCtx.fail = fail;
appCtx.rememberUser = rememberUser;
appCtx.refreshForSession = refreshForSession;
appCtx.loadMe = loadMe;
appCtx.toggleFavorite = toggleFavorite;
appCtx.consumePendingQuickBuy = consumePendingQuickBuy;
appCtx.submitLogin = submitLogin;
appCtx.submitRegister = submitRegister;
appCtx.submitChangePassword = submitChangePassword;
appCtx.submitPasswordReset = submitPasswordReset;
appCtx.authErrorText = authErrorText;
// 登录/注册表单状态原本只给 App.vue 内的 modal 用，独立登录页也要渲染「改密 / 找回」表单，
// 这里把对应表单状态显式挂上（页面用不到，但 LoginPage 需要）。
appCtx.loginForm = loginForm;
appCtx.registerForm = registerForm;
appCtx.authErrors = authErrors;
appCtx.authSubmitting = authSubmitting;
appCtx.changeForm = changeForm;
appCtx.changeErrors = changeErrors;
appCtx.changeSubmitting = changeSubmitting;
appCtx.resetForm = resetForm;
appCtx.resetErrors = resetErrors;
appCtx.resetSubmitting = resetSubmitting;
provide('appCtx', appCtx);
</script>
