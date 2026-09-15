<template>
  <section class="data-panel legal-page">
    <div v-if="loading" class="empty">加载中…</div>
    <div v-else-if="!doc" class="empty error">该文档暂不可用，请稍后再试</div>
    <template v-else>
      <div class="legal-head">
        <h2>{{ doc.title }}</h2>
        <div class="legal-meta">
          <span v-if="doc.version">{{ doc.version }}</span>
          <span v-if="doc.updatedAt">最近更新：{{ formatDate(doc.updatedAt) }}</span>
        </div>
      </div>
      <!-- 正文用 pre-wrap 直接渲染：协议是纯文本，不走 v-html，避免任何注入风险 -->
      <div class="legal-body">{{ doc.content }}</div>
    </template>
  </section>
</template>

<script>
import { inject, ref, watch, computed } from 'vue';
import { useRoute } from 'vue-router';
export default {
  name: 'LegalPage',
  setup() {
    const appCtx = inject('appCtx');
    const route = useRoute();
    // 路由名 → 文档 key，两个路由复用同一个页面组件
    const docKey = computed(() => (route.name === 'privacy' ? 'PRIVACY' : 'TERMS'));
    const doc = ref(null);
    const loading = ref(false);

    async function load() {
      loading.value = true;
      doc.value = await appCtx.loadLegalDoc(docKey.value);
      loading.value = false;
    }

    watch(docKey, load, { immediate: true });
    return { ...appCtx, doc, loading };
  }
};
</script>

<style scoped>
.legal-head {
  padding-bottom: 12px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line, #e8ecea);
}
.legal-head h2 { margin: 0 0 6px; font-size: 20px; }
.legal-meta {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--muted, #5a6673);
}
.legal-body {
  white-space: pre-wrap;
  line-height: 1.9;
  font-size: 14px;
  color: var(--ink, #0f1721);
}
</style>
