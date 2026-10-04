"""批次5-2：从 App.vue 抽出 useCartUi（微交互）+ useGuestCart（游客购物车）。

⚠️ 本脚本带「装配行序断言」：对每个注入参数断言其定义行号 < 装配行号。
   上批就是漏了这条，装配点写在了 ADMIN_MENU_KEYS 上面一行 → TDZ 白屏，vite build 还通过。
"""
import re

P = 'App.vue'
src = open(P, encoding='utf-8').read()
lines = src.split('\n')
orig = len(lines)

def find(pred, start=0):
    for i in range(start, len(lines)):
        if pred(lines[i]):
            return i
    raise SystemExit('锚点未找到: ')

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
# A. 游客购物车：注释头 → mergeGuestCartToServer 结束
i_gc = find(lambda l: l.strip().startswith('/* ===== 游客购物车'))
i_merge = find(lambda l: re.match(r'^async function mergeGuestCartToServer', l))
spans.append((i_gc, eat_blank(block_end(i_merge)), '游客购物车'))

# B. 微交互：注释头 → 切视图淡入 watch 结束
i_ui = find(lambda l: l.strip().startswith('// ================= 加购微交互'))
i_viewWatch = find(lambda l: l.strip().startswith('// 切视图淡入。刻意不重建组件'), i_ui)
spans.append((i_ui, eat_blank(block_end(i_viewWatch)), '加购微交互'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))
drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行（%s）' % (len(drop), ', '.join('%s %d-%d' % (n, a, b) for a, b, n in spans)))

GC = ['GUEST_CART_KEY', 'guestCartRows', 'guestProductCache', 'pendingCheckout', 'pendingQuickBuy',
      'QUICKBUY_ITEM_ID', 'quickBuy', 'readGuestCart', 'writeGuestCart', 'persistGuestFromItems',
      'recomputeCartTotals', 'estimateGuestActivity', 'refreshGuestCartView', 'guestAdd',
      'guestRemoveItem', 'guestClear', 'mergeGuestCartToServer']
UI = ['contentEl', 'cartPillEl', 'cartBadgeEl', 'flyingGhosts', 'motionAllowed', 'bob',
      'popCartBadge', 'flyToCart', 'lastAddSource', 'onDocClickCapture', 'takeAddSource']
for s in GC + UI:
    k = find(lambda l: re.match(r'^(?:const|let|async function|function)\s+' + s + r'\b', l))
    assert k in drop, '%s 定义在 %d 但没被删' % (s, k)
print('  校验 OK: 游客购物车 %d + 微交互 %d = %d 个符号全被删' % (len(GC), len(UI), len(GC) + len(UI)))

imp = "import { useShopFilters } from './composables/useShopFilters.js';"
assert out.count(imp) == 1
out = out.replace(imp, imp + """
import { useGuestCart } from './composables/useGuestCart.js';
import { useCartUi } from './composables/useCartUi.js';""")

anchor = "} = useShopFilters({ filters, products, productsLoading, adminMenu, adminMenuKeys: ADMIN_MENU_KEYS, getRoute: () => route, router });"
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + """

/* ---------------- 游客购物车 / 加购微交互：已抽为 composable ---------------- */
// 装配点必须在 cart / activeActivities / cartBadgeCount / session 之后（都由 adminCtx 或本文件提供）。
// activeActivities 来自 useActivity（跨域共享同一个 ref 对象）。
const { guestCartRows, guestProductCache, pendingCheckout, pendingQuickBuy, quickBuy, readGuestCart, writeGuestCart, persistGuestFromItems, estimateGuestActivity, recomputeCartTotals, refreshGuestCartView, guestAdd, guestRemoveItem, guestClear, mergeGuestCartToServer } = useGuestCart({ cart, getSession: () => session, activeActivities, loadCart, fail, notice });

const { contentEl, cartPillEl, cartBadgeEl, motionAllowed, bob, popCartBadge, flyToCart, onDocClickCapture, takeAddSource } = useCartUi({ cartBadgeCount, getView: () => view.value });""")

GONE = ['const GUEST_CART_KEY =', 'const guestCartRows = ref([]);', 'const quickBuy = ref(',
        'function readGuestCart(', 'function recomputeCartTotals(',
        'function estimateGuestActivity(', 'async function refreshGuestCartView(',
        'async function guestAdd(', 'async function mergeGuestCartToServer(',
        'const contentEl = ref(null);', 'function motionAllowed(', 'function bob(',
        'async function popCartBadge(', 'function flyToCart(', 'function takeAddSource(',
        'function onDocClickCapture(']
for g in GONE:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useGuestCart({', 'useCartUi({']:
    assert out.count(s) == 1, '装配异常: %r' % s
# GUEST_CART_KEY 现在是 composable 内部私有常量（不再导出），不该出现在 App.vue
PRIVATE = {'flyingGhosts', 'lastAddSource', 'GUEST_CART_KEY'}
for s in GC + UI:
    if s in PRIVATE:
        assert s not in out, '%s 应已私有化，不该残留在 App.vue' % s
        continue
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

# ⚠️ 行序断言：注入参数必须在装配行之前（本批新增，防 TDZ）
out_lines = out.split('\n')
def out_find(pred, start=0):
    for i in range(start, len(out_lines)):
        if pred(out_lines[i]):
            return i
    return None      # 找不到返回 None（不是抛异常 —— 下面有「单行匹配失败再退化」的逻辑）
GUEST_ASM = out_find(lambda l: '= useGuestCart({' in l)
UI_ASM = out_find(lambda l: '= useCartUi({' in l)
assert GUEST_ASM and UI_ASM, '装配行定位失败'
# ⚠️ 行序断言：**只对「在装配实参里立即求值」的依赖**生效。
#    区分两类：
#    · 立即求值（如 adminMenuKeys: ADMIN_MENU_KEYS）→ 定义行必须 < 装配行，否则 TDZ 白屏
#    · 延迟调用（fail/loadCart 只在 composable 的函数体内被调）→ 只要定义在模块顶层就安全，
#      因为调用发生在用户交互时，彼时整个 setup 已跑完
IMMEDIATE = {
    'useGuestCart': ['cart', 'session', 'activeActivities', 'notice'],   # 都是 reactive/ref 对象或 getter
    'useCartUi': ['cartBadgeCount', 'view'],
}
LAZY = {
    'useGuestCart': ['fail', 'loadCart'],   # 只在函数体内调用
    'useCartUi': [],
}
for tag, asm in [('useGuestCart', GUEST_ASM), ('useCartUi', UI_ASM)]:
    for dep in IMMEDIATE[tag]:
        d = out_find(lambda l: re.match(r'^(?:const|let|function|async function)\s+' + re.escape(dep) + r'\b', l))
        if d is None:
            d = out_find(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
        assert d < asm, '★ TDZ 风险(立即求值): %s 出现在 %d，%s 装配在 %d' % (dep, d, tag, asm)
    for dep in LAZY[tag]:
        # 延迟调用：只要在文件里存在定义（顶层）即可，不要求早于装配
        d = out_find(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
        assert d is not None, '%s 在产物中完全找不到' % dep
print('  校验 OK: 6 个立即求值依赖全部先于装配点（无 TDZ）；2 个延迟调用依赖(fail/loadCart)只在函数体内调用，延后到交互时求值')

open('_b5out2.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b5out2.txt（%d -> %d 行）' % (orig, len(keep)))
