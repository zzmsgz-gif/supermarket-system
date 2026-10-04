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
    """从 start 行起，按大括号配平找到块末行"""
    depth = 0
    started = False
    for i in range(start, len(lines)):
        s = lines[i]
        # 去掉字符串字面量，避免 '{' 计数被内容干扰
        s2 = re.sub(r"'[^']*'|\"[^\"]*\"|`[^`]*`", '', s)
        for ch in s2:
            if ch == '{':
                depth += 1
                started = True
            elif ch == '}':
                depth -= 1
        if started and depth == 0:
            return eat_blank(i)
    raise SystemExit('block_end: 配平失败')


spans = []

# A. state 区：adminAnnouncements → bannerUploading（连续 11 个声明，中间只有注释）
a_s = find(lambda l: re.match(r'^const adminAnnouncements = ref', l))
a_e = find(lambda l: re.match(r'^const bannerUploading = ref', l))
spans.append((a_s, a_e, '后台内容state'))

# B. 函数区：公告管理注释头 → deleteBanner 结束
b_s = find(lambda l: l.strip().startswith('/* ===== 公告管理（后台）'))
b_e = block_end(find(lambda l: re.match(r'^async function deleteBanner', l)))
spans.append((b_s, b_e, '公告+热搜+轮播函数区'))

# 注入
IMP = "import { useAdminContent } from './composables/useAdminContent.js';"
ANCHOR_IMPORT = "import { useCartUi } from './composables/useCartUi.js';"
assert ANCHOR_IMPORT in '\n'.join(lines), 'import 锚点未找到'
out_lines = list(lines)
i_imp = next(i for i, l in enumerate(out_lines) if l.strip() == ANCHOR_IMPORT)
out_lines.insert(i_imp + 1, IMP)

# 装配点：useCartUi 装配之后（那里已在所有依赖之后）
ASM = ("const { adminAnnouncements, announcementForm, announcementFormOpen, loadAdminAnnouncements, "
       "openAnnouncementForm, closeAnnouncementForm, saveAnnouncement, toggleAnnouncement, deleteAnnouncement, "
       "adminHotSearches, hotSearchForm, hotSearchFormOpen, loadAdminHotSearches, openHotSearchForm, "
       "closeHotSearchForm, saveHotSearch, toggleHotSearch, deleteHotSearch, adminBanners, bannerForm, "
       "bannerFormOpen, bannerUploading, loadAdminBanners, openBannerForm, closeBannerForm, saveBanner, "
       "toggleBanner, deleteBanner } = useAdminContent({ api, isAdmin, run, showAlert, askConfirm, loadHotSearches });")
ANCHOR_ASM = [i for i, l in enumerate(out_lines) if '= useCartUi({' in l]
assert ANCHOR_ASM, '装配锚点(useCartUi)未找到'
i_asm = ANCHOR_ASM[0]
# 插到该装配块结束（找到配平的 ');' 行）
depth = 0
started = False
for j in range(i_asm, len(out_lines)):
    s = re.sub(r"'[^']*'|\"[^\"]*\"|`[^`]*`", '', out_lines[j])
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
    raise SystemExit('useCartUi 装配块配平失败')

out_lines.insert(i_asm_end + 1, ASM)
out = '\n'.join(out_lines)

# 删除区间（在原文件坐标系上）
drop = set()
for a, b, name in spans:
    drop.update(range(a, b + 1))

# 每段末行断言：state 段以 ';' 结尾（单行声明），函数段以 '}' 结尾
for a, b, name in spans:
    k = b
    while k > a and lines[k].strip() == '':
        k -= 1
    last = lines[k].strip()
    ok = last.endswith('}') or last.endswith('};') or last.endswith('];') or last.endswith(';')
    assert ok, '%s 末行异常: %r' % (name, last[:70])

# 符号校验：搬走的 30 个符号必须仍然出现在 out 里（由 composable 装配解构提供）
SYM = ['adminAnnouncements', 'announcementForm', 'announcementFormOpen', 'loadAdminAnnouncements',
       'openAnnouncementForm', 'closeAnnouncementForm', 'saveAnnouncement', 'toggleAnnouncement', 'deleteAnnouncement',
       'adminHotSearches', 'hotSearchForm', 'hotSearchFormOpen', 'loadAdminHotSearches', 'openHotSearchForm',
       'closeHotSearchForm', 'saveHotSearch', 'toggleHotSearch', 'deleteHotSearch',
       'adminBanners', 'bannerForm', 'bannerFormOpen', 'bannerUploading', 'loadAdminBanners',
       'openBannerForm', 'closeBannerForm', 'saveBanner', 'toggleBanner', 'deleteBanner']
for s in SYM:
    assert re.search(r'\b' + re.escape(s) + r'\b', out), '符号丢失: ' + s

# ⚠️ 行序断言：注入参数分两类
#   立即求值（isAdmin 是 ref 对象本身，必须已定义）→ 必须在装配点之前
#   延迟调用（api/run/showAlert/askConfirm/loadHotSearches 只在函数体内被调）
ol = out.split('\n')
def of(pred, start=0):
    for i in range(start, len(ol)):
        if pred(ol[i]):
            return i
    return None

G = of(lambda l: '= useAdminContent({' in l)
assert G is not None, 'useAdminContent 装配行未找到'
# 立即求值的依赖
for dep in ['isAdmin']:
    d = of(lambda l: re.match(r'^(?:const|let)\s+' + re.escape(dep) + r'\b', l))
    assert d is not None and d < G, '★ TDZ 风险: %s 定义在 %s，装配在 %s' % (dep, d, G)
# 延迟调用的依赖：只需在模块顶层存在（可能是 adminCtx 多行解构里的键，单行匹配不到）
for dep in ['api', 'run', 'showAlert', 'askConfirm', 'loadHotSearches']:
    d = of(lambda l: re.search(r'\b' + re.escape(dep) + r'\b', l))
    assert d is not None, '★ 依赖缺失: %s' % dep
print('  校验 OK: isAdmin 早于装配点；4 个延迟依赖均存在')

# adminCtx / appCtx 必须仍含这些符号（AdminPanel 靠它们消费）
for s in ['adminAnnouncements', 'adminBanners', 'saveBanner', 'toggleBanner', 'deleteBanner',
          'adminHotSearches', 'hotSearchForm', 'saveHotSearch', 'toggleHotSearch', 'deleteHotSearch']:
    assert re.search(r'const (?:adminCtx|appCtx) = \{[^}]*\b' + re.escape(s) + r'\b', out, re.S), \
        '★ ctx 缺符号: ' + s
print('  校验 OK: adminCtx/appCtx 仍含全部消费符号')

keep = [l for i, l in enumerate(lines) if i not in drop]
res = '\n'.join(keep)
# 把 import + 装配应用到删除后的结果
res_lines = res.split('\n')
i_imp2 = next(i for i, l in enumerate(res_lines) if l.strip() == ANCHOR_IMPORT)
res_lines.insert(i_imp2 + 1, IMP)
i_asm2 = next(i for i, l in enumerate(res_lines) if '= useCartUi({' in l)
res_lines.insert(i_asm2 + 2, ASM)   # +2：useCartUi 装配是单行，下一行即插入点
res = '\n'.join(res_lines)

open('_b5out3.txt', 'w', encoding='utf-8').write(res)
print('共删除 %d 行 (%d -> %d)' % (len(drop), len(lines), len(res.split('\n'))))
for a, b, name in spans:
    print('  %s: %d-%d (%d 行)' % (name, a + 1, b + 1, b - a + 1))