<script setup>
// 后台工具条里的「每页条数」下拉：从 AdminPanel.vue 抽出来的共用件（原先 9 处各写 5 行）。
// ⚠️ onChange 里**先** update:modelValue **再** emit('change') —— 原来 v-model 先写回、@change 再触发，
//    父级的 changeAdminXxxPageSize 读到的是已更新的 size。顺序反了会按旧值重查。
defineProps({
  modelValue: { type: [Number, String], default: 10 },
});
const emit = defineEmits(['update:modelValue', 'change']);

function onChange(event) {
  emit('update:modelValue', Number(event.target.value));
  emit('change');
}
</script>

<template>
  <select :value="modelValue" class="filter-select" @change="onChange">
    <option :value="10">10 条/页</option>
    <option :value="20">20 条/页</option>
    <option :value="50">50 条/页</option>
  </select>
</template>
