"""批次4-6：一次拆完剩余 6 个模块（零交叉依赖，已验证）。

保留块（绝不能删）：
  - adminMenuItems / adminMenuGroups / currentAdminMenu：引用所有模块的状态做侧栏聚合
  - adminIcon / adminIcons：UI 基础设施，被 adminMenuItems 引用
  - 各种 *TotalPages computed 若被本模块搬走则由 composable 自带
"""
import re

P = 'AdminPanel.vue'
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

def span(start_pred, end_pred, name, from_=0):
    a = find(start_pred, from_)
    b = eat_blank(block_end(find(end_pred, a)))
    return (a, b, name)

S = []
# 1) 分类+库存分页：categoryKeyword → goStockPage 结束
S.append(span(lambda l: re.match(r'^const categoryKeyword = ref', l),
              lambda l: re.match(r'^function goStockPage', l), '分类库存分页'))
# 2) 分类表单（单行）—— 归入 useAdminCategoryStock
S.append((find(lambda l: re.match(r'^const categoryForm = reactive', l)),
           find(lambda l: re.match(r'^const categoryForm = reactive', l)), 'categoryForm'))
# 3) 公告工具+入库：noticeTypeLabel → submitStock 结束
S.append(span(lambda l: re.match(r'^function noticeTypeLabel', l),
              lambda l: re.match(r'^async function submitStock', l), '公告+入库'))
# 4) 订单发货+操作：searchAdminOrders → submitShip 结束；completeAdminOrder → cancelAdminOrder 结束
S.append(span(lambda l: re.match(r'^async function searchAdminOrders', l),
              lambda l: re.match(r'^async function submitShip', l), '订单发货'))
S.append(span(lambda l: re.match(r'^async function completeAdminOrder', l),
              lambda l: re.match(r'^async function cancelAdminOrder', l), '订单操作'))
# 5) 退款：searchRefunds → submitRefundReview 结束
S.append(span(lambda l: re.match(r'^async function searchRefunds', l),
              lambda l: re.match(r'^async function submitRefundReview', l), '退款审核'))
# 6) 优惠券：couponForm 单行 + searchAdminCoupons → toggleCoupon 结束
S.append((find(lambda l: re.match(r'^const couponForm = reactive', l)),
           find(lambda l: re.match(r'^const couponForm = reactive', l)), 'couponForm'))
S.append(span(lambda l: re.match(r'^async function searchAdminCoupons', l),
              lambda l: re.match(r'^async function toggleCoupon', l), '优惠券'))
# 7) 分类保存 + 用户：saveCategory 单函数；searchAdminUsers → toggleUser 结束
S.append((find(lambda l: re.match(r'^async function saveCategory', l)),
           eat_blank(block_end(find(lambda l: re.match(r'^async function saveCategory', l)))), 'saveCategory'))
S.append(span(lambda l: re.match(r'^async function searchAdminUsers', l),
              lambda l: re.match(r'^async function toggleUser', l), '用户管理'))
# 8) 三个 *TotalPages（订单/退款/用户/优惠券的由 composable 自带，这几个原本在 1258-1264）
S.append((find(lambda l: re.match(r'^const shipForm = reactive', l)),
           find(lambda l: re.match(r'^const adminCouponTotalPages = computed', l)), 'shipForm+TotalPages'))

S.sort()
for a, b in zip(S, S[1:]):
    if a[1] >= b[0]:
        raise SystemExit('区间重叠: %s / %s' % (a, b))
print('待删区间:')
for a, b, n in S:
    print('  %-18s %5d-%5d (%d行)' % (n, a, b, b - a + 1))

# 保留块断言
for nm, pat in [('adminMenuItems', r'^const adminMenuItems = computed'),
                ('adminMenuGroups', r'^const adminMenuGroups = computed'),
                ('currentAdminMenu', r'^const currentAdminMenu = computed'),
                ('adminIcons', r'^const adminIcons = ')]:
    k = find(lambda l: re.match(pat, l))
    assert not any(a <= k <= b for a, b, _ in S), '%s 被误纳入删除区' % nm
    print('  保留 %-18s 行%d [OK]' % (nm, k))

drop = set()
for a, b, _ in S:
    drop.update(range(a, b + 1))
keep = [l for i, l in enumerate(lines) if i not in drop]
out = '\n'.join(keep)
print('共删除 %d 行' % len(drop))

LAST_IMPORT = "import { useAdminReviews } from '../composables/useAdminReviews.js';"
assert out.count(LAST_IMPORT) == 1
out = out.replace(LAST_IMPORT, LAST_IMPORT + """
import { useAdminCategoryStockPaging } from '../composables/useAdminCategoryStockPaging.js';
import { useAdminCategoryStock } from '../composables/useAdminCategoryStock.js';
import { useAdminOrders } from '../composables/useAdminOrders.js';
import { useAdminRefunds } from '../composables/useAdminRefunds.js';
import { useAdminCoupons } from '../composables/useAdminCoupons.js';
import { useAdminUsers } from '../composables/useAdminUsers.js';""")

anchor = "} = useAdminReviews({ run, askConfirm, showAlert });"
assert out.count(anchor) == 1
out = out.replace(anchor, anchor + """

// ===== 分类 / 库存 / 订单 / 退款 / 优惠券 / 用户：已抽为 composable =====
const { categoryKeyword, categoryPage, categorySize, categoryJumpPage, categoryFiltered, categoryTotalPages, categoryPageItems, changeCategoryPage, goCategoryPage, stockKeyword, stockPage, stockSize, stockJumpPage, stockFiltered, stockTotalPages, stockPageItems, changeStockPage, goStockPage } = useAdminCategoryStockPaging({ categories, stockAlerts });

const { categoryForm, stockForm, noticeTypeLabel, noticeTypeClass, adminProductName, trendHasData, openStockForm, submitStock, saveCategory } = useAdminCategoryStock({ run, fail, askConfirm, loadAdminProducts, loadStockAlerts, loadProducts, loadCategories, adminProducts, adminStatsOverview });

const { shipForm, adminOrderTotalPages, searchAdminOrders, changeAdminOrderPage, changeAdminOrderPageSize, goAdminOrderPage, resetAdminOrderSearch, openShipForm, adminOrderReady, submitShip, completeAdminOrder, cancelAdminOrder } = useAdminOrders({ api, run, fail, askConfirm, money, orderSavedTotal, formatRole, adminOrders, adminOrderKeyword, adminOrderStatus, adminOrderJumpPage, loadAdminOrders, loadRefundOrders, loadAdminUsers });

const { refundReviewForm, refundTotalPages, searchRefunds, changeRefundPage, changeRefundPageSize, goRefundPage, resetRefundSearch, reviewAdminRefund, submitRefundReview } = useAdminRefunds({ api, run, askConfirm, money, orderSavedTotal, refundOrders, refundJumpPage, refundStatusFilter, loadRefundOrders, loadAdminOrders });

const { couponForm, adminCouponTotalPages, searchAdminCoupons, changeAdminCouponPage, changeAdminCouponPageSize, goAdminCouponPage, resetAdminCouponSearch, fillCouponPeriod, saveCoupon, toggleCoupon } = useAdminCoupons({ api, run, fail, askConfirm, money, adminCoupons, adminCouponKeyword, adminCouponJumpPage, loadAdminCoupons });

const { adminUserTotalPages, searchAdminUsers, changeAdminUserPage, changeAdminUserPageSize, goAdminUserPage, resetAdminUserSearch, toggleUser } = useAdminUsers({ api, run, askConfirm, money, formatRole, adminUsers, adminUserKeyword, adminUserRole, adminUserStatus, adminUserJumpPage, loadAdminUsers });""")

GONE = ['const categoryKeyword = ref(\'\')', 'function changeCategoryPage(', 'function goStockPage(',
        'const categoryForm = reactive(', 'const couponForm = reactive(',
        'const shipForm = reactive(', 'const refundReviewForm = reactive(',
        'const stockForm = reactive(',
        'const adminOrderTotalPages = computed(', 'const refundTotalPages = computed(',
        'const adminUserTotalPages = computed(', 'const adminCouponTotalPages = computed(',
        'function noticeTypeLabel(', 'function noticeTypeClass(', 'function adminProductName(',
        'const trendHasData = computed(', 'function openStockForm(', 'async function submitStock(',
        'async function searchAdminOrders(', 'function openShipForm(', 'async function adminOrderReady(',
        'async function submitShip(', 'async function searchRefunds(', 'function reviewAdminRefund(',
        'async function submitRefundReview(', 'async function searchAdminCoupons(',
        'function fillCouponPeriod(', 'async function saveCoupon(', 'async function toggleCoupon(',
        'async function completeAdminOrder(', 'async function cancelAdminOrder(',
        'async function saveCategory(', 'async function searchAdminUsers(', 'async function toggleUser(']
for g in GONE:
    assert out.count(g) == 0, '旧定义残留: %r x%d' % (g, out.count(g))
for s in ['useAdminCategoryStockPaging({', 'useAdminCategoryStock({', 'useAdminOrders({',
          'useAdminRefunds({', 'useAdminCoupons({', 'useAdminUsers({']:
    assert out.count(s) == 1, '装配异常: %r' % s

KEEP = ['adminMenuItems', 'adminMenuGroups', 'currentAdminMenu', 'adminIcon', 'adminIcons',
        'MEMBER_LEVEL_NAMES', 'memberLevelName', 'insightsPanelRef', 'adminMenuLoaders',
        'selectAdminMenu', 'refreshCurrentAdminMenu', 'props', 'isAdmin', 'view', 'categories']
for s in KEEP:
    assert re.search(r'\b' + s + r'\b', out), '保留符号丢失: ' + s

open('_b4out6.txt', 'w', encoding='utf-8').write(out)
print('已写出 _b4out6.txt（%d -> %d 行，净减 %d）' % (orig, len(keep), orig - len(keep)))
