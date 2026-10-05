"""把最后三个面板（orders / stock / categories）接进 AdminPanel.vue。

从后往前替换，每轮重新计算面板范围 —— 替换会改变行号。
绑定列表从各组件读回（_panel_lib.binding_of）：普通 prop 用 :xxx，defineModel 的用 v-model:xxx。
"""
from _panel_lib import load, panel_range, comp_name, binding_of

KEYS = ['orders', 'stock', 'categories']


def kebab(name):
    out = []
    for i, ch in enumerate(name):
        if ch.isupper() and i:
            out.append('-')
        out.append(ch.lower())
    return ''.join(out)


def main():
    lines = load()

    # 新 setter 要从分页 composable 解构出来（校验器已确认父级缺这两个）。
    # 一次 replace 追加全部：循环里逐个 replace 只有第一次生效 —— 第一次之后锚点
    # 已经变成「goStockPage, xxx }」，后面几次找不到原文。
    anchor = next(i for i, l in enumerate(lines) if '= useAdminCategoryStockPaging({' in l)
    added = [n for n in ('resetCategoryPage', 'resetCategorySearch', 'resetStockPage', 'resetStockSearch')
             if n not in lines[anchor]]
    assert 'goStockPage }' in lines[anchor], '分页解构行锚点变了'
    if added:
        lines[anchor] = lines[anchor].replace(
            'goStockPage }', 'goStockPage, %s }' % ', '.join(added))
    assert 'resetStockSearch' in lines[anchor], '分页解构行未更新'
    print('  分页解构新增: %s' % ', '.join(added))

    lazy = next(i for i, l in enumerate(lines)
                if "import('./AdminPasswordResetsPanel.vue')" in l)
    for key in reversed(KEYS):
        comp = comp_name(key)
        lines.insert(lazy + 1,
                     "const %s = defineAsyncComponent(() => import('./%s.vue'));" % (comp, comp))

    for key in reversed(KEYS):
        start, end = panel_range(lines, key)
        binds = binding_of(key)
        attrs = ' '.join('%s%s="%s"' % (m, kebab(n), n) for n, m in binds)
        lines[start:end + 1] = [
            '          <%s v-if="adminMenu === \'%s\'" %s/>' % (comp_name(key), key, attrs)
        ]
        print('  替换 %-12s %d-%d (%d 行 → 1，绑定 %d)'
              % (key, start + 1, end + 1, end - start + 1, len(binds)))

    open('AdminPanel.vue', 'w', encoding='utf-8', newline='').write('\n'.join(lines))
    print('总行 %d' % len(lines))


if __name__ == '__main__':
    main()
