"""批次4-4：从 AdminPanel.vue 抽出 useAdminMemberDays。

⚠️ MDC_WEEK / isoDateOf 虽然物理位置在「评价管理」区段开头，但**只被会员日用**
   （MDC_WEEK 另有模板 770 行的日历表头引用 → 搬进 composable 后由解构供模板使用）。
"""
import re

P = 'AdminPanel.vue'
src = open(P, encoding='utf-8').read()
lines = src.split('\n')
orig = len(lines)

def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    raise SystemExit('锚点未找到')

def block_end(start):
    depth, started = 0, False
    for j in range(start, len(lines)):
        depth += lines[j].count('{') - lines[j].count('}')
        if '{' in lines[j]:
            started = True
        if started and depth == 0:
            return j
    raise SystemExit('配平失败 start=%d' % start)

def eat_blank(i):
    while i + 1 < len(lines) and lines[i + 1].strip() == '':
        i += 1
    return i

spans = []
# A. state 段：会员日注释头 → resetMemberDayCalendar 前（含 MDC_WEEK / isoDateOf / 各 computed）
i_head = find(lambda l: l.strip().startswith('// ===== 会员日（指定日期消费积分翻倍）'))
i_resetCal = find(lambda l: re.match(r'^function resetMemberDayCalendar', l))
spans.append((i_head, eat_blank(block_end(i_resetCal)), '会员日A'))

# B. 函数段：memberDayCalendarCells → deleteMemberDay
i_cells = find(lambda l: re.match(r'^const memberDayCalendarCells = computed', l))
i_del = find(lambda l: re.match(r'^async function deleteMemberDay', l))
spans.append((i_cells, eat_blank(block_end(i_del)), '会员日B'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    assert lines[k].strip() in ('}', '});'), '%s 末行异常: %r' % (name, lines[k][:60])
print('待删区间:')
for a, b, n in spans:
    print('  %-10s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

# 校验：23 个会员日符号的定义行都落在删除区内
SYM = ['memberDays','memberDayFormOpen','memberDayForm','memberDayCalendarCursor',
       'memberDayEnabledCount','memberDaySlogan','MDC_WEEK','isoDateOf','memberDayTodayIso',
       'memberDayCalendarMonthText','memberDayCalendarCells','memberDayDateTaken',
       'memberDayDateLabel','memberDayShortLabel','firstFreeMemberDayDate',
       'shiftMemberDayCalendar','resetMemberDayCalendar','loadMemberDays',
       'openMemberDayForm','closeMemberDayForm','saveMemberDay','toggleMemberDay','deleteMemberDay']
lo = min(a for a, _, _ in spans); hi = max(b for _, b, _ in spans)
for s in SYM:
    k = find(lambda l: re.match(r'^(?:const|let)\s+' + s + r'\b', l) or re.match(r'^(?:async\s+)?function\s+' + s + r'\b', l))
    assert lo <= k <= hi, '%s 定义在 %d，落在删除区 %d-%d 之外!' % (s, k, lo, hi)
print('  校验 OK: 23 个会员日符号全在删除区内 (%d-%d)' % (lo, hi))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

imp = "import { useAdminPasswordResets } from '../composables/useAdminPasswordResets.js';"
assert out.count(imp) == 1
out = out.replace(imp, imp + "\nimport { useAdminMemberDays } from '../composables/useAdminMemberDays.js';")

anchor = "} = useAdminPasswordResets({ run, fail, askConfirm, notice });"
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + """

const { memberDays, memberDayFormOpen, memberDayForm, memberDayCalendarCursor, memberDayEnabledCount, memberDaySlogan, MDC_WEEK, isoDateOf, memberDayTodayIso, memberDayCalendarMonthText, memberDayCalendarCells, memberDayDateTaken, memberDayDateLabel, memberDayShortLabel, firstFreeMemberDayDate, shiftMemberDayCalendar, resetMemberDayCalendar, loadMemberDays, openMemberDayForm, closeMemberDayForm, saveMemberDay, toggleMemberDay, deleteMemberDay } = useAdminMemberDays({ isAdmin, fail, run, askConfirm, showAlert });""")

for g in ['const memberDays = ref([]);', 'function memberDayDateTaken(', 'async function loadMemberDays(',
          'async function deleteMemberDay(', 'const MDC_WEEK =', 'function isoDateOf(',
          'const memberDayCalendarCells = computed(', 'const memberDaySlogan = computed(']:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
assert out.count('useAdminMemberDays({') == 1
# 模板 770 行用 MDC_WEEK → 搬走后必须由解构供上
assert re.search(r'const \{[^}]*\bMDC_WEEK\b', out), 'MDC_WEEK 未在解构中（模板日历表头要用）'
for s in SYM:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b4out4.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out4.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
