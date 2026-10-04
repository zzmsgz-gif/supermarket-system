"""
批次3：从 App.vue 抽出 useActivity / useFavorites / useFlashSale。

三个域的区间（用锚点 + 配平定位）：
- 活动域：ensureActiveActivities 注释头 → cartActivityProgress computed 闭合
  ⚠️ activeActivities 的 state 在 858（域外！），要单独删。
- 收藏域：收藏注释头 → toggleFavorite 闭合
- 秒杀域：秒杀注释头 → flashLimitMessage 闭合
  ⚠️ flashSales/nowTick/flashTickTimer state 都在域内（1268-1272），随区间一起删。

装配点在 ROUTE_VIEWS 之前（与批次1、2 同一处），那里 fail/run/askConfirm/
cartLocalTotal/session/isAdmin/route/goLogin/navigate/notice 都已定义。
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
    raise SystemExit('锚点未找到: ' + repr(lines[start][:50]))

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

# ---- 活动域 ----
i_act = find(lambda l: l.strip().startswith('// 活动规则缓存拉取'))
i_act_end_fn = find(lambda l: re.match(r'^const cartActivityProgress = computed', l))
i_act_end = eat_blank(block_end(i_act_end_fn))
spans.append((i_act, i_act_end, '活动域'))

# activeActivities state（域外，单独一小段）
i_aa = find(lambda l: re.match(r'^const activeActivities = ref\(\[\]\);', l))
assert i_aa < i_act or i_aa > i_act_end, 'activeActivities 应在活动域外，实际 %d' % i_aa
spans.append((i_aa, i_aa, 'activeActivities'))

# ---- 收藏域 ----
i_fav = find(lambda l: l.strip().startswith('/* ---------------- 收藏 + 降价提醒'))
i_toggle = find(lambda l: re.match(r'^async function toggleFavorite', l))
i_fav_end = eat_blank(block_end(i_toggle))
spans.append((i_fav, i_fav_end, '收藏域'))

# ---- 秒杀域 ----
i_fs = find(lambda l: l.strip().startswith('// ===== 限时秒杀 ====='))
i_flm = find(lambda l: re.match(r'^function flashLimitMessage', l))
i_fs_end = eat_blank(block_end(i_flm))
spans.append((i_fs, i_fs_end, '秒杀域'))

spans.sort()
for a, b in zip(spans, spans[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s / %s' % (a, b))

# 边界硬校验
BOUNDS = {
    '活动域': '// 活动规则缓存拉取',
    'activeActivities': 'const activeActivities = ref([]);',
    '收藏域': '/* ---------------- 收藏 + 降价提醒',
    '秒杀域': '// ===== 限时秒杀 =====',
}
for a, b, name in spans:
    assert BOUNDS[name] in lines[a], '%s 起点异常: %r' % (name, lines[a][:60])
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    if name != 'activeActivities':
        # computed(() => {...}) 的闭合是 '});'，普通函数是 '}'
        assert lines[k].strip() in ('}', '});'), '%s 末行异常: %r' % (name, lines[k][:60])
print('待删区间:')
for a, b, n in spans:
    print('  %-20s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

drop = set()
for a, b, _ in spans:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

# ---- import ----
imp = "import { useRecharge } from './composables/useRecharge';"
assert out.count(imp) == 1, 'import 锚点异常 x%d' % out.count(imp)
out = out.replace(imp, imp + """
import { useActivity } from './composables/useActivity.js';
import { useFavorites } from './composables/useFavorites.js';
import { useFlashSale } from './composables/useFlashSale.js';""")

# ---- 装配 ----
asm = "const ROUTE_VIEWS = ["
assert out.count(asm) == 1, '装配锚点异常'
assembly = """/* ---------------- 活动 / 收藏 / 秒杀：已抽为 composable ---------------- */
// 装配点必须在 cartLocalTotal / session / isAdmin / route / goLogin / notice / fail 之后，
// 且被抽走的符号在本行之前无任何同步求值（无 immediate watch / watchEffect），无 TDZ 风险。
// ⚠️ activeActivities 虽然被游客购物车(estimateGuestActivity)与商品详情页读写，
//    但解构拿到的是**同一个 ref 对象**，跨域读写天然可用，无需额外注入。
const { activeActivities, noticeIndex, noticeList, rotatingNotice, topActivity, cartActivityProgress, ensureActiveActivities, activitySlogan, activityNoticeText, activityOffset, activityMatches, productActivityTag, startNoticeRotation, stopNoticeRotation } = useActivity({ getCartLocalTotal: () => cartLocalTotal.value });

const { favoriteIds, favorites, priceAlerts, alertUnread, pendingFavorite, isFavorite, setFavoriteLocal, loadFavoriteIds, loadFavorites, loadPriceAlerts, loadFavoritesPage, loadPriceAlertsPage, loadAlertUnread, markAlertsRead, toggleFavorite } = useFavorites({ getSession: () => session, isAdmin, getRoute: () => route, goLogin, notice, fail, getView: () => view.value });

const { flashSales, nowTick, runningFlashSales, FLASH_TICK_WINDOW_SECONDS, loadFlashSales, flashRemaining, flashDeadlineText, formatDuration, startFlashTick, stopFlashTick, flashSaleOfProduct, flashLimitOfProduct, cartQtyMax, cartQtyCapped, flashSplitNote, isFlashSplit, flashLimitMessage } = useFlashSale();

"""
out = out.replace(asm, assembly + asm)

# ---- 生命周期钩子里的定时器改为调用 composable 方法 ----
old_mount = "  noticeTimer = setInterval(() => { if (noticeList.value.length > 1) noticeIndex.value += 1; }, 4000);\n  startFlashTick();"
assert out.count(old_mount) == 1, 'onMounted 锚点异常 x%d' % out.count(old_mount)
out = out.replace(old_mount, "  startNoticeRotation();\n  startFlashTick();")

old_unmount = "  clearInterval(noticeTimer);\n  if (flashTickTimer) { clearInterval(flashTickTimer); flashTickTimer = null; }"
assert out.count(old_unmount) == 1, 'onBeforeUnmount 锚点异常 x%d' % out.count(old_unmount)
out = out.replace(old_unmount, "  stopNoticeRotation();\n  stopFlashTick();")

# ---- 硬断言 ----
gone = [
    'async function ensureActiveActivities(', 'function activitySlogan(',
    'const cartActivityProgress = computed(', 'const activeActivities = ref([]);',
    'const favoriteIds = ref([]);', 'async function toggleFavorite(',
    'const flashSales = ref([]);', 'let flashTickTimer = null;',
    'async function loadFlashSales(', 'function flashLimitMessage(',
    'let noticeTimer = null;', 'const noticeIndex = ref(0);',
]
for g in gone:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useActivity({', 'useFavorites({', 'useFlashSale()',
          'startNoticeRotation();', 'stopNoticeRotation();', 'stopFlashTick();']:
    assert out.count(s) >= 1, '装配缺失: %r' % s
# ctx 与模板用到的符号必须仍在
for s in ['activeActivities','noticeIndex','noticeList','rotatingNotice','topActivity',
          'cartActivityProgress','ensureActiveActivities','activitySlogan','activityNoticeText',
          'activityOffset','activityMatches','productActivityTag',
          'favoriteIds','favorites','priceAlerts','alertUnread','pendingFavorite',
          'isFavorite','setFavoriteLocal','loadFavoriteIds','loadFavorites','loadPriceAlerts',
          'loadFavoritesPage','loadPriceAlertsPage','loadAlertUnread','markAlertsRead','toggleFavorite',
          'flashSales','nowTick','runningFlashSales','FLASH_TICK_WINDOW_SECONDS','loadFlashSales',
          'flashRemaining','flashDeadlineText','formatDuration','startFlashTick',
          'flashSaleOfProduct','flashLimitOfProduct','cartQtyMax','cartQtyCapped',
          'flashSplitNote','isFlashSplit','flashLimitMessage']:
    assert re.search(r'\b' + s + r'\b', out), '符号丢失: ' + s

open('_b3out.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b3out.txt（原 %d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
