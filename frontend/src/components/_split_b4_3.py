"""批次4-3：从 AdminPanel.vue 抽出 useAdminPasswordResets。

⚠️ 区间内混了两个「非本模块」的块，必须保留在原地：
   ① loadAdminReviewUnreplied() —— 属评价域（写 adminReviewSummary）
   ② onMounted(...) 块 —— 同时调 密码重置 + 评价 + refreshCurrentAdminMenu 三个模块
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
# A. state + 前段函数：注释头 → loadPasswordResetPendingCount 结束
i_head = find(lambda l: l.strip().startswith('/* ===== 找回密码申请'))
i_pend = find(lambda l: re.match(r'^async function loadPasswordResetPendingCount', l))
spans.append((i_head, eat_blank(block_end(i_pend)), '密码重置A'))

# B. 后段函数：searchPasswordResets → copyTempPassword 结束
i_s = find(lambda l: re.match(r'^async function searchPasswordResets', l))
i_c = find(lambda l: re.match(r'^async function copyTempPassword', l))
spans.append((i_s, eat_blank(block_end(i_c)), '密码重置B'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    assert lines[k].strip() == '}', '%s 末行异常: %r' % (name, lines[k][:60])
print('待删区间:')
for a, b, n in spans:
    print('  %-12s %5d-%5d (%d行)' % (n, a, b, b - a + 1))
# 确认保留块在这两个区间之外
for keep_name, pat in [('loadAdminReviewUnreplied', r'^async function loadAdminReviewUnreplied'),
                      ('onMounted块', r'^onMounted\(')]:
    k = find(lambda l: re.match(pat, l))
    inside = any(a <= k <= b for a, b, _ in spans)
    print('  保留 %-24s 行%-5d 在删除区内? %s' % (keep_name, k, '★是(错!)' if inside else '否 [OK]'))
    assert not inside, keep_name + ' 被误纳入删除区间'

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

imp = "import { useAdminActivities } from '../composables/useAdminActivities.js';"
assert out.count(imp) == 1
out = out.replace(imp, imp + "\nimport { useAdminPasswordResets } from '../composables/useAdminPasswordResets.js';")

anchor = "} = useAdminActivities({ isAdmin, fail, run, askConfirm, categories });"
assert out.count(anchor) == 1, '装配锚点异常'
out = out.replace(anchor, anchor + """

const { passwordResets, passwordResetStatus, passwordResetJumpPage, passwordResetResult, passwordResetTotalPages, PASSWORD_RESET_STATUS_LABELS, passwordResetStatusLabel, passwordResetStatusClass, loadPasswordResets, loadPasswordResetPendingCount, searchPasswordResets, changePasswordResetPage, changePasswordResetPageSize, goPasswordResetPage, confirmResetPassword, confirmRejectPasswordReset, copyTempPassword } = useAdminPasswordResets({ run, fail, askConfirm, notice });""")

for g in ['const passwordResets = reactive(', 'function passwordResetStatusLabel(',
          'async function loadPasswordResets(', 'async function copyTempPassword(',
          'const PASSWORD_RESET_STATUS_LABELS =', 'const passwordResetTotalPages = computed(']:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
assert out.count('useAdminPasswordResets({') == 1
# 保留块必须还在
assert 'async function loadAdminReviewUnreplied(' in out, '误删 loadAdminReviewUnreplied'
assert re.search(r'^onMounted\(', out, re.M), '误删 onMounted 块'
for s in ['passwordResets','passwordResetStatus','passwordResetJumpPage','passwordResetResult',
          'passwordResetTotalPages','PASSWORD_RESET_STATUS_LABELS','passwordResetStatusLabel',
          'passwordResetStatusClass','loadPasswordResets','loadPasswordResetPendingCount',
          'searchPasswordResets','changePasswordResetPage','changePasswordResetPageSize',
          'goPasswordResetPage','confirmResetPassword','confirmRejectPasswordReset','copyTempPassword']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b4out3.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out3.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
