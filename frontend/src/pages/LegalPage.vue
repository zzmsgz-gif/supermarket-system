<template>
  <section class="data-panel legal-page">
    <div v-if="loading" class="sk-stack" aria-hidden="true">
      <skeleton-lines :lines="8" :title="true" />
    </div>
    <div v-else-if="!doc" class="empty error">该文档暂不可用，请稍后再试</div>
    <template v-else>
      <div class="legal-head">
        <h2>{{ doc.title }}</h2>
        <div class="legal-meta">
          <span v-if="doc.version">{{ doc.version }}</span>
          <span v-if="doc.updatedAt">最近更新：{{ formatDate(doc.updatedAt) }}</span>
        </div>
      </div>
      <!-- 正文按行解析成块渲染（仍然不用 v-html，零注入风险）：
           以前直接 {{ doc.content }} + pre-wrap，行内 **粗体** 标记会把星号原样显示出来。 -->
      <div class="legal-body">
        <p v-for="(b, i) in blocks" :key="i" :class="['blk', 'blk-' + b.type]">
          <template v-for="(s, j) in b.segs" :key="j">
            <strong v-if="s.bold">{{ s.text }}</strong>
            <span v-else>{{ s.text }}</span>
          </template>
        </p>
      </div>
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

    // 纯文本正文 → 结构化块（与小程序 legal.vue 同一套解析规则）：
    // 「一、」= 小节标题、「1.1」= 条目、空行分段、行内 **文字** = 加粗。
    const blocks = computed(() => {
      const raw = (doc.value && doc.value.content) || '';
      const out = [];
      raw.split(/\r?\n/).forEach((line) => {
        const text = line.trim();
        if (!text) return;
        let type = 'p';
        if (/^[一二三四五六七八九十]+、/.test(text)) type = 'h';
        else if (/^\d+(\.\d+)+/.test(text)) type = 'item';
        const segs = [];
        text.split('**').forEach((t, i) => {
          if (t) segs.push({ text: t, bold: i % 2 === 1 });
        });
        out.push({ type, segs: segs.length ? segs : [{ text, bold: false }] });
      });
      return out;
    });

    return { ...appCtx, doc, loading, blocks };
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
  line-height: 1.9;
  font-size: 14px;
  color: var(--ink, #0f1721);
}
.blk {
  margin: 0 0 10px;
  text-align: justify;
  word-break: break-word;
}
.blk-h {
  margin: 22px 0 8px;
  padding-left: 10px;
  border-left: 3px solid var(--brand, #0F6E56);
  font-size: 15px;
  font-weight: 700;
  color: var(--ink, #0f1721);
}
.blk-item { margin-bottom: 6px; }
.blk strong { font-weight: 700; color: var(--ink, #0f1721); }
</style>
