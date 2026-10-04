<template>
  <nav class="pager" v-if="totalPages > 1">
    <button
      class="pg-btn pg-prev"
      type="button"
      :disabled="page <= 1 || loading"
      @click="$emit('go', page - 1)"
    >‹ 上一页</button>

    <button
      v-for="(p, idx) in visiblePages"
      :key="idx"
      type="button"
      :class="['pg-num', { on: p !== '...' && p === page, dots: p === '...' }]"
      :disabled="p === '...' || loading"
      @click="p !== '...' && $emit('go', p)"
    >{{ p === '...' ? '…' : p }}</button>

    <button
      class="pg-btn pg-next"
      type="button"
      :disabled="page >= totalPages || loading"
      @click="$emit('go', page + 1)"
    >下一页 ›</button>
    <span class="pg-info">第 {{ page }} / {{ totalPages }} 页</span>
  </nav>
</template>

<script>
export default {
  name: 'Pager',
  props: {
    page: { type: Number, required: true },
    total: { type: Number, default: 0 },
    size: { type: Number, default: 10 },
    loading: { type: Boolean, default: false },
  },
  emits: ['go'],
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.total / (this.size || 10)));
    },
    visiblePages() {
      const tp = this.totalPages;
      const cur = this.page;
      // 页数较少时直接全部铺开
      if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1);
      // 较多时做窗口：始终保留首尾、当前页及其左右，缺口用 … 表示
      const candidates = new Set([1, tp, cur, cur - 1, cur + 1]);
      const sorted = [...candidates]
        .filter((n) => n >= 1 && n <= tp)
        .sort((a, b) => a - b);
      const out = [];
      let prev = 0;
      for (const n of sorted) {
        if (n - prev > 1) out.push('...');
        out.push(n);
        prev = n;
      }
      return out;
    },
  },
};
</script>

<style scoped>
.pager {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  justify-content: center;
  align-items: center;
  margin: 14px 0 4px;
}
.pg-btn,
.pg-num {
  /* 小按钮显式覆盖全局 button 基础样式，避免漏样式 */
  min-height: 0;
  min-width: 36px;
  height: 34px;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid #e2ebe7;
  background: #fff;
  color: var(--ink);
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
  box-sizing: border-box;
}
.pg-num.on {
  background: var(--brand-deep);
  border-color: var(--brand-deep);
  color: #fff;
  font-weight: 700;
}
.pg-num.dots,
.pg-btn:disabled,
.pg-num:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.pg-info {
  margin-left: 6px;
  font-size: 12px;
  color: var(--muted);
}
</style>
