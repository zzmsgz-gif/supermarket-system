<template>
<section class="data-panel favorites-page">
  <!-- 降价提醒 -->
  <div class="fav-section" v-if="priceAlerts.items.length">
    <div class="section-head">
      <h3>降价提醒 <span v-if="alertUnread" class="unread-pill">{{ alertUnread }} 条未读</span></h3>
      <small class="muted">你收藏的商品降价了，趁现在下手</small>
      <button v-if="alertUnread > 0" class="ghost sm read-all" @click="markAlertsRead">全部已读</button>
    </div>
    <ul class="alert-list">
      <li v-for="alert in priceAlerts.items" :key="alert.id" class="alert-row" :class="{ unread: !alert.isRead }">
        <div class="alert-thumb" @click="alert.product && openProductDetail(alert.product)">
          <img v-if="alert.product && alert.product.coverUrl" :src="alert.product.coverUrl" :alt="alert.product.name" @error="imgFallback($event, alert.product.name)" />
          <span v-else>{{ initials(alert.product ? alert.product.name : '?') }}</span>
        </div>
        <div class="alert-main" @click="alert.product && openProductDetail(alert.product)">
          <strong>{{ alert.product ? alert.product.name : '商品已下架' }}</strong>
          <small class="price-flow">
            收藏时 <s>{{ money(alert.oldPrice) }}</s>
            <em>→</em>
            现价 <b>{{ money(alert.newPrice) }}</b>
          </small>
          <small class="muted">{{ formatDate(alert.createdAt) }} 降价</small>
        </div>
        <div class="alert-drop">
          <span class="drop-badge">降 {{ money(alert.dropAmount) }}</span>
          <small>{{ alert.dropPercent }}% ↓</small>
        </div>
        <div class="alert-actions">
          <button class="primary sm" @click="addToCart(alert.product)">加入购物车</button>
          <button class="ghost sm" @click="toggleFavorite(alert.product)">取消收藏</button>
        </div>
      </li>
    </ul>
    <button
      v-if="priceAlerts.items.length < priceAlerts.total"
      class="ghost load-more"
      @click="loadPriceAlerts(false)"
      :disabled="priceAlerts.loading"
    >{{ priceAlerts.loading ? '加载中…' : '加载更多降价提醒' }}</button>
  </div>

  <!-- 收藏列表 -->
  <div class="fav-section">
    <div class="section-head">
      <h3>收藏的商品</h3>
      <small class="muted">共 {{ favorites.total }} 件</small>
    </div>
    <empty-state
      v-if="!favorites.items.length && !favorites.loading"
      icon="heart"
      text="还没有收藏，点商品卡右上角的心形就能收藏，降价会自动提醒你"
      action-text="去逛逛"
      @action="navigate('shop')"
    />
    <div v-else class="product-grid">
      <ProductCard
        v-for="fav in favorites.items"
        :key="fav.id"
        :product="fav.product"
        mode="full"
        addable
        badges
        :is-admin="isAdmin"
        @add="addToCart"
        @open="openProductDetail"
      />
    </div>
    <button
      v-if="favorites.items.length < favorites.total"
      class="ghost load-more"
      @click="loadFavorites(false)"
      :disabled="favorites.loading"
    >{{ favorites.loading ? '加载中…' : '加载更多收藏' }}</button>
  </div>
</section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'FavoritesPage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>

<style scoped>
.read-all { margin-left: auto; }
.fav-section { margin-top: 6px; }
.section-head { display: flex; align-items: baseline; gap: 10px; margin-bottom: 10px; }
.section-head h3 { margin: 0; font-size: 16px; display: flex; align-items: center; gap: 8px; }
.unread-pill {
  font-size: 12px; font-weight: 700; color: #fff; background: var(--danger);
  padding: 2px 9px; border-radius: 999px;
}
.alert-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 10px; }
.alert-row {
  display: grid; grid-template-columns: 64px 1fr auto auto; gap: 14px; align-items: center;
  border: 1px solid #eef2f0; border-radius: 12px; padding: 12px; background: #fff;
}
.alert-row.unread { border-color: #f0d9d5; background: #fffaf9; }
.alert-thumb {
  width: 64px; height: 64px; border-radius: 10px; overflow: hidden; cursor: pointer;
  background: var(--brand-soft); display: grid; place-items: center; font-weight: 800; color: var(--brand-deep);
}
.alert-thumb img { width: 100%; height: 100%; object-fit: cover; }
.alert-main { display: flex; flex-direction: column; gap: 4px; min-width: 0; cursor: pointer; }
.alert-main strong { font-size: 15px; color: var(--ink); }
.price-flow { color: var(--muted); font-size: 13px; display: flex; align-items: center; gap: 6px; }
.price-flow s { text-decoration: line-through; }
.price-flow em { font-style: normal; color: #b8c4bf; }
.price-flow b { color: var(--danger); font-size: 15px; }
.alert-drop { display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.drop-badge {
  background: #fdecea; color: var(--danger); font-weight: 800; font-size: 13px;
  padding: 3px 10px; border-radius: 999px; white-space: nowrap;
}
.alert-drop small { color: var(--danger); font-size: 12px; }
.alert-actions { display: flex; flex-direction: column; gap: 6px; }
.alert-actions .primary.sm, .alert-actions .ghost.sm { padding: 7px 12px; font-size: 13px; white-space: nowrap; }
.load-more { margin: 12px auto 0; display: block; }

@media (max-width: 900px) {
  .alert-row { grid-template-columns: 56px 1fr; grid-template-areas: "thumb main" "drop actions"; row-gap: 10px; }
  .alert-thumb { grid-area: thumb; }
  .alert-main { grid-area: main; }
  .alert-drop { grid-area: drop; align-items: flex-start; flex-direction: row; gap: 8px; }
  .alert-actions { grid-area: actions; flex-direction: row; justify-self: end; }
}
</style>
