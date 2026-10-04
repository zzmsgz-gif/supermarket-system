"""批次4-1：从 AdminPanel.vue 抽出 useAdminStores / useAdminFlash。"""
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
# 门店域
i_st = find(lambda l: l.strip().startswith('/* ---------------- 门店自提（后台）'))
i_delStore = find(lambda l: re.match(r'^async function deleteStore', l))
spans.append((i_st, eat_blank(block_end(i_delStore)), '门店'))
# 秒杀域（含那段设计说明注释）
i_fl = find(lambda l: l.strip().startswith('/* ===================== 限时秒杀管理'))
i_delFlash = find(lambda l: re.match(r'^async function deleteFlashSale', l))
spans.append((i_fl, eat_blank(block_end(i_delFlash)), '秒杀'))

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
    print('  %-6s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

# ---- import ----
m = re.search(r"^import .*from '\.\./api/client';$", out, re.M)
assert m, 'api/client import 锚点未找到'
out = out[:m.end()] + """
import { useAdminStores } from '../composables/useAdminStores.js';
import { useAdminFlash } from '../composables/useAdminFlash.js';""" + out[m.end():]

# ---- 装配：放在 adminMenuLoaders 之前(那里已有各 loader 表) ----
asm = 'const adminMenuLoaders'
i_asm = out.find(asm)
assert i_asm > 0, 'adminMenuLoaders 锚点未找到'
line_start = out.rfind('\n', 0, i_asm) + 1
assembly = """/* ---------------- 门店 / 秒杀：已抽为 composable ---------------- */
// 装配点必须在 isAdmin / fail / run / askConfirm 之后（adminCtx 已解构出它们）。
const { adminStores, storeFormOpen, storeEditingId, storeForm, loadAdminStores, resetStoreForm, openStoreForm, closeStoreForm, storePayload, saveStore, toggleStoreStatus, deleteStore } = useAdminStores({ isAdmin, fail, run, askConfirm });

const { adminFlashSales, flashFormOpen, flashEditingId, flashProductOptions, flashForm, loadAdminFlashSales, loadFlashProductOptions, flashStateLabel, flashStateClass, toDateTimeInput, openFlashForm, closeFlashForm, saveFlashSale, toggleFlashStatus, deleteFlashSale } = useAdminFlash({ isAdmin, fail, run, askConfirm });

"""
out = out[:line_start] + assembly + out[line_start:]

# ---- 硬断言 ----
for g in ['async function loadAdminStores(', 'function resetStoreForm(', 'async function deleteStore(',
          'const adminStores = ref([]);', 'const storeForm = reactive({',
          'async function loadAdminFlashSales(', 'async function deleteFlashSale(',
          'const adminFlashSales = ref([]);', 'const flashForm = reactive({',
          'function flashStateLabel(', 'function toDateTimeInput(']:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useAdminStores({', 'useAdminFlash({']:
    assert out.count(s) == 1, '装配异常: %r' % s
for s in ['adminStores','storeFormOpen','storeEditingId','storeForm','loadAdminStores',
          'resetStoreForm','openStoreForm','closeStoreForm','storePayload','saveStore',
          'toggleStoreStatus','deleteStore','adminFlashSales','flashFormOpen','flashEditingId',
          'flashProductOptions','flashForm','loadAdminFlashSales','loadFlashProductOptions',
          'flashStateLabel','flashStateClass','toDateTimeInput','openFlashForm','closeFlashForm',
          'saveFlashSale','toggleFlashStatus','deleteFlashSale']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b4out.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
