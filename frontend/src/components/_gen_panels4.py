"""生成最后三个面板：orders / stock / categories。

这三个和前几批的差别：
1. **stock + categories 是耦合的** —— 共用 useAdminCategoryStockPaging + useAdminCategoryStock，
   stockForm 还被 products 面板用。所以两边都只把 state 当**只读数据**传，模板里的
   「对 ref 重新赋值」（`stockPage = 1`）改成调 setter（resetStockPage / resetCategoryPage），
   否则子组件里改的是解包副本，父级不动 —— 分页看着能动、实际跳不动。
2. **reactive 对象（stockForm / categoryForm）跨组件传引用是安全的**，可以照常写。
3. orders 的 shipForm 同理。
"""
import re
from _panel_lib import load, extract_body, file_name

SPEC = {
    'orders': ('订单管理', 20, ['adminOrders', 'adminOrderKeyword', 'adminOrderStatus',
                               'adminOrderJumpPage', 'adminOrderTotalPages',
                               'shipForm',
                               'searchAdminOrders', 'changeAdminOrderPage',
                               'changeAdminOrderPageSize', 'goAdminOrderPage',
                               'resetAdminOrderSearch', 'openShipForm',
                               'adminOrderReady', 'submitShip', 'completeAdminOrder',
                               'cancelAdminOrder', 'openOrderDetail'],
               ['money', 'formatDate', 'formatPaymentStatus', 'orderStatusTag',
                'orderStatusLabel', 'fulfillmentLabel', 'orderSavedTotal'], []),
    'stock': ('库存预警', 18,
              ['stockAlerts', 'stockKeyword', 'stockPage', 'stockSize', 'stockJumpPage',
               'stockFiltered', 'stockTotalPages', 'stockPageItems',
               'changeStockPage', 'goStockPage', 'resetStockPage',
               'stockForm', 'openStockForm', 'submitStock',
               'resetStockSearch'], [], []),
    'categories': ('分类管理', 14,
                   ['categories', 'categoryKeyword', 'categoryPage', 'categorySize',
                    'categoryJumpPage', 'categoryFiltered', 'categoryTotalPages',
                    'categoryPageItems', 'changeCategoryPage', 'goCategoryPage',
                    'resetCategoryPage', 'resetCategorySearch',
                    'categoryForm', 'saveCategory', 'categoryName'],
                   [], []),
}

# 手工核实的类型；未列出 → Function
TYPES = {
    'adminOrders': 'Object', 'adminOrderKeyword': 'String', 'adminOrderStatus': 'String',
    'adminOrderJumpPage': 'Object', 'adminOrderTotalPages': 'Number', 'shipForm': 'Object',
    'stockAlerts': 'Array', 'stockKeyword': 'String', 'stockPage': 'Number',
    'stockSize': 'Number', 'stockJumpPage': 'Object', 'stockFiltered': 'Array',
    'stockTotalPages': 'Number', 'stockPageItems': 'Array', 'stockForm': 'Object',
    'categories': 'Array', 'categoryKeyword': 'String', 'categoryPage': 'Number',
    'categorySize': 'Number', 'categoryJumpPage': 'Object', 'categoryFiltered': 'Array',
    'categoryTotalPages': 'Number', 'categoryPageItems': 'Array', 'categoryForm': 'Object',
}

# 模板里对 ref 的重新赋值 → setter。key = (面板, 原表达式)
ASSIGN_FIX = [
    ('stock', '@search="stockPage = 1"', '@search="resetStockPage"'),
    ('stock', '@change="stockPage = 1"', '@change="resetStockPage"'),
    ('stock', "@click=\"stockKeyword = ''; stockPage = 1\"", '@click="resetStockSearch"'),
    ('categories', '@search="categoryPage = 1"', '@search="resetCategoryPage"'),
    ('categories', '@change="categoryPage = 1"', '@change="resetCategoryPage"'),
    ('categories', "@click=\"categoryKeyword = ''; categoryPage = 1\"",
     '@click="resetCategorySearch"'),
]

LOCAL_COMPONENTS = ['AdminSearchBox', 'AdminPageSize', 'AdminPager', 'AdminStockFormRow',
                   'ImageUpload']

HEADER = ('// 后台「{name}」面板：从 AdminPanel.vue 整块搬过来的。\n'
          '//\n'
          '// 依赖走显式 props（不接 adminCtx）：面板只服务一个 tab，列全依赖比藏起来好。\n')


def apply_assign_fixes(key, body):
    for k, old, new in ASSIGN_FIX:
        if k != key:
            continue
        assert old in body, '%s 未找到赋值表达式: %r' % (key, old)
        body = body.replace(old, new)
    # 搬进子组件后不该再有裸 ref 赋值
    leftover = re.findall(r'"[^"]*\b(?:stock|category)(?:Page|Size)\s*=[^"]*"', body)
    leftover += re.findall(r'"[^"]*\b(?:stock|category)Keyword\s*=[^"]*"', body)
    assert not leftover, '%s 仍有 ref 赋值: %s' % (key, leftover)
    return body


def main():
    lines = load()
    for key, (name, _, props, utils, extra) in SPEC.items():
        body = apply_assign_fixes(key, extract_body(lines, key))
        out = '<script setup>\n' + HEADER.format(name=name)
        if utils:
            out += "import { %s } from '../utils/format';\n" % ', '.join(utils)
        for e in extra:
            out += e + '\n'
        if key == 'stock':
            out += ('// stockForm 还被 products 面板（AdminProductsPanel）共用做「入库」——\n'
                    '// 它是 reactive 对象，传引用跨组件写安全，所以这里可以照常改它的字段。\n')
        if key == 'categories':
            out += ('// 分页相关的「回到第 1 页」走 resetCategoryPage()：\n'
                    '// 模板里直接写 `categoryPage = 1` 搬进子组件后改的是解包副本，父级不动。\n')
        # ⚠️ 局部组件必须自己 import：AdminSearchBox / AdminPager / AdminPageSize /
        #    AdminStockFormRow 只在 AdminPanel.vue 里 import 过，不是全局注册。
        #    漏了不会报错 —— Vue 把未解析标签当普通自定义元素渲染，**页面上直接不显示**，
        #    而构建照样通过。校验器查不到（它只看标识符，不看标签）。
        for comp in LOCAL_COMPONENTS:
            if re.search(r'<%s[ />]' % comp, body):
                out += "import %s from './%s.vue';\n" % (comp, comp)
        out += '\nconst props = defineProps({\n'
        out += '\n'.join('  %s: { type: %s, required: true },' % (n, TYPES.get(n, 'Function'))
                         for n in props)
        out += '\n});\n'
        out += 'const { %s } = props;\n' % ', '.join(props)
        out += '</script>\n\n<template>\n%s</template>\n' % body
        fn = file_name(key)
        open(fn, 'w', encoding='utf-8', newline='').write(out)
        print('%-28s %3d 行  props %2d' % (fn, out.count('\n'), len(props)))


if __name__ == '__main__':
    main()
