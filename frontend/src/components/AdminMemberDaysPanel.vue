<script setup>
// 后台「会员日」面板：从 AdminPanel.vue 整块搬过来的。
//
// 依赖刻意走显式 props 而不是 adminCtx：这个面板只服务一个 tab，把真正用到的东西列全，
// 一眼就能看出「它为什么需要这些」。接 adminCtx 会让依赖变成隐式的。

const props = defineProps({
  MDC_WEEK: { type: Array, required: true },
  closeMemberDayForm: { type: Function, required: true },
  deleteMemberDay: { type: Function, required: true },
  memberDayCalendarCells: { type: Array, required: true },
  memberDayCalendarMonthText: { type: String, required: true },
  memberDayDateLabel: { type: Function, required: true },
  memberDayDateTaken: { type: Function, required: true },
  memberDayEnabledCount: { type: Number, required: true },
  memberDayForm: { type: Object, required: true },
  memberDayFormOpen: { type: Boolean, required: true },
  memberDaySlogan: { type: String, required: true },
  memberDays: { type: Array, required: true },
  openMemberDayForm: { type: Function, required: true },
  resetMemberDayCalendar: { type: Function, required: true },
  saveMemberDay: { type: Function, required: true },
  shiftMemberDayCalendar: { type: Function, required: true },
  toggleMemberDay: { type: Function, required: true },
});
const { MDC_WEEK, closeMemberDayForm, deleteMemberDay, memberDayCalendarCells, memberDayCalendarMonthText, memberDayDateLabel, memberDayDateTaken, memberDayEnabledCount, memberDayForm, memberDayFormOpen, memberDaySlogan, memberDays, openMemberDayForm, resetMemberDayCalendar, saveMemberDay, shiftMemberDayCalendar, toggleMemberDay } = props;
</script>

<template>
  <div class="data-panel">
    <div class="toolbar">
      <button @click="openMemberDayForm(null)">新增会员日</button>
      <span v-if="memberDayEnabledCount" class="tag muted">生效中 {{ memberDayEnabledCount }} 天 · {{ memberDaySlogan }}</span>
    </div>

    <div v-if="memberDayFormOpen" class="form-card admin-form-card">
      <div class="form-title">
        <span>{{ memberDayForm.id ? '编辑会员日' : '新增会员日' }}</span>
        <small>从日历上挑日期：<b>挑中的那天</b>消费积分按倍率翻倍（2 = 双倍）。
          可以配多天（比如 10 月 1 日、11 月 11 日各配一条），不再按「每月几号」循环。
          已配过的日期会划掉、今天之前的日期不可选。
          公告栏那条「会员日…」是运营文案，<b>由你自己维护</b>，系统不会自动改它。</small>
      </div>
      <div class="admin-form-grid">
        <!-- 挑日期：真日历（可翻月），点一天就是选那个具体日期。
             已配置的日期划掉不可点 / 已过去的日期不可选 —— 免得填完保存才报错。 -->
        <div class="field span-all">
          <span class="field-label">会员日日期 <i class="req">*</i></span>
          <div class="md-calendar">
            <div class="mdc-head">
              <button type="button" class="mdc-nav" aria-label="上个月" @click="shiftMemberDayCalendar(-1)">‹</button>
              <span class="mdc-month">{{ memberDayCalendarMonthText }}</span>
              <button type="button" class="mdc-nav" aria-label="下个月" @click="shiftMemberDayCalendar(1)">›</button>
              <button type="button" class="mdc-today" @click="resetMemberDayCalendar">回到本月</button>
            </div>
            <div class="mdc-week">
              <span v-for="w in MDC_WEEK" :key="w">{{ w }}</span>
            </div>
            <div class="mdc-grid">
              <template v-for="(cell, ci) in memberDayCalendarCells" :key="ci">
                <span v-if="!cell.day" class="mdc-day blank"></span>
                <button v-else type="button" class="mdc-day"
                        :class="{ on: cell.iso === memberDayForm.memberDate, taken: cell.taken, today: cell.today, past: cell.past }"
                        :disabled="cell.taken || cell.past"
                        :title="cell.past ? '已经过去的日期不能配（那天不会再翻倍）'
                                : (cell.taken ? `${cell.label} 已经配置过了` : `选 ${cell.label}`)"
                        @click="memberDayForm.memberDate = cell.iso">{{ cell.day }}</button>
              </template>
            </div>
            <p class="mdc-hint">
              已选：<b>{{ memberDayForm.memberDate ? memberDayDateLabel(memberDayForm.memberDate) : '还没选' }}</b>
              <span v-if="memberDayDateTaken(memberDayForm.memberDate)" class="mdc-warn">这天已经配置过了，请另选</span>
              <span v-else-if="memberDayForm.memberDate">· 当天消费积分按 {{ memberDayForm.multiplier || 2 }} 倍发放</span>
            </p>
          </div>
        </div>
        <label class="field">
          <span class="field-label">积分倍率</span>
          <input v-model.number="memberDayForm.multiplier" type="number" min="1" max="10" step="0.5" placeholder="2 = 双倍" />
        </label>
        <label class="field">
          <span class="field-label">备注（仅后台可见）</span>
          <input v-model="memberDayForm.remark" maxlength="60" placeholder="如：超级会员日" />
        </label>
        <div class="field">
          <span class="field-label">是否启用</span>
          <label class="check-line"><input type="checkbox" v-model="memberDayForm.enabled" /> 启用（当天消费积分翻倍）</label>
        </div>
      </div>
      <div class="admin-form-foot">
        <button class="ghost" @click="closeMemberDayForm">取消</button>
        <button @click="saveMemberDay">保存</button>
      </div>
    </div>

    <template v-else>
      <div class="admin-cards" v-if="memberDays.length">
        <div v-for="d in memberDays" :key="d.id" class="admin-card" :class="{ 'is-expired': d.expired }">
          <span class="tag">{{ memberDayDateLabel(d.memberDate) }}</span>
          <div class="card-info">
            <p class="card-title"><span class="card-title-text">积分 ×{{ d.multiplier }}</span></p>
            <p class="card-meta">
              <span>{{ d.remark || '未填备注' }}</span>
              <span v-if="d.expired" class="off-word">已过期</span>
              <span v-else :class="Number(d.enabled) === 1 ? 'on-word' : 'off-word'">{{ Number(d.enabled) === 1 ? '生效中' : '已停用' }}</span>
            </p>
          </div>
          <div class="card-actions">
            <button class="ghost" @click="openMemberDayForm(d)">编辑</button>
            <button class="ghost" @click="toggleMemberDay(d)">{{ Number(d.enabled) === 1 ? '停用' : '启用' }}</button>
            <button class="ghost danger" @click="deleteMemberDay(d)">删除</button>
          </div>
        </div>
      </div>
      <empty-state v-else icon="star" text="还没有会员日，点上方「新增会员日」从日历上挑一天（公告文案由你自己维护，系统不会代写）" />
    </template>
  </div>
</template>
