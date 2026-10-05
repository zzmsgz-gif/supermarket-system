"""把面板绑定里的 `:xxx` 改成 `v-model:xxx`（凡是该面板用 defineModel 声明的 prop）。

接线在 defineModel 转换**之前**跑，所以先接线、后转换时，绑定语法还是旧的 `:xxx`。
本脚本负责补这一步 —— 单独跑，幂等。

为什么必须这样：defineModel 的 prop 只接上了读。父组件写 `:xxx` 时没有 `update:xxx`
的监听者，面板里 emit 出去的事件没人收 → 赋值丢失 → **静默失败**：
页面正常、构建通过、控制台干净，但搜索/翻页完全不响应。
"""
import re
from _panel_lib import load, binding_of, comp_name

KEYS = ['products', 'notices', 'banners', 'hotSearches', 'coupons', 'users',
        'reviews', 'refunds', 'flashSales', 'activities', 'memberDays',
        'passwordResets', 'orders', 'stock', 'categories']


def main():
    src = open('AdminPanel.vue', encoding='utf-8').read()
    total = 0
    for key in KEYS:
        try:
            binds = binding_of(key)
        except AssertionError:
            continue                      # 该面板组件不存在
        models = {n for n, m in binds if m == 'v-model'}
        if not models:
            continue
        # 标签可能跨多行（props 多的面板），[^>]* 要能吃掉换行
        tag = re.search(r'<%s\b[^>]*/>' % comp_name(key), src, re.S)
        if not tag:
            print('  ★ %s 在 AdminPanel 里没找到标签' % comp_name(key))
            continue
        text = tag.group(0)
        changed = 0
        for name in sorted(models, key=len, reverse=True):
            kebab = re.sub(r'(?<!^)(?=[A-Z])', '-', name).lower()
            plain = ' :%s="%s"' % (kebab, name)
            two = ' v-model:%s="%s"' % (kebab, name)
            if two in text:
                continue
            if plain in text:
                text = text.replace(plain, two)
                changed += 1
            elif two in text:
                continue                      # 已经是 v-model
            else:
                # 该 prop 走 adminCtx 传递（早期面板），adminCtx 是普通对象、
                # 不能 v-model —— 必须在标签上**额外**补一个 v-model 绑定，
                # 子组件里 defineModel 会优先用显式 prop 而非 adminCtx 里的同名键。
                text = text[:-2].rstrip() + ' %s/>' % two
                changed += 1
        if changed:
            src = src.replace(tag.group(0), text)
            total += changed
            print('  %-30s 改 %d 个为 v-model' % (comp_name(key), changed))
    open('AdminPanel.vue', 'w', encoding='utf-8', newline='').write(src)
    print('合计 %d 个绑定改成 v-model:xxx' % total)


if __name__ == '__main__':
    main()
