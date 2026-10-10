<template>
  <article class="product-card clickable" @click="$emit('open', product)">
    <div class="product-image" @click.stop="$emit('open', product)">
      <img v-if="product.coverUrl" :src="product.coverUrl" :alt="product.name" @error="imgFallback($event, product.name)"  loading="lazy" decoding="async"/>
      <span v-else>{{ initials(product.name) }}</span>
      <!-- 左上角标横排：原先 省X/热/新/会员 四个都 absolute 在 top:8/left:8 同一坐标，
           同现时互相叠盖只露出最后一张；包进 badge-row 用 flex 横排错开。 -->
      <div v-if="badges" class="badge-row">
        <span v-if="discountSave(memberView ? memberView.original : product.originalPrice, memberView ? memberView.price : product.price) >= 1" class="corner-badge">省{{ money(discountSave(memberView ? memberView.original : product.originalPrice, memberView ? memberView.price : product.price)) }}</span>
        <span v-if="product.isHot" class="corner-badge hot">热</span>
        <span v-if="product.isNew" class="corner-badge new">新</span>
        <span v-if="memberPrice" class="corner-badge vip">会员价</span>
      </div>
      <span v-if="flashPrice" class="corner-badge flash">秒杀</span>
      <span v-if="activityTag" class="activity-chip">{{ activityTag }}</span>
      <!-- 售罄遮罩（2026-10-10）：图片盖一层半透明 +「已售罄」角标。
           光靠底部那行小字「已售 N」不够醒目 —— 用户扫列表时最容易漏掉的
           恰恰是「这个根本买不了」。遮罩让不可买的商品在一眼扫过时就被排除。 -->
      <div v-if="soldOut" class="soldout-mask">
        <span class="soldout-tag">已售罄</span>
      </div>
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
    <!-- 品牌 + 副标题合并为一行：原先两行同灰度小字糊成一片，合一行更清爽 -->
    <p>{{ [product.brand, product.subtitle || formatUnit(product.unit)].filter(Boolean).join(' · ') }}</p>
    <p v-if="extra" class="brand-line">{{ extra }}</p>
    <div class="price-block" v-if="mode === 'full'">
      <div class="price-line">
        <div class="price-main">
          <strong :class="{ 'flash-now': flashPrice }">{{ money(flashPrice || memberView?.price || product.price) }}</strong>
          <span v-if="flashPrice" class="origin-price">{{ money(product.price) }}</span>
          <template v-else-if="memberView">
            <span class="sale-price">{{ money(product.price) }}</span>
          </template>
          <span v-else-if="Number(product.originalPrice) > Number(product.price)" class="origin-price">{{ money(product.originalPrice) }}</span>
          <span v-if="memberView && memberView.source === 'member'" class="member-price-tag">会员价 {{ money(memberView.price) }}</span>
          <span v-else-if="memberView && memberView.source === 'tier'" class="member-price-tag">{{ shortTier(memberView.name) }} {{ (memberView.rate * 10).toFixed(1) }}折</span>
          <span v-else-if="memberPrice" class="member-price-tag">会员价 {{ money(memberPrice) }}</span>
        </div>
        <!-- 右下角圆形「＋」加购（生鲜电商主流做法）：替代旧的双通栏按钮，卡片更轻盈。
             整卡可点看详情，不再需要「查看详情」按钮；秒杀角标在图上有了，价格旁不再重复标。 -->
        <button v-if="addable && !flashCapped" type="button" class="add-fab" aria-label="加入购物车" title="加入购物车" @click.stop="$emit('add', product)">＋</button>
        <span v-else-if="flashCapped" class="flash-cap-note">
          {{ flashSale && flashSale.myUnpaidOrderId ? '待支付订单占用名额' : flashCappedReason }}
          <button v-if="flashSale && flashSale.sourceProductId && !flashSale.myUnpaidOrderId" type="button" class="link-btn" @click.stop="openOriginal">去原商品</button>
        </span>
        <span v-else-if="isAdmin" class="admin-inline-note">管理员仅查看上架商品</span>
      </div>
      <div class="meta-line">
        <!-- 2026-10-07 用户要求去掉兜底的「7 天内发货」：它是句无信息量的占位话
             —— 所有商品都这么写，等于什么都没说；缺货/有销量时才显示对应信息即可。
             留着空白不补文案。 -->
        <small v-if="lowStock || soldCountText" :class="{ 'low-stock': lowStock }">
          <template v-if="lowStock">仅剩 {{ lowStockCount }} 件<template v-if="soldCountText"> · </template></template>
          <template v-if="soldCountText">{{ soldCountText }}</template>
        </small>
        <span v-if="ratingInfo" class="rating-brief"><i>★</i>{{ ratingInfo.avg.toFixed(1) }}<em>({{ ratingInfo.count }})</em></span>
      </div>
    </div>
    <div class="price-line" v-else>
      <div class="price-main">
        <strong :class="{ 'flash-now': flashPrice }">{{ money(flashPrice || memberView?.price || product.price) }}</strong>
        <span v-if="flashPrice" class="origin-price">{{ money(product.price) }}</span>
        <template v-else-if="memberView">
          <span class="sale-price">{{ money(product.price) }}</span>
        </template>
        <span v-else-if="Number(product.originalPrice) > Number(product.price)" class="origin-price">{{ money(product.originalPrice) }}</span>
        <span v-if="memberView && memberView.source === 'member'" class="member-price-tag">会员价 {{ money(memberView.price) }}</span>
        <span v-else-if="memberView && memberView.source === 'tier'" class="member-price-tag">{{ shortTier(memberView.name) }} {{ (memberView.rate * 10).toFixed(1) }}折</span>
        <span v-else-if="memberPrice" class="member-price-tag">会员价 {{ money(memberPrice) }}</span>
      </div>
    </div>
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

// 卡片价格区可用宽度只有 ~135px（右下角「＋」按钮占掉约 50px）：
// 「银卡会员」这类全称太宽会把折标挤到第三行，卡片上统一去掉「会员」二字（→「银卡」）。
// 详情页宽度足够，仍显示档位全称。
const shortTier = (name) => String(name || '').replace(/会员/g, '');

const appCtx = inject('appCtx', null);
// 商品平均星级（来自 /products/rating-summary 聚合）。评价少于 3 条时不展示：
// 一两条评价就挂「满分 5.0」更像刷出来的，反而减分
const ratingInfo = computed(() => {
  if (!appCtx || !appCtx.ratingSummaryMap) return null;
  const info = appCtx.ratingSummaryMap.value[props.product.id];
  return info && Number(info.count) >= 3 ? info : null;
});
// 商品命中的营销活动标签（满减/折扣），让活动在浏览商品时可见
const activityTag = computed(() => (appCtx && typeof appCtx.productActivityTag === 'function'
  ? appCtx.productActivityTag(props.product)
  : ''));

// 库存预警：低于阈值且仍有货时高亮。
// ⚠️ 秒杀品一律走「剩余名额」而不是 product.stock（2026-10-07）：
// 秒杀品的 product.stock 初始等于总名额、且与 flash_sale 名额是两条独立加减路径，
// 历史数据已漂移（实测 159 差 2、162 差 5）。拿 stock 判「仅剩 N 件」会显示一个
// 比真实名额大的数字，用户加购时才被拒。
// 阈值：普通品用 lowStockThreshold，秒杀品固定 10（与详情页的「仅剩 N 件」一致）。
const lowStockCount = computed(() => {
  if (flashSale.value) {
    const left = flashAllLeft.value;
    return left === null ? 0 : Math.max(left, 0);
  }
  return Number(props.product.stock || 0);
});
const lowStock = computed(() => {
  const n = lowStockCount.value;
  if (n <= 0) return false;
  if (flashSale.value) return n <= 10;              // 秒杀：统一 10 件口径
  const threshold = Number(props.product.lowStockThreshold || 0);
  return threshold > 0 && n <= threshold;
});

// 「已售 N」：秒杀品必须用 flash_sale.soldQuota，不能用 product.sales。
// 两者是两条独立加减路径 —— 下单只加 soldQuota，product.sales 建秒杀品时置 0 之后没人再动，
// 于是卖出去一件也仍显示「已售 0」（2026-10-07 用户实测：牛奶秒杀 1 件、销量 0）。
// ⚠️ 定义在 flashSale 之前也没关系：computed 是惰性求值，读取时才解析依赖。
const salesText = computed(() => {
  const sales = Number(props.product.sales || 0);
  if (sales <= 0) return '';
  return sales >= 10000 ? `${(sales / 10000).toFixed(1)}万` : String(sales);
});

// 秒杀品的销量文案（已抢 N 件）；非秒杀回落到 salesText
const soldCountText = computed(() => {
  if (flashSale.value) {
    const n = Number(flashSale.value.soldQuota || 0);
    return n > 0 ? `已抢 ${n} 件` : '';
  }
  return salesText.value ? `已售 ${salesText.value}` : '';
});

/**
 * 是否售罄 —— 用于在图片上盖「售罄」遮罩（2026-10-10 用户提的）。
 *
 * <p>判定口径与「加购/下单能不能买」保持一致：**都买不了才算售罄**。
 * 秒杀品看名额（`soldQuota >= totalQuota`），普通品看库存 `stock <= 0`。
 * 不能只看 `stock`：秒杀品的 product.stock 初值常等于总名额（历史数据见
 * ProductCard 顶部注释），拿它判会把还有名额的秒杀品误标成售罄。
 */
const soldOut = computed(() => {
  const f = flashSale.value;
  if (f) {
    const total = Number(f.totalQuota ?? 0);
    const sold = Number(f.soldQuota ?? 0);
    if (total > 0 && sold >= total) return true;
  }
  return Number(props.product.stock ?? 0) <= 0;
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

// 秒杀「还能买几件」= min(每人限购剩余, 全场剩余名额)，与详情页 ProductPage 的 flashCapped 同口径。
// ⚠️ 2026-10-07 修正：原实现有三处错 ——
//   ① 只看 myRemainingQuota，漏判「全场名额被抢完」→ 详情页已禁、列表页还能点；
//   ② 拿购物车件数去比「剩余额度」，车里已有 1 件就判买满，其实还能再买；
//   ③ myRemainingQuota 为 null（不限购场次）直接 return false → 全场抢完也不禁。
// 三者都是「前端显示与真实可买数脱节」，用户在列表页加购成功、到结算才被拒。
const flashMyLeft = computed(() => {
  const l = flashSale.value?.myRemainingQuota;
  return (l === null || l === undefined) ? null : Number(l);   // null = 本场不限购
});
const flashAllLeft = computed(() => {
  const r = flashSale.value?.remainingQuota;
  return (r === null || r === undefined) ? null : Number(r);
});
const flashCapped = computed(() => {
  const my = flashMyLeft.value;
  const all = flashAllLeft.value;
  // 任一维度为 0 就买不了；都不限购时才不禁用（此时只能靠库存，加购接口会兜）
  if (my === null && all === null) return false;
  if (my !== null && my <= 0) return true;
  if (all !== null && all <= 0) return true;
  return false;
});
// 买满原因：区分「我买满」与「全场抢完」，文案与给出的出路不同
const flashCappedReason = computed(() => {
  const my = flashMyLeft.value;
  const all = flashAllLeft.value;
  const limit = Number(flashSale.value?.perUserLimit || 0);
  if (my !== null && my <= 0) {
    return `已达限购（每人 ${limit} 件）`;
  }
  return `本场已抢完（共 ${Number(flashSale.value?.totalQuota || 0)} 件）`;
});

const flashLimitText = computed(() => Number(flashSale.value?.perUserLimit || 0));

// 会员价：仅在低于售价时展示（与后端结算口径一致）；秒杀更低时不展示，免得两个价签打架
const memberPrice = computed(() => {
  const mp = Number(props.product.memberPrice || 0);
  const price = Number(props.product.price || 0);
  if (!(mp > 0 && mp < price)) return 0;
  return flashPrice.value > 0 && flashPrice.value < mp ? 0 : mp;
});

// 登录会员的实付价视图（与后端 unitPriceFor 同口径）：非会员/游客返回 null。
// 列表/详情据此把主价切换成「会员价」，并挂「X折会员价」标签，价格从浏览到下单全程一致。
const memberView = computed(() => (appCtx && typeof appCtx.productMemberView === 'function'
  ? appCtx.productMemberView(props.product)
  : null));

// 收藏状态：直接复用 appCtx（favoriteIds 变化时自动重算），点击即切换
const favorited = computed(() => (appCtx && typeof appCtx.isFavorite === 'function'
  ? appCtx.isFavorite(props.product.id)
  : false));

function onToggleFavorite() {
  if (appCtx && typeof appCtx.toggleFavorite === 'function') appCtx.toggleFavorite(props.product);
}
// 限购买满后引导去独立原商品按原价购买（sourceProductId 由秒杀列表透出）
function openOriginal() {
  const srcId = flashSale.value && flashSale.value.sourceProductId;
  if (srcId && appCtx && typeof appCtx.openProductDetail === 'function') appCtx.openProductDetail({ id: srcId });
}
</script>
