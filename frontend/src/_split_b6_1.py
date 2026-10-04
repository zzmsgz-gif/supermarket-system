import re

SRC = 'App.vue'
lines = open(SRC, encoding='utf-8').read().split('\n')


def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    return None


def eat_blank(b):
    while b + 1 < len(lines) and lines[b + 1].strip() == '':
        b += 1
    return b


def block_end(start):
    depth = 0
    started = False
    for i in range(start, len(lines)):
        s = re.sub(r"'[^']*'|\"[^\"]*\"|`[^`]*`", '', lines[i])
        for ch in s:
            if ch == '{':
                depth += 1
                started = True
            elif ch == '}':
                depth -= 1
        if started and depth == 0:
            return eat_blank(i)
    raise SystemExit('block_end 配平失败')


spans = []

# A. 订单域：loadOrders → closeOrderDetail 结束
a_s = find(lambda l: re.match(r'^async function loadOrders', l))
a_e = block_end(find(lambda l: re.match(r'^function closeOrderDetail', l)))
spans.append((a_s, a_e, '订单域'))

# B. 立即购买：addDetailToCart → consumePendingQuickBuy 结束
b_s = find(lambda l: re.match(r'^async function addDetailToCart', l))
b_e = block_end(find(lambda l: re.match(r'^async function consumePendingQuickBuy', l)))
spans.append((b_s, b_e, '立即购买'))

# C. 优惠券领取：loadCoupons → receiveCoupon 结束
c_s = find(lambda l: re.match(r'^async function loadCoupons', l))
c_e = block_end(find(lambda l: re.match(r'^async function receiveCoupon', l)))
spans.append((c_s, c_e, '优惠券领取'))

# 末行断言
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    last = lines[k].strip()
    assert last.endswith(('}', '};', '];', ';', '),')), '%s 末行异常: %r' % (name, last[:70])

drop = set()
for a, b, name in spans:
    drop.update(range(a, b + 1))

# 保留块
KEEP = {
    'orders': '^const orders = reactive',
    'reviewedMap': '^const reviewedMap',
    'refundForm': '^const refundForm = reactive',
    'orderDetail': '^const orderDetail = reactive',
    'orderNavLock': '^let orderNavLock = false',
    'syncRoute': '^async function syncRoute',
    'coupons': '^const coupons = ref',
    'goCheckout': '^async function goCheckout',
    'createOrder': '^async function createOrder',
    'loadAdminProducts': '^async function loadAdminProducts',
    'refreshForSession': '^async function refreshForSession',
    'refreshAdminData': '^async function refreshAdminData',
    'currentTitle': '^const currentTitle = computed',
}
for name, pat in KEEP.items():
    k = find(lambda l, p=pat: re.match(p, l))
    assert k is not None, '保留块定位失败: ' + name
    assert k not in drop, '★ 保留块被误删: ' + name
print('  校验 OK: %d 个保留块全在删除区之外' % len(KEEP))

keep = [l for i, l in enumerate(lines) if i not in drop]

IMP_O = "import { useOrders } from './composables/useOrders.js';"
IMP_Q = "import { useQuickBuy } from './composables/useQuickBuy.js';"
ANCHOR_IMP = "import { useMemberPoints } from './composables/useMemberPoints.js';"
i_imp = keep.index(ANCHOR_IMP)
keep[i_imp + 1:i_imp + 1] = [IMP_O, IMP_Q]

ASM_O = ("const { loadOrders, loadMoreOrders, loadOrdersPage, loadReviewedFlags, payOrder, reorder, "
         "cancelOrder, confirmReceipt, openRefundForm, submitRefund, openOrderDetail, closeOrderDetail } = "
         "useOrders({ api, run, fail, askConfirm, showAlert, money, session, isAdmin, router, navigate, "
         "orders, reviewedMap, refundForm, orderDetail, "
         "isOrderNavLocked: () => orderNavLock, setOrderNavLock: (v) => { orderNavLock = v; }, "
         "loadWallet, loadMe, loadCart, loadFlashSales });")
ASM_Q = ("const { addDetailToCart, buildQuickBuyCartItem, buyDetailNow, enterQuickBuy, consumePendingQuickBuy } = "
         "useQuickBuy({ api, run, fail, session, isAdmin, router, navigate, notice, cart, quickBuy, "
         "pendingQuickBuy, productDetail, detailQuantity, selectedSpecText, effectiveDetailPrice, "
         "selectedSkuPrice, getFlashSaleOfProduct: flashSaleOfProduct, "
         "getFlashLimitOfProduct: flashLimitOfProduct, reportDwell, guestAdd, takeAddSource, flyToCart, "
         "setPendingAction, takePendingAction, clearPendingAction, goLogin });")

# 装配点：useMemberPoints 之后（loadOrders 会用到 loadCart/loadWallet 等，都在更早处）
ANCHOR_ASM = [i for i, l in enumerate(keep) if '} = useMemberPoints({' in l]
assert ANCHOR_ASM, '装配锚点未找到'
di = ANCHOR_ASM[0]
de = di
while '});' not in keep[de]:
    de += 1
keep[de + 1:de + 1] = [
    '// 订单：列表/支付/取消/收货/退款/详情。orderNavLock 与 orderDetail 被 syncRoute 共用 → 留在原地，',
    '//    这里用读写器与注入的方式访问，保持单一真相源。',
    ASM_O,
    '// 立即购买：虚拟购物车项通道（不写购物车表）。依赖秒杀与商品详情两个域的状态。',
    ASM_Q,
]

out = '\n'.join(keep)

SYM = ['loadOrders', 'loadMoreOrders', 'loadOrdersPage', 'loadReviewedFlags', 'payOrder', 'reorder',
       'cancelOrder', 'confirmReceipt', 'openRefundForm', 'submitRefund', 'openOrderDetail',
       'closeOrderDetail', 'addDetailToCart', 'buildQuickBuyCartItem', 'buyDetailNow',
       'enterQuickBuy', 'consumePendingQuickBuy', 'loadCoupons', 'receiveCoupon']
for s in SYM:
    assert re.search(r'\b' + re.escape(s) + r'\b', out), '符号丢失: ' + s

# ⭐ 裸实参必须真实存在；且来源（const/解构）必须早于装配行
def split_top(body):
    parts, depth, buf, q = [], 0, '', None
    for ch in body:
        if q:
            buf += ch
            if ch == q:
                q = None
            continue
        if ch in '"\'`':
            q = ch
            buf += ch
            continue
        if ch in '({[':
            depth += 1
        elif ch in ')}]':
            depth -= 1
        if ch == ',' and depth == 0:
            parts.append(buf)
            buf = ''
        else:
            buf += ch
    if buf.strip():
        parts.append(buf)
    return [p.strip() for p in parts if p.strip()]


ol = out.split('\n')
def of(pred):
    for i in range(len(ol)):
        if pred(ol[i]):
            return i
    return None


def src_line(name):
    for j, l in enumerate(ol):
        if re.match(r'^import \{[^}]*\b' + re.escape(name) + r'\b', l):
            return ('import', j)
        if re.match(r'^(?:const|let)\s+' + re.escape(name) + r'\b', l):
            return ('const', j)
        if re.match(r'^(?:async function|function)\s+' + re.escape(name) + r'\b', l):
            return ('func', j)
    for j, l in enumerate(ol):
        if l.startswith('const {'):
            k = j
            buf = ''
            while '}' not in buf and k < len(ol):
                buf += ol[k]
                k += 1
            if re.search(r'\b' + re.escape(name) + r'\b\s*[,}]', buf):
                return ('destructure', j)
    return ('none', -1)


O = of(lambda l: '} = useOrders({' in l)
Q = of(lambda l: '} = useQuickBuy({' in l)
assert O and Q, '装配行未找到'
for asm, tag, ai in [(ASM_O, 'useOrders', O), (ASM_Q, 'useQuickBuy', Q)]:
    m = re.search(r'\(\{(.*)\}\);', asm)
    for part in split_top(m.group(1)):
        if ':' in part:
            continue
        name = part.strip()
        if not name or '=' in name or '=>' in name:
            continue
        kind, d = src_line(name)
        assert kind != 'none', '★ %s 裸实参「%s」在 App.vue 中不存在' % (tag, name)
        if kind in ('const', 'destructure'):
            assert d < ai, '★ TDZ: %s 裸实参「%s」来源 %d 晚于装配 %d' % (tag, name, d + 1, ai + 1)
print('  校验 OK: 两个装配行的裸实参均已定义且早于装配点')

# appCtx 仍含这些符号
for s in ['loadOrders', 'loadMoreOrders', 'loadOrdersPage', 'payOrder', 'cancelOrder', 'confirmReceipt',
          'openRefundForm', 'submitRefund', 'openOrderDetail', 'closeOrderDetail', 'buyDetailNow',
          'addDetailToCart', 'loadCoupons', 'receiveCoupon',
          'reviewedMap', 'refundForm']:
    assert re.search(r'(?:const (?:adminCtx|appCtx) = \{|appCtx\.)[^;\n]*\b' + re.escape(s) + r'\b', out), \
        '★ ctx 缺符号: ' + s
print('  校验 OK: appCtx/adminCtx 仍含全部消费符号')

open('_b6out1.txt', 'w', encoding='utf-8').write(out)
print('共删除 %d 行 (%d -> %d)' % (len(drop), len(lines), len(out.split('\n'))))
for a, b, name in spans:
    print('  %-12s %d-%d (%d 行)' % (name, a + 1, b + 1, b - a + 1))