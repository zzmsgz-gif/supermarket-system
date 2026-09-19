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

    <!-- 无限循环轮播：渲染序列首尾各插一张克隆图，使「最后一张 → 第一张」也保持向左连续滑动 -->
    <div class="hero-carousel" @mouseenter="pauseAuto" @mouseleave="resumeAuto">
      <div
        ref="trackEl"
        class="carousel-track"
        :class="{ 'no-anim': !animateOn }"
        :style="{ transform: 'translateX(-' + (pos * 100) + '%)' }"
        @transitionend="onTrackTransitionEnd"
      >
        <div
          v-for="slide in renderSlides"
          :key="slide.key"
          class="carousel-slide"
          :class="slide.image ? 'slide-image' : 'slide-' + slide.parity"
        >
          <template v-if="slide.image">
            <img class="slide-photo" :src="slide.image" alt="" decoding="async" @click="openBannerTarget(slide)" />
          </template>
          <template v-else>
            <span class="slide-tag">{{ slide.tag }}</span>
            <div class="slide-main">
              <h3>{{ slide.title }}</h3>
              <p v-if="slide.value" class="slide-value">{{ slide.value }}</p>
            </div>
            <button type="button" class="slide-cta" @click="openBannerTarget(slide)">{{ slide.linkProductId ? '去看看 ›' : '立即抢购 ›' }}</button>
          </template>
        </div>
        <div v-if="!slideCount" class="carousel-slide slide-default">
          <div class="slide-main">
            <h3>优鲜超市 · 新鲜到家</h3>
            <p class="slide-value">产地直发 · 当日送达</p>
          </div>
          <button type="button" class="slide-cta" @click="chooseCategory('')">去逛逛 ›</button>
        </div>
      </div>
      <button v-if="slideCount > 1" type="button" class="carousel-arrow prev" @click="prevSlide" aria-label="上一张">‹</button>
      <button v-if="slideCount > 1" type="button" class="carousel-arrow next" @click="nextSlide" aria-label="下一张">›</button>
      <div v-if="slideCount > 1" class="carousel-dots">
        <span
          v-for="(s, i) in slides"
          :key="i"
          class="dot"
          :class="{ on: i === realIndex }"
          @click="goSlide(i)"
        ></span>
      </div>
    </div>

    <aside class="hero-notice">
      <div class="notice-head"><span class="notice-ico">📢</span>商城公告</div>
      <ul class="notice-list">
        <li v-for="a in announcements" :key="a.id" class="notice-item clickable" @click="openAnnouncement(a)">
          <span class="notice-tag" :class="'nt-' + (a.type || 'notice').toLowerCase()">{{ noticeTag(a.type) }}</span>
          <div class="notice-body">
            <p class="notice-title">{{ a.title }}</p>
            <p class="notice-date">{{ (a.publishTime || '').slice(0, 10) }}</p>
          </div>
          <span class="notice-arrow">›</span>
        </li>
        <li v-if="!announcements.length" class="notice-empty">暂无公告</li>
      </ul>
    </aside>
  </div>

  <!-- ②.5 限时秒杀：进行中的场次优先，倒计时逐秒走，抢完即从列表消失 -->
  <div v-if="flashSales.length" class="flash-zone">
    <div class="flash-head">
      <span class="flash-badge">限时秒杀</span>
      <small class="muted-note">
        {{ runningFlashSales.length ? '正在抢购中，名额有限先到先得' : '下一场即将开始，先来蹲个点' }}
      </small>
    </div>
    <div class="flash-row">
      <article
        v-for="sale in flashSales"
        :key="sale.id"
        class="flash-card"
        :class="{ upcoming: sale.state !== 'RUNNING' }"
        @click="openProductDetail({ id: sale.productId })"
      >
        <div class="flash-img">
          <img v-if="sale.productCoverUrl" :src="sale.productCoverUrl" :alt="sale.productName" @error="imgFallback($event, sale.productName)" />
          <span v-else>{{ initials(sale.productName) }}</span>
          <span class="flash-tag">{{ sale.state === 'RUNNING' ? '抢购中' : '即将开始' }}</span>
        </div>
        <div class="flash-body">
          <h4>{{ sale.productName }}</h4>
          <div class="flash-price">
            <strong>{{ money(sale.flashPrice) }}</strong>
            <s v-if="Number(sale.price) > Number(sale.flashPrice)">{{ money(sale.price) }}</s>
          </div>
          <div class="flash-meta">
            <span>{{ flashDeadlineText(sale) }}</span>
            <span v-if="sale.perUserLimit > 0">限购 {{ sale.perUserLimit }} 件</span>
            <!-- 登录后后端会带回「我还能买几件」，提前把额度说清楚，别等结算才拦 -->
            <span v-if="sale.myRemainingQuota !== null && sale.myRemainingQuota !== undefined"
                  :class="['flash-mine', { capped: sale.myRemainingQuota === 0 }]">
              {{ sale.myRemainingQuota > 0 ? '你还能买 ' + sale.myRemainingQuota + ' 件' : '你已买满' }}
            </span>
          </div>
          <div class="flash-progress" :title="`已抢 ${sale.soldQuota}/${sale.totalQuota}`">
            <i :style="{ width: (sale.progressPercent || 0) + '%' }"></i>
          </div>
          <small class="flash-left">
            已抢 {{ sale.progressPercent || 0 }}% · 剩 {{ sale.remainingQuota }} 件
          </small>
        </div>
      </article>
    </div>
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

  <!-- 公告详情弹层：正文此前从未展示过，点列表项即看全文 -->
  <Transition name="notice-fade">
    <div v-if="announcementDetail" class="modal-overlay" @click.self="announcementDetail = null">
      <div class="notice-dialog">
        <div class="notice-dialog-head">
          <span class="notice-tag" :class="'nt-' + (announcementDetail.type || 'notice').toLowerCase()">{{ noticeTag(announcementDetail.type) }}</span>
          <h3>{{ announcementDetail.title }}</h3>
          <small class="notice-date">{{ (announcementDetail.publishTime || '').slice(0, 10) }}</small>
        </div>
        <p class="notice-dialog-body">{{ announcementDetail.content }}</p>
        <button @click="announcementDetail = null">我知道了</button>
      </div>
    </div>
  </Transition>
</template>

<script>
import { inject, onUnmounted, watch, nextTick } from 'vue';
import { useRoute } from 'vue-router';

const CAT_EMOJI = {
  生鲜食品: '🥬', 时令蔬菜: '🥬', 时令水果: '🍓', 肉禽蛋品: '🥩', 海鲜水产: '🦐',
  酒水饮料: '🥤', 休闲零食: '🍪', 日用百货: '🧻', 粮油调味: '🧂', 乳品烘焙: '🍞',
};

export default {
  name: 'ShopPage',
  setup() {
    const ctx = inject('appCtx');
    const { api, ref, computed, onMounted, watch, categories, filters, products, loadProducts, openProductDetail, chooseCategory } = ctx;
    const route = useRoute();

    // 本页自建状态（不污染 App.vue）
    const activities = ref([]);
    const announcements = ref([]);
    const allProducts = ref([]);
    const announcementDetail = ref(null);
    function openAnnouncement(a) { announcementDetail.value = a; }
    const banners = ref([]);

    async function loadBanners() {
      const list = await api.get('/banners').catch(() => []);
      banners.value = Array.isArray(list) ? list : [];
    }

    // 轮播 CTA：绑了商品直达详情，否则回到商城（不能叫 goSlide——已用于翻页）
    function openBannerTarget(slide) {
      if (slide.linkProductId) openProductDetail({ id: slide.linkProductId });
      else chooseCategory('');
    }

    // ================= 轮播控制（无限循环） =================
    // 渲染序列 = [末张克隆, 真实 1..N, 首张克隆]，pos 为渲染序列下标：
    // 静止时 pos ∈ [1..N]；「下一张」永远向左推、「上一张」永远向右推；
    // 推到克隆位后用「无动画瞬移」回到真实同位（同一张图，肉眼无缝），
    // 因此最后一张 → 第一张同样是连续左移，而不是整条轨道向右倒回。
    const trackEl = ref(null);
    const pos = ref(1);
    const animateOn = ref(true);        // false → transition:none（用于无缝瞬移）
    const slideCount = computed(() => (slides.value || []).length);
    let autoTimer = null;
    let hovering = false;
    let queuedDir = 0;                  // 瞬移期间被点掉的翻页请求，瞬移完成后补做（保证点击不丢）
    let settleTimer = null;
    let settling = false;

    // 小圆点高亮用的真实下标（pos=0 → 末张；pos=N+1 → 首张）
    const realIndex = computed(() => {
      const n = slideCount.value;
      if (n <= 1) return 0;
      return ((pos.value - 1) % n + n) % n;
    });

    // 真正渲染的序列：N>1 时首尾各加一张克隆图
    const renderSlides = computed(() => {
      const list = slides.value || [];
      const n = list.length;
      if (!n) return [];
      const wrap = (s, i, key) => ({ ...s, key, parity: i % 2 });
      if (n === 1) return [wrap(list[0], 0, 's0')];
      return [
        wrap(list[n - 1], n - 1, 'clone-last'),
        ...list.map((s, i) => wrap(s, i, 's' + i)),
        wrap(list[0], 0, 'clone-first'),
      ];
    });

    async function loadActivities() {
      const list = await api.get('/activities/active').catch(() => []);
      activities.value = Array.isArray(list) ? list : [];
    }

    async function loadAnnouncements() {
      const list = await api.get('/announcements').catch(() => []);
      announcements.value = Array.isArray(list) ? list : [];
    }

    // 预加载轮播图：大图提前解码，切换时不会闪白/半张图
    watch(banners, (list) => {
      for (const b of list || []) {
        if (!b.imageUrl) continue;
        const img = new Image();
        img.src = b.imageUrl;
      }
    }, { immediate: true });

    // 轮播优先用管理员维护的轮播位（带图），无 banner 时回落到活动文字轮播
    const slides = computed(() => {
      const bannerSlides = (banners.value || []).map((b) => ({
        kind: 'banner',
        title: '',
        tag: '',
        value: '',
        image: b.imageUrl || '',
        linkProductId: b.linkProductId || null,
      }));
      if (bannerSlides.length) return bannerSlides;
      return (activities.value || []).map((a) => ({
        kind: 'activity',
        title: a.name,
        tag: a.type === 'DISCOUNT' ? '折扣' : '满减',
        value: activityText(a),
        image: '',
        linkProductId: null,
      }));
    });

    function clearTimer() {
      if (autoTimer) { clearInterval(autoTimer); autoTimer = null; }
    }
    function startTimer() {
      clearTimer();
      if (!hovering && slideCount.value > 1) autoTimer = setInterval(() => nextSlide(), 4500);
    }
    // 悬停暂停 / 移出恢复
    function pauseAuto() { hovering = true; clearTimer(); }
    function resumeAuto() { hovering = false; startTimer(); }
    // 手动翻页后重置倒计时：避免刚点完就被自动翻走，看起来像"点了没反应"
    function restartAuto() { if (!hovering) startTimer(); }

    // 无动画瞬移到指定 pos（同一张图之间跳位，肉眼无缝），随后恢复过渡动画
    async function jumpTo(p) {
      animateOn.value = false;
      pos.value = p;
      await nextTick();
      if (trackEl.value) void trackEl.value.offsetWidth;   // 强制样式刷新，确保 no-anim 已生效
      animateOn.value = true;
    }

    // 兜底：万一 transitionend 没触发（切标签页等），也要归位，否则后续点击会一直排队
    function scheduleSettle() {
      if (settleTimer) clearTimeout(settleTimer);
      settleTimer = setTimeout(() => { settleTimer = null; settle(); }, 700);
    }

    // 过渡结束后把克隆位归位到真实同位；并补做被点掉的翻页请求
    async function settle() {
      if (settling) return;
      settling = true;
      try {
        const n = slideCount.value;
        if (n <= 1) { queuedDir = 0; return; }
        if (pos.value > n) await jumpTo(1);
        else if (pos.value < 1) await jumpTo(n);
        const dir = queuedDir;
        queuedDir = 0;
        if (dir) {
          // 等一帧确保过渡已恢复，否则补做的这次会变成瞬移
          await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
          if (dir > 0) nextSlide(); else prevSlide();
        }
      } finally {
        settling = false;
      }
    }

    function onTrackTransitionEnd(ev) {
      if (ev.target !== trackEl.value || ev.propertyName !== 'transform') return;
      if (settleTimer) { clearTimeout(settleTimer); settleTimer = null; }
      settle();
    }

    function step(dir) {
      const n = slideCount.value;
      if (n <= 1) return;
      // 正处于克隆位（上一次瞬移尚未完成）：记下请求，瞬移后补做——绝不吞掉点击
      if (pos.value > n || pos.value < 1) { queuedDir = dir; scheduleSettle(); return; }
      pos.value += dir;
      scheduleSettle();
      restartAuto();
    }

    function nextSlide() { step(1); }
    function prevSlide() { step(-1); }

    function goSlide(i) {
      const n = slideCount.value;
      if (n <= 1) return;
      if (pos.value < 1 || pos.value > n) { queuedDir = 0; jumpTo(i + 1).then(() => restartAuto()); return; }
      if (pos.value === i + 1) return;
      pos.value = i + 1;
      scheduleSettle();
      restartAuto();
    }

    // 轮播数据到位 / 数量变化时复位（首尾克隆只在 N>1 时存在），并启动自动播放
    watch(slideCount, async (n) => {
      queuedDir = 0;
      await jumpTo(n > 1 ? 1 : 0);
      startTimer();
    }, { immediate: true });

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
      loadBanners();
      loadActivities();
      loadAllProducts();
      loadAnnouncements();
      applyQueryKeyword();
    });

    watch(() => route.query.kw, applyQueryKeyword);

    onUnmounted(() => {
      clearTimer();
      if (settleTimer) { clearTimeout(settleTimer); settleTimer = null; }
    });

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
      banners,
      openBannerTarget,
      announcementDetail,
      openAnnouncement,
      slides,
      slideCount,
      renderSlides,
      pos,
      realIndex,
      animateOn,
      trackEl,
      onTrackTransitionEnd,
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
      pauseAuto,
      resumeAuto,
      noticeTag,
      productCount: computed(() => products.total),
    };
  }
};
</script>
