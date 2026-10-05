<template>
<section class="data-panel product-detail" v-reveal>
        <div class="detail-nav">
          <button class="ghost" @click="backFromProduct">返回商品列表</button>
          <span class="detail-crumb">商品列表 / {{ categoryName(productDetail.data?.categoryId) }} / {{ productDetail.data?.name || '' }}</span>
        </div>

        <div v-if="productDetail.loading || !productDetail.data" class="empty">商品信息加载中…</div>

        <template v-else>
          <div class="detail-grid">
            <div class="detail-gallery">
              <div class="detail-image">
                <img v-if="currentGalleryImage" :src="currentGalleryImage" :alt="productDetail.data.name" @error="imgFallback($event, productDetail.data.name)" />
                <span v-else class="detail-fallback">{{ initials(productDetail.data.name) }}</span>
                <span v-if="productDetail.data.isHot" class="corner-badge hot">热</span>
                <span v-if="productDetail.data.isNew" class="corner-badge new">新</span>
              </div>
              <div class="gallery-thumbs" v-if="galleryImages.length > 1">
                <button
                  v-for="(img, i) in galleryImages"
                  :key="i"
                  type="button"
                  class="thumb"
                  :class="{ active: i === currentImageIndex }"
                  @click="currentImageIndex = i"
                >
                  <img :src="img" alt="商品图" @error="imgFallback($event, productDetail.data.name)"  loading="lazy" decoding="async"/>
                </button>
              </div>
              <div class="gallery-note">商品编号 {{ productDetail.data.sku }}</div>
            </div>

            <div class="detail-info">
              <div class="detail-tags">
                <span class="tag">{{ categoryName(productDetail.data.categoryId) }}</span>
                <span :class="['tag', productDetail.data.status === 'ON_SALE' ? 'ok' : 'muted']">{{ formatProductStatus(productDetail.data.status) }}</span>
                <span v-if="Number(productDetail.data.stock) <= 0" class="tag warn">已售罄</span>
              </div>
              <h2>{{ productDetail.data.name }}</h2>
              <p class="detail-subtitle">{{ productDetail.data.subtitle || '暂无副标题' }}</p>

              <div class="detail-price">
                <strong :class="{ 'flash-now': flashPrice }">{{ money(flashPrice || detailPrice) }}</strong>
                <template v-if="flashPrice">
                  <span class="detail-origin">原价 {{ money(flashOrigin) }}</span>
                  <span class="detail-flash">限时秒杀 · {{ flashDeadlineText(flashSale) }}<template v-if="Number(flashSale.perUserLimit) > 0"> · 每人限购 {{ flashSale.perUserLimit }} 件</template><template v-if="flashSale.remainingQuota <= 10"> · 仅剩 {{ flashSale.remainingQuota }} 件</template></span>
                </template>
              <template v-else>
                <template v-if="memberView">
                  <span class="detail-sale">普通价 {{ money(memberView.original) }}</span>
                </template>
                <template v-else>
                  <span v-if="detailOrigin > 0" class="detail-origin">原价 {{ money(detailOrigin) }}</span>
                  <span v-if="discountSave(detailOrigin, detailPrice) >= 1" class="detail-discount">省 {{ money(discountSave(detailOrigin, detailPrice)) }} · 约 {{ discountRate(detailOrigin, detailPrice) }} 折</span>
                </template>
              </template>
              <span v-if="memberView && memberView.source === 'member'" class="detail-member">会员价 {{ money(memberView.price) }}</span>
              <span v-else-if="memberView && memberView.source === 'tier'" class="detail-member">{{ memberView.name }} {{ (memberView.rate * 10).toFixed(1) }}折</span>
              <span v-else-if="memberPrice" class="detail-member">会员专享价 {{ money(memberPrice) }}</span>
                <span v-if="selectedSkuPrice" class="detail-spec-price">已选规格价</span>
                <span class="detail-unit">/ {{ formatUnit(productDetail.data.unit) }}</span>
              </div>

              <dl class="detail-meta">
                <div><dt>剩余库存</dt><dd>{{ productDetail.data.stock }} {{ formatUnit(productDetail.data.unit) }}</dd></div>
                <div><dt>累计销量</dt><dd>{{ productDetail.data.sales ?? 0 }} {{ formatUnit(productDetail.data.unit) }}</dd></div>
                <div><dt>所属分类</dt><dd>{{ categoryName(productDetail.data.categoryId) }}</dd></div>
                <div><dt>商品编号</dt><dd>{{ productDetail.data.sku }}</dd></div>
              </dl>

              <p class="detail-brand" v-if="productDetail.data.brand">品牌：{{ productDetail.data.brand }}</p>

              <div class="spec-area" v-if="Object.keys(specDimensions).length">
                <div class="spec-dim" v-for="(values, dim) in specDimensions" :key="dim">
                  <span class="spec-label">{{ dim }}</span>
                  <div class="spec-options">
                    <button
                      v-for="val in values"
                      :key="val"
                      type="button"
                      class="spec-opt"
                      :class="{ active: selectedSpec[dim] === val }"
                      @click="selectedSpec[dim] = val"
                    >{{ val }}</button>
                  </div>
                </div>
                <p class="spec-selected" v-if="selectedSpecText">已选：{{ selectedSpecText }}</p>
              </div>

              <div v-if="isAdmin" class="admin-note">管理员仅查看商品信息，不能下单</div>
              <div v-else-if="Number(productDetail.data.stock) <= 0" class="admin-note">该商品已售罄，暂时无法购买</div>
              <div v-else class="detail-buy">
                <div class="qty-picker">
                  <button type="button" :disabled="flashCapped" @click="changeDetailQty(-1)">−</button>
                  <span>{{ detailQuantity }}</span>
                  <button type="button" :disabled="flashCapped" @click="changeDetailQty(1)">+</button>
                </div>
                <button class="js-add-cart" :disabled="flashCapped" @click="addDetailToCart">加入购物车</button>
                <button class="ghost" :disabled="flashCapped" @click="buyDetailNow">立即购买</button>
                <button class="ghost fav-detail-btn" :class="{ on: favorited }" @click="toggleFavorite(productDetail.data)">
                  <svg viewBox="0 0 24 24" :fill="favorited ? 'currentColor' : 'none'" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 21s-7-4.9-7-10.2A4.3 4.3 0 0 1 12 7.9 4.3 4.3 0 0 1 19 10.8C19 16.1 12 21 12 21z"/></svg>
                  {{ favorited ? '已收藏' : '收藏' }}
                </button>
              </div>
              <div v-if="flashCapped" class="flash-capped-buy">
                <p>该秒杀商品你已买满 {{ flashSale.perUserLimit }} 件（每人限购），已不可再购买。</p>
                <button v-if="flashSale.sourceProductId" class="link-btn" type="button" @click="openOriginalProduct">查看原商品 · 按原价购买</button>
              </div>
            </div>
          </div>

          <div class="detail-section">
            <h3>商品描述</h3>
            <p class="detail-desc">{{ productDetail.data.description || '暂无商品描述' }}</p>
          </div>

          <div class="detail-section" v-if="productDetail.data.attributes && productDetail.data.attributes.length">
            <h3>商品参数</h3>
            <table class="param-table">
              <tbody>
                <tr v-for="attr in productDetail.data.attributes" :key="attr.id || attr.attrName">
                  <th>{{ attr.attrName }}</th>
                  <td>{{ attr.attrValue }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="detail-section" v-if="relatedProducts.length" v-reveal>
            <h3>相关推荐</h3>
              <div class="related-grid" v-reveal.stagger>
                <ProductCard v-for="p in relatedProducts" :key="p.id" :product="p" mode="compact" @open="openProductDetail" />
              </div>
          </div>

          <div class="detail-section">
            <div class="panel-head">
              <h3>用户评价（{{ productDetail.reviews.length }}）</h3>
              <span v-if="detailRating" class="rating-overall">
                平均 <StarRating :rating="detailRating.avg" /> <b>{{ detailRating.avg.toFixed(1) }}</b> 分 · 共 {{ detailRating.count }} 条
              </span>
            </div>
            <div v-if="!productDetail.reviews.length" class="empty">暂无评价，购买并确认收货后即可评价</div>
            <div v-else class="review-list">
              <div v-for="review in productDetail.reviews" :key="review.id" class="review-item">
                <div class="review-head">
                  <span class="review-user">{{ review.nickname || '匿名用户' }}</span>
                  <StarRating :rating="review.rating" />
                  <span class="review-date">{{ formatDate(review.createdAt) }}</span>
                </div>
                <p>{{ review.content || '默认好评' }}</p>
                <div v-if="review.imageUrls && review.imageUrls.length" class="review-imgs">
                  <img v-for="(img, idx) in review.imageUrls" :key="idx" :src="img" alt="评价图片" @error="imgFallback($event, '晒图')"  loading="lazy" decoding="async"/>
                </div>
                <!-- 商家回复：后台回完必须在这里露出来，否则"回复"这个动作等于白做 -->
                <div v-if="review.replyContent" class="review-reply">
                  <span class="review-reply-tag">商家回复</span>
                  <span class="review-reply-text">{{ review.replyContent }}</span>
                  <small v-if="review.replyAt" class="review-reply-date">{{ formatDate(review.replyAt) }}</small>
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
</template>

<script>
import { inject } from 'vue';
import { computed } from 'vue';
export default {
  name: 'ProductPage',
  setup() {
    const appCtx = inject('appCtx');
    // 当前商品的平均星级（ratingSummaryMap 无该商品时为 null，即暂无评价）
    const detailRating = computed(() => (appCtx.ratingSummaryMap.value || {})[appCtx.productDetail.data.id] || null);
    // 限时秒杀：从 appCtx 已加载的秒杀列表按 productId 匹配（无需商品接口透出秒杀价）
    const flashSale = computed(() => {
      const list = (appCtx.flashSales && appCtx.flashSales.value) || [];
      return list.find((f) => Number(f.productId) === Number(appCtx.productDetail.data?.id)
        && f.state === 'RUNNING') || null;
    });
    // 秒杀限购买满（myRemainingQuota===0）：这件秒杀品彻底不能下单，引导去原商品按原价购买
    const flashCapped = computed(() => !!flashSale.value && flashSale.value.myRemainingQuota === 0);
    function openOriginalProduct() {
      const srcId = flashSale.value && flashSale.value.sourceProductId;
      if (srcId && typeof appCtx.openProductDetail === 'function') appCtx.openProductDetail({ id: srcId });
    }
    // 秒杀价：低于「售价与会员价的较低者」才算数（与后端取 min() 的口径一致）
    // 走规格价时按折扣率套到规格价上：规格价 × (基准秒杀价 / 基准价)，与后端 flashPriceFor 一致
    const flashPrice = computed(() => {
      const fp = Number((flashSale.value && flashSale.value.flashPrice) || 0);
      const basePrice = Number(appCtx.productDetail.data?.price ?? 0);
      const effectivePrice = Number(appCtx.effectiveDetailPrice?.value ?? basePrice);
      const usingSkuPrice = appCtx.selectedSkuPrice?.value != null;
      const skuFlash = usingSkuPrice && basePrice > 0
        ? Math.round((effectivePrice * fp / basePrice) * 100) / 100 : fp;
      const mp = Number(appCtx.productDetail.data?.memberPrice || 0);
      // 会员价不与规格价叠加：走规格价时 floor 就是规格价，否则取 min(售价, 会员价)
      const floor = usingSkuPrice ? effectivePrice : (mp > 0 && mp < effectivePrice ? mp : effectivePrice);
      return skuFlash > 0 && skuFlash < floor ? skuFlash : 0;
    });
    // 命中秒杀时的「对照原价」（划线价）：打折前的价 —— 走了规格价就是规格价，否则商品基准价
    const flashOrigin = computed(() => {
      if (!flashPrice.value) return 0;
      return appCtx.selectedSkuPrice?.value != null
        ? Number(appCtx.effectiveDetailPrice?.value ?? appCtx.productDetail.data?.price ?? 0)
        : Number(appCtx.productDetail.data?.price ?? 0);
    });
    // 会员价：仅在低于售价时展示；秒杀更低时不展示，避免两个价签互相打架。
    // 选了「单独定价的规格」时按后端口径不叠加会员价（规格价优先）。
    const memberPrice = computed(() => {
      if (appCtx.selectedSkuPrice?.value != null) return 0;
      const mp = Number(appCtx.productDetail.data?.memberPrice || 0);
      const price = Number(appCtx.effectiveDetailPrice?.value ?? appCtx.productDetail.data?.price ?? 0);
      if (!(mp > 0 && mp < price)) return 0;
      return flashPrice.value > 0 && flashPrice.value < mp ? 0 : mp;
    });
    // 收藏状态：复用 appCtx 的 favoriteIds，收藏/取消后立即反映
    const favorited = computed(() => (typeof appCtx.isFavorite === 'function'
      ? appCtx.isFavorite(appCtx.productDetail.data?.id)
      : false));
    // 登录会员的实付价视图（与后端 unitPriceFor 同口径）：作用于「已选单价」(规格价或基础价)，
    // 默认规格也照常享会员折扣，保证 详情→购物车 价格一致。
    const memberView = computed(() => {
      const unit = Number(appCtx.selectedSkuPrice?.value ?? appCtx.productDetail.data?.price ?? 0);
      const mp = Number(appCtx.productDetail.data?.memberPrice ?? 0);
      // 【单一基准 = 售价】不再传吊牌价：会员视图的对照价就是当前单价（售价/规格价）
      return (typeof appCtx.memberUnitView === 'function') ? appCtx.memberUnitView(unit, mp) : null;
    });
    // 当前生效单价（含规格价/会员价）：供主价与折扣率计算对照
    const detailPrice = computed(() => {
      if (memberView.value) return memberView.value.price;
      return Number(appCtx.effectiveDetailPrice?.value ?? appCtx.productDetail.data?.price ?? 0);
    });
    // 对照原价（划线价）：会员看「非会员价」作为对照；否则看吊牌价（有折扣才显示）
    const detailOrigin = computed(() => {
      if (memberView.value) return memberView.value.original;
      const orig = Number(appCtx.selectedSkuOriginalPrice?.value ?? appCtx.productDetail.data?.originalPrice ?? 0);
      return orig > detailPrice.value ? orig : 0;
    });
    return { ...appCtx, detailRating, memberPrice, favorited, flashSale, flashPrice, flashOrigin, detailPrice, detailOrigin, memberView, flashCapped, openOriginalProduct };
  }
};
</script>
