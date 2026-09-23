import { createApp } from 'vue';
import App from './App.vue';
import { pinia } from './stores';
import { createAppRouter } from './router';
import ShopPage from './pages/ShopPage.vue';
import ProductPage from './pages/ProductPage.vue';
import OrderDetailPage from './pages/OrderDetailPage.vue';
import CartPage from './pages/CartPage.vue';
import CheckoutPage from './pages/CheckoutPage.vue';
import OrdersPage from './pages/OrdersPage.vue';
import CouponsPage from './pages/CouponsPage.vue';
import AddressesPage from './pages/AddressesPage.vue';
import RechargePage from './pages/RechargePage.vue';
import ProductCard from './components/ProductCard.vue';
import CouponCard from './components/CouponCard.vue';
import AddressCard from './components/AddressCard.vue';
import StarRating from './components/StarRating.vue';
import ImageUpload from './components/ImageUpload.vue';
import EmptyState from './components/EmptyState.vue';
import SkeletonGrid from './components/SkeletonGrid.vue';
import FestiveDecor from './components/FestiveDecor.vue';
import { reveal } from './directives/reveal';
import { applyFestiveTheme } from './festive';
import './styles.css';

// 节日氛围：给 <html> 打 data-festive 标记（换/撤节日只改 ./festive.js 里那个常量）
applyFestiveTheme();

const app = createApp(App);
app.use(pinia);
app.use(createAppRouter());

// 滚动入场：v-reveal（元素自身）/ v-reveal.stagger（直接子元素逐张错开入场）
app.directive('reveal', reveal);

// 全局注册抽取出的页面组件，使 App.vue 模板中的 <XPage /> 可被解析。
app.component('ShopPage', ShopPage);
app.component('ProductPage', ProductPage);
app.component('OrderDetailPage', OrderDetailPage);
app.component('CartPage', CartPage);
app.component('CheckoutPage', CheckoutPage);
app.component('OrdersPage', OrdersPage);
app.component('CouponsPage', CouponsPage);
app.component('AddressesPage', AddressesPage);
app.component('RechargePage', RechargePage);

// 叶子组件：页面用普通 <script>+setup() 返回 appCtx，编译器不会静态解析
// 其中的组件引用，故需全局注册才能让 <ProductCard> 等被 resolveComponent 找到。
app.component('ProductCard', ProductCard);
app.component('CouponCard', CouponCard);
app.component('AddressCard', AddressCard);
app.component('StarRating', StarRating);
app.component('ImageUpload', ImageUpload);
app.component('EmptyState', EmptyState);
app.component('SkeletonGrid', SkeletonGrid);   // 商品骨架屏（多根节点，直接铺在 .product-grid 里）
app.component('FestiveDecor', FestiveDecor);   // 节日装饰层（内部按开关自行 v-if）

app.mount('#app');
