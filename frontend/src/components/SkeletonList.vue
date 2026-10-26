<template>
  <!-- 列表行骨架屏：缩略图(或圆形头像) + 多行文本 + 可选尾部块(价格/状态)。
       用在订单/收藏/优惠券/消息/积分流水等"行式"列表的首屏加载。
       ⚠️ 纯装饰、aria-hidden；显隐由调用方的 loading 决定。 -->
  <div class="sk-list" aria-hidden="true">
    <div v-for="i in count" :key="i" class="sk-row">
      <div v-if="thumb" class="sk-thumb sk-shimmer"></div>
      <div v-else-if="avatar" class="sk-avatar sk-shimmer"></div>
      <div class="sk-row-lines">
        <div
          v-for="j in lines"
          :key="j"
          class="sk-line sk-shimmer"
          :style="{ width: rowLineWidth(j) }"
        ></div>
      </div>
      <div v-if="tail" class="sk-tail sk-shimmer"></div>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  count: { type: Number, default: 6 },     // 显示几行
  thumb: { type: Boolean, default: true }, // 方形缩略图（订单/收藏的商品图）
  avatar: { type: Boolean, default: false }, // 圆形头像（消息/积分等无图场景）
  lines: { type: Number, default: 2 },     // 每行文本条数
  tail: { type: Boolean, default: false }, // 右侧尾部块（金额/状态）
});

// 行内第一行当"标题"宽一点，末行收短，中间满宽
function rowLineWidth(j) {
  if (j === 1) return '68%';
  if (j === props.lines) return '42%';
  return '88%';
}
</script>
