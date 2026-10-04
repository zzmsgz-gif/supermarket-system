/**
 * 协议 / 隐私正文域（后台可编辑，按 key 拉取）
 *
 * 从 App.vue 抽出（原 1499-1519 行）。协议正文存在后台「内容管理 → 协议与隐私」，
 * 前端按 key（TERMS / PRIVACY）拉取；拉取失败时回落到已缓存的那份，避免点开协议白屏。
 */
import { reactive } from 'vue';
import { api } from '../api/client';

export function useLegalDoc() {
  const legalDocs = reactive({ data: {}, loading: false });

  async function loadLegalDoc(docKey) {
    const key = String(docKey || '').toUpperCase();
    if (!key) return null;
    legalDocs.loading = true;
    try {
      legalDocs.data[key] = await api.get(`/legal-docs/${key}`);
      return legalDocs.data[key];
    } catch (e) {
      return legalDocs.data[key] || null;
    } finally {
      legalDocs.loading = false;
    }
  }

  // 注册弹窗里点《用户协议》《隐私政策》：新标签页打开，避免关掉弹窗丢失已填内容
  function openLegal(docKey) {
    window.open(docKey === 'PRIVACY' ? '/privacy' : '/terms', '_blank', 'noopener');
  }

  return { legalDocs, loadLegalDoc, openLegal };
}
