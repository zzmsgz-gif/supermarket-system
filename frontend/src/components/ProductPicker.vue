<script setup>
/**
 * 可搜索的商品选择器 —— 替代「把全部商品塞进原生 <select>」。
 *
 * <p>**为什么要换掉原生下拉**（2026-10-10）：
 * 原生 select 里塞 500 个 option，商品一多就变成「超长滚动列表」，
 * 而且同名商品（不同规格/分类）只显示名字，**根本分不清是哪一个**。
 * 用户反馈「适用商品选择不了，列出来下拉列表又太长」。
 *
 * <p>本组件的行为：
 * <ul>
 *   <li>输入框打关键词 → **调后端接口**模糊搜索（`/products?keyword=…`），
 *       不是在前端全量列表里筛 —— 商品上千时前端筛选会把整个列表拉下来。</li>
 *   <li>每条结果显示 `商品名 · 分类 · ¥价格`，解决同名分不清的问题。</li>
 *   <li>已选项显示成标签（chip），点 × 移除；单选模式下选中即收起。</li>
 *   <li>点外部 / 按 Esc 关闭下拉；下拉里可滚动，不会顶满屏。</li>
 * </ul>
 *
 * <p>用 `multiple` 切换单选/多选，两个模式共用一套 UI。
 */
import { ref, computed, watch } from 'vue';

const props = defineProps({
  /** api 对象（需有 get 方法） */
  api: { type: Object, required: true },
  /** v-model 绑定的值：单选为 id(Number)，多选为 id 数组 */
  modelValue: { type: [Number, Array, String, null], default: null },
  /** 多选模式 */
  multiple: { type: Boolean, default: false },
  /** 已选项的展示数据 [{ id, name }] —— 由父组件传入，避免组件内重复请求 */
  selectedItems: { type: Array, default: () => [] },
  placeholder: { type: String, default: '输入商品名 / 分类 / 编号搜索' },
  /** 搜索结果条数上限 */
  pageSize: { type: Number, default: 20 },
});
const emit = defineEmits(['update:modelValue']);

const keyword = ref('');
const open = ref(false);
const loading = ref(false);
const results = ref([]);
const activeId = ref(0);   // 键盘上下键选中项

// 兜底：请求异常时不要把已有选中项弄丢
const pick = computed(() => props.selectedItems || []);

async function search() {
  const kw = keyword.value.trim();
  loading.value = true;
  try {
    // 关键词直接交给后端模糊匹配（商品名/分类/编号），前端只负责展示
    const q = new URLSearchParams({ page: '1', size: String(props.pageSize) });
    if (kw) q.set('keyword', kw);
    const data = await props.api.get(`/products?${q}`);
    results.value = (data && data.items) || [];
    activeId.value = 0;
  } catch (e) {
    results.value = [];
  } finally {
    loading.value = false;
  }
}

// 打开就搜一次（空关键词 = 销量最高的若干个，让用户有起点）
watch(open, (v) => { if (v) search(); });

/** 每次输入都重新搜（去抖 250ms，避免每敲一个字打一次接口） */
let timer = null;
watch(keyword, () => {
  if (!open.value) return;
  clearTimeout(timer);
  timer = setTimeout(search, 250);
});

function isPicked(id) {
  if (props.multiple) return (props.modelValue || []).some((x) => Number(x) === Number(id));
  return Number(props.modelValue) === Number(id);
}

function toggle(item) {
  if (props.multiple) {
    const cur = Array.isArray(props.modelValue) ? [...props.modelValue] : [];
    const i = cur.findIndex((x) => Number(x) === Number(item.id));
    if (i >= 0) cur.splice(i, 1);
    else cur.push(item.id);
    emit('update:modelValue', cur);
  } else {
    emit('update:modelValue', item.id);
    open.value = false;
  }
}

function remove(item) {
  if (props.multiple) {
    emit('update:modelValue', (props.modelValue || []).filter((x) => Number(x) !== Number(item.id)));
  } else {
    emit('update:modelValue', null);
  }
}

function onKeydown(e) {
  if (!open.value) return;
  const n = results.value.length;
  if (e.key === 'ArrowDown') { activeId.value = Math.min(activeId.value + 1, n - 1); e.preventDefault(); }
  else if (e.key === 'ArrowUp') { activeId.value = Math.max(activeId.value - 1, 0); e.preventDefault(); }
  else if (e.key === 'Enter') {
    if (results.value[activeId.value]) { toggle(results.value[activeId.value]); e.preventDefault(); }
  } else if (e.key === 'Escape') { open.value = false; }
}

/** 点击组件外部时收起下拉（用 mousedown 避免和 input 的 click 抢顺序） */
const root = ref(null);
function onDocDown(e) {
  if (root.value && !root.value.contains(e.target)) open.value = false;
}
watch(root, (el) => {
  if (el) document.addEventListener('mousedown', onDocDown);
  else document.removeEventListener('mousedown', onDocDown);
});

function clear() {
  keyword.value = '';
  if (props.multiple) emit('update:modelValue', []);
  else emit('update:modelValue', null);
}

defineExpose({ clear, search });
</script>

<template>
  <div ref="root" class="pk">
    <!-- 已选标签 -->
    <div v-if="pick.length" class="pk-chips">
      <span v-for="it in pick" :key="it.id" class="pk-chip">
        {{ it.name }}
        <i class="pk-x" title="移除" @click="remove(it)">×</i>
      </span>
    </div>

    <div class="pk-input-wrap">
      <input
        class="pk-input"
        :value="keyword"
        :placeholder="pick.length ? '继续搜索添加…' : placeholder"
        @input="keyword = $event.target.value"
        @focus="open = true"
        @click="open = true"
        @keydown="onKeydown"
      />
      <button v-if="keyword || pick.length" type="button" class="pk-clear" title="清空" @click="clear">×</button>
      <span v-if="loading" class="pk-loading">搜…</span>
    </div>

    <div v-if="open" class="pk-drop">
      <div v-if="!results.length" class="pk-empty">{{ loading ? '搜索中…' : '没有匹配的商品' }}</div>
      <button
        v-for="(item, idx) in results"
        :key="item.id"
        type="button"
        class="pk-item"
        :class="{ on: isPicked(item.id), active: idx === activeId }"
        :disabled="multiple ? false : isPicked(item.id)"
        @click="toggle(item)"
        @mouseenter="activeId = idx">
        <span class="pk-name">{{ item.name }}</span>
        <!-- 副信息：**SKU 编号 + 规格单位 + 价格**。
             选这个而不是分类名，是因为 `/products` 接口目前不返回 categoryName
             （只有 categoryId），硬拼分类还得再发一次请求。
             SKU 同样能解决「同名商品分不清」——同名不同 SKU 就是两个商品，
             而且 SKU 本来就是后台识别商品用的字段。 -->
        <span class="pk-meta">{{ item.sku || ('ID ' + item.id) }}{{ item.unit ? ' · ' + item.unit : '' }} · ¥{{ Number(item.price || 0).toFixed(2) }}</span>
        <span v-if="isPicked(item.id)" class="pk-tick">已选</span>
      </button>
      <!-- 结果被条数上限截断时明确告知，否则用户会以为只有这些 -->
      <div v-if="results.length >= pageSize" class="pk-more">仅显示前 {{ pageSize }} 条，请输入更具体的关键词</div>
    </div>
  </div>
</template>