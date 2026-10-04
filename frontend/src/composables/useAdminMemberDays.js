/**
 * 后台「会员日」（指定日期消费积分翻倍）
 *
 * 从 AdminPanel.vue 抽出（state 原 1906-1935、函数原 1937-2095）。
 * 原注释的既定原则保留：状态放本地 composable，**不进 App.vue 那条 60+ 字段的 adminCtx**
 * （那条是「一整行」，漏一个字段就是后台白屏，09-20 踩过）。
 *
 * 日历部分的关键约定（别改）：
 * - `isoDateOf` 必须自己拼 YYYY-MM-DD，**不能用 toISOString()**：它按 UTC 算，东八区会差一天。
 * - `memberDayCalendarCells` 只放**该月真实存在**的日子（不是 1-31 循环）——
 *   旧版按「每月几号」才需要补 31 号，现在挑的是具体日期，多出来的号没有意义。
 * - 已过去的日期（`past`）与已被别的会员日占用的日期（`taken`）都禁用；编辑自己时 `taken` 不算。
 *
 * 依赖注入：api 直接 import；isAdmin / fail / run / askConfirm / showAlert 由 AdminPanel 注入。
 */
import { computed, reactive, ref } from 'vue';
import { api } from '../api/client';

export function useAdminMemberDays({ isAdmin, fail, run, askConfirm, showAlert }) {
  const memberDays = ref([]);
  const memberDayFormOpen = ref(false);
  const memberDayForm = reactive({ id: null, memberDate: '', multiplier: 2, remark: '', enabled: true });
  // 日历当前显示的月份（可翻月挑未来的日期）
  const memberDayCalendarCursor = ref(new Date());

  const memberDayEnabledCount = computed(
    () => memberDays.value.filter((d) => Number(d.enabled) === 1 && !d.expired).length,
  );
  const memberDaySlogan = computed(() => {
    const active = memberDays.value.filter((d) => Number(d.enabled) === 1 && !d.expired);
    if (!active.length) return '当前没有生效中的会员日';
    const labels = active.slice(0, 3).map((d) => memberDayShortLabel(d.memberDate)).join('、');
    const more = active.length > 3 ? ` 等 ${active.length} 天` : '';
    const mult = Math.max(...active.map((d) => Number(d.multiplier) || 2));
    return labels + more + ' 消费积分 ' + (mult === 2 ? '双倍' : '×' + mult);
  });

  // ===== 日历（挑具体日期）=====
  const MDC_WEEK = ['日', '一', '二', '三', '四', '五', '六'];

  /** 本地时区的 YYYY-MM-DD（⚠️ 不能用 toISOString：它按 UTC 算，东八区会差一天） */
  function isoDateOf(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  const memberDayTodayIso = computed(() => isoDateOf(new Date()));
  const memberDayCalendarMonthText = computed(() => {
    const cursor = memberDayCalendarCursor.value;
    return `${cursor.getFullYear()} 年 ${cursor.getMonth() + 1} 月`;
  });

  /** 该日期是否已被**别的**会员日占用（编辑自己时不算）。 */
  function memberDayDateTaken(iso) {
    return !!iso && memberDays.value.some((row) => row.memberDate === iso && row.id !== memberDayForm.id);
  }

  /** 展示用：「10 月 1 日（周四）」 */
  function memberDayDateLabel(iso) {
    if (!iso) return '未设置';
    const [y, m, d] = iso.split('-').map(Number);
    const weekday = MDC_WEEK[new Date(y, m - 1, d).getDay()];
    const thisYear = new Date().getFullYear();
    return `${y === thisYear ? '' : `${y} 年 `}${m} 月 ${d} 日（周${weekday}）`;
  }

  /** 列表/Toolbar 里用的短标签：「10月1日」 */
  function memberDayShortLabel(iso) {
    if (!iso) return '';
    const [, m, d] = iso.split('-').map(Number);
    return `${m}月${d}日`;
  }

  /** 新增时默认落在今天（今天已被占用就往后找第一个空闲日期） */
  function firstFreeMemberDayDate() {
    const base = new Date();
    for (let i = 0; i < 90; i += 1) {
      const candidate = isoDateOf(new Date(base.getFullYear(), base.getMonth(), base.getDate() + i));
      if (!memberDayDateTaken(candidate)) return candidate;
    }
    return isoDateOf(base);
  }

  function shiftMemberDayCalendar(delta) {
    const cursor = memberDayCalendarCursor.value;
    memberDayCalendarCursor.value = new Date(cursor.getFullYear(), cursor.getMonth() + delta, 1);
  }

  function resetMemberDayCalendar() {
    memberDayCalendarCursor.value = new Date();
  }

  /**
   * 当前显示月份的全部格子。
   * ⚠️ 这里必须只放**该月真实存在**的日子（日历嘛）—— 与上一版「每月几号」不同：
   * 那时格子要覆盖 1-31（因为 2 月也得能配 31 号），现在挑的是具体日期，多出来的号没有意义。
   * 已过去的日期（`past`）与已配置的日期（`taken`）都禁用。
   */
  const memberDayCalendarCells = computed(() => {
    const cursor = memberDayCalendarCursor.value;
    const year = cursor.getFullYear();
    const month = cursor.getMonth();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const todayIso = memberDayTodayIso.value;
    const cells = [];
    for (let i = 0; i < new Date(year, month, 1).getDay(); i += 1) {
      cells.push({ day: null });
    }
    for (let d = 1; d <= daysInMonth; d += 1) {
      const iso = isoDateOf(new Date(year, month, d));
      cells.push({
        day: d,
        iso,
        label: `${month + 1} 月 ${d} 日`,
        today: iso === todayIso,
        past: iso < todayIso,
        taken: memberDayDateTaken(iso),
      });
    }
    return cells;
  });

  async function loadMemberDays() {
    if (!isAdmin.value) return;
    try {
      memberDays.value = (await api.get('/admin/member-days')) || [];
    } catch (err) {
      fail(err?.message || '会员日加载失败');
    }
  }

  function openMemberDayForm(row) {
    memberDayForm.id = row ? row.id : null;
    memberDayForm.memberDate = row ? row.memberDate : firstFreeMemberDayDate();
    memberDayForm.multiplier = row ? Number(row.multiplier) : 2;
    memberDayForm.remark = row ? (row.remark || '') : '';
    memberDayForm.enabled = row ? Number(row.enabled) === 1 : true;
    if (row && row.memberDate) {
      const [y, m] = row.memberDate.split('-').map(Number);
      memberDayCalendarCursor.value = new Date(y, m - 1, 1);   // 编辑时把日历翻到那个月
    } else {
      memberDayCalendarCursor.value = new Date();
    }
    memberDayFormOpen.value = true;
  }

  function closeMemberDayForm() {
    memberDayFormOpen.value = false;
  }

  async function saveMemberDay() {
    const form = memberDayForm;
    const date = form.memberDate;
    if (!date) {
      showAlert('请在日历上挑一个日期');
      return;
    }
    if (date < memberDayTodayIso.value) {
      showAlert('会员日不能设在今天之前，请在日历上另选一天');
      return;
    }
    if (memberDayDateTaken(date)) {
      showAlert(`${memberDayDateLabel(date)} 已经配置过了，请在日历上另选一天`);
      return;
    }
    await run(async () => {
      const payload = {
        memberDate: date,
        multiplier: Number(form.multiplier) || 2,
        remark: (form.remark || '').trim(),
        enabled: !!form.enabled,
      };
      if (form.id) await api.put(`/admin/member-days/${form.id}`, payload);
      else await api.post('/admin/member-days', payload);
      closeMemberDayForm();
      await loadMemberDays();
    }, '会员日已保存');
  }

  async function toggleMemberDay(row) {
    const enabled = Number(row.enabled) !== 1;
    await run(async () => {
      await api.put(`/admin/member-days/${row.id}`, {
        memberDate: row.memberDate,
        multiplier: Number(row.multiplier),
        remark: row.remark || '',
        enabled,
      });
      await loadMemberDays();
    }, enabled ? '已启用' : '已停用');
  }

  async function deleteMemberDay(row) {
    const confirmed = await askConfirm({
      title: '删除会员日',
      message: `删除后 ${memberDayDateLabel(row.memberDate)} 当天消费不再翻倍积分。`,
      confirmText: '确认删除',
    });
    if (!confirmed) return;
    await run(async () => {
      await api.delete(`/admin/member-days/${row.id}`);
      await loadMemberDays();
    }, '已删除');
  }

  return {
    memberDays, memberDayFormOpen, memberDayForm, memberDayCalendarCursor,
    memberDayEnabledCount, memberDaySlogan,
    MDC_WEEK, isoDateOf, memberDayTodayIso, memberDayCalendarMonthText, memberDayCalendarCells,
    memberDayDateTaken, memberDayDateLabel, memberDayShortLabel, firstFreeMemberDayDate,
    shiftMemberDayCalendar, resetMemberDayCalendar,
    loadMemberDays, openMemberDayForm, closeMemberDayForm,
    saveMemberDay, toggleMemberDay, deleteMemberDay,
  };
}
