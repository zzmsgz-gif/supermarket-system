"""批次5-1：从 App.vue 抽出 useChannels + useShopFilters。

保留块（断言它们不在任何删除区内）：
  - syncRoute：跨全部视图（含 admin / pay），且在装配点之前注册 watch
  - filters / products / productsLoading / adminMenu：来自 props 或其它模块，被本批注入而非搬走
  - ADMIN_MENU_KEYS：ctx 契约的一部分，留在 App.vue 传给 composable
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
# A. 运营频道：hotProducts(704) 起 → channelRotatable(737) 止
#    中间 704-708 是 hot/new/relatedProducts/guess/dwell 五个 ref，relatedProducts 属商品详情域要保留
i_hot = find(lambda l: re.match(r'^const hotProducts = ref', l))
i_rel = find(lambda l: re.match(r'^const relatedProducts = ref', l))
i_rot = find(lambda l: re.match(r'^function channelRotatable', l))
i_a_end = i_rot if lines[i_rot].rstrip().endswith('}') else eat_blank(block_end(i_rot))
spans.append((i_hot, i_rel - 1, '频道refs前段'))      # 704-705: hot/new
spans.append((i_rel + 1, i_a_end, '频道refs后段+池子'))  # 707-737: guess/dwell + 池子 + channelPage/loadChannel/rotate/rotatable

# B. 筛选↔URL：注释头 → applyShopQueryFromRoute 结束
i_flt = find(lambda l: l.strip().startswith('// ===== 首页筛选状态 ↔ URL query'))
i_applyShop = find(lambda l: re.match(r'^function applyShopQueryFromRoute', l))
spans.append((i_flt, eat_blank(block_end(i_applyShop)), '筛选URL前段'))

# C. 后台 tab↔URL：注释头 → applyAdminQueryFromRoute 结束（含 watch(adminMenu)）
i_adminTab = find(lambda l: l.strip().startswith('// 后台当前模块也进 URL'))
i_applyAdmin = find(lambda l: re.match(r'^function applyAdminQueryFromRoute', l))
spans.append((i_adminTab, eat_blank(block_end(i_applyAdmin)), '后台tabURL'))

# D. 滚动+加载+点分类：scrollToResultsIfNeeded 注释头 → chooseCategory 结束
i_scroll = find(lambda l: l.strip().startswith('// 点分类后把结果区带进视野'))
i_choose = find(lambda l: re.match(r'^async function chooseCategory', l))
spans.append((i_scroll, eat_blank(block_end(i_choose)), '滚动加载'))

# E. 4 个频道 loader：注释头 → loadDwellRank 结束
i_loaders = find(lambda l: l.strip().startswith('// 四个运营频道：合并成一个标签区块'))
i_dwell = find(lambda l: re.match(r'^async function loadDwellRank', l))
spans.append((i_loaders, eat_blank(block_end(i_dwell)), '频道loader'))

# F. applyFilters + resetFilters
i_applyF = find(lambda l: re.match(r'^function applyFilters', l))
i_resetF = find(lambda l: re.match(r'^function resetFilters', l))
spans.append((i_applyF, eat_blank(block_end(i_resetF)), 'apply/reset'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s' % (spans,))
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    last = lines[k].strip()
    # 末行合法形态：纯 '}' / '});' / 单行函数(以 } 结尾) / 单行 state(const x = ref([]);)
    ok = (last in ('}', '});') or last.endswith('}') or last.endswith('});')
          or re.match(r'^(?:const|let)\s+\w+\s*=', last) is not None)
    assert ok, '%s 末行异常: %r' % (name, last[:70])
print('待删区间:')
for a, b, n in spans:
    print('  %-10s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

# 保留块断言
for nm, pat in [('syncRoute', r'^async function syncRoute'),
                ('ADMIN_MENU_KEYS', r'^const ADMIN_MENU_KEYS'),
                ('filters定义', r'^const filters = reactive'),
                ('products定义', r'^const products = reactive'),
                ('relatedProducts', r'^const relatedProducts = ref')]:
    k = find(lambda l: re.match(pat, l))
    assert not any(a <= k <= b for a, b, _ in spans), '%s 被误纳入删除区(行%d)' % (nm, k)
    print('  保留 %-16s 行%d [OK]' % (nm, k))

# 符号校验：各符号的定义行必须落在「被删除的行集合」里
drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))

CH = ['hotProducts','newProducts','guessProducts','dwellRankProducts','CHANNEL_PAGE',
      'channelPool','channelCursor','channelRefs','channelPage','loadChannel',
      'rotateChannel','channelRotatable','loadHot','loadNew','loadGuess','loadDwellRank','loadHomeChannels']
FL = ['SHOP_FILTER_KEYS','ADMIN_TAB_DEFAULT','filterQueryFromFilters','sameShopQuery',
      'syncShopQuery','applyShopQueryFromRoute','syncAdminQuery','applyAdminQueryFromRoute',
      'scrollToResultsIfNeeded','loadProducts','fetchProducts','chooseCategory',
      'applyFilters','resetFilters']
for s in CH + FL:
    k = find(lambda l: re.match(r'^(?:const|let|async function|function)\s+' + s + r'\b', l))
    assert k in drop, '%s 定义在 %d 但没被删掉' % (s, k)
print('  校验 OK: 频道 %d + 筛选 %d = %d 个符号全在被删区内' % (len(CH), len(FL), len(CH) + len(FL)))

keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

imp = "import { useFlashSale } from './composables/useFlashSale.js';"
assert out.count(imp) == 1
out = out.replace(imp, imp + """
import { useChannels } from './composables/useChannels.js';
import { useShopFilters } from './composables/useShopFilters.js';""")

anchor = "} = useFlashSale();"
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + """

/* ---------------- 运营频道 / 筛选↔URL：已抽为 composable ---------------- */
// 装配点必须在 filters / products / productsLoading / adminMenu / ADMIN_MENU_KEYS 之后。
// scrollToResultsAfterLoad 变成 composable 私有标记，goSearch 改用 markScrollToResults() 置位。
const { hotProducts, newProducts, guessProducts, dwellRankProducts, CHANNEL_PAGE, channelPool, channelCursor, channelRefs, channelPage, loadChannel, rotateChannel, channelRotatable, loadHot, loadNew, loadGuess, loadDwellRank, loadHomeChannels } = useChannels();

const { SHOP_FILTER_KEYS, ADMIN_TAB_DEFAULT, filterQueryFromFilters, sameShopQuery, syncShopQuery, applyShopQueryFromRoute, syncAdminQuery, applyAdminQueryFromRoute, scrollToResultsIfNeeded, loadProducts, fetchProducts, chooseCategory, applyFilters, resetFilters, markScrollToResults } = useShopFilters({ filters, products, productsLoading, adminMenu, adminMenuKeys: ADMIN_MENU_KEYS, getRoute: () => route, router });""")

# goSearch 改用 markScrollToResults()
old_gs = "  scrollToResultsAfterLoad = true;   // 由紧随其后的 loadProducts() 消费（它比在这里猜时机更准）"
assert out.count(old_gs) == 1, 'goSearch 锚点异常'
out = out.replace(old_gs, "  markScrollToResults();   // 由紧随其后的 loadProducts() 消费（它比在这里猜时机更准）")

GONE = ['const hotProducts = ref([]);', 'const CHANNEL_PAGE = 6;', 'function channelPage(',
        'async function loadChannel(', 'function rotateChannel(', 'function channelRotatable(',
        'async function loadHot(', 'async function loadNew(', 'async function loadGuess(',
        'async function loadDwellRank(', 'async function loadHomeChannels(',
        'const SHOP_FILTER_KEYS =', 'const ADMIN_TAB_DEFAULT =', 'function sameShopQuery(',
        'function syncShopQuery(', 'function applyShopQueryFromRoute(', 'function syncAdminQuery(',
        'function applyAdminQueryFromRoute(', 'async function scrollToResultsIfNeeded(',
        'async function loadProducts(', 'async function fetchProducts(', 'async function chooseCategory(',
        'function applyFilters(', 'function resetFilters(', 'let scrollToResultsAfterLoad = false;']
for g in GONE:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useChannels()', 'useShopFilters({']:
    assert out.count(s) == 1, '装配异常: %r' % s
assert 'markScrollToResults();' in out
for s in CH + FL:
    if s == 'loadHomeChannels': continue
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s
# 相关但保留的
for s in ['filters','products','productsLoading','adminMenu','ADMIN_MENU_KEYS','headerKeyword',
          'relatedProducts','syncRoute']:
    assert re.search(r'\b' + s + r'\b', out), '保留符号丢失: ' + s

open('_b5out.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b5out.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
