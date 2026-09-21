<script setup>
// 后台工具条里的「搜索框 + 查询按钮」：从 AdminPanel.vue 抽出来的共用件（原先 7 处各写一遍）。
// 与 AdminPager 同一套路：**不持有状态**，关键词由父级持有（v-model），回车与点按钮都只发一个 search 事件。
// @search 可以是函数名，也可以是内联语句（如库存/分类那两块写的是 `stockPage = 1`）。
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' },
});
const emit = defineEmits(['update:modelValue', 'search']);
</script>

<template>
  <div class="search-box admin-search">
    <input
      :value="modelValue"
      :placeholder="placeholder"
      @input="emit('update:modelValue', $event.target.value)"
      @keyup.enter="emit('search')"
    />
    <button @click="emit('search')">查询</button>
  </div>
</template>
