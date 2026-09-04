<template>
  <div class="img-upload">
    <div class="iu-list">
      <div v-for="(url, i) in previews" :key="i" class="iu-thumb">
        <img :src="url" alt="图片预览" />
        <button type="button" class="iu-del" @click="removeAt(i)" title="移除">×</button>
      </div>
      <label v-if="canAdd" class="iu-add" :class="{ 'iu-busy': uploading }">
        <input
          type="file"
          accept="image/*"
          :multiple="multiple"
          :disabled="uploading"
          @change="onPick"
          hidden
        />
        <span v-if="uploading">上传中…</span>
        <span v-else>＋ 选择图片</span>
      </label>
    </div>
    <p v-if="error" class="iu-error">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { api } from '../api/client';

const props = defineProps({
  modelValue: { type: [String, Array], default: '' },
  multiple: { type: Boolean, default: false },
  type: { type: String, default: 'misc' },
  max: { type: Number, default: 9 },
});
const emit = defineEmits(['update:modelValue']);

const uploading = ref(false);
const error = ref('');

const list = computed(() =>
  props.multiple
    ? (Array.isArray(props.modelValue) ? props.modelValue : [])
    : (props.modelValue ? [props.modelValue] : [])
);
const previews = list;
const canAdd = computed(() =>
  props.multiple ? list.value.length < props.max : list.value.length === 0
);

function commit(arr) {
  if (props.multiple) emit('update:modelValue', arr);
  else emit('update:modelValue', arr.length ? arr[0] : '');
}

function removeAt(i) {
  const arr = [...list.value];
  arr.splice(i, 1);
  commit(arr);
}

async function onPick(e) {
  const files = Array.from(e.target.files || []);
  if (!files.length) return;
  const MAX = 10 * 1024 * 1024;
  if (files.some(f => f.size > MAX)) {
    error.value = '每张图片大小不能超过 10MB，请压缩后重试';
    e.target.value = '';
    return;
  }
  error.value = '';
  uploading.value = true;
  try {
    const urls = [];
    for (const f of files) {
      const fd = new FormData();
      fd.append('file', f);
      fd.append('type', props.type);
      const data = await api.post('/files/upload', fd);
      urls.push(data.url);
    }
    commit([...list.value, ...urls]);
  } catch (err) {
    error.value = err?.message || '上传失败，请重试';
  } finally {
    uploading.value = false;
    e.target.value = '';
  }
}
</script>
