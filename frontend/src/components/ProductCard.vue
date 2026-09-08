<template>
  <article class="product-card clickable" @click="$emit('open', product)">
    <div class="product-image" @click.stop="$emit('open', product)">
      <img v-if="product.coverUrl" :src="product.coverUrl" :alt="product.name" />
      <span v-else>{{ initials(product.name) }}</span>
      <span v-if="badges && discountSave(product.originalPrice, product.price) > 0" class="corner-badge">省{{ money(discountSave(product.originalPrice, product.price)) }}</span>
      <span v-if="badges && product.isHot" class="corner-badge hot">热</span>
      <span v-if="badges && product.isNew" class="corner-badge new">新</span>
    </div>
    <h3 @click.stop="$emit('open', product)">{{ product.name }}</h3>
    <p class="brand-line" v-if="product.brand">{{ product.brand }}</p>
    <p>{{ product.subtitle || formatUnit(product.unit) }}</p>
    <p v-if="extra" class="brand-line">{{ extra }}</p>
    <div class="price-line" v-if="mode === 'full'">
      <div class="price-main">
        <strong>{{ money(product.price) }}</strong>
        <span v-if="Number(product.originalPrice) > Number(product.price)" class="origin-price">{{ money(product.originalPrice) }}</span>
      </div>
      <small :class="{ 'low-stock': lowStock }">
        <template v-if="lowStock">仅剩 {{ product.stock }} 件</template>
        <template v-else>库存 {{ product.stock }}</template>
        <template v-if="salesText"> · 已售 {{ salesText }}</template>
      </small>
    </div>
    <div class="price-line" v-else>
      <div class="price-main"><strong>{{ money(product.price) }}</strong></div>
    </div>
    <template v-if="mode === 'full'">
      <button class="ghost" @click.stop="$emit('open', product)">查看详情</button>
      <button v-if="addable" @click.stop="$emit('add', product)">加入购物车</button>
      <span v-else-if="isAdmin" class="admin-note">管理员仅查看上架商品</span>
    </template>
  </article>
</template>

<script setup>
import { computed } from 'vue';
import { money, initials, formatUnit, discountSave } from '../utils/format';

const props = defineProps({
  product: { type: Object, required: true },
  mode: { type: String, default: 'full' }, // 'full' | 'compact'
  addable: { type: Boolean, default: false },
  isAdmin: { type: Boolean, default: false },
  badges: { type: Boolean, default: false },
  extra: { type: String, default: '' },
});

defineEmits(['open', 'add']);

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
</script>
