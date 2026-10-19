<script setup>
// 后台「首页氛围」面板：切换首页 hero 区的节日装饰（日常 / 中秋 / 关闭）。
// 配置走后端 system_config.festive_theme；切完即时本地预览，保存即落库、前台即时生效。

import { ref, onMounted } from 'vue';
import { api } from '../api/client';
import { FESTIVE_THEMES, currentFestiveTheme, setFestiveTheme } from '../festive';

const props = defineProps({
  adminCtx: { type: Object, required: true },
});
const { run, fail, hint } = props.adminCtx;

const KEY = 'festive_theme';
const selected = ref('daily');
const saving = ref(false);

async function load() {
  await run(async () => {
    const res = await api.get(`/system-config?key=${KEY}`);
    const value = (res && res.configValue) || 'daily';
    selected.value = value;
    setFestiveTheme(value); // 打开面板即按库里的值预览，所见即所得
  });
}

async function save(value) {
  if (saving.value) return;
  selected.value = value;
  setFestiveTheme(value); // 先本地即时预览，不等后端
  saving.value = true;
  try {
    const found = FESTIVE_THEMES.find((t) => t.value === value);
    await api.put(`/admin/system-config/${KEY}`, {
      value,
      description: found ? found.desc : '',
    });
    hint(`已切换为「${found ? found.label : value}」，前台即时生效`);
  } catch (err) {
    // 失败：把预览回退到库里当前值，避免界面显示与库里不一致
    await load();
    fail(err.message || '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}

onMounted(load);
defineExpose({ load });
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <span class="tag muted">首页 hero 区的节日装饰，切换后前台即时生效（日常 / 中秋 / 关闭）</span>
    </div>

    <div class="form-card admin-form-card">
      <div class="form-title">
        <span>选择首页氛围</span>
        <small>「日常」是常年默认；「中秋」是节日限定，过完节切回「日常」或「关闭」即可。
          装饰层不挡任何点击；系统开了「减少动效」偏好时会自动关闭飘落 / 上浮动画。</small>
      </div>

      <div class="festive-options">
        <label
          v-for="t in FESTIVE_THEMES"
          :key="t.value"
          class="festive-option"
          :class="{ on: selected === t.value, disabled: saving }"
        >
          <input
            type="radio"
            name="festive-theme"
            :value="t.value"
            :checked="selected === t.value"
            :disabled="saving"
            @change="save(t.value)"
          />
          <span class="fo-main">
            <span class="fo-label">{{ t.label }}</span>
            <span class="fo-desc">{{ t.desc }}</span>
          </span>
        </label>
      </div>

      <p class="festive-current">
        当前生效：<b>{{ (FESTIVE_THEMES.find((t) => t.value === currentFestiveTheme) || {}).label || currentFestiveTheme }}</b>
        <span class="muted">（含未保存的本地预览）</span>
      </p>
    </div>
  </div>
</template>
