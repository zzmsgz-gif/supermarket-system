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
import './styles.css';

const app = createApp(App);
app.use(pinia);
app.use(createAppRouter());

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

app.mount('#app');
