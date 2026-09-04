// 前端路由（Vue Router，history 模式）。
// 9 个用户页面各自成为独立路由，`<router-view>` 在 App.vue 中按路由名渲染；
// 后台管理面板 AdminPanel 作为例外直接挂载（它依赖 props 注入 adminCtx）。
import { createRouter, createWebHistory } from 'vue-router'
import ShopPage from '../pages/ShopPage.vue'
import ProductPage from '../pages/ProductPage.vue'
import OrderDetailPage from '../pages/OrderDetailPage.vue'
import CartPage from '../pages/CartPage.vue'
import CheckoutPage from '../pages/CheckoutPage.vue'
import OrdersPage from '../pages/OrdersPage.vue'
import CouponsPage from '../pages/CouponsPage.vue'
import AddressesPage from '../pages/AddressesPage.vue'
import RechargePage from '../pages/RechargePage.vue'
import AdminPanel from '../components/AdminPanel.vue'

export const routes = [
  { path: '/', redirect: '/shop' },
  { path: '/shop', name: 'shop', component: ShopPage },
  { path: '/product/:id', name: 'product', component: ProductPage },
  { path: '/order/:id', name: 'orderDetail', component: OrderDetailPage },
  { path: '/cart', name: 'cart', component: CartPage },
  { path: '/checkout', name: 'checkout', component: CheckoutPage },
  { path: '/orders', name: 'orders', component: OrdersPage },
  { path: '/coupons', name: 'coupons', component: CouponsPage },
  { path: '/addresses', name: 'addresses', component: AddressesPage },
  { path: '/recharge', name: 'recharge', component: RechargePage },
  // 后台面板：路由表映射 AdminPanel（语义清晰），但 App.vue 用 v-else 直接挂载并传 props，
  // 因此此处映射实际不参与渲染，仅保证路由可被识别。
  { path: '/admin', name: 'admin', component: AdminPanel },
  { path: '/:pathMatch(.*)*', redirect: '/shop' },
]

export function createAppRouter() {
  return createRouter({
    history: createWebHistory('/'),
    routes,
  })
}
