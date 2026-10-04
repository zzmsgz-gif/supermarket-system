"""
批次1：从 App.vue 抽出 useStores / useLegalDoc / useMessages 三个 composable。

按 skill「手法1：块边界用标签配平/精确锚点」+「手法2：写盘前硬断言」。
删除区间（0-based 行号，闭区间）由精确锚点定位，不依赖「下一个标记」。
装配点插在 fail() 之后、ROUTE_VIEWS 之前 —— 那里 fail/notice/session/isAdmin/
navigate/cartLocalTotal 全部已定义，且被删符号在装配点之前无任何同步求值。
"""
import re, sys, io

P = 'App.vue'
src = open(P, encoding='utf-8').read()
lines = src.split('\n')
orig_len = len(lines)

def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    raise SystemExit('锚点未找到')

def block_end(start):
    """从 start 行(函数头)开始做大括号配平，返回函数闭合行号。"""
    depth = 0
    started = False
    for j in range(start, len(lines)):
        depth += lines[j].count('{') - lines[j].count('}')
        if '{' in lines[j]:
            started = True
        if started and depth == 0:
            return j
    raise SystemExit('配平失败, start=%d' % start)

def eat_blank(i):
    """从 i 起向后吞掉连续空行，返回最后一个非空行号。"""
    while i + 1 < len(lines) and lines[i + 1].strip() == '':
        i += 1
    return i

# ---------- 1) 待删区间定位 ----------
# A. 履约域：注释头 → loadDeliverySlots 函数**闭合**(配平)
i_ful_start = find(lambda l: l.strip().startswith('/* ---------------- 履约'))
i_loadSlots = find(lambda l: re.match(r'^async function loadDeliverySlots', l))
i_ful_end = eat_blank(block_end(i_loadSlots))

# B. selectFulfillment/selectStore/resetFulfillment 三函数（含前置注释行）
i_selStart = find(lambda l: l.strip().startswith('// 三种履约互斥'))
i_resetFn = find(lambda l: re.match(r'^function resetFulfillment', l))
i_resetEnd = eat_blank(block_end(i_resetFn))

# C. 协议域：注释头 → openLegal 函数闭合(配平)
i_legal_start = find(lambda l: l.strip().startswith('// ===== 协议 / 隐私正文'))
i_openLegal = find(lambda l: re.match(r'^function openLegal', l))
i_legal_end = eat_blank(block_end(i_openLegal))

# D. 消息域：注释头 → openMessage 函数闭合(配平)
#    accountDotTitle 依赖 alertUnread（收藏域），留在原地不搬
i_msg_start = find(lambda l: l.strip().startswith('/* ---------------- 消息中心'))
i_openMsg = find(lambda l: re.match(r'^async function openMessage', l))
i_msg_end = eat_blank(block_end(i_openMsg))

spans = [
    (i_ful_start, i_ful_end, '履约域'),
    (i_selStart, i_resetEnd, '履约select三函数'),
    (i_legal_start, i_legal_end, '协议域'),
    (i_msg_start, i_msg_end, '消息域'),
]
spans.sort()
# 区间不得重叠
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s / %s' % (a, b))

# 边界内容硬校验：每个区间的首行/末行必须符合预期，防止配平算错静默落盘
BOUNDS = {
    '履约域': ('/* ---------------- 履约', '}'),
    '履约select三函数': ('// 三种履约互斥', '}'),
    '协议域': ('// ===== 协议 / 隐私正文', '}'),
    '消息域': ('/* ---------------- 消息中心', '}'),
}
for a, b, name in spans:
    head, tail = BOUNDS[name]
    assert head in lines[a], '%s 区间首行异常: %r' % (name, lines[a][:60])
    # 末行往下回退到最近的非空行（eat_blank 会把尾部空行纳入区间）
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    assert lines[k].strip() == tail, '%s 区间末非空行异常: %r' % (name, lines[k][:60])
print('待删区间:', spans)

removed_lines = sum(b - a + 1 for a, b, _ in spans)
print('共删除', removed_lines, '行')

# ---------- 2) 组装新内容 ----------
keep = []
drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]

out = '\n'.join(keep)

# ---------- 3) 插 import ----------
imp_anchor = "import { setPendingAction, takePendingAction, clearPendingAction } from './composables/pendingAction.js';"
assert out.count(imp_anchor) == 1, 'import 锚点异常'
new_imports = imp_anchor + """
import { useStores } from './composables/useStores';
import { useLegalDoc } from './composables/useLegalDoc';
import { useMessages } from './composables/useMessages';"""
out = out.replace(imp_anchor, new_imports)

# ---------- 4) 插装配（放在 fail 定义之后、ROUTE_VIEWS 之前） ----------
asm_anchor = "const ROUTE_VIEWS = ["
assert out.count(asm_anchor) == 1, '装配锚点异常(%d)' % out.count(asm_anchor)
assembly = """/* ---------------- 履约 / 协议 / 消息：已抽为 composable ---------------- */
// 装配点必须在 fail() 之后（消息的 markMessagesRead 要用），且被抽走的符号在本行之前
// 无任何同步求值（无 immediate watch / watchEffect），故不存在 TDZ 风险。
const { stores, deliverySlots, fulfillment, EXPRESS_FREE_THRESHOLD, EXPRESS_FREIGHT, isPickup, isExpress, expressFreight, selectedStore, activeStoreId, loadStores, loadDeliverySlots, selectFulfillment, selectStore, resetFulfillment } = useStores({ getCartLocalTotal: () => cartLocalTotal.value });

const { legalDocs, loadLegalDoc, openLegal } = useLegalDoc();

const { messages, messageUnread, messageTypeFilter, loadMessages, loadMessageUnread, changeMessageFilter, loadMessagesPage, markMessagesRead, openMessage } = useMessages({ getSession: () => session, isAdmin, navigate, notice, fail });

"""
out = out.replace(asm_anchor, assembly + asm_anchor)

# ---------- 5) 硬断言（不符不落盘） ----------
must_gone = [
    'const stores = ref([]);', 'const deliverySlots = ref([]);',
    'const fulfillment = reactive({ type:', 'const legalDocs = reactive({ data:',
    'const messages = reactive({ items:',
    'async function loadStores(', 'async function loadMessages(',
    'function selectFulfillment(', 'function openLegal(',
]
for g in must_gone:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
# 每个新符号必须恰好装配一次
for s in ['useStores({', 'useLegalDoc()', 'useMessages({',
          'const { stores,', 'const { legalDocs,', 'const { messages,']:
    assert out.count(s) == 1, '装配异常: %r x%d' % (s, out.count(s))
# appCtx/adminCtx 引用的符号必须仍然存在于文件（供解构）
for s in ['stores', 'deliverySlots', 'fulfillment', 'legalDocs', 'messages',
          'messageUnread', 'messageTypeFilter', 'loadMessages', 'loadMessageUnread',
          'markMessagesRead', 'openMessage', 'loadStores', 'loadDeliverySlots',
          'selectFulfillment', 'selectStore', 'resetFulfillment', 'loadLegalDoc',
          'isPickup', 'isExpress', 'expressFreight', 'selectedStore', 'activeStoreId',
          'EXPRESS_FREE_THRESHOLD', 'EXPRESS_FREIGHT', 'changeMessageFilter',
          'loadMessagesPage', 'openLegal']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open(P, 'w', encoding='utf-8').write(out)
print('写入完成: %d -> %d 行 (净减 %d)' % (orig_len, len(keep), orig_len - len(keep)))
