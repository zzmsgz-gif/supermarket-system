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

# A. state: myCoupons/usableCoupons/selectedUserCouponId/userOptedOutCoupon/cartSyncTimers（428-433）
for nm in ['myCoupons', 'usableCoupons', 'selectedUserCouponId', 'userOptedOutCoupon', 'cartSyncTimers']:
    k = find(lambda l, p=nm: re.match(r'^const ' + p + r' = ', l))
    assert k is not None, '定位失败: ' + nm
    spans.append((k, k, nm))

# B. computed 区: selectedCoupon → cartListTotal 结束（中间夹着 selectedAddress，要留下）
b1s = find(lambda l: re.match(r'^const selectedCoupon = computed', l))
b1e = find(lambda l: l.strip().startswith('// 购物车实时合计')) - 1
spans.append((b1s, b1e, 'selectedCoupon'))

# 购物车金额 computed 分两段：cartBadgeCount 刻意留在 App.vue（见 keep 注释）
b2s = find(lambda l: l.strip().startswith('// 购物车实时合计'))
b2e = find(lambda l: l.strip().startswith('// 顶栏购物车角标')) - 1
spans.append((b2s, b2e, '购物车金额computed-前段'))

b3s = find(lambda l: re.match(r'^const orderPayPreview = computed', l))
b3e = find(lambda l: re.match(r'^const selectedAddress = computed', l)) - 1
while b3e > b3s and lines[b3e].strip() == '':
    b3e -= 1
spans.append((b3s, b3e, '购物车金额computed-后段'))

# C. 函数区: addToCart → clearCart 结束（loadAddresses 之前）
c_s = find(lambda l: re.match(r'^async function addToCart', l))
c_e = find(lambda l: re.match(r'^async function loadAddresses', l)) - 1
while c_e > c_s and lines[c_e].strip() == '':
    c_e -= 1
spans.append((c_s, c_e, '购物车CRUD+券'))

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

# 保留块断言
KEEP = {
    'cart': '^const cart = reactive',
    'ratingSummaryMap': '^const ratingSummaryMap',
    'orders': '^const orders = reactive',
    'addresses': '^const addresses = ref',
    'selectedAddressId': '^const selectedAddressId',
    'coupons': '^const coupons = ref',
    'paying': '^const paying = ref',
    'reviewedMap': '^const reviewedMap',
    'refundOrders': '^const refundOrders',
    'selectedAddress(computed)': '^const selectedAddress = computed',
    'cartBadgeCount': '^const cartBadgeCount = computed',
    'loadAddresses': '^async function loadAddresses',
    'goCheckout': '^async function goCheckout',
    'createOrder': '^async function createOrder',
    'loadOrders': '^async function loadOrders',
}
for name, pat in KEEP.items():
    k = find(lambda l, p=pat: re.match(p, l))
    assert k is not None, '保留块定位失败: ' + name
    assert k not in drop, '★ 保留块被误删: ' + name
print('  校验 OK: %d 个保留块全在删除区之外' % len(KEEP))

keep = [l for i, l in enumerate(lines) if i not in drop]

# import
IMP = "import { useCart } from './composables/useCart.js';"
ANCHOR_IMP = "import { useProductDetail } from './composables/useProductDetail.js';"
i_imp = keep.index(ANCHOR_IMP)
keep[i_imp + 1:i_imp + 1] = [IMP]

# 装配点：必须在 useMemberPoints 之前（它依赖 orderPayPreview / cartMemberDiscount / round2）
ASM = ("const { myCoupons, usableCoupons, selectedUserCouponId, userOptedOutCoupon, cartSyncTimers, "
       "selectedCoupon, cartLocalTotal, cartOriginalSave, orderPayPreview, cartSelectedQty, "
       "cartTotalSaved, productBasePriceMap, catalogBasePrice, cartMemberDiscount, cartListTotal, addToCart, "
       "loadCart, loadMyCoupons, loadMoreMyCoupons, loadMyCouponsPage, loadUsableCoupons, couponEligible, "
       "couponShortfall, autoSelectCoupon, selectCoupon, chooseNoCoupon, stepQty, onQtyChange, onQtyInput, "
       "removeCartItem, clearCart } = useCart({ api, run, fail, askConfirm, session, isAdmin, cart, cartStore, "
       "setNotice: (v) => { notice.value = v; }, "
       "onAddedFeedback: (src, url) => flyToCart(takeAddSource(), url), guestAdd, guestRemoveItem, guestClear, "
       "persistGuestFromItems, recomputeCartTotals, refreshGuestCartView, "
       "getFlashLimitOfProduct: flashLimitOfProduct, getFlashSaleOfProduct: flashSaleOfProduct, "
       "getCartQtyMax: cartQtyMax, getProducts: () => products.value });")
ANCHOR_ASM = [i for i, l in enumerate(keep) if '= useGuestCart({' in l]
assert ANCHOR_ASM, '装配锚点(useGuestCart)未找到'
# 插到 useGuestCart 装配块的配平结束处（它上面的注释不动）
i_asm = ANCHOR_ASM[0]
depth = 0
started = False
for j in range(i_asm, len(keep)):
    s = re.sub(r"'[^']*'|\"[^\"]*\"|`[^`]*`", '', keep[j])
    for ch in s:
        if ch == '{':
            depth += 1
            started = True
        elif ch == '}':
            depth -= 1
    if started and depth == 0:
        i_asm_end = j
        break
else:
    raise SystemExit('useGuestCart 装配块配平失败')
i_asm = i_asm_end
keep[i_asm + 1:i_asm + 1] = [
    '// 购物车：金额合计、增删改、可用券。',
    '// ⚠️ 装配点必须早于 useCartUi（它要 cartBadgeCount）且早于 useMemberPoints（它要 orderPayPreview',
    '//    / cartMemberDiscount）。整体顺序固定为 useGuestCart → useCart → useCartUi → useMemberPoints',
    '//    → useProductDetail，别为「统一位置」调换。',
    ASM,
    '',
]

out = '\n'.join(keep)

SYM = ['myCoupons', 'usableCoupons', 'selectedUserCouponId', 'userOptedOutCoupon', 'cartSyncTimers',
       'selectedCoupon', 'cartLocalTotal', 'cartOriginalSave', 'cartBadgeCount', 'orderPayPreview',
       'cartSelectedQty', 'cartTotalSaved', 'productBasePriceMap', 'catalogBasePrice',
       'cartMemberDiscount', 'cartListTotal', 'addToCart', 'loadCart', 'loadMyCoupons',
       'loadMoreMyCoupons', 'loadMyCouponsPage', 'loadUsableCoupons', 'couponEligible',
       'couponShortfall', 'autoSelectCoupon', 'selectCoupon', 'chooseNoCoupon',
       'stepQty', 'onQtyChange', 'onQtyInput', 'removeCartItem', 'clearCart']
for s in SYM:
    assert re.search(r'\b' + re.escape(s) + r'\b', out), '符号丢失: ' + s

# ⭐ 装配行裸实参必须在 App.vue 中真实存在
ol = out.split('\n')
def of(pred, start=0):
    for i in range(start, len(ol)):
        if pred(ol[i]):
            return i
    return None

C = of(lambda l: '= useCart({' in l)
M = of(lambda l: '= useMemberPoints({' in l)
U = of(lambda l: '= useCartUi({' in l)
assert C and M and U, '装配行未找到'
assert C < M, '★ 行序错误: useCart 装配(%s) 必须在 useMemberPoints(%s) 之前' % (C, M)
assert C < U, '★ 行序错误: useCart(%s) 必须早于 useCartUi(%s)（它要用 takeAddSource/flyToCart）' % (C, U)

# 只取「顶层逗号」分隔的实参：回调函数体内部的逗号不能当分隔符。
# 顶层逗号的判据：不在 () 或 {} 或 `` 内。
def split_top(body):
    parts, depth, buf, q = [], 0, '', None
    for ch in body:
        if q:
            buf += ch
            if ch == q: q = None
            continue
        if ch in '"\'`':
            q = ch; buf += ch; continue
        if ch in '({[': depth += 1
        elif ch in ')}]': depth -= 1
        if ch == ',' and depth == 0:
            parts.append(buf); buf = ''
        else:
            buf += ch
    if buf.strip(): parts.append(buf)
    return [p.strip() for p in parts if p.strip()]

body = ASM[ASM.index('{') + 1:ASM.rindex('}')]
for part in split_top(body):
    # 跳过改名实参(k: v) —— 冒号右侧另行校验；函数式注入的名字本身仍要校验
    name = part.split(':')[0].strip() if ':' in part else part
    if not name or '=' in name or name.startswith('{') or name.startswith('('):
        continue
    assert re.search(r'^(?:const|let|async function|function)\s+' + re.escape(name) + r'\b', out, re.M) or \
           re.search(r'\b' + re.escape(name) + r'\b\s*[,:]', out), \
        '★ 实参「%s」在 App.vue 中不存在（名字写错了？build 抓不到）' % name
print('  校验 OK: 装配行所有裸实参真实存在；useCart(%s) 早于 useCartUi(%s) 与 useMemberPoints(%s)' % (C, U, M))

# 立即求值依赖行序
for dep in ['cart', 'session', 'isAdmin', 'cartStore']:
    d = of(lambda l: re.match(r'^(?:const|let)\s+' + re.escape(dep) + r'\b', l))
    assert d is not None and d < C, '★ TDZ(%s): %s vs 装配 %s' % (dep, d, C)
# takeAddSource/flyToCart 走 onAddedFeedback 回调（在实参的函数体里，延迟到调用时才求值），
# 所以它们不要求早于 useCart 装配—— 但为稳妥仍要求 useCartUi 装配在其之前。
for dep in ['guestAdd', 'guestRemoveItem', 'guestClear',
            'persistGuestFromItems', 'recomputeCartTotals', 'refreshGuestCartView',
            'flashLimitOfProduct', 'flashSaleOfProduct', 'cartQtyMax']:
    d = of(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
    assert d is not None and d < C, '★ TDZ(%s): 首次出现 %s 装配 %s' % (dep, d, C)
print('  校验 OK: 4 个立即依赖 + 12 个函数式依赖均早于装配点')

# appCtx / adminCtx 仍含这些符号
for s in ['cartLocalTotal', 'cartOriginalSave', 'cartSelectedQty', 'cartTotalSaved',
          'cartMemberDiscount', 'autoSelectCoupon', 'selectCoupon', 'chooseNoCoupon',
          'couponEligible', 'couponShortfall', 'loadCart', 'clearCart', 'removeCartItem',
          'onQtyChange', 'onQtyInput', 'stepQty', 'loadMyCoupons', 'loadMoreMyCoupons',
          'loadUsableCoupons', 'usableCoupons', 'selectedCoupon', 'userOptedOutCoupon',
          'addToCart', 'catalogBasePrice', 'orderPayPreview']:
    assert re.search(r'(?:const (?:adminCtx|appCtx) = \{|appCtx\.)[^;\n]*\b' + re.escape(s) + r'\b', out), \
        '★ ctx 缺符号: ' + s
print('  校验 OK: appCtx/adminCtx 仍含全部消费符号')

open('_b5out5.txt', 'w', encoding='utf-8').write(out)
print('共删除 %d 行 (%d -> %d)' % (len(drop), len(lines), len(out.split('\n'))))
for a, b, name in spans:
    print('  %-20s %d-%d (%d 行)' % (name, a + 1, b + 1, b - a + 1))