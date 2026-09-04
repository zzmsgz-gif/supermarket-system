<script setup>
// 收货地址卡片
const props = defineProps({
  address: { type: Object, required: true },
  selected: { type: Boolean, default: false },
});

const emit = defineEmits(['select']);

const fullText = () => {
  const a = props.address;
  return `${a.province || ''}${a.city || ''}${a.district || ''}${a.detailAddress || ''}`;
};
</script>

<template>
  <div
    class="addr-card"
    :class="{ selected }"
  >
    <div class="addr-card-top">
      <strong class="addr-name">{{ address.receiverName }}</strong>
      <span class="addr-phone">{{ address.receiverPhone }}</span>
      <span v-if="address.isDefault" class="addr-badge">默认</span>
    </div>
    <p class="addr-text">{{ fullText() }}</p>
    <div class="addr-card-actions">
      <button class="ghost sm" @click="emit('select', address)">
        {{ selected ? '已选择' : '选择并使用' }}
      </button>
    </div>
  </div>
</template>
