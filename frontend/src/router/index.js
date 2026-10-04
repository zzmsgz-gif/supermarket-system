// 前端路由（Vue Router，history 模式）。
// 各页面改為动态 import（路由级代码分割）：进入哪个页面才加载对应 chunk，首屏体积大幅下降。
// 后台管理面板 AdminPanel 作为例外直接挂载（依赖 props 注入 adminCtx），其真实懒加载在 App.vue 用 defineAsyncComponent 处理。
import { createRouter, createWebHistory } from 'vue-router'

export const routes = [
  { path: '/', redirect: '/shop' },
  // 首页（默认落地页）
  { path: '/shop', name: 'shop', component: () => import('../pages/ShopPage.vue') },
  { path: '/product/:id', name: 'product', component: () => import('../pages/ProductPage.vue') },
  { path: '/order/:id', name: 'orderDetail', component: () => import('../pages/OrderDetailPage.vue') },
  { path: '/cart', name: 'cart', component: () => import('../pages/CartPage.vue') },
  { path: '/checkout', name: 'checkout', component: () => import('../pages/CheckoutPage.vue') },
  // 收银台：必须是 /pay/<订单id>，支持刷新与外部链接直达（PayPage 自己按 id 拉订单）
  { path: '/pay/:id', name: 'pay', component: () => import('../pages/PayPage.vue') },
  { path: '/orders', name: 'orders', component: () => import('../pages/OrdersPage.vue') },
  { path: '/coupons', name: 'coupons', component: () => import('../pages/CouponsPage.vue') },
  { path: '/addresses', name: 'addresses', component: () => import('../pages/AddressesPage.vue') },
  { path: '/recharge', name: 'recharge', component: () => import('../pages/RechargePage.vue') },
  { path: '/points', name: 'points', component: () => import('../pages/PointsPage.vue') },
  { path: '/favorites', name: 'favorites', component: () => import('../pages/FavoritesPage.vue') },
  { path: '/messages', name: 'messages', component: () => import('../pages/MessagesPage.vue') },
  // 登录 / 注册 / 改密 / 找回：独立全屏路由页（原来是 App.vue 里的 modal）。
  // query: tab=login|register|change|reset，redirect=登录后落点路由名，forced=1 表示强制改密。
  { path: '/login', name: 'login', component: () => import('../pages/LoginPage.vue') },
  // 协议 / 隐私政策：正文由后台维护（legal-docs 接口），游客也能查看
  { path: '/terms', name: 'terms', component: () => import('../pages/LegalPage.vue') },
  { path: '/privacy', name: 'privacy', component: () => import('../pages/LegalPage.vue') },
  // 后台面板：路由表映射 AdminPanel（语义清晰），但 App.vue 用 v-else 直接挂载并传 props，
  // 因此此处映射实际不参与渲染，仅保证路由可被识别。真实懒加载在 App.vue 用 defineAsyncComponent 处理。
  { path: '/admin', name: 'admin', component: () => import('../components/AdminPanel.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/shop' },
]

export function createAppRouter() {
  return createRouter({
    history: createWebHistory('/'),
    routes,
  })
}
