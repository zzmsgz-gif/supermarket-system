<script setup>
// 收货地址卡片
//
// ⚠️ 「选择并使用」与「编辑」是两个不同动作，各自一个按钮（2026-10-09）：
// 之前只有「选择」，用户想改个门牌号只能删了重填，或者干脆新增一条重复的地址。
// 「删除」独立成按钮并由父组件做二次确认 —— 删除不可恢复，不能和别的操作混在一格里。
const props = defineProps({
  address: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  /** 正在被编辑的那条：卡片上标出来，用户知道正在改谁 */
  editing: { type: Boolean, default: false },
});

const emit = defineEmits(['select', 'edit', 'remove', 'set-default']);

const fullText = () => {
  const a = props.address;
  return `${a.province || ''}${a.city || ''}${a.district || ''}${a.detailAddress || ''}`;
};
</script>

<template>
  <div
    class="addr-card"
    :class="{ selected, editing }"
  >
    <div class="addr-card-top">
      <strong class="addr-name">{{ address.receiverName }}</strong>
      <span class="addr-phone">{{ address.receiverPhone }}</span>
      <span v-if="address.isDefault" class="addr-badge">默认</span>
      <span v-if="editing" class="addr-badge addr-badge-edit">编辑中</span>
    </div>
    <p class="addr-text">{{ fullText() }}</p>
    <div class="addr-card-actions">
      <button class="ghost sm" @click="emit('select', address)">
        {{ selected ? '已选择' : '选择并使用' }}
      </button>
      <button class="ghost sm" @click="emit('edit', address)">编辑</button>
      <button
        v-if="!address.isDefault"
        class="ghost sm"
        @click="emit('set-default', address)"
      >设为默认</button>
      <button class="ghost sm danger" @click="emit('remove', address)">删除</button>
    </div>
  </div>
</template>