"""批次4-5：从 AdminPanel.vue 抽出 useAdminReviews。

⚠️ 保留块：紧邻的 onMounted(...) 同时调 密码重置 + 评价 + refreshCurrentAdminMenu 三个模块，
   拆分时绝不能连带删掉（脚本显式断言）。
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
# A. loadAdminReviewUnreplied（含前两行注释）
i_unreplied = find(lambda l: re.match(r'^async function loadAdminReviewUnreplied', l))
i_a = i_unreplied
while i_a > 0 and lines[i_a - 1].strip().startswith('//'):
    i_a -= 1
spans.append((i_a, eat_blank(block_end(i_unreplied)), 'loadAdminReviewUnreplied'))

# B. 评价 state + 函数：注释头 → toggleReviewHidden 结束
i_head = find(lambda l: l.strip().startswith('// ===== 评价管理 ====='))
i_toggle = find(lambda l: re.match(r'^async function toggleReviewHidden', l))
spans.append((i_head, eat_blank(block_end(i_toggle)), '评价主体'))

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
    print('  %-28s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

# 保留块断言
i_mnt = find(lambda l: re.match(r'^onMounted\(', l))
assert not any(a <= i_mnt <= b for a, b, _ in spans), 'onMounted 块被误纳入删除区'
print('  保留 onMounted 块: 行%d [OK]' % i_mnt)

SYM = ['adminReviews','adminReviewSummary','adminReviewRating','adminReviewReplied',
       'adminReviewKeyword','reviewReplyDraft','adminReviewTotalPages','loadAdminReviews',
       'searchAdminReviews','changeAdminReviewPage','loadAdminReviewUnreplied',
       'saveReviewReply','toggleReviewHidden']
lo = min(a for a, _, _ in spans); hi = max(b for _, b, _ in spans)
for s in SYM:
    k = find(lambda l: re.match(r'^(?:const|let)\s+' + s + r'\b', l) or re.match(r'^(?:async\s+)?function\s+' + s + r'\b', l))
    assert lo <= k <= hi, '%s 定义在 %d，落在删除区 %d-%d 之外!' % (s, k, lo, hi)
print('  校验 OK: 13 个评价符号全在删除区内 (%d-%d)' % (lo, hi))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

imp = "import { useAdminMemberDays } from '../composables/useAdminMemberDays.js';"
assert out.count(imp) == 1
out = out.replace(imp, imp + "\nimport { useAdminReviews } from '../composables/useAdminReviews.js';")

anchor = "} = useAdminMemberDays({ isAdmin, fail, run, askConfirm, showAlert });"
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + """

const { adminReviews, adminReviewSummary, adminReviewRating, adminReviewReplied, adminReviewKeyword, reviewReplyDraft, adminReviewTotalPages, loadAdminReviews, searchAdminReviews, changeAdminReviewPage, loadAdminReviewUnreplied, saveReviewReply, toggleReviewHidden } = useAdminReviews({ run, askConfirm, showAlert });""")

for g in ['const adminReviews = reactive(', 'const adminReviewSummary = ref(',
          'async function loadAdminReviews(', 'async function loadAdminReviewUnreplied(',
          'async function toggleReviewHidden(', 'const adminReviewTotalPages = computed(',
          'const reviewReplyDraft = reactive(']:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
assert out.count('useAdminReviews({') == 1
assert re.search(r'^onMounted\(', out, re.M), '误删 onMounted'
assert 'loadAdminReviewUnreplied();' in out, 'onMounted 里对它的调用丢了'
for s in SYM:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b4out5.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out5.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
