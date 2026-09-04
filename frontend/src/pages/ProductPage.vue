<template>
<section class="data-panel product-detail">
        <div class="detail-nav">
          <button class="ghost" @click="backFromProduct">返回商品列表</button>
          <span class="detail-crumb">商品列表 / {{ categoryName(productDetail.data?.categoryId) }} / {{ productDetail.data?.name || '' }}</span>
        </div>

        <div v-if="productDetail.loading || !productDetail.data" class="empty">商品信息加载中…</div>

        <template v-else>
          <div class="detail-grid">
            <div class="detail-gallery">
              <div class="detail-image">
                <img v-if="currentGalleryImage" :src="currentGalleryImage" :alt="productDetail.data.name" />
                <span v-else>{{ initials(productDetail.data.name) }}</span>
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
                  <img :src="img" :alt="商品图" />
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
                <strong>{{ money(productDetail.data.price) }}</strong>
                <span v-if="Number(productDetail.data.originalPrice) > Number(productDetail.data.price)" class="detail-origin">原价 {{ money(productDetail.data.originalPrice) }}</span>
                <span v-if="discountSave(productDetail.data.originalPrice, productDetail.data.price) > 0" class="detail-discount">省 {{ money(discountSave(productDetail.data.originalPrice, productDetail.data.price)) }} · 约 {{ discountRate(productDetail.data.originalPrice, productDetail.data.price) }} 折</span>
                <span class="detail-unit">/ {{ formatUnit(productDetail.data.unit) }}</span>
              </div>
              <div v-if="activeActivities.length" class="activity-banner">
                <span class="activity-tag" v-for="act in activeActivities" :key="act.id">
                  <template v-if="act.type === 'FULL_REDUCTION'">满{{ money(act.threshold) }}减{{ money(act.discount) }}</template>
                  <template v-else>{{ (Number(act.discount) * 10).toFixed(1) }}折</template>
                </span>
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
                  <button type="button" @click="changeDetailQty(-1)">−</button>
                  <span>{{ detailQuantity }}</span>
                  <button type="button" @click="changeDetailQty(1)">+</button>
                </div>
                <button @click="addDetailToCart">加入购物车</button>
                <button class="ghost" @click="buyDetailNow">立即购买</button>
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

          <div class="detail-section" v-if="relatedProducts.length">
            <h3>相关推荐</h3>
              <div class="related-grid">
                <ProductCard v-for="p in relatedProducts" :key="p.id" :product="p" mode="compact" @open="openProductDetail" />
              </div>
          </div>

          <div class="detail-section">
            <div class="panel-head">
              <h3>用户评价（{{ productDetail.reviews.length }}）</h3>
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
                  <img v-for="(img, idx) in review.imageUrls" :key="idx" :src="img" alt="评价图片" />
                </div>
              </div>
            </div>
          </div>
        </template>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'ProductPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
