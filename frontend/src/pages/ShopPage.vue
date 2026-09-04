<template>
<section class="shop-home">
        <div class="search-hero">
          <div class="hero-copy">
            <span>超市精选</span>
            <h2>{{ isAdmin ? '查看当前上架商品' : '新鲜好物，一站选购' }}</h2>
          </div>
          <div class="search-box">
            <input v-model="filters.keyword" placeholder="搜索上架商品" @keyup.enter="loadProducts" />
            <button @click="loadProducts">搜索</button>
          </div>
        </div>

        <div class="shop-filters">
          <div class="filter-group">
            <label>价格</label>
            <input v-model.number="filters.minPrice" type="number" min="0" placeholder="最低" @keyup.enter="applyFilters" />
            <span class="dash">—</span>
            <input v-model.number="filters.maxPrice" type="number" min="0" placeholder="最高" @keyup.enter="applyFilters" />
          </div>
          <div class="filter-group">
            <label>品牌</label>
            <input v-model="filters.brand" placeholder="按品牌筛选" @keyup.enter="applyFilters" />
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

        <div class="shop-layout">
          <aside class="category-panel">
            <div class="category-title">商品分类</div>
            <button :class="{ active: !filters.categoryId }" @click="chooseCategory('')">全部分类</button>
            <button
              v-for="category in categories"
              :key="category.id"
              :class="{ active: String(filters.categoryId) === String(category.id) }"
              @click="chooseCategory(category.id)"
            >
              <span>{{ category.name }}</span>
            </button>
          </aside>

          <div class="product-area">
            <div class="product-head">
              <strong>为你推荐</strong>
              <select v-model="filters.categoryId" @change="loadProducts">
                <option value="">全部分类</option>
                <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
              </select>
            </div>

            <div class="product-grid">
              <ProductCard v-for="product in products.items" :key="product.id" :product="product" mode="full" :addable="!isAdmin" :is-admin="isAdmin" :badges="true" @open="openProductDetail" @add="addToCart" />
            </div>
          </div>
        </div>

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

      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'ShopPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
