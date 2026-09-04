<script setup>
// 优惠券卡片：支持两种形态
//  - mode="claim" 可领取列表（字段 name / receivedByCurrentUser / receivedCount / totalCount）
//  - mode="mine"  我的优惠券（字段 couponName / usable / unusableReason）
import { computed } from 'vue';
import { money, formatDate } from '../utils/format.js';

const props = defineProps({
  coupon: { type: Object, required: true },
  mode: { type: String, default: 'mine' }, // 'claim' | 'mine'
});

const emit = defineEmits(['receive']);

const name = computed(() => props.coupon.name || props.coupon.couponName || '优惠券');
const endTimeLabel = computed(() => formatDate(props.coupon.endTime));
const isClaim = computed(() => props.mode === 'claim');
</script>

<template>
  <div
    v-if="isClaim"
    class="coupon-ticket"
    :class="{ claimed: coupon.receivedByCurrentUser }"
  >
    <div class="coupon-left">
      <div class="coupon-amount"><span class="yen">¥</span>{{ coupon.discountAmount }}</div>
      <div class="coupon-threshold">满 {{ coupon.thresholdAmount }} 可用</div>
    </div>
    <div class="coupon-body">
      <strong class="coupon-name">{{ name }}</strong>
      <small class="coupon-meta">{{ endTimeLabel }} 前有效</small>
      <small class="coupon-meta">已领 {{ coupon.receivedCount }}{{ coupon.totalCount ? ` / ${coupon.totalCount}` : ' / 不限量' }}</small>
    </div>
    <button
      class="coupon-action"
      :disabled="coupon.receivedByCurrentUser"
      @click="emit('receive', coupon.id)"
    >
      {{ coupon.receivedByCurrentUser ? '已领取' : '立即领取' }}
    </button>
  </div>

  <div
    v-else
    class="coupon-ticket"
    :class="coupon.usable ? 'usable' : 'disabled'"
  >
    <div class="coupon-left">
      <div class="coupon-amount"><span class="yen">¥</span>{{ coupon.discountAmount }}</div>
      <div class="coupon-threshold">满 {{ coupon.thresholdAmount }} 可用</div>
    </div>
    <div class="coupon-body">
      <strong class="coupon-name">{{ name }}</strong>
      <small class="coupon-meta">{{ endTimeLabel }} 前有效</small>
      <small class="coupon-meta">{{ coupon.usable ? '结算时自动可用' : (coupon.unusableReason || '暂不可用') }}</small>
    </div>
    <span class="coupon-tag">{{ coupon.usable ? '可使用' : (coupon.unusableReason || '不可用') }}</span>
  </div>
</template>
