<template>
  <view class="wallet">
    <view class="balance-card">
      <view class="b-lab">账户余额（元）</view>
      <view class="b-num">¥{{ balance }}</view>
      <button class="recharge" @click="doRecharge">充值</button>
    </view>

    <view class="list-title">交易明细</view>

    <!-- 收支汇总：与 PC 金额明细页调同一个接口（GET /wallet/summary），
         让用户一眼看到「一共花出去多少、收回来多少」，不用自己翻列表加。 -->
    <view class="sum-card">
      <view class="sum-item">
        <text class="sum-lab">累计支出</text>
        <text class="sum-num out">¥{{ summary.out }}</text>
      </view>
      <view class="sum-item">
        <text class="sum-lab">累计收入</text>
        <text class="sum-num in">¥{{ summary.income }}</text>
      </view>
    </view>

    <view
      v-for="t in txns"
      :key="t.id"
      class="tx"
    >
      <view class="tx-left">
        <view class="tx-type">{{ txLabel(t.type) }}</view>
        <!-- 后端 remark 是财务对账用的内部备注（英文），这里翻成中文再显示 -->
        <view class="tx-remark" v-if="remarkZh(t.remark)">{{ remarkZh(t.remark) }}</view>
        <!-- 退款时「已花掉、收不回」的发放积分会按 100 分 = 1 元从退款现金里等价扣回。
             不说明的话，用户看到「退款 +¥78.21」对不上自己算的 ¥79.00，只会以为少退了钱。
             入账金额是**净额**，原应退 = amount + pointsDeducted（与 PC 金额明细同口径）。 -->
        <view class="tx-deduct" v-if="t.type === 'REFUND' && Number(t.pointsDeducted || 0) > 0">
          已花积分等价扣回 ¥{{ Number(t.pointsDeducted).toFixed(2) }}（原应退 ¥{{ origRefund(t).toFixed(2) }}）
        </view>
        <view class="tx-time">{{ formatTime(t.createdAt) }}</view>
      </view>
      <view class="tx-right">
        <view class="tx-amt" :class="isOut(t) ? 'out' : 'in'">
          {{ isOut(t) ? '-' : '+' }}¥{{ Math.abs(Number(t.amount || 0)).toFixed(2) }}
        </view>
        <view class="tx-balance" v-if="t.balanceAfter != null">余额 ¥{{ Number(t.balanceAfter).toFixed(2) }}</view>
      </view>
    </view>

    <EmptyState
      v-if="!loading && !txns.length"
      icon="💳"
      title="暂无交易记录"
      desc="充值或下单支付后，每一笔余额变动都会记在这里"
    />
    <view class="loading" v-if="loading">加载中…</view>
    <view class="more" v-else-if="hasMore" @click="loadMore">点击加载更多</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow, onReachBottom } from '@dcloudio/uni-app'
import { getWallet, getWalletSummary, recharge, listWalletTransactions } from '@/api/wallet'
import { formatTime } from '@/utils/format'
import EmptyState from '@/components/EmptyState/EmptyState.vue'

const balance = ref('0.00')
const txns = ref([])
const page = ref(0)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)
const summary = ref({ out: '0.00', income: '0.00' })

/**
 * 交易类型中文名。后端 WalletService 只有三个常量：RECHARGE / PAYMENT / REFUND。
 *
 * ⚠️ 原来映射写的是 PAY / ORDER_PAY / POINT_REDEEM —— 都是猜的名字，真正的 `PAYMENT`
 * 没命中，于是界面上直接把英文 "PAYMENT" 怼出来（2026-11-21 用户反馈截图）。
 * 未知类型一律回落到「交易」，不再暴露英文原始值。
 */
function txLabel(type) {
  const map = { RECHARGE: '充值', PAYMENT: '支付', REFUND: '退款' }
  return map[type] || '交易'
}

/**
 * 是否支出。
 *
 * ⚠️ 后端 wallet_transaction.amount 存的是**绝对值**（恒为正），方向由 type 决定，
 * 接口已经把它透成 `direction`（1 = 支出，-1 = 收入）。
 * 原来这里用 `Number(amount) >= 0` 判正负 —— amount 永远 ≥ 0，结果每一笔都显示绿色「+」，
 * 连支付也是加号（截图里 PAYMENT 显示 +¥20.90 就是这个 bug）。
 */
function isOut(t) {
  return Number(t.direction) === 1
}

/**
 * 退款行的「原应退金额」= 入账净额 + 已花积分等价扣回额。
 * refundAmount 落库时已经是**扣过积分缺口**的净额，所以不能直接拿它当「应退」给用户看 ——
 * 用户自己算的是「商品 79.00 全退」，看到 78.21 只会以为平台少退了。
 */
function origRefund(t) {
  return Number(t.amount || 0) + Number(t.pointsDeducted || 0)
}

/**
 * 后端 remark 是给财务对账用的内部备注，内容是英文
 * （User recharge / Order payment / Refund approved…），夹在中文界面里既看不懂也没信息量。
 * 这里映射成中文；**没命中映射且含英文的一律不显示**，
 * 以后后端再加新文案也不会漏出英文（宁可少一行，不要露英文）。
 */
const REMARK_ZH = {
  'User recharge': '余额充值',
  '充值订单到账': '充值到账',
  'Order payment': '订单支付',
  'Order cancel refund': '取消订单退回',
  'Admin order cancel refund': '平台取消订单退回',
  'Refund approved': '退款到账',
  'Refund approved (item)': '部分商品退款到账'
}

function remarkZh(r) {
  if (!r) return ''
  const hit = REMARK_ZH[r]
  if (hit) return hit
  return /[A-Za-z]{3,}/.test(r) ? '' : r
}

async function loadSummary() {
  try {
    const s = await getWalletSummary()
    summary.value = {
      out: Number((s && s.totalOut) || 0).toFixed(2),
      income: Number((s && s.totalIncome) || 0).toFixed(2)
    }
  } catch (e) {
    // 汇总失败不影响明细列表
  }
}

async function loadWallet() {
  try {
    const w = await getWallet()
    balance.value = Number((w && w.balance) || 0).toFixed(2)
  } catch (e) {
    balance.value = '0.00'
  }
}

async function loadTxns(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 0
    txns.value = []
  }
  loading.value = true
  try {
    const res = await listWalletTransactions({ page: page.value + 1, size: size.value })
    const items = (res && res.items) ? res.items : []
    total.value = (res && res.total) || 0
    txns.value = page.value === 0 ? items : txns.value.concat(items)
    page.value += 1
    hasMore.value = txns.value.length < total.value
  } catch (e) {
    uni.showToast({ title: (e && e.message) || '加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

// editable 弹窗的占位文案。⚠️ 必须放 placeholderText，不能放 content ——
// uni.showModal 在 editable:true 时会把 content 当作**输入框初始值**（而不是提示），
// 用户点进去就看到"输入充值金额（元）"这几个字，得先手动按退格删掉才能输入。
// （2026-11-15 用户反馈「这种提示文字在点击后不应该还留在那里」）
const RECHARGE_PLACEHOLDER = '输入充值金额（元），如 100'

function doRecharge() {
  uni.showModal({
    title: '充值',
    // 留空：内容由用户输入
    content: '',
    editable: true,
    placeholderText: RECHARGE_PLACEHOLDER,
    success: async (r) => {
      if (!r.confirm) return
      const raw = String(r.content || '').trim()
      // H5 下没输入时 res.content 会回填 placeholderText（这个坑之前踩过），要排除
      if (!raw || raw === RECHARGE_PLACEHOLDER) {
        uni.showToast({ title: '请输入充值金额', icon: 'none' })
        return
      }
      const amt = Number(raw)
      if (!amt || amt <= 0) {
        uni.showToast({ title: '金额无效', icon: 'none' })
        return
      }
      uni.showLoading({ title: '充值中' })
      try {
        await recharge({ amount: amt })
        uni.hideLoading()
        uni.showToast({ title: '充值成功', icon: 'success' })
        loadWallet()
        loadTxns(true)
      } catch (e) {
        uni.hideLoading()
        uni.showToast({ title: (e && e.message) || '充值失败', icon: 'none' })
      }
    }
  })
}

onShow(() => {
  loadWallet()
  loadSummary()
  loadTxns(true)
})
onReachBottom(() => {
  if (hasMore.value) loadTxns()
})
</script>

<style scoped>
.wallet {
  min-height: 100vh;
  background: #f5f5f5;
}
.balance-card {
  background: linear-gradient(135deg, #0F6E56, #05a050);
  color: #fff;
  padding: 48rpx 32rpx;
  text-align: center;
}
.b-lab {
  font-size: 24rpx;
  opacity: 0.9;
}
.b-num {
  font-size: 56rpx;
  font-weight: bold;
  margin-top: 12rpx;
}
.recharge {
  display: inline-block;
  margin-top: 24rpx;
  background: #fff;
  color: #0F6E56;
  font-size: 26rpx;
  padding: 8rpx 40rpx;
  border-radius: 40rpx;
  line-height: 1.6;
}
.list-title {
  font-size: 26rpx;
  color: #999;
  padding: 24rpx 32rpx 8rpx;
}
/* ===== 收支汇总（/wallet/summary）：支出红、收入绿，与列表颜色一致 ===== */
.sum-card {
  display: flex;
  margin: 8rpx 16rpx 16rpx;
  padding: 24rpx 8rpx;
  background: #fff;
  border-radius: 16rpx;
}
.sum-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.sum-lab {
  font-size: 22rpx;
  color: #8A8578;
}
.sum-num {
  margin-top: 8rpx;
  font-size: 32rpx;
  font-weight: 600;
}
.sum-num.out {
  color: #C0392B;
}
.sum-num.in {
  color: #0F6E56;
}
.tx {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 0 16rpx 2rpx;
  padding: 20rpx 24rpx;
}
.tx-left {
  flex: 1;
  min-width: 0;
}
.tx-type {
  font-size: 28rpx;
  font-weight: bold;
}
.tx-remark {
  font-size: 22rpx;
  color: #999;
  margin-top: 6rpx;
}
/* 「已花积分等价扣回」——用暖色小字，跟普通备注区分开：这是一条解释钱为什么变少的说明 */
.tx-deduct {
  font-size: 22rpx;
  line-height: 1.45;
  color: #B7791F;
  margin-top: 6rpx;
}
.tx-time {
  font-size: 22rpx;
  color: #999;
  margin-top: 6rpx;
}
.tx-amt {
  font-size: 30rpx;
  font-weight: bold;
}
/* 右侧两行：金额 + 变动后余额（让用户自己核对「这笔之后还剩多少」，PC 端已有） */
.tx-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.tx-balance {
  margin-top: 6rpx;
  font-size: 20rpx;
  color: #B4AEA2;
}
.tx-amt.in {
  color: #0F6E56;
}
.tx-amt.out {
  color: #C0392B;
}
.empty {
  text-align: center;
  color: #999;
  padding: 120rpx 0;
}
.loading,
.more {
  text-align: center;
  color: #999;
  font-size: 24rpx;
  padding: 24rpx 0;
}
</style>
