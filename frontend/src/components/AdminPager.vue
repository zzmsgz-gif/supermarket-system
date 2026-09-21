<script setup>
// 后台列表的分页控件：从 AdminPanel.vue 抽出来的共用件。
// 原先 16 个面板各自复制了一段几乎一样的分页模板，只有绑定的变量名不同（adminOrders.page / stockPage / …）。
//
// ⚠️ 刻意**不持有任何状态**：page / totalPages / jumpPage 全部由父级持有，这里只负责渲染和发事件。
//    这样抽组件时不用动 AdminPanel 的响应式数据，也不用把 adminCtx 传进来 —— 零逻辑改动。
//    没传 jumpPage 时不渲染「跳至 N 页」（评价管理那块是简版，本来就没有跳页）。
defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  jumpPage: { type: [Number, String], default: null },
});
const emit = defineEmits(['change', 'jump', 'update:jumpPage']);
</script>

<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="ghost" :disabled="page <= 1" @click="emit('change', -1)">上一页</button>
    <span class="page-info">第 {{ page }} / {{ totalPages }} 页</span>
    <button class="ghost" :disabled="page >= totalPages" @click="emit('change', 1)">下一页</button>
    <span v-if="jumpPage !== null" class="page-jump-wrap">跳至
      <input
        type="number"
        min="1"
        :max="totalPages"
        :value="jumpPage"
        class="page-jump"
        @input="emit('update:jumpPage', Number($event.target.value))"
        @keyup.enter="emit('jump')"
      />
      页
      <button class="ghost" @click="emit('jump')">跳转</button>
    </span>
  </div>
</template>
