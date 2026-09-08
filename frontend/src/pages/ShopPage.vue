<template>
<section class="shop-home">

  <!-- 头部 hero：左分类 / 中轮播 / 右公告（均为后端真实数据） -->
  <div class="hero">
    <aside class="hero-cats">
      <button class="hero-cat" :class="{ on: !filters.categoryId }" @click="chooseCategory('')">
        <span class="cat-emoji">🛒</span>全部商品
      </button>
      <button
        v-for="category in categories"
        :key="category.id"
        class="hero-cat"
        :class="{ on: String(filters.categoryId) === String(category.id) }"
        @click="chooseCategory(category.id)"
      >
        <span class="cat-emoji">{{ catEmoji(category.name) }}</span>{{ category.name }}
      </button>
    </aside>

    <div class="hero-carousel" @mouseenter="stopAuto" @mouseleave="startAuto">
      <div class="carousel-track" :style="{ transform: 'translateX(-' + (currentSlide * 100) + '%)' }">
        <div v-for="(slide, i) in slides" :key="i" class="carousel-slide" :class="'slide-' + (i % 2)">
          <span class="slide-tag">{{ slide.tag }}</span>
          <div class="slide-main">
            <h3>{{ slide.title }}</h3>
            <p class="slide-value">{{ slide.value }}</p>
          </div>
          <button class="slide-cta" @click="chooseCategory('')">立即抢购 ›</button>
        </div>
        <div v-if="!slides.length" class="carousel-slide slide-default">
          <div class="slide-main">
            <h3>优鲜超市 · 新鲜到家</h3>
            <p class="slide-value">产地直发 · 当日送达</p>
          </div>
          <button class="slide-cta" @click="chooseCategory('')">去逛逛 ›</button>
        </div>
      </div>
      <button v-if="slides.length > 1" class="carousel-arrow prev" @click="prevSlide" aria-label="上一张">‹</button>
      <button v-if="slides.length > 1" class="carousel-arrow next" @click="nextSlide" aria-label="下一张">›</button>
      <div v-if="slides.length > 1" class="carousel-dots">
        <span
          v-for="(s, i) in slides"
          :key="i"
          class="dot"
          :class="{ on: i === currentSlide }"
          @click="goSlide(i)"
        ></span>
      </div>
    </div>

    <aside class="hero-notice">
      <div class="notice-head"><span class="notice-ico">📢</span>商城公告</div>
      <ul class="notice-list">
        <li v-for="a in announcements" :key="a.id" class="notice-item">
          <span class="notice-tag" :class="'nt-' + (a.type || 'notice').toLowerCase()">{{ noticeTag(a.type) }}</span>
          <div class="notice-body">
            <p class="notice-title">{{ a.title }}</p>
            <p class="notice-date">{{ (a.publishTime || '').slice(0, 10) }}</p>
          </div>
        </li>
        <li v-if="!announcements.length" class="notice-empty">暂无公告</li>
      </ul>
    </aside>
  </div>

  <!-- ③ 工具条：搜索 / 价格 / 品牌 / 排序 -->
  <div class="shop-filters">
    <div class="search-box">
      <input v-model="filters.keyword" placeholder="搜索上架商品" @keyup.enter="loadProducts" />
      <button @click="loadProducts">搜索</button>
    </div>
    <div class="filter-group">
      <label>价格</label>
      <input v-model="filters.minPrice" type="number" min="0" placeholder="最低" @keyup.enter="applyFilters" />
      <span class="dash">—</span>
      <input v-model="filters.maxPrice" type="number" min="0" placeholder="最高" @keyup.enter="applyFilters" />
    </div>
    <div class="filter-group">
      <label>品牌</label>
      <select v-model="filters.brand" @change="applyFilters">
        <option value="">全部品牌</option>
        <option v-for="brand in brands" :key="brand" :value="brand">{{ brand }}</option>
      </select>
    </div>
    <div class="filter-group">
      <label>排序</label>
      <select v-model="filters.sort" @change="applyFilters">
        <option value="">综合</option>
        <option value="price_asc">价格从低到高</option>
        <option value="price_desc">价格从高到低</option>
        <option value="sales_desc">销量优先</option>
        <option value="new_desc">最新上架</option>
      </select>
    </div>
    <button class="ghost" @click="resetFilters">重置</button>
  </div>

  <!-- ④ 主体：无筛选时按真实分类分楼层，筛选时显示结果网格 -->
  <div v-if="isFiltering" class="product-area">
    <div class="product-head">
      <strong>筛选结果</strong>
      <span class="muted-note">共 {{ products.total }} 件</span>
      <button class="ghost mini" @click="resetFilters">清空筛选</button>
    </div>
    <div class="product-grid">
      <ProductCard v-for="product in products.items" :key="product.id" :product="product" mode="full" :addable="!isAdmin" :is-admin="isAdmin" :badges="true" @open="openProductDetail" @add="addToCart" />
    </div>
    <p v-if="!products.items.length" class="empty-hint">没有符合条件的商品，换个条件试试</p>
  </div>

  <div v-else class="floor-list">
    <div v-for="floor in floors" :key="floor.id" class="floor">
      <div class="fhead">
        <span class="fbar"></span>
        <strong>{{ floor.name }}</strong>
        <span class="muted-note">{{ floor.items.length }} 件在售</span>
        <button class="ghost mini" @click="chooseCategory(floor.id)">查看全部 ›</button>
      </div>
      <div class="product-grid">
        <ProductCard v-for="product in floor.items" :key="product.id" :product="product" mode="full" :addable="!isAdmin" :is-admin="isAdmin" :badges="true" @open="openProductDetail" @add="addToCart" />
      </div>
    </div>
    <p v-if="!floors.length" class="empty-hint">暂无上架商品</p>
  </div>

  <!-- ⑤ 运营栏目：均为后端真实数据 -->
  <div class="home-channels">
    <div class="channel" v-if="hotProducts.length">
      <div class="channel-head">
        <h3>🔥 热门推荐</h3>
        <button class="ghost mini" @click="loadHot">换一批</button>
      </div>
      <div class="channel-row">
        <ProductCard v-for="p in hotProducts" :key="p.id" :product="p" mode="compact" @open="openProductDetail" />
      </div>
    </div>

    <div class="channel" v-if="newProducts.length">
      <div class="channel-head">
        <h3>🆕 新品上架</h3>
        <button class="ghost mini" @click="loadNew">换一批</button>
      </div>
      <div class="channel-row">
        <ProductCard v-for="p in newProducts" :key="p.id" :product="p" mode="compact" @open="openProductDetail" />
      </div>
    </div>

    <div class="channel" v-if="guessProducts.length">
      <div class="channel-head">
        <h3>🤖 猜你喜欢</h3>
        <button class="ghost mini" @click="loadGuess">换一批</button>
      </div>
      <div class="channel-row">
        <ProductCard v-for="p in guessProducts" :key="p.id" :product="p" mode="compact" :badges="true" @open="openProductDetail" />
      </div>
    </div>

    <div class="channel" v-if="dwellRankProducts.length">
      <div class="channel-head">
        <h3>👀 大家都在看</h3>
        <button class="ghost mini" @click="loadDwellRank">换一批</button>
      </div>
      <div class="channel-row">
        <ProductCard v-for="p in dwellRankProducts" :key="p.productId" :product="{ id: p.productId, name: p.productName, coverUrl: p.coverUrl }" mode="compact" :extra="'浏览 ' + p.viewCount + ' 次 · 均 ' + p.avgSeconds + 's'" @open="openProductDetail({ id: p.productId })" />
      </div>
    </div>
  </div>

  <!-- ⑥ 品牌墙：从真实在售商品聚合去重，点击按品牌筛选 -->
  <div v-if="brandWall.length" class="brand-wall">
    <span class="lb">合作品牌</span>
    <button v-for="brand in brandWall" :key="brand" class="brand-chip" @click="filterBrand(brand)">{{ brand }}</button>
    <span v-if="brands.length > brandWall.length" class="muted-note">等 {{ brands.length }} 个品牌</span>
  </div>

</section>
</template>

<script>
import { inject, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';

const CAT_EMOJI = {
  生鲜食品: '🥬', 时令蔬菜: '🥬', 时令水果: '🍓', 肉禽蛋品: '🥩', 海鲜水产: '🦐',
  酒水饮料: '🥤', 休闲零食: '🍪', 日用百货: '🧻', 粮油调味: '🧂', 乳品烘焙: '🍞',
};

export default {
  name: 'ShopPage',
  setup() {
    const ctx = inject('appCtx');
    const { api, ref, computed, onMounted, categories, filters, products, loadProducts } = ctx;
    const route = useRoute();

    // 本页自建状态（不污染 App.vue）
    const activities = ref([]);
    const announcements = ref([]);
    const allProducts = ref([]);

    // 轮播控制
    const currentSlide = ref(0);
    let autoTimer = null;

    async function loadActivities() {
      const list = await api.get('/activities/active').catch(() => []);
      activities.value = Array.isArray(list) ? list : [];
      startAuto();
    }

    async function loadAnnouncements() {
      const list = await api.get('/announcements').catch(() => []);
      announcements.value = Array.isArray(list) ? list : [];
    }

    // 活动即促销轮播图：每个活动一张幻灯片
    const slides = computed(() => (activities.value || []).map((a) => ({
      title: a.name,
      tag: a.type === 'DISCOUNT' ? '折扣' : '满减',
      value: activityText(a),
    })));

    function startAuto() {
      stopAuto();
      if (slides.value.length > 1) autoTimer = setInterval(() => nextSlide(), 4500);
    }

    function stopAuto() {
      if (autoTimer) {
        clearInterval(autoTimer);
        autoTimer = null;
      }
    }

    function nextSlide() {
      if (!slides.value.length) return;
      currentSlide.value = (currentSlide.value + 1) % slides.value.length;
    }

    function prevSlide() {
      if (!slides.value.length) return;
      currentSlide.value = (currentSlide.value - 1 + slides.value.length) % slides.value.length;
    }

    function goSlide(i) {
      currentSlide.value = i;
    }

    function noticeTag(type) {
      if (type === 'PROMOTION' || type === 'ACTIVITY') return '活动';
      if (type === 'WARNING') return '提醒';
      return '公告';
    }

    async function loadAllProducts() {
      const data = await api.get('/products?page=1&size=60').catch(() => null);
      allProducts.value = Array.isArray(data?.items) ? data.items : [];
    }

    // 头部搜索：从路由 query.kw 读取关键词并应用到筛选（挂载时 + 路由变化时）
    function applyQueryKeyword() {
      const kw = (route.query.kw || '').toString().trim();
      if (!kw) return;
      if (kw === (filters.keyword || '')) return;
      filters.keyword = kw;
      loadProducts();
    }

    onMounted(() => {
      loadActivities();
      loadAllProducts();
      loadAnnouncements();
      applyQueryKeyword();
    });

    watch(() => route.query.kw, applyQueryKeyword);

    onUnmounted(stopAuto);

    // 按真实分类分组的楼层，只保留有商品的分类
    const floors = computed(() => (categories.value || [])
      .map((category) => ({
        id: category.id,
        name: category.name,
        items: allProducts.value.filter((p) => String(p.categoryId) === String(category.id)).slice(0, 8),
      }))
      .filter((floor) => floor.items.length));

    // 品牌墙：真实商品品牌去重（保持出现顺序）
    const brands = computed(() => {
      const seen = [];
      allProducts.value.forEach((p) => {
        if (p.brand && !seen.includes(p.brand)) seen.push(p.brand);
      });
      return seen;
    });

    // 品牌墙只展示前 14 个（品牌筛选下拉仍用完整列表），避免 chip 过多糊成一片
    const brandWall = computed(() => brands.value.slice(0, 14));

    // 只要用户主动筛选过，就切换到结果网格（排序不算筛选，仍走楼层）
    const isFiltering = computed(() => Boolean(
      filters.keyword || filters.brand || filters.categoryId
      || (filters.minPrice !== '' && filters.minPrice != null)
      || (filters.maxPrice !== '' && filters.maxPrice != null),
    ));

    function activityText(act) {
      if (!act) return '';
      if (act.type === 'DISCOUNT') return `满${Number(act.threshold)} 打 ${(Number(act.discount) * 10).toFixed(1)} 折`;
      return `满${Number(act.threshold)} 减 ${Number(act.discount)}`;
    }

    function catEmoji(name) {
      return CAT_EMOJI[name] || '🛍️';
    }

    async function filterBrand(brand) {
      filters.brand = brand;
      await ctx.loadProducts();
    }

    return {
      ...ctx,
      activities,
      announcements,
      slides,
      currentSlide,
      floors,
      brands,
      brandWall,
      isFiltering,
      activityText,
      catEmoji,
      filterBrand,
      nextSlide,
      prevSlide,
      goSlide,
      startAuto,
      stopAuto,
      noticeTag,
      productCount: computed(() => products.total),
    };
  }
};
</script>
