"""从 AdminPanel.vue 抽出 4 个独占型面板 → 独立组件。

为什么只做这 4 个：它们的 composable（useAdminCoupons / useAdminUsers /
useAdminReviews / useAdminRefunds）互不共享，也不与别的面板共享 state。
stock 与 categories 共用 useAdminCategoryStockPaging + useAdminCategoryStock
且 stockForm 跨面板共享（products 面板的入库行也要用），必须成对处理，不在此列。

用法：python _gen_panels2.py    （在 components/ 下执行）
"""
import re

SRC = 'AdminPanel.vue'

# key -> (中文名, 需要的 adminCtx 符号, 额外 props, 额外 import, 需要的 utils)
SPEC = {
    'coupons': {
        'title': '优惠券管理',
        'ctx': ['adminCoupons', 'adminCouponKeyword', 'adminCouponJumpPage',
                'loadAdminCoupons'],
        'props': [],   # (propName, 表达式)
        'imports': ["import AdminSearchBox from './AdminSearchBox.vue';",
                    "import AdminPageSize from './AdminPageSize.vue';",
                    "import AdminPager from './AdminPager.vue';"],
        'utils': ['money', 'formatDate'],
    },
    'users': {
        'title': '用户管理',
        'ctx': ['adminUsers', 'adminUserKeyword', 'adminUserRole', 'adminUserStatus',
                'adminUserJumpPage', 'loadAdminUsers'],
        'props': [],
        'imports': ["import AdminSearchBox from './AdminSearchBox.vue';",
                    "import AdminPageSize from './AdminPageSize.vue';",
                    "import AdminPager from './AdminPager.vue';"],
        'utils': ['money', 'formatRole', 'formatDate', 'initials'],
    },
    'reviews': {
        'title': '评价管理',
        # 评价数据不在 adminCtx（来自 useAdminReviews），走 props
        'ctx': [],
        'props': [('adminReviews', 'Object'), ('adminReviewSummary', 'Object'),
                  ('adminReviewRating', 'Object'), ('adminReviewReplied', 'Object'),
                  ('adminReviewKeyword', 'Object'), ('loadAdminReviews', 'Function'),
                  ('loadAdminReviewUnreplied', 'Function')],
        'props': [],
        'imports': ["import AdminPager from './AdminPager.vue';"],
        'utils': ['formatDate'],
    },
    'refunds': {
        'title': '售后管理',
        'ctx': ['refundOrders', 'refundStatusFilter', 'refundJumpPage', 'loadRefundOrders'],
        'props': [],
        'imports': ["import AdminPageSize from './AdminPageSize.vue';",
                    "import AdminPager from './AdminPager.vue';"],
        'utils': ['formatDate', 'formatRefundStatus', 'money'],
    },
}

# 这些函数由父级的 composable 装配产出，不在 adminCtx 里 → 走 props
FUNCS = {
    'coupons': ['fillCouponPeriod', 'saveCoupon', 'searchAdminCoupons',
                'changeAdminCouponPageSize', 'resetAdminCouponSearch',
                'toggleCoupon', 'changeAdminCouponPage', 'goAdminCouponPage'],
    'users': ['searchAdminUsers', 'changeAdminUserPage', 'changeAdminUserPageSize',
              'goAdminUserPage', 'resetAdminUserSearch', 'toggleUser'],
    'reviews': ['searchAdminReviews', 'changeAdminReviewPage', 'saveReviewReply',
                'toggleReviewHidden'],
    'refunds': ['searchRefunds', 'changeRefundPage', 'changeRefundPageSize',
                'goRefundPage', 'resetRefundSearch', 'reviewAdminRefund',
                'submitRefundReview'],
}
# computed（父级 computed，不是函数）
COMPUTEDS = {
    'coupons': ['adminCouponTotalPages'],
    'users': ['adminUserTotalPages'],
    'reviews': ['adminReviewTotalPages'],
    'refunds': ['refundTotalPages'],
}
# 父级还有 form state（refundReviewForm 等）
EXTRA_STATE = {'refunds': ['refundReviewForm']}

COMP = {k: 'Admin' + k[0].upper() + k[1:] + 'Panel' for k in SPEC}


def panel_ranges(lines):
    """用缩进定界：目标 div 起点 → 下一个缩进 <= 目标的顶层起点之前。"""
    ts = next(i for i, l in enumerate(lines) if l.strip() == '<script setup>')
    starts = []
    for i in range(1, ts):
        s = lines[i]
        if not s.strip():
            continue
        if (len(s) - len(s.lstrip())) == 10 and s.strip().startswith('<') \
                and not s.strip().startswith('</'):
            m = re.search(r"adminMenu === '(\w+)'", s)
            if m:
                starts.append((i, m.group(1)))
    out = {}
    for idx, (i, name) in enumerate(starts):
        end = (starts[idx + 1][0] - 1) if idx + 1 < len(starts) else ts - 1
        out[name] = (i, end)
    return out


def main():
    lines = open(SRC, encoding='utf-8').read().split('\n')
    ranges = panel_ranges(lines)

    for key, spec in SPEC.items():
        assert key in ranges, '面板未找到: ' + key
        S, E = ranges[key]
        body = '\n'.join(lines[S:E + 1])
        # 最外层 div 去掉 v-if（改由父级组件控制显隐）
        body = re.sub(r'^\s*<div v-if="adminMenu === \'%s\'" class="data-panel">' % key,
                      '  <div class="data-panel">', body)
        # 缩进 10 → 2
        body = '\n'.join(l[8:] if l.startswith(' ' * 8) else l
                         for l in body.split('\n')).rstrip() + '\n'

        # adminCtx 必须第一个声明：组件里用 props.adminCtx 解构，漏了就是运行时 undefined
        props = [('adminCtx', '{ type: Object, required: true }')] + list(spec['props'])
        # 函数 / computed / state 都从父级注入
        for fn in FUNCS[key] + COMPUTEDS[key] + EXTRA_STATE.get(key, []):
            props.append((fn, '{ type: %s, required: true }'
                          % ('Object' if fn in EXTRA_STATE.get(key, []) else 'Function')))
        ps = '\n'.join('  %s: %s,' % (n, t) for n, t in props)
        utils = spec['utils']
        imp = list(spec['imports'])
        if utils:
            imp.insert(0, "import { %s } from '../utils/format';" % ', '.join(utils))

        ctx_destr = ', '.join(spec['ctx'])  # 从 props.adminCtx 解构
        extra_names = [n for n, _ in props]
        extra_destr = (', '.join(extra_names)) if extra_names else ''

        out = '<script setup>\n'
        out += '// 后台「%s」面板：从 AdminPanel.vue 整块搬过来的（模板 %d 行）。\n' % (spec['title'], body.count('\n') - 1)
        out += '//\n'
        out += '// adminCtx 给数据与动作；分页/表单/审核这些由父级 composable 装配产出，\n'
        out += '// 不在 adminCtx 里，所以按 props 注入。\n'
        out += '//\n'
        out += '// ⚠️ 解构列表照抄父级：漏一个就是运行时 undefined，模板编译不报错、\n'
        out += '//    要跑起来才在 console 报 xxx is not a function。改完跑 _check_panels.py。\n'
        if imp:
            out += '\n'.join(imp) + '\n'
        out += '\nconst props = defineProps({\n%s\n});\n' % ps
        out += 'const { %s } = props.adminCtx;\n' % ctx_destr
        if extra_destr:
            out += 'const { %s } = props;\n' % extra_destr
        out += '</script>\n\n<template>\n%s</template>\n' % body
        fn = COMP[key] + '.vue'
        open(fn, 'w', encoding='utf-8', newline='').write(out)
        print('生成 %-28s %3d 行（模板 %d 行，%d 个 prop）'
              % (fn, out.count('\n'), body.count('\n') - 1, len(props)))


if __name__ == '__main__':
    main()
