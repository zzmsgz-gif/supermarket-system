// 前端路由（Vue Router，history 模式）。
// 9 个用户页面各自成为独立路由，`<router-view>` 在 App.vue 中按路由名渲染；
// 后台管理面板 AdminPanel 作为例外直接挂载（它依赖 props 注入 adminCtx）。
import { createRouter, createWebHistory } from 'vue-router'
import ShopPage from '../pages/ShopPage.vue'
import ProductPage from '../pages/ProductPage.vue'
import OrderDetailPage from '../pages/OrderDetailPage.vue'
import CartPage from '../pages/CartPage.vue'
import CheckoutPage from '../pages/CheckoutPage.vue'
// 待付款收银台：下单成功后跳转到这里，显示支付倒计时 + 真正付款按钮
import PayPage from '../pages/PayPage.vue'
import OrdersPage from '../pages/OrdersPage.vue'
import CouponsPage from '../pages/CouponsPage.vue'
import AddressesPage from '../pages/AddressesPage.vue'
import RechargePage from '../pages/RechargePage.vue'
import PointsPage from '../pages/PointsPage.vue'
import FavoritesPage from '../pages/FavoritesPage.vue'
import MessagesPage from '../pages/MessagesPage.vue'
import LegalPage from '../pages/LegalPage.vue'
import AdminPanel from '../components/AdminPanel.vue'

export const routes = [
  { path: '/', redirect: '/shop' },
  { path: '/shop', name: 'shop', component: ShopPage },
  { path: '/product/:id', name: 'product', component: ProductPage },
  { path: '/order/:id', name: 'orderDetail', component: OrderDetailPage },
  { path: '/cart', name: 'cart', component: CartPage },
  { path: '/checkout', name: 'checkout', component: CheckoutPage },
  // 收银台：必须是 /pay/<订单id>，支持刷新与外部链接直达（PayPage 自己按 id 拉订单）
  { path: '/pay/:id', name: 'pay', component: PayPage },
  { path: '/orders', name: 'orders', component: OrdersPage },
  { path: '/coupons', name: 'coupons', component: CouponsPage },
  { path: '/addresses', name: 'addresses', component: AddressesPage },
  { path: '/recharge', name: 'recharge', component: RechargePage },
  { path: '/points', name: 'points', component: PointsPage },
  { path: '/favorites', name: 'favorites', component: FavoritesPage },
  { path: '/messages', name: 'messages', component: MessagesPage },
  // 协议 / 隐私政策：正文由后台维护（legal-docs 接口），游客也能查看
  { path: '/terms', name: 'terms', component: LegalPage },
  { path: '/privacy', name: 'privacy', component: LegalPage },
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
