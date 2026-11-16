<template>
  <!--
    申请退款 / 申请售后的原因选择弹层。
    为什么不用 uni.showModal({editable:true})：那样只能让用户手打一段文字，
    而 PC 端是「先选一个标准原因，再可选补充」—— 标准原因对商家审核、
    对后端统计都更有价值，也避免用户随手打个"不知道"。
  -->
  <view class="as-mask" v-if="visible" @click="onClose">
    <view class="as-sheet" @click.stop>
      <view class="as-head">
        <text class="as-title">{{ title }}</text>
        <text class="as-close" @click="onClose">✕</text>
      </view>

      <text class="as-hint">提交后需要商家审核，审核通过后款项按原支付方式退回</text>

      <scroll-view scroll-y class="as-body">
        <view class="as-group">请选择原因</view>
        <view
          v-for="r in reasons"
          :key="r"
          class="as-opt"
          :class="{ on: reason === r }"
          @click="reason = r"
        >
          <text class="as-dot">{{ reason === r ? '●' : '○' }}</text>
          <text class="as-opt-text">{{ r }}</text>
        </view>

        <view class="as-group">补充说明（选填）</view>
        <textarea
          class="as-textarea"
          v-model="remark"
          placeholder="可以补充具体情况，方便商家更快处理"
          placeholder-class="as-ph"
          maxlength="200"
          :auto-height="true"
        />
      </scroll-view>

      <view class="as-foot">
        <view class="as-btn ghost" @click="onClose">再想想</view>
        <view class="as-btn primary" :class="{ disabled: !reason || submitting }" @click="onSubmit">
          {{ submitting ? '提交中…' : '提交申请' }}
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '申请退款' },
  reasons: { type: Array, default: () => [] },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'submit'])

const reason = ref('')
const remark = ref('')

// 每次打开都重置，避免上一次选的原因/备注残留到下一次（尤其跨订单时很误导）
watch(
  () => props.visible,
  (v) => {
    if (v) {
      reason.value = ''
      remark.value = ''
    }
  }
)

function onClose() {
  emit('close')
}

function onSubmit() {
  if (!reason.value || props.submitting) return
  emit('submit', { reason: reason.value, remark: remark.value.trim() })
}
</script>

<style scoped>
.as-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 1000;
}
.as-sheet {
  width: 100%;
  max-height: 78vh;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding-bottom: env(safe-area-inset-bottom);
}
.as-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx 12rpx;
}
.as-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1F2937;
}
.as-close {
  font-size: 30rpx;
  color: #9CA3AF;
  padding: 0 8rpx;
}
.as-hint {
  display: block;
  padding: 0 32rpx 16rpx;
  font-size: 22rpx;
  color: #A08A6A;
  line-height: 1.5;
}
.as-body {
  flex: 1;
  min-height: 0;
  padding: 0 32rpx;
}
.as-group {
  margin: 12rpx 0 12rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #6B7280;
}
.as-opt {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 22rpx 20rpx;
  margin-bottom: 12rpx;
  border: 1rpx solid #EDE8DD;
  border-radius: 14rpx;
  background: #FDFCF9;
}
.as-opt.on {
  border-color: #0F6E56;
  background: #F1F8F5;
}
.as-dot {
  font-size: 26rpx;
  color: #0F6E56;
}
.as-opt-text {
  font-size: 27rpx;
  color: #1F2937;
}
.as-textarea {
  width: 100%;
  min-height: 120rpx;
  padding: 18rpx;
  box-sizing: border-box;
  border: 1rpx solid #EDE8DD;
  border-radius: 14rpx;
  background: #FDFCF9;
  font-size: 26rpx;
  color: #1F2937;
  line-height: 1.6;
}
.as-ph {
  color: #B8B2A6;
}
.as-foot {
  display: flex;
  gap: 18rpx;
  padding: 20rpx 32rpx 24rpx;
  border-top: 1rpx solid #F5F2EC;
}
.as-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  border-radius: 42rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.as-btn.ghost {
  background: #F5F2EC;
  color: #6B7280;
}
.as-btn.primary {
  background: #0F6E56;
  color: #fff;
}
.as-btn.primary.disabled {
  background: #C9D6D1;
  color: #FFFFFF;
}
</style>
