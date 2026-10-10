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

/** 是否「每日可领」券（2026-10-10） */
const isDaily = computed(() => Number(props.coupon.claimType || 0) === 1);

/**
 * 领取按钮文案。
 * 每日券今天领过了要明确说「明天还能来」——
 * 只写「已领取」的话，用户会以为这辈子都领不到了。
 */
const claimLabel = computed(() => {
  if (!props.coupon.receivedByCurrentUser) return '立即领取';
  return isDaily.value ? '今日已领，明天再来' : '已领取';
});
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
      <!-- 每日可领券的标识与状态（2026-10-10）。
           后端对 claimType=1 的券把 receivedByCurrentUser 取成「今天领没领过」，
           所以这里能直接复用同一个禁用态，只是文案要说清楚「明天还能来」。 -->
      <small v-if="isDaily" class="coupon-meta coupon-daily-tag">每日可领</small>
      <small class="coupon-meta">已领 {{ coupon.receivedCount }}{{ coupon.totalCount ? ` / ${coupon.totalCount}` : ' / 不限量' }}</small>
    </div>
    <button
      class="coupon-action"
      :disabled="coupon.receivedByCurrentUser"
      @click="emit('receive', coupon.id)"
    >
      {{ claimLabel }}
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
