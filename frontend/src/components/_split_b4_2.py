"""批次4-2：从 AdminPanel.vue 抽出 useAdminActivities。"""
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
# A. 活动 state 段：adminActivities .. activityProductsLoaded
i_st = find(lambda l: re.match(r'^const adminActivities = reactive', l))
i_stEnd = find(lambda l: re.match(r'^const activityProductsLoaded = ref', l))
spans.append((i_st, i_stEnd, '活动state'))

# B. activityForm 单行（在 couponForm 之后）
i_af = find(lambda l: re.match(r'^const activityForm = reactive', l))
spans.append((i_af, i_af, 'activityForm'))

# C. 函数区：loadActivityProducts → deleteActivity
i_fn = find(lambda l: re.match(r'^async function loadActivityProducts', l))
i_del = find(lambda l: re.match(r'^async function deleteActivity', l))
spans.append((i_fn, eat_blank(block_end(i_del)), '活动函数'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))

for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    if name == '活动函数':
        assert lines[k].strip() == '}', '%s 末行异常: %r' % (name, lines[k][:60])
print('待删区间:')
for a, b, n in spans:
    print('  %-10s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

# ---- import ----
imp = "import { useAdminFlash } from '../composables/useAdminFlash.js';"
assert out.count(imp) == 1, 'import 锚点异常'
out = out.replace(imp, imp + "\nimport { useAdminActivities } from '../composables/useAdminActivities.js';")

# ---- 装配：放在上一批的 useAdminFlash 装配之后 ----
anchor = "const { adminFlashSales, flashFormOpen"
i_a = out.find(anchor)
assert i_a > 0, 'useAdminFlash 装配锚点未找到'
# 找到该行行尾
eol = out.find('\n', out.find(';', i_a))
assembly = """
const { adminActivities, adminActivityKeyword, adminActivityJumpPage, adminActivityTotalPages, activityProducts, activityProductsLoaded, activityForm, loadActivityProducts, loadAdminActivities, searchAdminActivities, changeAdminActivityPage, changeAdminActivityPageSize, goAdminActivityPage, resetAdminActivitySearch, resetActivityForm, onActivityScopeChange, fillActivityPeriod, activityTypeLabel, activityScopeLabel, activityDiscountLabel, editActivity, saveActivity, toggleActivity, deleteActivity } = useAdminActivities({ isAdmin, fail, run, askConfirm, categories });"""
out = out[:eol] + assembly + out[eol:]

# ---- 硬断言 ----
for g in ['async function loadActivityProducts(', 'async function loadAdminActivities(',
          'async function deleteActivity(', 'function activityDiscountLabel(',
          'function editActivity(', 'const adminActivities = reactive(',
          'const activityForm = reactive(', 'const activityProducts = ref([]);',
          'const adminActivityTotalPages = computed(']:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
assert out.count('useAdminActivities({') == 1, '装配异常'
for s in ['adminActivities','adminActivityKeyword','adminActivityJumpPage','adminActivityTotalPages',
          'activityProducts','activityProductsLoaded','activityForm','loadActivityProducts',
          'loadAdminActivities','searchAdminActivities','changeAdminActivityPage',
          'changeAdminActivityPageSize','goAdminActivityPage','resetAdminActivitySearch',
          'resetActivityForm','onActivityScopeChange','fillActivityPeriod','activityTypeLabel',
          'activityScopeLabel','activityDiscountLabel','editActivity','saveActivity',
          'toggleActivity','deleteActivity']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b4out2.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out2.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
