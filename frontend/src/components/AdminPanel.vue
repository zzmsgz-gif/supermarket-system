<template>
      <section class="admin-layout">
        <aside class="admin-sidebar">
          <div class="sidebar-head">
            <span class="sidebar-eyebrow">管理后台</span>
            <strong>{{ session.user?.username || '管理员' }}</strong>
          </div>
          <nav class="sidebar-nav">
            <template v-for="group in adminMenuGroups" :key="group.name">
              <span class="sidebar-group">{{ group.name }}</span>
              <button
                v-for="item in group.items"
                :key="item.key"
                :class="['sidebar-item', { active: adminMenu === item.key }]"
                @click="selectAdminMenu(item.key)"
              >
                <span class="sidebar-icon" v-html="adminIcons[item.key]"></span>
                <span class="sidebar-label">{{ item.label }}</span>
                <span v-if="item.badge" :class="['sidebar-badge', { warn: item.warn }]">{{ item.badge }}</span>
              </button>
            </template>
          </nav>
          <div class="sidebar-foot">
            <button class="ghost" @click="refreshAdminData">刷新全部数据</button>
          </div>
        </aside>

        <div class="admin-main">
          <header class="admin-head">
            <div>
              <h2>{{ currentAdminMenu?.label }}</h2>
              <small>{{ currentAdminMenu?.desc }}</small>
            </div>
            <button @click="refreshCurrentAdminMenu">刷新</button>
          </header>

          <AdminOrdersPanel v-if="adminMenu === 'orders'" :admin-orders="adminOrders" v-model:admin-order-keyword="adminOrderKeyword" :admin-order-status="adminOrderStatus" v-model:admin-order-jump-page="adminOrderJumpPage" :admin-order-total-pages="adminOrderTotalPages" :ship-form="shipForm" :search-admin-orders="searchAdminOrders" :change-admin-order-page="changeAdminOrderPage" :change-admin-order-page-size="changeAdminOrderPageSize" :go-admin-order-page="goAdminOrderPage" :reset-admin-order-search="resetAdminOrderSearch" :open-ship-form="openShipForm" :admin-order-ready="adminOrderReady" :submit-ship="submitShip" :complete-admin-order="completeAdminOrder" :cancel-admin-order="cancelAdminOrder" :open-order-detail="openOrderDetail"/>

          <AdminRefundsPanel v-if="adminMenu === 'refunds'" :admin-ctx="adminCtx" :search-refunds="searchRefunds" :change-refund-page="changeRefundPage" :change-refund-page-size="changeRefundPageSize" :go-refund-page="goRefundPage" :reset-refund-search="resetRefundSearch" :review-admin-refund="reviewAdminRefund" :submit-refund-review="submitRefundReview" :refund-review-form="refundReviewForm" :refund-total-pages="refundTotalPages"  v-model:refund-status-filter="refundStatusFilter"  v-model:refund-jump-page="refundJumpPage"/>
          <AdminStockPanel v-if="adminMenu === 'stock'" :stock-alerts="stockAlerts" v-model:stock-keyword="stockKeyword" :stock-page="stockPage" :stock-size="stockSize" v-model:stock-jump-page="stockJumpPage" :stock-filtered="stockFiltered" :stock-total-pages="stockTotalPages" :stock-page-items="stockPageItems" :change-stock-page="changeStockPage" :go-stock-page="goStockPage" :reset-stock-page="resetStockPage" :stock-form="stockForm" :open-stock-form="openStockForm" :submit-stock="submitStock" :reset-stock-search="resetStockSearch"/>

          <AdminProductsPanel v-if="adminMenu === 'products'" :admin-ctx="adminCtx" :stock-form="stockForm" :open-stock-form="openStockForm" :submit-stock="submitStock"  v-model:admin-product-keyword="adminProductKeyword"  v-model:admin-jump-page="adminJumpPage"/>

          <AdminCategoriesPanel v-if="adminMenu === 'categories'" :categories="categories" v-model:category-keyword="categoryKeyword" :category-page="categoryPage" :category-size="categorySize" v-model:category-jump-page="categoryJumpPage" :category-filtered="categoryFiltered" :category-total-pages="categoryTotalPages" :category-page-items="categoryPageItems" :change-category-page="changeCategoryPage" :go-category-page="goCategoryPage" :reset-category-page="resetCategoryPage" :reset-category-search="resetCategorySearch" :category-form="categoryForm" :save-category="saveCategory" :category-name="categoryName"/>

          <AdminCouponsPanel v-if="adminMenu === 'coupons'" :admin-ctx="adminCtx" :fill-coupon-period="fillCouponPeriod" :save-coupon="saveCoupon" :search-admin-coupons="searchAdminCoupons" :change-admin-coupon-page-size="changeAdminCouponPageSize" :reset-admin-coupon-search="resetAdminCouponSearch" :toggle-coupon="toggleCoupon" :change-admin-coupon-page="changeAdminCouponPage" :go-admin-coupon-page="goAdminCouponPage" :admin-coupon-total-pages="adminCouponTotalPages" :coupon-form="couponForm"  v-model:admin-coupon-jump-page="adminCouponJumpPage"  v-model:admin-coupon-keyword="adminCouponKeyword"/>
          <AdminInsightsPanel v-if="adminMenu === 'insights'" ref="insightsPanelRef" :admin-ctx="adminCtx" :select-menu="selectAdminMenu" />

          <AdminFlashSalesPanel v-if="adminMenu === 'flashSales'" :admin-flash-sales="adminFlashSales" :categories="categories" :close-flash-form="closeFlashForm" :delete-flash-sale="deleteFlashSale" :flash-editing-id="flashEditingId" :flash-form="flashForm" :flash-form-open="flashFormOpen" :flash-product-options="flashProductOptions" :flash-state-class="flashStateClass" :flash-state-label="flashStateLabel" :open-flash-form="openFlashForm" :save-flash-sale="saveFlashSale" :toggle-flash-status="toggleFlashStatus"/>

          <AdminActivitiesPanel v-if="adminMenu === 'activities'" :activity-discount-label="activityDiscountLabel" :activity-form="activityForm" :activity-products="activityProducts" :activity-scope-label="activityScopeLabel" :activity-type-label="activityTypeLabel" :admin-activities="adminActivities" v-model:admin-activity-jump-page="adminActivityJumpPage" v-model:admin-activity-keyword="adminActivityKeyword" :admin-activity-total-pages="adminActivityTotalPages" :categories="categories" :change-admin-activity-page="changeAdminActivityPage" :change-admin-activity-page-size="changeAdminActivityPageSize" :delete-activity="deleteActivity" :edit-activity="editActivity" :fill-activity-period="fillActivityPeriod" :on-activity-scope-change="onActivityScopeChange" :reset-activity-form="resetActivityForm" :reset-admin-activity-search="resetAdminActivitySearch" :save-activity="saveActivity" :search-admin-activities="searchAdminActivities" :toggle-activity="toggleActivity"/>

          <AdminNoticesPanel v-if="adminMenu === 'notices'" :admin-ctx="adminCtx" :notice-type-label="noticeTypeLabel" :notice-type-class="noticeTypeClass" />
          <AdminHotSearchesPanel v-if="adminMenu === 'hotSearches'" :admin-ctx="adminCtx" />

          <!-- 会员日：指定日期消费积分翻倍（从日历上挑具体日期，不再按「每月几号」循环）。
               原先「会员日 每月18号 双倍积分」只是公告里的一句话、后端没实现（假承诺）。
               公告文案由管理员自己维护，系统不改写。 -->
          <AdminMemberDaysPanel v-if="adminMenu === 'memberDays'" :m-d-c_-w-e-e-k="MDC_WEEK" :close-member-day-form="closeMemberDayForm" :delete-member-day="deleteMemberDay" :member-day-calendar-cells="memberDayCalendarCells" :member-day-calendar-month-text="memberDayCalendarMonthText" :member-day-date-label="memberDayDateLabel" :member-day-date-taken="memberDayDateTaken" :member-day-enabled-count="memberDayEnabledCount" :member-day-form="memberDayForm" :member-day-form-open="memberDayFormOpen" :member-day-slogan="memberDaySlogan" :member-days="memberDays" :open-member-day-form="openMemberDayForm" :reset-member-day-calendar="resetMemberDayCalendar" :save-member-day="saveMemberDay" :shift-member-day-calendar="shiftMemberDayCalendar" :toggle-member-day="toggleMemberDay"/>

          <AdminBannersPanel v-if="adminMenu === 'banners'" :admin-ctx="adminCtx" :admin-product-name="adminProductName" />
          <AdminStoresPanel v-if="adminMenu === 'stores'" :admin-stores="adminStores" :store-form="storeForm" :store-form-open="storeFormOpen" :store-editing-id="storeEditingId" :open-store-form="openStoreForm" :close-store-form="closeStoreForm" :save-store="saveStore" :toggle-store-status="toggleStoreStatus" :delete-store="deleteStore" :load-admin-stores="loadAdminStores"/>

          <AdminUsersPanel v-if="adminMenu === 'users'" :admin-ctx="adminCtx" :search-admin-users="searchAdminUsers" :change-admin-user-page="changeAdminUserPage" :change-admin-user-page-size="changeAdminUserPageSize" :go-admin-user-page="goAdminUserPage" :reset-admin-user-search="resetAdminUserSearch" :toggle-user="toggleUser" :member-level-name="memberLevelName" :admin-user-total-pages="adminUserTotalPages"  v-model:admin-user-jump-page="adminUserJumpPage"  v-model:admin-user-keyword="adminUserKeyword"  v-model:admin-user-status="adminUserStatus"  v-model:admin-user-role="adminUserRole"/>
          <AdminReviewsPanel v-if="adminMenu === 'reviews'" :admin-ctx="adminCtx" :search-admin-reviews="searchAdminReviews" :change-admin-review-page="changeAdminReviewPage" :save-review-reply="saveReviewReply" :toggle-review-hidden="toggleReviewHidden" :admin-review-total-pages="adminReviewTotalPages" :admin-reviews="adminReviews" :admin-review-summary="adminReviewSummary" :admin-review-rating="adminReviewRating" :admin-review-replied="adminReviewReplied" :admin-review-keyword="adminReviewKeyword" :load-admin-reviews="loadAdminReviews" :load-admin-review-unreplied="loadAdminReviewUnreplied" :review-reply-draft="reviewReplyDraft"/>
          <AdminPasswordResetsPanel v-if="adminMenu === 'passwordResets'" :change-password-reset-page="changePasswordResetPage" :change-password-reset-page-size="changePasswordResetPageSize" :confirm-reject-password-reset="confirmRejectPasswordReset" :confirm-reset-password="confirmResetPassword" :copy-temp-password="copyTempPassword" v-model:password-reset-jump-page="passwordResetJumpPage" :password-reset-result="passwordResetResult" :password-reset-status="passwordResetStatus" :password-reset-status-class="passwordResetStatusClass" :password-reset-status-label="passwordResetStatusLabel" :password-reset-total-pages="passwordResetTotalPages" :password-resets="passwordResets" :refresh-current-admin-menu="refreshCurrentAdminMenu"/>
        </div>
      </section>
</template>

<script setup>
import { ref, reactive, computed, onMounted, toRef, nextTick, watch, defineAsyncComponent } from 'vue';
import { api } from '../api/client';
import { useAdminStores } from '../composables/useAdminStores.js';
import { useAdminFlash } from '../composables/useAdminFlash.js';
import { useAdminActivities } from '../composables/useAdminActivities.js';
import { useAdminPasswordResets } from '../composables/useAdminPasswordResets.js';
import { useAdminMemberDays } from '../composables/useAdminMemberDays.js';
import { useAdminReviews } from '../composables/useAdminReviews.js';
import { useAdminCategoryStockPaging } from '../composables/useAdminCategoryStockPaging.js';
import { useAdminCategoryStock } from '../composables/useAdminCategoryStock.js';
import { useAdminOrders } from '../composables/useAdminOrders.js';
import { useAdminRefunds } from '../composables/useAdminRefunds.js';
import { useAdminCoupons } from '../composables/useAdminCoupons.js';
import { useAdminUsers } from '../composables/useAdminUsers.js';
import { discountRate, discountSave, fulfillmentLabel, formatCouponStatus, formatDate, formatPaymentStatus, formatProductStatus, formatRefundStatus, formatRole, formatUnit, initials, itemOriginalSave, money, orderSavedTotal, orderStatusLabel, orderStatusTag, refundStatusTag, resolveUnit } from '../utils/format';
import ImageUpload from './ImageUpload.vue';
import AdminPager from './AdminPager.vue';
import AdminStockFormRow from './AdminStockFormRow.vue';
import AdminPageSize from './AdminPageSize.vue';
import AdminSearchBox from './AdminSearchBox.vue';
// 两个重面板改为懒加载：首次进入对应 tab 才下载该面板 chunk，不进 AdminPanel 主包
const AdminInsightsPanel = defineAsyncComponent(() => import('./AdminInsightsPanel.vue'));
const AdminProductsPanel = defineAsyncComponent(() => import('./AdminProductsPanel.vue'));
const AdminBannersPanel = defineAsyncComponent(() => import('./AdminBannersPanel.vue'));
const AdminNoticesPanel = defineAsyncComponent(() => import('./AdminNoticesPanel.vue'));
const AdminHotSearchesPanel = defineAsyncComponent(() => import('./AdminHotSearchesPanel.vue'));
const AdminRefundsPanel = defineAsyncComponent(() => import('./AdminRefundsPanel.vue'));
const AdminReviewsPanel = defineAsyncComponent(() => import('./AdminReviewsPanel.vue'));
const AdminFlashSalesPanel = defineAsyncComponent(() => import('./AdminFlashSalesPanel.vue'));
const AdminActivitiesPanel = defineAsyncComponent(() => import('./AdminActivitiesPanel.vue'));
const AdminMemberDaysPanel = defineAsyncComponent(() => import('./AdminMemberDaysPanel.vue'));
const AdminPasswordResetsPanel = defineAsyncComponent(() => import('./AdminPasswordResetsPanel.vue'));
const AdminStoresPanel = defineAsyncComponent(() => import('./AdminStoresPanel.vue'));
const AdminOrdersPanel = defineAsyncComponent(() => import('./AdminOrdersPanel.vue'));
const AdminStockPanel = defineAsyncComponent(() => import('./AdminStockPanel.vue'));
const AdminCategoriesPanel = defineAsyncComponent(() => import('./AdminCategoriesPanel.vue'));
const AdminUsersPanel = defineAsyncComponent(() => import('./AdminUsersPanel.vue'));
const AdminCouponsPanel = defineAsyncComponent(() => import('./AdminCouponsPanel.vue'));

const props = defineProps({
  // 响应式：admin 内会读取 view / categories
  view: { type: Object, required: true },
  categories: { type: Array, required: true },
  // 共享上下文：内含 App.vue 的 ref / reactive / 函数（整体传入，避免 Vue 对顶层 ref 自动解包）
  adminCtx: { type: Object, required: true },
});

// AdminPanel only mounts when isAdmin && view==='admin', so isAdmin is always true here.
const isAdmin = { value: true };
const view = toRef(props, 'view');
const categories = toRef(props, 'categories');
const { adminChartProducts, adminCouponJumpPage, adminCouponKeyword, adminCoupons, adminJumpPage, adminMenu, adminOrderJumpPage, adminOrderKeyword, adminOrderStatus, adminOrders, adminProductKeyword, adminProductStatus, adminProducts, adminAnnouncements, adminBanners, adminStatsOverview, announcementForm, announcementFormOpen, bannerForm, bannerFormOpen, bannerUploading, adminUserJumpPage, adminUserKeyword, adminUserRole, adminUserStatus, adminUsers, alertDialog, askConfirm, categoryName, confirmDialog, coupons, error, fail, filters, loadAdminAnnouncements, loadAdminBanners, loadAdminCoupons, loadAdminOrders, loadAdminProducts, loadAdminStatsOverview, loadAdminUsers, loadCategories, loadProducts, loadRefundOrders, loadStockAlerts, notice, openAnnouncementForm, openBannerForm, openOrderDetail, orderDetail, orders, productForm, saveAnnouncement, products, refreshAdminData, refundJumpPage, refundOrders, refundStatusFilter, run, safeParseSpec, session, showAlert, stockAlerts, closeAnnouncementForm, closeBannerForm, saveBanner, toggleBanner, deleteBanner, toggleAnnouncement, deleteAnnouncement, adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch, } = props.adminCtx;

// 会员等级名称（与后端 MemberService 档位一致，后台仅展示用）
const MEMBER_LEVEL_NAMES = ['普通用户', '银卡会员', '金卡会员', '钻石会员', '紫钻会员', '黑卡会员', '至尊会员'];
const memberLevelName = (level) => MEMBER_LEVEL_NAMES[Number(level) || 0] || '普通用户';

const adminIcon = (paths) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">${paths}</svg>`;

const adminIcons = {
  dashboard: adminIcon('<rect x="3.5" y="11" width="4.5" height="9.5" rx="1"/><rect x="9.75" y="3.5" width="4.5" height="17" rx="1"/><rect x="16" y="7.5" width="4.5" height="13" rx="1"/>'),
  orders: adminIcon('<rect x="4" y="3.5" width="16" height="17" rx="2.5"/><path d="M8.5 9h7M8.5 13h7M8.5 17h4"/>'),
  refunds: adminIcon('<path d="M9.5 14.5 4.5 9.5l5-5"/><path d="M4.5 9.5H15a5.5 5.5 0 0 1 0 11h-4"/>'),
  stock: adminIcon('<path d="M12 4 2.8 20h18.4L12 4z"/><path d="M12 10v4.2M12 17.2h.01"/>'),
  products: adminIcon('<path d="M3.5 7.5 12 3.5l8.5 4-8.5 4-8.5-4z"/><path d="M3.5 7.5v9L12 20.5l8.5-4v-9"/><path d="M12 11.5v9"/>'),
  categories: adminIcon('<rect x="3.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.5"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.5"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.5"/>'),
  coupons: adminIcon('<path d="M3.5 8.5A2 2 0 0 1 5.5 6.5h13a2 2 0 0 1 2 2v1.6a2.4 2.4 0 0 0 0 3.8v1.6a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-1.6a2.4 2.4 0 0 0 0-3.8V8.5z"/><path d="M12 8v8"/>'),
  users: adminIcon('<circle cx="9" cy="8" r="3.4"/><path d="M3 20.2c0-3.3 2.7-5.2 6-5.2s6 1.9 6 5.2"/><path d="M16.2 5.2a3 3 0 0 1 0 5.6"/><path d="M18.4 14.9c1.9.7 3 2.4 3 4.4"/>'),
  activities: adminIcon('<path d="M3.5 16.5 9 11l3.5 3.5L20.5 7"/><path d="M15.5 7H20.5V12"/>'),
  stores: adminIcon('<path d="M4 9.5 5.2 4.5h13.6L20 9.5"/><path d="M4 9.5h16v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-10z"/><path d="M9.5 20.5v-5h5v5"/>'),
  insights: adminIcon('<path d="M4 19.5h16"/><path d="M7 16.5V10M12 16.5V5.5M17 16.5v-4.5"/>'),
  flashSales: adminIcon('<path d="M13.5 2.5 5 13.5h5.5l-1 8 9-11h-5.5l.5-8z"/>'),
  passwordResets: adminIcon('<circle cx="7.5" cy="15.5" r="3.5"/><path d="M10 13 19.5 3.5"/><path d="M16.5 3.5H20V7"/>'),
  memberDays: adminIcon('<rect x="3.5" y="5" width="17" height="15.5" rx="2.5"/><path d="M8 3v4M16 3v4M3.5 10h17"/><path d="M12 12.6l1.1 2.2 2.4.35-1.75 1.7.42 2.4-2.17-1.14-2.17 1.14.42-2.4-1.75-1.7 2.4-.35z"/>'),
};

const adminMenuItems = computed(() => [
  { key: 'insights', label: '经营看板', desc: '全量概览 + 按时间维度看成交、客单价、复购与品类结构（含环比）', group: '经营' },
  { key: 'orders', label: '订单管理', desc: '查询订单、录入快递单号发货、完成或取消订单', group: '经营', badge: (adminOrders.total || 0) || '' },
  { key: 'refunds', label: '售后管理', desc: '审核用户的退款申请，同意后款项退回用户钱包', group: '经营', badge: (refundOrders.total || 0) || '', warn: true },
  { key: 'stock', label: '库存预警', desc: '低于预警阈值的商品列表，支持一键补货', group: '经营', badge: stockAlerts.value.length || '', warn: true },
  { key: 'reviews', label: '评价管理', desc: '查看、回复与隐藏用户评价；角标是未回复数（有人等你回话）', group: '经营', badge: (adminReviewSummary.value?.unrepliedCount || 0) || '', warn: true },
  { key: 'products', label: '商品管理', desc: '新增商品、查看上架状态、手动入库', group: '管理', badge: (adminProducts.total || 0) || '' },
  { key: 'categories', label: '分类管理', desc: '维护商品分类与排序', group: '管理', badge: categories.value.length || '' },
  { key: 'coupons', label: '优惠券管理', desc: '创建满减券、发放与停用', group: '管理', badge: (adminCoupons.total || 0) || '' },
  { key: 'activities', label: '营销活动', desc: '创建满减/折扣活动，按全场、类目或商品精准投放', group: '管理', badge: (adminActivities.total || 0) || '' },
  { key: 'flashSales', label: '限时秒杀', desc: '按商品开秒杀场次：秒杀价、独立名额、每人限购与档期', group: '管理', badge: adminFlashSales.value.filter((f) => f.state === 'RUNNING').length || '' },
  { key: 'notices', label: '公告管理', desc: '发布商城公告：类型分类（促销类标题会进首页顶栏）、排序、随时停用', group: '管理', badge: adminAnnouncements.value.length || '' },
  { key: 'hotSearches', label: '热搜词', desc: '维护首页头部搜索框下方的「热搜」那排词：搜索词、展示文案、排序与启停（点击即跳转搜索）', group: '管理', badge: adminHotSearches.value.length || '' },
  { key: 'memberDays', label: '会员日', desc: '从日历上挑日期：挑中的那天消费积分翻倍（可配多天、可调倍率）；公告文案由你自己维护', group: '管理', badge: memberDays.value.filter((d) => Number(d.enabled) === 1 && !d.expired).length || '' },
  { key: 'stores', label: '门店自提', desc: '维护门店/自提点：名称、地址、营业时间与自提须知，停用后前台不可选', group: '管理', badge: adminStores.value.filter((s) => s.status === 1).length || '' },
  { key: 'banners', label: '轮播管理', desc: '维护首页轮播位：图片、文案、跳转商品与排序', group: '管理', badge: adminBanners.value.length || '' },
  { key: 'users', label: '用户管理', desc: '查看账号余额，启用或禁用账号', group: '管理', badge: (adminUsers.total || 0) || '' },
  { key: 'passwordResets', label: '找回密码', desc: '核对身份后重置为一次性临时密码，用户首次登录强制改密', group: '管理', badge: passwordResets.pending || '', warn: true },
]);

const adminMenuGroups = computed(() => {
  const groups = [];
  for (const item of adminMenuItems.value) {
    let group = groups.find((entry) => entry.name === item.group);
    if (!group) {
      group = { name: item.group, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  }
  return groups;
});

const currentAdminMenu = computed(() => adminMenuItems.value.find((item) => item.key === adminMenu.value) || adminMenuItems.value[0]);

// 进后台就拉一次待处理数：否则菜单角标要等点进「找回密码」才显示，等于没提醒
onMounted(() => {
  loadPasswordResetPendingCount();
  loadAdminReviewUnreplied();
  // 当前模块数据补一次：默认「经营看板」此前没有任何地方会触发 dashboard 接口（只有面板里的手动刷新按钮），
  // 深链 /admin?tab=notices 同理。挂载时兜住，保证「进来就有数据」。
  refreshCurrentAdminMenu();
});

const insightsPanelRef = ref(null);
/* ---------------- 门店 / 秒杀：已抽为 composable ---------------- */
// 装配点必须在 isAdmin / fail / run / askConfirm 之后（adminCtx 已解构出它们）。
const { adminStores, storeFormOpen, storeEditingId, storeForm, loadAdminStores, resetStoreForm, openStoreForm, closeStoreForm, storePayload, saveStore, toggleStoreStatus, deleteStore } = useAdminStores({ isAdmin, fail, run, askConfirm });

const { adminFlashSales, flashFormOpen, flashEditingId, flashProductOptions, flashForm, loadAdminFlashSales, loadFlashProductOptions, flashStateLabel, flashStateClass, toDateTimeInput, openFlashForm, closeFlashForm, saveFlashSale, toggleFlashStatus, deleteFlashSale } = useAdminFlash({ isAdmin, fail, run, askConfirm });

const { adminActivities, adminActivityKeyword, adminActivityJumpPage, adminActivityTotalPages, activityProducts, activityProductsLoaded, activityForm, loadActivityProducts, loadAdminActivities, searchAdminActivities, changeAdminActivityPage, changeAdminActivityPageSize, goAdminActivityPage, resetAdminActivitySearch, resetActivityForm, onActivityScopeChange, fillActivityPeriod, activityTypeLabel, activityScopeLabel, activityDiscountLabel, editActivity, saveActivity, toggleActivity, deleteActivity } = useAdminActivities({ isAdmin, fail, run, askConfirm, categories });

const { passwordResets, passwordResetStatus, passwordResetJumpPage, passwordResetResult, passwordResetTotalPages, PASSWORD_RESET_STATUS_LABELS, passwordResetStatusLabel, passwordResetStatusClass, loadPasswordResets, loadPasswordResetPendingCount, searchPasswordResets, changePasswordResetPage, changePasswordResetPageSize, goPasswordResetPage, confirmResetPassword, confirmRejectPasswordReset, copyTempPassword } = useAdminPasswordResets({ run, fail, askConfirm, notice });

const { memberDays, memberDayFormOpen, memberDayForm, memberDayCalendarCursor, memberDayEnabledCount, memberDaySlogan, MDC_WEEK, isoDateOf, memberDayTodayIso, memberDayCalendarMonthText, memberDayCalendarCells, memberDayDateTaken, memberDayDateLabel, memberDayShortLabel, firstFreeMemberDayDate, shiftMemberDayCalendar, resetMemberDayCalendar, loadMemberDays, openMemberDayForm, closeMemberDayForm, saveMemberDay, toggleMemberDay, deleteMemberDay } = useAdminMemberDays({ isAdmin, fail, run, askConfirm, showAlert });

const { adminReviews, adminReviewSummary, adminReviewRating, adminReviewReplied, adminReviewKeyword, reviewReplyDraft, adminReviewTotalPages, loadAdminReviews, searchAdminReviews, changeAdminReviewPage, loadAdminReviewUnreplied, saveReviewReply, toggleReviewHidden } = useAdminReviews({ run, askConfirm, showAlert });

// ===== 分类 / 库存 / 订单 / 退款 / 优惠券 / 用户：已抽为 composable =====
const { categoryKeyword, categoryPage, categorySize, categoryJumpPage, categoryFiltered, categoryTotalPages, categoryPageItems, changeCategoryPage, goCategoryPage, stockKeyword, stockPage, stockSize, stockJumpPage, stockFiltered, stockTotalPages, stockPageItems, changeStockPage, goStockPage, resetCategoryPage, resetCategorySearch, resetStockPage, resetStockSearch } = useAdminCategoryStockPaging({ categories, stockAlerts });

const { categoryForm, stockForm, noticeTypeLabel, noticeTypeClass, adminProductName, trendHasData, openStockForm, submitStock, saveCategory } = useAdminCategoryStock({ run, fail, askConfirm, loadAdminProducts, loadStockAlerts, loadProducts, loadCategories, adminProducts, adminStatsOverview });

const { shipForm, adminOrderTotalPages, searchAdminOrders, changeAdminOrderPage, changeAdminOrderPageSize, goAdminOrderPage, resetAdminOrderSearch, openShipForm, adminOrderReady, submitShip, completeAdminOrder, cancelAdminOrder } = useAdminOrders({ api, run, fail, askConfirm, money, orderSavedTotal, formatRole, adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage, loadAdminOrders, loadRefundOrders, loadAdminUsers });

const { refundReviewForm, refundTotalPages, searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage, resetRefundSearch, reviewAdminRefund, submitRefundReview } = useAdminRefunds({ api, run, askConfirm, money, orderSavedTotal, refundOrders, refundJumpPage, refundStatusFilter, loadRefundOrders, loadAdminOrders });

const { couponForm, adminCouponTotalPages, searchAdminCoupons, changeAdminCouponPage, changeAdminCouponPageSize, goAdminCouponPage, resetAdminCouponSearch, fillCouponPeriod, saveCoupon, toggleCoupon } = useAdminCoupons({ api, run, fail, askConfirm, money, adminCoupons, adminCouponKeyword, adminCouponJumpPage, loadAdminCoupons });

const { adminUserTotalPages, searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage, resetAdminUserSearch, toggleUser } = useAdminUsers({ api, run, askConfirm, money, formatRole, adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, loadAdminUsers });

const adminMenuLoaders = {
  insights: () => insightsPanelRef.value?.load(),
  orders: () => loadAdminOrders(),
  refunds: () => loadRefundOrders(),
  reviews: () => loadAdminReviews(),
  stock: () => loadStockAlerts(),
  products: () => loadAdminProducts(),
  categories: () => loadCategories(),
  coupons: () => loadAdminCoupons(),
  activities: () => loadAdminActivities(),
  flashSales: () => loadAdminFlashSales(),
  notices: () => loadAdminAnnouncements(),
  hotSearches: () => loadAdminHotSearches(),
  memberDays: () => loadMemberDays(),
  stores: () => loadAdminStores(),
  banners: () => loadAdminBanners(),
  users: () => loadAdminUsers(),
  passwordResets: () => loadPasswordResets(),
};

// 只负责改 adminMenu。数据加载统一交给下面的 watch —— 「点侧边菜单」和「URL 回填（深链/返回键）」
// 因此走同一条加载路径；写 URL 由 App.vue 的 syncAdminQuery 负责，这里不碰路由。
async function selectAdminMenu(key) {
  if (adminMenu.value === key) {
    await refreshCurrentAdminMenu();   // 值没变 → watch 不触发，这里手动刷新
    return;
  }
  adminMenu.value = key;
}

async function refreshCurrentAdminMenu() {
  const loader = adminMenuLoaders[adminMenu.value];
  if (loader) await loader();

}

// adminMenu 一变就加载对应模块数据：触发源无论是「点侧边菜单」还是「URL 回填（深链/刷新/返回键）」都走这里，
// 不再两处各管一半。挂载时也要跑一次 —— 深链进来时 App.vue 已在 AdminPanel 挂载前就把 adminMenu 改成目标模块，
// watch 看不到这次变化（初值不算变更），只能靠 onMounted 兜住。
watch(adminMenu, () => { refreshCurrentAdminMenu(); });
</script>
