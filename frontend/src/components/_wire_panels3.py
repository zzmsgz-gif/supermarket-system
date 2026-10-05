"""把 _gen_panels3.py 生成的四个面板接进 AdminPanel.vue。

从后往前替换，且每轮重新计算面板范围 —— 因为替换会改变行号。
prop 列表从各组件的 defineProps 读回（_panel_lib.props_of），不在这里重复抄。
"""
import re
from _panel_lib import load, panel_range, comp_name, props_of

KEYS = ['flashSales', 'activities', 'memberDays', 'passwordResets']


def kebab(name):
    return re.sub(r'(?<!^)(?=[A-Z])', '-', name).lower()


def main():
    lines = load()
    anchor = next(i for i, l in enumerate(lines)
                  if "import('./AdminReviewsPanel.vue')" in l)
    for key in reversed(KEYS):
        comp = comp_name(key)
        lines.insert(anchor + 1,
                     "const %s = defineAsyncComponent(() => import('./%s.vue'));" % (comp, comp))

    for key in reversed(KEYS):
        start, end = panel_range(lines, key)
        head = lines[start]
        assert 'adminMenu === ' in head or lines[start].strip().startswith('<!--'), \
            '%s 起点异常: %r' % (key, head[:60])
        div = next(l for l in lines[start:end + 1]
                   if 'adminMenu === ' in l and 'v-if' in l)
        attrs = ' '.join(':%s="%s"' % (kebab(n), n) for n in props_of(key))
        lines[start:end + 1] = [
            '          <%s v-if="adminMenu === \'%s\'" %s/>' % (comp_name(key), key, attrs)
        ]
        print('  替换 %-15s %d-%d (%d 行 → 1，props %d)'
              % (key, start + 1, end + 1, end - start + 1, len(props_of(key))))

    open('AdminPanel.vue', 'w', encoding='utf-8', newline='').write('\n'.join(lines))
    print('总行 %d' % len(lines))


if __name__ == '__main__':
    main()
