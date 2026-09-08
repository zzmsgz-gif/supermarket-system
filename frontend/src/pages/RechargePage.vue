<template>
<section class="data-panel recharge-page">
        <div class="recharge-head">
          <div>
            <p>当前余额 <strong>{{ money(wallet.balance) }}</strong>，充值即时到账，可用于商城购物。</p>
          </div>
        </div>

        <div v-if="recharge.step !== 'paying'" class="recharge-body">
          <div class="form-block">
            <div class="form-title"><span class="title-bar"></span>充值金额</div>
            <div class="amount-grid">
              <button
                v-for="preset in rechargePresets"
                :key="preset"
                :class="['amount-chip', { active: recharge.amount === preset && !recharge.customAmount }]"
                @click="selectRechargePreset(preset)"
              >{{ money(preset) }}</button>
              <div class="amount-custom" :class="{ active: !!recharge.customAmount }">
                <span>¥</span>
                <input
                  v-model="recharge.customAmount"
                  type="number"
                  min="0.01"
                  step="0.01"
                  placeholder="自定义金额"
                  @input="onCustomAmountInput"
                />
              </div>
            </div>
          </div>

          <div class="form-block">
            <div class="form-title"><span class="title-bar"></span>支付方式</div>
            <div class="method-cards">
              <button
                :class="['method-card', { active: recharge.method === 'ALIPAY' }]"
                @click="recharge.method = 'ALIPAY'"
              >
                <span class="method-icon alipay">支</span>
                <span class="method-name">支付宝</span>
                <small>推荐使用，秒到账</small>
              </button>
              <button
                :class="['method-card', { active: recharge.method === 'WECHAT' }]"
                @click="recharge.method = 'WECHAT'"
              >
                <span class="method-icon wechat">微</span>
                <span class="method-name">微信支付</span>
                <small>扫码即付，安全便捷</small>
              </button>
            </div>
          </div>

          <div class="recharge-action">
            <button class="primary block" @click="confirmRecharge">
              确认充值 {{ money(recharge.amount || 0) }}
            </button>
            <p class="recharge-tip">点击下方按钮即创建一笔充值订单，可在支付页选择「立即支付」或「取消订单」。</p>
          </div>
        </div>

        <!-- 支付浮层：支付中 / 成功 / 超时 -->
        <div v-if="recharge.step === 'paying' || recharge.step === 'success' || recharge.step === 'expired'" class="modal-mask">
          <div class="pay-modal">
            <button class="modal-close" @click="closeRechargeModal">×</button>

            <template v-if="recharge.step === 'success'">
              <div class="pay-success">
                <div class="pay-success-icon">✓</div>
                <h3>充值成功</h3>
                <p>已通过{{ methodLabel(recharge.method) }}为你充值 <strong>{{ money(recharge.order?.amount || 0) }}</strong></p>
                <p class="pay-success-balance">当前余额 {{ money(wallet.balance) }}</p>
                <button class="primary block" @click="backToShop">返回首页</button>
              </div>
            </template>

            <template v-else-if="recharge.step === 'expired'">
              <div class="pay-fail">
                <div class="pay-fail-icon">!</div>
                <h3>订单已超时</h3>
                <p>该充值订单超过有效时间未支付，已自动关闭。</p>
                <button class="primary block" @click="resetRecharge">重新充值</button>
              </div>
            </template>

            <template v-else>
              <div class="pay-head">
                <span class="pay-method" :class="recharge.method === 'ALIPAY' ? 'alipay' : 'wechat'">
                  {{ methodLabel(recharge.method) }}
                </span>
                <h3>向「超市购物系统」付款</h3>
              </div>
              <div class="pay-amount">{{ money(recharge.order?.amount || 0) }}</div>
              <div class="pay-qr">
                <div class="qr-frame" v-html="qrSvg"></div>
                <p>请使用{{ methodLabel(recharge.method) }}扫一扫付款</p>
              </div>
              <div class="pay-countdown">
                支付剩余时间
                <strong :class="{ urgent: recharge.countdown <= 60 }">{{ formatCountdown(recharge.countdown) }}</strong>
              </div>
              <div class="pay-actions">
                <button class="primary block" :disabled="recharge.paying" @click="payRechargeOrder">
                  {{ recharge.paying ? '支付中…' : '立即支付' }}
                </button>
                <button class="ghost block" :disabled="recharge.paying" @click="cancelRechargeOrder">取消订单</button>
              </div>
            </template>
          </div>
        </div>
      </section>
</template>

<script>
import { inject } from 'vue';
export default {
  name: 'RechargePage',
  setup() {
    const appCtx = inject('appCtx');
    return { ...appCtx };
  }
};
</script>
