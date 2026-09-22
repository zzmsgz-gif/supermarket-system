<script setup>
// 后台「补货/入库」表单行：从 AdminPanel.vue 抽出来的共用件。
//
// 为什么必须先抽它：「库存预警」和「商品管理」两个面板各写了一段几乎相同的补货行（<tr>），
// 差异只有 4 处（目标对象、colspan、文案、submit 的参数）—— 这也正是商品面板一直没法单独拆出去的原因：
// 补货表单是跨模块共用的，谁都不能独占。
//
// ⚠️ `form` 由父级持有（stockForm 是共享状态），本组件只负责渲染与发事件；
//    这里直接改 form 的属性（不是整体替换 props），与原来直接改 stockForm 的行为完全一致。
defineProps({
  form: { type: Object, required: true },
  target: { type: Object, required: true },
  colspan: { type: Number, required: true },
  label: { type: String, default: '补货数量' },
});
const emit = defineEmits(['submit', 'cancel']);
</script>

<template>
  <tr v-if="form.productId === target.id" class="row-extra-tr">
    <td :colspan="colspan">
      <div class="row-extra">
        <span class="extra-label">{{ label }}</span>
        <input v-model.number="form.quantity" type="number" min="1" step="1" class="qty-input" />
        <button type="button" class="chip" @click="form.quantity = 10">+10</button>
        <button type="button" class="chip" @click="form.quantity = 50">+50</button>
        <button type="button" class="chip" @click="form.quantity = 100">+100</button>
        <input v-model="form.remark" placeholder="备注（选填，如：供应商补货）" class="remark-input" />
        <button @click="emit('submit')">确认入库</button>
        <button class="ghost" @click="emit('cancel')">取消</button>
      </div>
    </td>
  </tr>
</template>
