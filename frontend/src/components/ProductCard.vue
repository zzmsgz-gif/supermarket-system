<template>
  <article class="product-card clickable" @click="$emit('open', product)">
    <div class="product-image" @click.stop="$emit('open', product)">
      <img v-if="product.coverUrl" :src="product.coverUrl" :alt="product.name" @error="imgFallback($event, product.name)" />
      <span v-else>{{ initials(product.name) }}</span>
      <span v-if="badges && discountSave(product.originalPrice, product.price) > 0" class="corner-badge">省{{ money(discountSave(product.originalPrice, product.price)) }}</span>
      <span v-if="badges && product.isHot" class="corner-badge hot">热</span>
      <span v-if="badges && product.isNew" class="corner-badge new">新</span>
      <span v-if="badges && memberPrice" class="corner-badge vip">会员</span>
      <span v-if="flashPrice" class="corner-badge flash">秒杀</span>
      <span v-if="activityTag" class="activity-chip">{{ activityTag }}</span>
      <button
        v-if="!isAdmin"
        type="button"
        class="fav-btn"
        :class="{ on: favorited }"
        :aria-label="favorited ? '取消收藏' : '收藏'"
        :title="favorited ? '已收藏，点击取消' : '收藏（降价会提醒你）'"
        @click.stop="onToggleFavorite"
      >
        <svg viewBox="0 0 24 24" :fill="favorited ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7-4.9-7-10.2A4.3 4.3 0 0 1 12 7.9 4.3 4.3 0 0 1 19 10.8C19 16.1 12 21 12 21z"/></svg>
      </button>
    </div>
    <h3 @click.stop="$emit('open', product)">{{ product.name }}</h3>
    <p class="brand-line" v-if="product.brand">{{ product.brand }}</p>
    <p>{{ product.subtitle || formatUnit(product.unit) }}</p>
    <p v-if="extra" class="brand-line">{{ extra }}</p>
    <div class="price-block" v-if="mode === 'full'">
      <div class="price-line">
        <div class="price-main">
          <strong :class="{ 'flash-now': flashPrice }">{{ money(flashPrice || product.price) }}</strong>
          <span v-if="flashPrice" class="origin-price">{{ money(product.price) }}</span>
          <span v-else-if="Number(product.originalPrice) > Number(product.price)" class="origin-price">{{ money(product.originalPrice) }}</span>
          <span v-if="memberPrice" class="member-price-tag">会员价 {{ money(memberPrice) }}</span>
          <span v-if="flashPrice" class="member-price-tag flash-mini">秒杀</span>
        </div>
        <span v-if="ratingInfo" class="rating-brief"><i>★</i>{{ ratingInfo.avg.toFixed(1) }}<em>({{ ratingInfo.count }})</em></span>
      </div>
      <div class="meta-line">
        <small :class="{ 'low-stock': lowStock }">
          <template v-if="lowStock">仅剩 {{ product.stock }} 件<template v-if="salesText"> · </template></template>
          <template v-if="salesText">已售 {{ salesText }}</template>
          <template v-if="!lowStock && !salesText">7 天内发货</template>
        </small>
      </div>
    </div>
    <div class="price-line" v-else>
      <div class="price-main">
        <strong :class="{ 'flash-now': flashPrice }">{{ money(flashPrice || product.price) }}</strong>
        <span v-if="flashPrice" class="origin-price">{{ money(product.price) }}</span>
        <span v-if="memberPrice" class="member-price-tag">会员价 {{ money(memberPrice) }}</span>
        <span v-if="flashPrice" class="member-price-tag flash-mini">秒杀</span>
      </div>
    </div>
    <template v-if="mode === 'full'">
      <button class="ghost" @click.stop="$emit('open', product)">查看详情</button>
      <button v-if="addable && !flashCapped" @click.stop="$emit('add', product)">加入购物车</button>
      <span v-else-if="flashCapped" class="admin-note flash-cap-note">已达限购（每人 {{ flashLimitText }} 件）</span>
      <span v-else-if="isAdmin" class="admin-note">管理员仅查看上架商品</span>
    </template>
  </article>
</template>

<script setup>
import { computed, inject } from 'vue';
import { money, initials, formatUnit, discountSave, imgFallback, compactNum } from '../utils/format';

const props = defineProps({
  product: { type: Object, required: true },
  mode: { type: String, default: 'full' }, // 'full' | 'compact'
  addable: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
  badges: { type: Boolean, default: false },
  extra: { type: String, default: '' },
});

defineEmits(['open', 'add']);

const appCtx = inject('appCtx', null);
// 商品平均星级（来自 /products/rating-summary 聚合），无评价时不显示
const ratingInfo = computed(() => (appCtx && appCtx.ratingSummaryMap
  ? appCtx.ratingSummaryMap.value[props.product.id]
  : null));
// 商品命中的营销活动标签（满减/折扣），让活动在浏览商品时可见
const activityTag = computed(() => (appCtx && typeof appCtx.productActivityTag === 'function'
  ? appCtx.productActivityTag(props.product)
  : ''));

// 库存预警：低于阈值且仍有货时高亮，比干巴巴的「库存 N」更能促单
const lowStock = computed(() => {
  const stock = Number(props.product.stock || 0);
  const threshold = Number(props.product.lowStockThreshold || 0);
  return threshold > 0 && stock > 0 && stock <= threshold;
});

const salesText = computed(() => {
  const sales = Number(props.product.sales || 0);
  if (sales <= 0) return '';
  return sales >= 10000 ? `${(sales / 10000).toFixed(1)}万` : String(sales);
});

// 限时秒杀：从 appCtx 已加载的秒杀列表里按 productId 匹配，因此不必在商品接口上透出秒杀价，
// 也就绕开了商品列表缓存的时效问题。
const flashSale = computed(() => {
  const list = (appCtx && appCtx.flashSales && appCtx.flashSales.value) || [];
  return list.find((f) => Number(f.productId) === Number(props.product.id) && f.state === 'RUNNING') || null;
});

// 秒杀价：只有当它低于「售价与会员价的较低者」时才算数 —— 与后端取 min() 的口径一致
const flashPrice = computed(() => {
  const fp = Number((flashSale.value && flashSale.value.flashPrice) || 0);
  const price = Number(props.product.price || 0);
  const mp = Number(props.product.memberPrice || 0);
  const floor = mp > 0 && mp < price ? mp : price;
  return fp > 0 && fp < floor ? fp : 0;
});

// 秒杀每人限购已用满（含购物车里已有的 + 已下单占用的）。
// myRemainingQuota 是后端算好的「我还能买几件」，再购入一件即越界，就算到顶。
// 处理方式是「换一句能读懂的话」而不是留个点不动的灰按钮 —— 灰按钮不解释原因，用户只会以为坏了。
const flashCapped = computed(() => {
  const left = flashSale.value?.myRemainingQuota;
  if (left === null || left === undefined) return false;
  const items = (appCtx && appCtx.cart && appCtx.cart.items) || [];
  const inCart = items.find((i) => Number(i.productId) === Number(props.product.id));
  return Number(inCart?.quantity || 0) >= Number(left);
});

const flashLimitText = computed(() => Number(flashSale.value?.perUserLimit || 0));

// 会员价：仅在低于售价时展示（与后端结算口径一致）；秒杀更低时不展示，免得两个价签打架
const memberPrice = computed(() => {
  const mp = Number(props.product.memberPrice || 0);
  const price = Number(props.product.price || 0);
  if (!(mp > 0 && mp < price)) return 0;
  return flashPrice.value > 0 && flashPrice.value < mp ? 0 : mp;
});

// 收藏状态：直接复用 appCtx（favoriteIds 变化时自动重算），点击即切换
const favorited = computed(() => (appCtx && typeof appCtx.isFavorite === 'function'
  ? appCtx.isFavorite(props.product.id)
  : false));

function onToggleFavorite() {
  if (appCtx && typeof appCtx.toggleFavorite === 'function') appCtx.toggleFavorite(props.product);
}
</script>
