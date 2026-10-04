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

# A. reviewForm（438）与productDetail/detailQuantity/currentImageIndex（456/458/459）之间夹着
#    orderDetail(457) / wallet(461) / safeParseSpec(462)—— 只能逐行取，绝不能吃成连续段
a1s = find(lambda l: re.match(r'^const reviewForm = reactive', l))
spans.append((a1s, a1s, 'reviewForm'))

# productDetail / detailQuantity / currentImageIndex 三个单行（跳过 orderDetail、wallet）
for nm in ['productDetail', 'detailQuantity', 'currentImageIndex']:
    k = find(lambda l, p=nm: re.match(r'^const ' + p + r' = ', l))
    assert k is not None, '定位失败: ' + nm
    spans.append((k, k, nm))

# B. safeParseSpec → selectedSkuImage（462-467）
b1s = find(lambda l: re.match(r'^function safeParseSpec', l))
b1e = find(lambda l: l.strip().startswith('// 选中规格的配图'))
spans.append((b1s, b1e - 1, 'safeParseSpec'))

# C. galleryImages → effectiveDetailPrice（469-531）
c_s = find(lambda l: re.match(r'^const galleryImages = computed', l))
c_e = find(lambda l: l.strip().startswith('const recharge = reactive'))
spans.append((c_s, c_e - 1, '图集+规格+取价'))

# D. relatedProducts → selectedSpec + watch(selectedSkuImage)（552-561）
d_s = find(lambda l: re.match(r'^const relatedProducts = ref', l))
d_e = find(lambda l: l.strip().startswith('watch(selectedSkuImage'))
spans.append((d_s, d_e, '停留+选规格+watch'))

# E. 会员积分体系：注释头 → watch(usePoints) 块结束
e_s = find(lambda l: l.strip().startswith('// ---------------- 会员积分体系'))
e_e = find(lambda l: re.match(r'^watch\(usePoints', l))
spans.append((e_s, block_end(e_e), '会员积分体系'))

# F. openReviewForm → submitReview 结束
f_s = find(lambda l: re.match(r'^function openReviewForm', l))
f_e = block_end(find(lambda l: re.match(r'^async function submitReview', l)))
spans.append((f_s, f_e, '评价提交'))

# G. openProductDetail 结束
g_s = find(lambda l: re.match(r'^async function openProductDetail', l))
g_e = block_end(g_s)
spans.append((g_s, g_e, 'openProductDetail'))

# H. reportDwell → changeDetailQty 结束
h_s = find(lambda l: re.match(r'^function reportDwell', l))
h_e = block_end(find(lambda l: re.match(r'^function changeDetailQty', l)))
spans.append((h_s, h_e, '停留上报+返回+数量步进'))

# 末行断言
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    last = lines[k].strip()
    assert last.endswith(('}', '};', '];', ';')), '%s 末行异常: %r' % (name, last[:70])

drop = set()
for a, b, name in spans:
    drop.update(range(a, b + 1))

# 保留块断言：这些必须落在任何删除区之外
KEEP = {
    'orderDetail': '^const orderDetail = reactive',
    'wallet': '^const wallet = reactive',
    'recharge': '^const recharge = reactive',
    'productForm': '^const productForm = reactive',
    'productNavLock': '^let productNavLock = false',
    'syncRoute': '^async function syncRoute',
    'openOrderDetail': '^async function openOrderDetail',
    'addDetailToCart': '^async function addDetailToCart',
    'cartMemberDiscount': '^const cartMemberDiscount = computed',
    'catalogBasePrice': '^function catalogBasePrice',
    'rememberUser': '^function rememberUser',
}
for name, pat in KEEP.items():
    k = find(lambda l, p=pat: re.match(p, l))
    assert k is not None, '保留块定位失败: ' + name
    assert k not in drop, '★ 保留块被误删: ' + name
print('  校验 OK: %d 个保留块全在删除区之外' % len(KEEP))

# 符号校验：搬走的符号必须仍出现在 out 里（由 composable 装配解构提供）
MEM = ['memberProfile', 'memberLedger', 'memberLevels', 'usePoints', 'pointsToUse',
       'TIER_NAMES_FALLBACK', 'TIER_RATES_FALLBACK', 'tierRateForLevel', 'tierNameFor',
       'productMemberView', 'memberUnitView', 'round2', 'loadMemberLevels',
       'loadMemberProfile', 'loadMemberLedger', 'memberPreview', 'balanceSufficient']
DET = ['productDetail', 'detailQuantity', 'currentImageIndex', 'reviewForm',
       'relatedProducts', 'dwellEnterTs', 'dwellProductId', 'dwellSource', 'selectedSpec',
       'safeParseSpec', 'selectedSkuImage', 'galleryImages', 'currentGalleryImage',
       'specDimensions', 'selectedSku', 'selectedSpecText', 'selectedSkuPrice',
       'selectedSkuOriginalPrice', 'effectiveDetailPrice', 'openReviewForm', 'submitReview',
       'openProductDetail', 'reportDwell', 'backFromProduct', 'changeDetailQty']

keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)

MEM_IMP = "import { useMemberPoints } from './composables/useMemberPoints.js';"
DET_IMP = "import { useProductDetail } from './composables/useProductDetail.js';"
MEM_ASM = ("const { memberProfile, memberLedger, memberLevels, usePoints, pointsToUse, "
           "TIER_NAMES_FALLBACK, TIER_RATES_FALLBACK, tierRateForLevel, tierNameFor, "
           "productMemberView, memberUnitView, round2, loadMemberLevels, loadMemberProfile, "
           "loadMemberLedger, memberPreview, balanceSufficient } = useMemberPoints({ api, session, isAdmin, "
           "wallet, effectiveMemberLevel, orderPayPreview, cartMemberDiscount, expressFreight });")
DET_ASM = ("const { productDetail, detailQuantity, currentImageIndex, reviewForm, relatedProducts, "
           "dwellEnterTs, dwellProductId, dwellSource, selectedSpec, safeParseSpec, selectedSkuImage, "
           "galleryImages, currentGalleryImage, specDimensions, selectedSku, selectedSpecText, "
           "selectedSkuPrice, selectedSkuOriginalPrice, effectiveDetailPrice, openReviewForm, submitReview, "
           "openProductDetail, reportDwell, backFromProduct, changeDetailQty } = useProductDetail({ api, run, fail, "
           "isAdmin, router, navigate, activeActivities, reviewedMap, loadRatingSummary, loadProducts, "
           "isProductNavLocked: () => productNavLock, setProductNavLock: (v) => { productNavLock = v; }, "
           "getFlashLimitOfProduct: flashLimitOfProduct, getView: () => view.value });")

# 装配点：放在 useAdminContent 装配之后（那里已在所有依赖之后）
ANCHOR = [i for i, l in enumerate(keep) if '= useAdminContent({' in l]
assert ANCHOR, '装配锚点(useAdminContent)未找到'
i_asm = ANCHOR[0]
keep[i_asm + 1:i_asm + 1] = [
    '// 会员积分体系：档位/资料/流水/结算预览。⚠️ 装配点必须在 orderPayPreview / cartMemberDiscount /',
    '// effectiveMemberLevel / expressFreight / wallet 之后（它们是 ref，装配实参里立即求值）。',
    MEM_ASM,
    '// 商品详情：SKU 规格、图集、停留上报、评价提交。productNavLock 是 App.vue 的 let 变量',
    '//（syncRoute 也要用）→ 留在原地，这里用读写器操作，避免两个来源各管一半。',
    DET_ASM,
]

ANCHOR_IMP = "import { useAdminContent } from './composables/useAdminContent.js';"
i_imp = keep.index(ANCHOR_IMP)
keep[i_imp + 1:i_imp + 1] = [MEM_IMP, DET_IMP]

out = '\n'.join(keep)

for s in MEM + DET:
    assert re.search(r'\b' + re.escape(s) + r'\b', out), '符号丢失: ' + s

# ⚠️ 行序断言：立即求值依赖必须早于各自的装配点
ol = out.split('\n')
def of(pred, start=0):
    for i in range(start, len(ol)):
        if pred(ol[i]):
            return i
    return None

M = of(lambda l: '= useMemberPoints({' in l)
D = of(lambda l: '= useProductDetail({' in l)
assert M and D, '装配行未找到'
# 立即求值（ref/reactive 对象本身）
MEM_NOW = ['wallet', 'effectiveMemberLevel', 'orderPayPreview', 'cartMemberDiscount', 'expressFreight', 'isAdmin', 'session']
for dep in MEM_NOW:
    # const/let/ref/reactive 单行定义，或 function 声明（effectiveMemberLevel 是后者）
    d = of(lambda l: re.match(r'^(?:const|let)\s+' + re.escape(dep) + r'\b', l))
    if d is None:
        d = of(lambda l: re.match(r'^(?:async\s+)?function\s+' + re.escape(dep) + r'\b', l))
    if d is None:
        # 来自 adminCtx / 其他 composable 的多行解构 → 退化为首次出现
        d = of(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
    assert d is not None and d < M, '★ TDZ(%s): 定义 %s 装配 %s' % (dep, d, M)
DET_NOW = ['activeActivities', 'reviewedMap', 'isAdmin', 'run', 'fail']
for dep in DET_NOW:
    d = of(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
    assert d is not None and d < D, '★ TDZ(%s): 首次出现 %s 装配 %s' % (dep, d, D)
# 延迟调用/函数式注入
for dep in ['navigate', 'router', 'loadRatingSummary', 'loadProducts', 'getFlashLimitOfProduct']:
    d = of(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
    assert d is not None, '★ 依赖缺失: ' + dep
print('  校验 OK: 会员 7 个 / 详情 5 个立即依赖均早于装配点；5 个延迟依赖均存在')

# ⭐ 装配行里的每个「裸标识符实参」都必须在 App.vue 中真实存在（不是只有 get 前缀那种名字）
#    真实案例：写了 getFlashLimitOfProduct，但 App.vue 里的实际函数叫 flashLimitOfProduct
#    → vite build 通过、真机整页 ReferenceError。必须逐个核。
for asm_line, tag in [(MEM_ASM, 'useMemberPoints'), (DET_ASM, 'useProductDetail')]:
    body = asm_line[asm_line.index('{') + 1:asm_line.rindex('}')]
    for part in body.split(','):
        part = part.strip()
        if not part or ':' in part or part.startswith('get') or part.startswith('('):
            continue
        # 裸实参 → 必须在 out 中作为定义出现
        assert re.search(r'\b' + re.escape(part) + r'\b\s*[,:]', out) or \
               re.search(r'^(?:const|let|async function|function)\s+' + re.escape(part) + r'\b', out, re.M), \
            '★ %s 实参「%s」在 App.vue 中不存在（写错了名字？vite build 抓不到）' % (tag, part)
print('  校验 OK: 两个装配行的所有裸实参在 App.vue 中都真实存在')

# appCtx / adminCtx 必须仍含这些符号
for s in ['openProductDetail', 'submitReview', 'reviewForm', 'productDetail', 'detailQuantity',
          'changeDetailQty', 'backFromProduct', 'reportDwell', 'specDimensions', 'galleryImages',
          'currentGalleryImage', 'currentImageIndex', 'relatedProducts', 'selectedSpec',
          'selectedSpecText', 'selectedSku', 'memberProfile', 'memberLedger', 'memberLevels',
          'memberPreview', 'balanceSufficient', 'tierRateForLevel', 'tierNameFor', 'usePoints', 'pointsToUse']:
    assert re.search(r'(?:const (?:adminCtx|appCtx) = \{|appCtx\.)[^;\n]*\b' + re.escape(s) + r'\b', out), \
        '★ ctx 缺符号: ' + s
print('  校验 OK: adminCtx/appCtx 仍含全部消费符号')

open('_b5out4.txt', 'w', encoding='utf-8').write(out)
print('共删除 %d 行 (%d -> %d)' % (len(drop), len(lines), len(out.split('\n'))))
for a, b, name in spans:
    print('  %-16s %d-%d (%d 行)' % (name, a + 1, b + 1, b - a + 1))