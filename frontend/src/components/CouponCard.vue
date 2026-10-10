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
  // 「今日已领，明天再来」一行 7 个字太宽，会把卡片 body 挤到竖排（截图反馈）。
  // 改两行：按钮 white-space:pre-line，换行符生效。
  return isDaily.value ? '今日已领\n明天再来' : '已领取';
});

/** 领取进度百分比（0~100，封顶 100 防御脏数据） */
const claimPercent = computed(() => {
  const total = Number(props.coupon.totalCount || 0);
  if (total <= 0) return 0;
  return Math.min(100, Math.round((Number(props.coupon.receivedCount || 0) * 100) / total));
});

/**
 * 进度条里的分母文案：超 1 万缩写成「x.x万」。
 * 「已抢 0/99999」七个字符在小卡片里会把进度条文字挤爆。
 */
const totalCountLabel = computed(() => {
  const total = Number(props.coupon.totalCount || 0);
  return total >= 10000 ? `${(total / 10000).toFixed(1)}万` : String(total);
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
      <!-- 2026-10-10 布局重排：「每日可领」从独立一行挪进名字行（原先单独占行
           把卡片撑高、和进度条挤在一起）；名字单行省略 —— 卡片窄时会竖排成一字一行。 -->
      <strong class="coupon-name">
        {{ name }}<small v-if="isDaily" class="coupon-daily-tag">每日可领</small>
      </strong>
      <small class="coupon-meta">{{ endTimeLabel }} 前有效</small>
      <div v-if="coupon.totalCount" class="coupon-progress" :title="`已领 ${coupon.receivedCount} / ${coupon.totalCount} 张`">
        <i :style="{ width: claimPercent + '%' }"></i>
        <span>已抢 {{ coupon.receivedCount }}/{{ totalCountLabel }}</span>
      </div>
      <small v-else class="coupon-meta">已抢 {{ coupon.receivedCount }} / 不限量</small>
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
