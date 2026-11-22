<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { api } from '../api/client';
import { formatDate } from '../utils/format';

/**
 * 后台「协议与隐私」面板（2026-11-22 新增）。
 *
 * 背景：后端 /admin/legal-docs 的 GET/PUT 早就有了（前台协议页也从这里取正文），
 * 但后台一直没有界面 —— 协议正文里那些【平台名称】【公司全称】占位符、条款修订，
 * 只能靠人肉调接口改。这里补上编辑入口。
 *
 * 直接 import api（adminCtx 里并没有挂 api，只有 appCtx 有），
 * 也不往父级 ctx 里加新状态（少一个漏解构就白屏的风险）。
 */
const props = defineProps({
  adminCtx: { type: Object, required: true },
});

const tabs = [
  { key: 'TERMS', label: '用户协议' },
  { key: 'PRIVACY', label: '隐私政策' },
];
const activeKey = ref('TERMS');
const loading = ref(false);
const saving = ref(false);
const tip = ref('');

// 两份各自保留编辑状态：切 tab 不会丢掉另一份还没保存的改动
const forms = reactive({
  TERMS: { title: '', content: '', version: '', enabled: true, updatedAt: null },
  PRIVACY: { title: '', content: '', version: '', enabled: true, updatedAt: null },
});
const current = computed(() => forms[activeKey.value]);

function flash(msg) {
  tip.value = msg;
  setTimeout(() => { tip.value = ''; }, 4000);
}

async function load() {
  loading.value = true;
  try {
    const list = await api.get('/admin/legal-docs');
    (Array.isArray(list) ? list : []).forEach((d) => {
      const key = String(d.docKey || '').toUpperCase();
      const f = forms[key];
      if (!f) return;
      f.title = d.title || '';
      f.content = d.content || '';
      f.version = d.version || '';
      f.enabled = Number(d.enabled) === 1;
      f.updatedAt = d.updatedAt || null;
    });
  } catch (e) {
    flash('加载失败：' + (e?.message || '请刷新重试'));
  } finally {
    loading.value = false;
  }
}

async function save() {
  const key = activeKey.value;
  const f = forms[key];
  if (!f.content.trim()) {
    flash('正文不能为空');
    return;
  }
  saving.value = true;
  try {
    const res = await api.put('/admin/legal-docs/' + key, {
      title: f.title.trim() || undefined,
      content: f.content,
      version: f.version.trim() || undefined,
      enabled: f.enabled ? 1 : 0,
    });
    if (res?.updatedAt) f.updatedAt = res.updatedAt;
    flash('已保存，前台（PC 与小程序）立即生效');
  } catch (e) {
    flash('保存失败：' + (e?.message || '请重试'));
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <div class="data-panel legal-admin">
    <div class="toolbar">
      <button
        v-for="t in tabs"
        :key="t.key"
        :class="{ ghost: activeKey !== t.key }"
        @click="activeKey = t.key"
      >{{ t.label }}</button>
      <span v-if="current.updatedAt" class="tag muted">最近保存：{{ formatDate(current.updatedAt) }}</span>
      <span class="la-spacer"></span>
      <button class="ghost" :disabled="loading || saving" @click="load">重新加载</button>
      <button :disabled="saving" @click="save">{{ saving ? '保存中…' : '保存' }}</button>
    </div>

    <p v-if="tip" class="la-tip">{{ tip }}</p>

    <p class="la-hint">
      排版规则（前台按行解析）：<b>一、二、</b> 开头是小节标题，<b>1.1</b> 开头是条目，
      空行分段，行内 <b>**文字**</b> 显示为加粗。保存后 PC 与小程序协议页同时生效。
    </p>

    <div v-if="loading" class="empty">加载中…</div>
    <form v-else class="la-form" @submit.prevent="save">
      <div class="la-row">
        <label class="la-field">
          <span>标题</span>
          <input v-model="current.title" maxlength="60" placeholder="用户协议" />
        </label>
        <label class="la-field la-field--sm">
          <span>版本（选填）</span>
          <input v-model="current.version" maxlength="20" placeholder="如 v1.0" />
        </label>
      </div>

      <label class="la-check">
        <input type="checkbox" v-model="current.enabled" /> 前台可访问（取消勾选后协议页显示「暂不可用」）
      </label>

      <label class="la-field la-field--area">
        <span>正文 <i class="la-count">{{ (current.content || '').length }} 字</i></span>
        <textarea v-model="current.content" spellcheck="false" placeholder="在这里维护协议正文…"></textarea>
      </label>

      <div class="la-actions">
        <span class="la-note">建议保留末尾的「客服电话 / 联系邮箱 / 注册地址」三行，合规检查会看。</span>
        <button type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存' }}</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.legal-admin { display: block; }
.la-spacer { flex: 1; }
.la-tip {
  margin: 0 0 10px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #e8f6f0;
  color: #0a6b52;
  font-size: 13px;
}
.la-hint {
  margin: 0 0 14px;
  color: var(--muted, #5a6673);
  font-size: 12.5px;
  line-height: 1.7;
}
.la-hint b { color: var(--ink, #0f1721); }
.la-form { display: block; }
.la-row { display: flex; gap: 12px; flex-wrap: wrap; }
.la-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 12px;
  flex: 1 1 240px;
  min-width: 0;
  font-size: 12.5px;
  color: var(--muted, #5a6673);
}
.la-field--sm { flex: 0 1 200px; }
.la-field input {
  padding: 9px 12px;
  border: 1px solid var(--line, #e2e8e5);
  border-radius: 8px;
  font-size: 14px;
  color: var(--ink, #0f1721);
  background: #fff;
}
.la-check {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  font-size: 13px;
  color: var(--muted, #5a6673);
}
.la-check input { width: auto; min-height: auto; }
.la-field--area { flex: 1 1 100%; }
.la-count { color: var(--muted, #8a97a4); font-style: normal; }
.la-field--area textarea {
  width: 100%;
  min-height: 430px;
  padding: 12px 14px;
  border: 1px solid var(--line, #e2e8e5);
  border-radius: 10px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13.5px;
  line-height: 1.85;
  color: var(--ink, #0f1721);
  background: #fff;
  resize: vertical;
}
.la-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
.la-note { color: var(--muted, #8a97a4); font-size: 12px; }
</style>
