<template>
<section class="data-panel points-page">
  <!-- 会员概览卡片 -->
  <div class="member-overview">
    <div class="mo-level">
      <span class="mo-badge" :class="'lv' + (session.user?.memberLevel || 0)">{{ tierNameFor(session.user?.memberLevel) }}</span>
      <div class="mo-meta">
        <strong>{{ memberProfile.points }} 分</strong>
        <small>累计消费 {{ money(memberProfile.totalSpent) }}</small>
      </div>
    </div>
    <div class="mo-perks">
      <div class="mo-perk">
        <span>当前折扣</span>
        <strong>{{ memberProfile.discountRate < 1 ? (memberProfile.discountRate * 10).toFixed(1) + ' 折' : '无' }}</strong>
      </div>
      <div class="mo-perk">
        <span>积分价值</span>
        <strong>100 分 = ¥1</strong>
      </div>
    </div>
  </div>

  <!-- 会员日：哪天翻倍由后台「会员日」菜单配置（原先公告里那句「每月18号双倍」，后端其实并没实现） -->
  <div class="member-day-tip" v-if="memberDay.enabled">
    <span class="mdt-badge">会员日</span>
    <span class="mdt-text">{{ memberDayText }}</span>
    <small v-if="memberDayNextText" class="mdt-next">{{ memberDayNextText }}</small>
  </div>

  <!-- 升级进度 -->
  <div class="member-progress" v-if="memberProfile.nextLevelName">
    <div class="mp-head">
      <span>距 <b>{{ memberProfile.nextLevelName }}</b> 还需消费 {{ money(memberProfile.nextLevelThreshold - memberProfile.totalSpent) }}</span>
      <small>{{ Math.round(memberProfile.progressToNext * 100) }}%</small>
    </div>
    <div class="mp-bar"><i :style="{ width: Math.max(4, Math.round(memberProfile.progressToNext * 100)) + '%' }"></i></div>
  </div>
  <div class="member-progress done" v-else>
    <span>已达到最高等级 🎉</span>
  </div>

  <!-- 等级体系 -->
  <div class="tier-grid">
    <div v-for="t in memberLevels" :key="t.level" class="tier-card" :class="{ on: t.level === (session.user?.memberLevel || 0) }">
      <span class="tier-name" :class="'lv' + t.level">{{ t.name }}</span>
      <small class="tier-th">累计消费 ¥{{ t.threshold }}</small>
      <strong class="tier-rate">{{ t.rate < 1 ? (t.rate * 10).toFixed(1) + ' 折' : '无折扣' }}</strong>
    </div>
  </div>

  <!-- 积分流水 -->
  <div class="ledger-block">
    <div class="ledger-head">
      <h3>积分明细</h3>
      <button class="ghost sm" @click="loadMemberLedger(1)" :disabled="memberLedger.loading">刷新</button>
    </div>
    <empty-state v-if="!memberLedger.items.length && !memberLedger.loading" icon="star" text="还没有积分变动记录，下单可得积分哦" />
    <ul v-else class="ledger-list">
      <li v-for="row in memberLedger.items" :key="row.id" class="ledger-row">
        <span class="lr-type" :class="'t-' + row.type">{{ ledgerLabel(row.type) }}</span>
        <div class="lr-main">
          <strong>{{ row.remark || ledgerLabel(row.type) }}</strong>
          <small v-if="row.refOrderId">订单 #{{ row.refOrderId }}</small>
          <small>{{ formatDate(row.createdAt) }}</small>
        </div>
        <span class="lr-amount" :class="row.type === 'REDEEM' ? 'minus' : 'plus'">
          {{ row.type === 'REDEEM' ? '-' : '+' }}{{ row.amount }}
        </span>
        <span class="lr-bal">余 {{ row.balanceAfter }}</span>
      </li>
    </ul>
    <button v-if="memberLedger.items.length < memberLedger.total" class="ghost load-more" @click="loadMemberLedger(memberLedger.page + 1)" :disabled="memberLedger.loading">
      {{ memberLedger.loading ? '加载中…' : '加载更多' }}
    </button>
  </div>
</section>
</template>

<script>
import { computed, inject, onMounted, ref } from 'vue';
export default {
  name: 'PointsPage',
  setup() {
    const appCtx = inject('appCtx');
    const ledgerLabel = (type) => ({
      EARN: '消费获得', REDEEM: '积分抵扣', REFUND: '订单回退', ADJUST: '系统调整',
    }[type] || type || '变动');

    // 会员日（每月几号消费积分翻倍）是**公共配置**、但只有本页与结算页用得上，
    // 所以直接在页面里读公开接口，不往 App.vue 那条 60+ 字段的 appCtx 里加东西（改动面越小越安全）。
    const memberDay = ref({ enabled: false, slogan: '', nextDate: null });
    onMounted(async () => {
      try {
        memberDay.value = (await appCtx.api.get('/member-days')) || memberDay.value;
      } catch (err) {
        // 拿不到就不显示这一块，不影响积分页其它内容
      }
    });
    const memberDayNextText = computed(() => {
      const raw = memberDay.value?.nextDate;
      if (!raw) return '';
      const parts = String(raw).split('-');
      if (parts.length < 3) return '';
      return `下次：${Number(parts[1])} 月 ${Number(parts[2])} 日`;
    });
    // 徽标里已经写了「会员日」，正文再去掉重复的开头，读起来是「会员日（每月18号）消费可得双倍积分」
    const memberDayText = computed(() => {
      const slogan = memberDay.value?.slogan || '';
      return slogan.startsWith('会员日') ? slogan.slice(3) : slogan;
    });

    return { ...appCtx, ledgerLabel, memberDay, memberDayText, memberDayNextText };
  }
};
</script>

<style scoped>
.member-day-tip {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  background: #fff7e6; border: 1px solid #f3d9a4; border-radius: 12px;
  padding: 10px 14px; margin-top: 12px; font-size: 14px; color: #7a5417;
}
.mdt-badge {
  min-height: 0; background: #f0a020; color: #fff; border-radius: 999px;
  padding: 2px 10px; font-size: 12px; font-weight: 700;
}
.mdt-next { margin-left: auto; color: #96703a; }
.member-overview {
  display: flex; flex-wrap: wrap; gap: 14px; align-items: center;
  background: linear-gradient(120deg, #f3f8f6, #eaf3ef);
  border: 1px solid #cfe3db; border-radius: 12px; padding: 16px 18px;
}
.mo-level { display: flex; align-items: center; gap: 12px; }
.mo-badge {
  font-size: 13px; font-weight: 800; color: #fff; padding: 6px 12px; border-radius: 999px;
  background: #9aa7a2;
}
.mo-badge.lv1 { background: #8c9bab; }
.mo-badge.lv2 { background: #c8a24b; }
.mo-badge.lv3 { background: #7d6bd6; }
.mo-meta { display: flex; flex-direction: column; line-height: 1.3; }
.mo-meta strong { font-size: 22px; color: var(--brand-deep); }
.mo-meta small { color: var(--muted); }
.mo-perks { display: flex; gap: 22px; margin-left: auto; }
.mo-perk { display: flex; flex-direction: column; align-items: flex-end; }
.mo-perk span { font-size: 12px; color: var(--muted); }
.mo-perk strong { color: var(--ink); }

.member-progress { margin: 16px 2px 4px; }
.member-progress.done { color: var(--brand-deep); font-weight: 700; }
.mp-head { display: flex; justify-content: space-between; font-size: 13px; color: var(--muted); margin-bottom: 6px; }
.mp-head b { color: var(--ink); }
.mp-bar { height: 8px; border-radius: 999px; background: #e6ede9; overflow: hidden; }
.mp-bar i { display: block; height: 100%; background: linear-gradient(90deg, #6fcf97, #2f9e6e); border-radius: 999px; transition: width .3s ease; }

.tier-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 16px 0; }
.tier-card {
  border: 1px solid #e2ebe7; border-radius: 12px; padding: 14px 12px; text-align: center;
  background: #fff; display: flex; flex-direction: column; gap: 6px; align-items: center;
}
.tier-card.on { border-color: var(--brand-deep); box-shadow: 0 0 0 2px rgba(47,158,110,.18); }
.tier-name { font-size: 13px; font-weight: 800; color: #fff; padding: 4px 10px; border-radius: 999px; background: #9aa7a2; }
.tier-name.lv1 { background: #8c9bab; }
.tier-name.lv2 { background: #c8a24b; }
.tier-name.lv3 { background: #7d6bd6; }
.tier-th { color: var(--muted); font-size: 12px; }
.tier-rate { color: var(--brand-deep); }

.ledger-block { margin-top: 8px; }
.ledger-head { display: flex; align-items: center; justify-content: space-between; }
.ledger-head h3 { margin: 10px 0; font-size: 16px; }
.ledger-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.ledger-row {
  display: grid; grid-template-columns: 64px 1fr auto auto; gap: 12px; align-items: center;
  border: 1px solid #eef2f0; border-radius: 10px; padding: 10px 12px; background: #fff;
}
.lr-type { font-size: 12px; font-weight: 700; padding: 3px 0; border-radius: 6px; text-align: center; }
.lr-type.t-EARN { color: #2f9e6e; background: #e7f6ee; }
.lr-type.t-REDEEM { color: #d9534f; background: #fdecec; }
.lr-type.t-REFUND { color: #3b82c4; background: #e8f1fa; }
.lr-type.t-ADJUST { color: #8a8a8a; background: #f0f0f0; }
.lr-main { display: flex; flex-direction: column; line-height: 1.35; min-width: 0; }
.lr-main strong { font-size: 14px; color: var(--ink); }
.lr-main small { color: var(--muted); font-size: 12px; }
.lr-amount { font-weight: 800; font-size: 16px; }
.lr-amount.plus { color: #2f9e6e; }
.lr-amount.minus { color: #d9534f; }
.lr-bal { color: var(--muted); font-size: 12px; }
.load-more { margin: 12px auto 0; display: block; }

@media (max-width: 720px) {
  .tier-grid { grid-template-columns: repeat(2, 1fr); }
  .mo-perks { margin-left: 0; width: 100%; justify-content: space-between; }
  .ledger-row { grid-template-columns: 56px 1fr auto; }
  .lr-bal { display: none; }
}
</style>
