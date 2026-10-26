<script setup>
import { computed } from 'vue';
// 后台/用户端列表共用的分页控件。
//
// 两种形态：
//   - 默认（numeric=false，后台面板用）：上一页 / 第 X / Y 页 / 下一页 + 可选跳页。
//     翻页走 `change` 增量事件（±1），与既有 16 个后台面板的 handler 完全兼容，不破坏任何现有逻辑。
//   - numeric=true（用户端售后页 / 金额明细页用，2026-10-22 #714）：上一页 + 窗口化数字页码 + 下一页 +
//     第 X / Y 页 + 可选跳页。数字页码与上下页统一走 `go` 绝对页码事件，父级 handler 直接 set page。
//
// ⚠️ 刻意**不持有任何状态**：page / totalPages / jumpPage 全部由父级持有，这里只渲染和发事件。
//    没传 jumpPage 时不渲染「跳至 N 页」（要不要跳页由父级决定，传了才有）。
// ⚠️ 必须捕获返回值：在 <script setup> 里，不赋值的 defineProps 不会把 prop 暴露成 JS 标识符，
// 模板里能直接写 `page`/`totalPages`，但脚本（computed / 函数）里必须用 `props.xxx`，
// 否则运行期抛 ReferenceError 导致整个 AdminPager 渲染崩溃（2026-10-24 #714 回归修复）。
const props = defineProps({
  page: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  jumpPage: { type: [Number, String], default: null },
  numeric: { type: Boolean, default: false },
});
const emit = defineEmits(['change', 'go', 'jump', 'update:jumpPage']);

// 窗口化的页码序列：数字页码 + '...' 占位（不可点）。仅在 numeric 模式使用。
const pages = computed(() => {
  const tp = Math.max(1, props.totalPages);
  const cur = props.page;
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1);
  const out = [1];
  const start = Math.max(2, cur - 2);
  const end = Math.min(tp - 1, cur + 2);
  if (start > 2) out.push('...');
  for (let i = start; i <= end; i++) out.push(i);
  if (end < tp - 1) out.push('...');
  out.push(tp);
  return out;
});

function onPrev() {
  if (props.numeric) emit('go', props.page - 1);
  else emit('change', -1);
}
function onNext() {
  if (props.numeric) emit('go', props.page + 1);
  else emit('change', 1);
}
function go(n) {
  if (n === '...') return;
  emit('go', Number(n));
}
</script>

<template>
  <div v-if="totalPages > 1" class="pagination">
    <button class="ghost" :disabled="page <= 1" @click="onPrev">上一页</button>

    <template v-if="numeric">
      <button
        v-for="p in pages"
        :key="String(p)"
        class="page-num"
        :class="{ active: p === page, ellipsis: p === '...' }"
        :disabled="p === '...' || p === page"
        @click="go(p)"
      >{{ p === '...' ? '···' : p }}</button>
    </template>
    <span v-else class="page-info">第 {{ page }} / {{ totalPages }} 页</span>

    <button class="ghost" :disabled="page >= totalPages" @click="onNext">下一页</button>

    <span v-if="numeric" class="page-info">第 {{ page }} / {{ totalPages }} 页</span>

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

<style scoped>
.pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.page-num {
  min-width: 32px;
  height: 32px;
  padding: 0 6px;
  border: 1px solid var(--line, #e5e7eb);
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--text, #1f2937);
}
.page-num:hover:not(:disabled) {
  border-color: var(--primary, #ff5000);
  color: var(--primary, #ff5000);
}
.page-num.active {
  background: var(--primary, #ff5000);
  border-color: var(--primary, #ff5000);
  color: #fff;
  cursor: default;
}
.page-num.ellipsis {
  border-color: transparent;
  background: transparent;
  cursor: default;
  color: var(--muted, #9ca3af);
}
.page-num:disabled {
  cursor: default;
  opacity: 0.6;
}
</style>
