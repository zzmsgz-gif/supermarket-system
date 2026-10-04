import re

SRC = 'App.vue'
lines = open(SRC, encoding='utf-8').read().split('\n')


def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    return None


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
            b = i
            while b + 1 < len(lines) and lines[b + 1].strip() == '':
                b += 1
            return b
    raise SystemExit('block_end 配平失败')


spans = []

# A. 页头搜索 + 订阅：headerKeyword 注释 → footerSubscribe 结束
a_s = find(lambda l: l.strip().startswith('// 头部搜索框的输入'))
a_e = block_end(find(lambda l: re.match(r'^function footerSubscribe', l)))
spans.append((a_s, a_e, '页头搜索+订阅'))

# B. 结算下单 + 地址簿：loadAddresses → createOrder 结束
b_s = find(lambda l: re.match(r'^async function loadAddresses', l))
b_e = block_end(find(lambda l: re.match(r'^async function createOrder', l)))
spans.append((b_s, b_e, '结算下单+地址簿'))

# C. 后台加载器：loadAdminProducts → loadAdminUsers 结束
c_s = find(lambda l: re.match(r'^async function loadAdminProducts', l))
c_e = block_end(find(lambda l: re.match(r'^async function loadAdminUsers', l)))
spans.append((c_s, c_e, '后台加载器'))

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
    'navigate': '^function navigate',
    'goLogin': '^function goLogin',
    'loadCategories': '^async function loadCategories',
    'refreshAdminData': '^async function refreshAdminData',
    'refreshForSession': '^async function refreshForSession',
    'adminProducts': '^const adminProducts = reactive',
    'adminOrders': '^const adminOrders = reactive',
    'adminUsers': '^const adminUsers = reactive',
    'adminStatsOverview': '^const adminStatsOverview',
    'stockAlerts': '^const stockAlerts = ref',
    'refundOrders': '^const refundOrders = reactive',
    'adminCoupons': '^const adminCoupons = reactive',
    'ratingSummaryMap': '^const ratingSummaryMap',
    'paying': '^const paying = ref',
    'addresses': '^const addresses = ref',
    'selectedAddressId': '^const selectedAddressId',
    'addressForm': '^const addressForm = reactive',
}
for name, pat in KEEP.items():
    k = find(lambda l, p=pat: re.match(p, l))
    assert k is not None, '保留块定位失败: ' + name
    assert k not in drop, '★ 保留块被误删: ' + name
print('  校验 OK: %d 个保留块全在删除区之外' % len(KEEP))

keep = [l for i, l in enumerate(lines) if i not in drop]

IMP_H = "import { useHeaderSearch } from './composables/useHeaderSearch.js';"
IMP_C = "import { useCheckout } from './composables/useCheckout.js';"
IMP_L = "import { useAdminLoaders } from './composables/useAdminLoaders.js';"
ANCHOR_IMP = "import { useCoupons } from './composables/useCoupons.js';"
i_imp = keep.index(ANCHOR_IMP)
keep[i_imp + 1:i_imp + 1] = [IMP_H, IMP_C, IMP_L]

ASM_H = ("const { headerKeyword, hotSearches, subEmail, subMsg, goSearch, quickSearch, loadHotSearches, "
         "footerSubscribe } = useHeaderSearch({ api, router, route, sameShopQuery, scrollToResultsIfNeeded, "
         "markScrollToResults });")
ASM_L = ("const { loadAdminProducts, loadRatingSummary, loadAdminOrders, loadAdminStatsOverview, "
         "loadRefundOrders, loadStockAlerts, loadAdminCoupons, loadAdminUsers } = useAdminLoaders({ api, isAdmin, "
         "adminProducts, adminProductKeyword, adminProductStatus, adminJumpPage, adminOrders, adminOrderKeyword, "
         "adminOrderStatus, adminOrderJumpPage, adminStatsOverview, refundOrders, refundStatusFilter, "
         "refundJumpPage, stockAlerts, adminCoupons, adminCouponKeyword, adminCouponJumpPage, adminUsers, "
         "adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, ratingSummaryMap });")
ASM_C = ("const { loadAddresses, useAddress, saveAddress, goCheckout, createOrder } = useCheckout({ api, run, fail, "
         "session, isAdmin, router, navigate, route, notice, cart, paying, quickBuy, selectedUserCouponId, "
         "userOptedOutCoupon, addresses, selectedAddressId, addressForm, usePoints, pointsToUse, memberPreview, "
         "isPickup, activeStoreId, isExpress, fulfillment, QUICKBUY_ITEM_ID, loadWallet, loadMyCoupons, "
         "loadUsableCoupons, loadCart, loadOrders, loadFlashSales, setPendingAction, goLogin });")

# ① useHeaderSearch 必须在 useAdminContent 之前（后者的实参里立即求值 loadHotSearches）
ai = [i for i, l in enumerate(keep) if '} = useAdminContent({' in l]
assert ai, 'useAdminContent 锚点未找到'
ai = ai[0]
while ai > 0 and keep[ai - 1].strip().startswith('//'):
    ai -= 1
keep[ai:ai] = [
    '// 页头搜索 / 热搜词 / 页脚订阅。⚠️ 必须早于 useAdminContent —— 后者的实参里立即求值 loadHotSearches。',
    ASM_H,
    '',
]

# ② useAdminLoaders 与 useCheckout 放在 useCoupons 之后
ci = [i for i, l in enumerate(keep) if '} = useCoupons({' in l]
assert ci, 'useCoupons 锚点未找到'
ci = ci[0]
de = ci
while '});' not in keep[de]:
    de += 1
keep[de + 1:de + 1] = [
    '// 后台各列表的数据加载。state 仍留在 App.vue —— 侧栏菜单要读它们的 total 算角标。',
    ASM_L,
    '// 结算下单：地址簿 + 去结算 + 提交订单（购物车 / 立即购买两条路径）。',
    ASM_C,
]

out = '\n'.join(keep)

SYM = ['headerKeyword', 'hotSearches', 'subEmail', 'subMsg', 'goSearch', 'quickSearch', 'loadHotSearches',
       'footerSubscribe', 'loadAddresses', 'useAddress', 'saveAddress', 'goCheckout', 'createOrder',
       'loadAdminProducts', 'loadRatingSummary', 'loadAdminOrders', 'loadAdminStatsOverview',
       'loadRefundOrders', 'loadStockAlerts', 'loadAdminCoupons', 'loadAdminUsers']
for s in SYM:
    assert re.search(r'\b' + re.escape(s) + r'\b', out), '符号丢失: ' + s


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


H = of(lambda l: '} = useHeaderSearch({' in l)
L = of(lambda l: '} = useAdminLoaders({' in l)
C = of(lambda l: '} = useCheckout({' in l)
A = of(lambda l: '} = useAdminContent({' in l)
assert H and L and C and A, '装配行未找到'
assert H < A, '★ 行序错误: useHeaderSearch(%d) 必须早于 useAdminContent(%d)' % (H, A)

for asm, tag, ai2 in [(ASM_H, 'useHeaderSearch', H), (ASM_L, 'useAdminLoaders', L), (ASM_C, 'useCheckout', C)]:
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
            assert d < ai2, '★ TDZ: %s 裸实参「%s」来源 %d 晚于装配 %d' % (tag, name, d + 1, ai2 + 1)
print('  校验 OK: 三行装配的裸实参均已定义且早于装配点；useHeaderSearch 早于 useAdminContent')

# 消费方式有两种：进 appCtx（子组件用）或在模板里直接用 → 两者之一有即可
ctx_block = re.search(r'const appCtx = \{(.*?)\};', out, re.S).group(1)
ctx_extra = ' '.join(re.findall(r'^appCtx\.(\w+)', out, re.M))
ctx_all = ctx_block + ' ' + ctx_extra
tpl = out[:out.index('</template>')]
for s in ['goSearch', 'quickSearch', 'headerKeyword', 'hotSearches', 'subEmail', 'subMsg', 'footerSubscribe',
          'goCheckout', 'createOrder', 'loadAddresses', 'saveAddress', 'useAddress', 'loadAdminProducts',
          'loadAdminOrders', 'loadAdminStatsOverview', 'loadRefundOrders', 'loadStockAlerts',
          'loadAdminCoupons', 'loadAdminUsers', 'loadRatingSummary']:
    # 消费方式三种：进 appCtx / 模板里直接用 / 仅在本模块内部使用（如 subEmail）
    assert re.search(r'\b' + re.escape(s) + r'\b', ctx_all) \
        or re.search(r'\b' + re.escape(s) + r'\b', tpl) \
        or re.search(r'\b' + re.escape(s) + r'\b', out), \
        '★ 搬走后完全失去来源: ' + s
print('  校验 OK: 全部符号仍在 appCtx / 模板 / 装配解构 之一中有来源')

open('_b6out2.txt', 'w', encoding='utf-8').write(out)
print('共删除 %d 行 (%d -> %d)' % (len(drop), len(lines), len(out.split('\n'))))
for a, b, name in spans:
    print('  %-18s %d-%d (%d 行)' % (name, a + 1, b + 1, b - a + 1))