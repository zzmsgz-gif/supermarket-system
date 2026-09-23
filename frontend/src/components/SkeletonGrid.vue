<template>
  <!-- 商品骨架屏：多根节点（fragment），直接作为 .product-grid 的子元素铺开 ——
       这样列数由父级网格决定，与真实商品卡完全对齐，不需要单独维护一套列数。
       纯装饰，aria-hidden；调用方负责用 v-if 控制显隐。
       ⚠️ 不带 .product-card 类：否则会被「卡片 hover / 入场」那些规则一起命中。 -->
  <div v-for="i in count" :key="i" class="sk-card" aria-hidden="true">
    <div class="sk-img sk-shimmer"></div>
    <div class="sk-line sk-shimmer" style="width: 84%"></div>
    <div class="sk-line sk-shimmer" style="width: 56%"></div>
    <div class="sk-line sk-shimmer sk-line--price" style="width: 38%"></div>
  </div>
</template>

<script setup>
// 只负责「长什么样」，不持有任何业务状态：显隐由调用方的 loading 决定。
defineProps({
  count: { type: Number, default: 10 },
});
</script>
