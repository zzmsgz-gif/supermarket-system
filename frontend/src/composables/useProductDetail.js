// 商品详情页：SKU 规格切换、图集、停留时长上报、评价提交。
// 取价口径全部对齐后端 SkuPriceSupport / CartService（见 selectedSkuPrice / effectiveDetailPrice 注释）。
import { reactive, ref, computed, watch } from 'vue';

export function useProductDetail({
  api, run, fail, isAdmin, router, navigate,
  activeActivities, reviewedMap, loadProducts, ratingSummaryMap,
  // productNavLock 是 App.vue 里的 let 变量，syncRoute 也要用 → 留在原地，这里用读写器操作
  isProductNavLocked, setProductNavLock,
  getFlashLimitOfProduct, getView,
}) {
  const productDetail = reactive({ data: null, reviews: [], loading: false });
  const detailQuantity = ref(1);
  const currentImageIndex = ref(0);
  const reviewForm = reactive({ orderId: null, rating: 5, content: '', images: [] });
  const relatedProducts = ref([]);
  const dwellEnterTs = ref(0);
  const dwellProductId = ref(null);
  const dwellSource = ref('detail');
  const selectedSpec = reactive({});

  function safeParseSpec(str) {
    try { return JSON.parse(str || '{}') || {}; } catch (e) { return {}; }
  }

  // 选中规格的配图：用户端「选规格即换主图」的数据源。无图则为空（回落封面/图集）。
  const selectedSkuImage = computed(() => selectedSku.value?.image || '');

  const galleryImages = computed(() => {
    const d = productDetail.data;
    if (!d) return [];
    const imgs = (d.images || []).map((it) => it.url).filter(Boolean);
    if (d.coverUrl && !imgs.includes(d.coverUrl)) imgs.unshift(d.coverUrl);
    // 选中带图的规格时，把规格图置顶为主图（缩略图同步置顶）
    const skuImg = selectedSkuImage.value;
    if (skuImg && !imgs.includes(skuImg)) imgs.unshift(skuImg);
    return imgs;
  });
  const currentGalleryImage = computed(() => galleryImages.value[currentImageIndex.value] || '');

  const specDimensions = computed(() => {
    const skus = productDetail.data?.skus || [];
    const dims = {};
    for (const sku of skus) {
      const spec = safeParseSpec(sku.specJson);
      for (const key of Object.keys(spec)) {
        if (!dims[key]) dims[key] = [];
        if (!dims[key].includes(spec[key])) dims[key].push(spec[key]);
      }
    }
    return dims;
  });

  const selectedSku = computed(() => {
    const skus = productDetail.data?.skus || [];
    if (!Object.keys(selectedSpec).length) return null;
    return skus.find((sku) => {
      const spec = safeParseSpec(sku.specJson);
      return Object.keys(spec).every((k) => String(spec[k]) === String(selectedSpec[k]));
    }) || null;
  });

  const selectedSpecText = computed(() =>
    Object.keys(selectedSpec).sort().map((k) => `${k}:${selectedSpec[k]}`).join(' '));

  // 选中规格的「规格价」：仅当该规格单独定价（price 非 null）时才有值；
  // 与后端 SkuPriceSupport 同口径 —— null 表示该规格跟随商品基准价。
  const selectedSkuPrice = computed(() => {
    const sku = selectedSku.value;
    return sku && sku.price != null ? Number(sku.price) : null;
  });

  // 选中规格的「吊牌价(划线价)」：选了单独定价且带吊牌价的规格 → 用规格吊牌价；
  // 否则回落商品级 originalPrice（与后端取价口径一致）。无折扣时返回 null（不划线）。
  const selectedSkuOriginalPrice = computed(() => {
    const sku = selectedSku.value;
    if (sku && sku.originalPrice != null && Number(sku.originalPrice) > 0) {
      return Number(sku.originalPrice);
    }
    const p = Number(productDetail.data?.originalPrice || 0);
    const base = Number(productDetail.data?.price || 0);
    return p > base ? p : null;
  });

  // 详情页「当前生效单价」：选了单独定价的规格 → 用规格价；否则回落商品基准价。
  // 前端展示与后端取价（SkuPriceSupport / CartService / OrderService）保持一致。
  const effectiveDetailPrice = computed(() => {
    const p = selectedSkuPrice.value;
    if (p != null) return p;
    return Number(productDetail.data?.price || 0);
  });

  // 选了带图的规格 → 主图回到置顶的规格图；切换/取消规格 → 回到封面。手动点缩略图不受影响。
  // ⚠️ 本 watch 必须在 selectedSku / selectedSpec 定义之后注册：注册时会立即求值
  // selectedSkuImage → selectedSku → selectedSpec，顺序反了就是 TDZ 整页白屏。
  watch(selectedSkuImage, () => { currentImageIndex.value = 0; });

  // 公开接口：全量商品星级聚合（有评价的商品才会出现）。
  // 仪表盘「商品库存排行」与商品卡/详情都用它，与分页后的表格数据解耦。
  async function loadRatingSummary() {
    try {
      const list = await api.get('/products/rating-summary');
      const map = {};
      for (const item of list || []) {
        map[item.productId] = { avg: Number(item.avgRating || 0), count: Number(item.reviewCount || 0) };
      }
      ratingSummaryMap.value = map;
    } catch (e) {
      ratingSummaryMap.value = {};
    }
  }

  function openReviewForm(orderId) {
    reviewForm.orderId = orderId;
    reviewForm.rating = 5;
    reviewForm.content = '';
  }

  async function submitReview(id) {
    await run(async () => {
      await api.post(`/reviews/orders/${id}`, {
        rating: reviewForm.rating,
        content: reviewForm.content.trim(),
        imageUrls: reviewForm.images,
      });
      reviewedMap[id] = true;
      await loadRatingSummary(); // 新评价改变平均分，刷新商品卡/详情的星级聚合
      reviewForm.orderId = null;
      reviewForm.images = [];
      await loadProducts();
      // 若用户正在查看该订单对应商品的详情页，立即刷新评价列表
      try {
        const order = await api.get(`/orders/${id}`).catch(() => null);
        const pid = order?.items?.[0]?.productId;
        if (pid && getView() === 'product' && productDetail.data && productDetail.data.id === pid) {
          const r = await api.get(`/reviews/products/${pid}?page=1&size=20`).catch(() => ({ items: [] }));
          productDetail.reviews = r?.items || [];
        }
      } catch (_) { /* 刷新评价失败不影响主流程 */ }
    }, '评价提交成功，感谢反馈');
  }

  async function openProductDetail(product, { fromHistory = false } = {}) {
    if (isProductNavLocked()) return;
    setProductNavLock(true);
    try {
      reportDwell(); // 离开上一个详情页，先上报停留时长
      productDetail.data = null;
      productDetail.reviews = [];
      relatedProducts.value = [];
      activeActivities.value = [];
      currentImageIndex.value = 0;
      for (const k in selectedSpec) delete selectedSpec[k];
      productDetail.loading = true;
      detailQuantity.value = 1;
      dwellProductId.value = product.id;
      dwellEnterTs.value = Date.now();
      dwellSource.value = 'detail';
      router.push({ name: 'product', params: { id: product.id } });
      try {
        const [detail, reviews, related, activities] = await Promise.all([
          api.get(isAdmin.value ? `/admin/products/${product.id}` : `/products/${product.id}`).catch(() => null),
          api.get(`/reviews/products/${product.id}?page=1&size=20`).catch(() => ({ items: [] })),
          api.get(`/products/related/${product.id}?limit=6`).catch(() => []),
          api.get(`/activities/active`).catch(() => []),
        ]);
        productDetail.data = detail;
        // 有 SKU 的商品：进入详情页默认选中第一个规格（sort_no 最小者，对应基准规格价）
        for (const k in selectedSpec) delete selectedSpec[k];
        const skusForDefault = detail?.skus || [];
        if (skusForDefault.length) {
          const firstSpec = safeParseSpec(skusForDefault[0].specJson);
          for (const k in firstSpec) selectedSpec[k] = firstSpec[k];
        }
        productDetail.reviews = reviews?.items || [];
        relatedProducts.value = related || [];
        activeActivities.value = activities || [];
      } catch (err) {
        fail(err?.message || '商品详情加载失败');
      } finally {
        productDetail.loading = false;
      }
    } finally {
      setProductNavLock(false);
    }
  }

  function reportDwell() {
    const pid = dwellProductId.value;
    const ts = dwellEnterTs.value;
    if (pid == null || !ts) return;
    const seconds = Math.round((Date.now() - ts) / 1000);
    dwellProductId.value = null;
    dwellEnterTs.value = 0;
    if (seconds < 3) return; // 太短忽略，避免误触
    api.post('/dwell', { productId: pid, seconds, source: dwellSource.value || 'detail' }).catch(() => {});
  }

  function backFromProduct() {
    reportDwell();
    // 站内导航进来的商品页，走路由回退避免重复堆积历史记录；直接打开链接/刷新则回首页
    if (window.history.length > 1) {
      router.back();
      return;
    }
    navigate('shop');
  }

  // 详情页数量步进：上限 = min(库存, 秒杀还能买几件)。名额用完后 detailFlashCapped 会禁用按钮，这里再夹一道。
  function changeDetailQty(delta) {
    const stock = Number(productDetail.data?.stock || 0);
    const left = getFlashLimitOfProduct(productDetail.data?.id);
    const max = left === null ? stock : Math.min(stock, left);
    const next = Number(detailQuantity.value || 1) + delta;
    detailQuantity.value = Math.min(Math.max(next, 1), Math.max(max, 1));
  }

  return {
    productDetail, detailQuantity, currentImageIndex, reviewForm,
    relatedProducts, dwellEnterTs, dwellProductId, dwellSource, selectedSpec,
    safeParseSpec, selectedSkuImage, galleryImages, currentGalleryImage,
    specDimensions, selectedSku, selectedSpecText,
    selectedSkuPrice, selectedSkuOriginalPrice, effectiveDetailPrice,
    openReviewForm, submitReview, openProductDetail, reportDwell, backFromProduct, changeDetailQty,
    loadRatingSummary,
  };
}