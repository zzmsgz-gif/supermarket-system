"""把面板里的 `const { a, b, c } = props;` 换成 `const { a, b, c } = toRefs(props);`。

## 🔴 这是拆面板最容易犯、也最难发现的错

`const { x } = props` 拿到的是**值的快照**，不是响应式的。首屏渲染时 props 是什么，
之后就一直是那个值 —— 父组件更新了 props，子组件的模板却读不到。

**症状极其隐蔽**：
- 页面首屏完全正常（数据是首屏那一刻的）
- `vite build` 通过、控制台干净、无任何报错
- 交互全部失效：翻页不动、搜索过滤不了、表格永远是初始那批数据
- 容易被误判成「点击没送达」「工具问题」「响应式没追踪」—— 我为此绕了十几轮

**唯一可靠的判据**：不要只看首屏，要**改一个只有交互才会变的值**再看 DOM。
最省事的探针（加在面板里，验完删掉）：

```js
import { watch } from 'vue';
watch(() => props.categoryPageItems, (v, o) =>
  console.log('[DBG] items', (o || []).length, '->', (v || []).length));
```

数据层变了（`10 -> 1`）但 DOM 没变 → 就是 props 解构丢响应性。

## 为什么 toRefs 可以

`toRefs(props)` 返回的是 ref 对象，`.value` 直通 props，父组件更新时 ref 一起变。
模板里不用改写法 —— Vue 对 ref 做自动解包。

## 注意

- 局部变量名（`defineModel` 出来的、函数式注入的）不在 toRefs 里，别一起塞进去
- `props.adminCtx` 是对象整体，不解构成 toRefs
"""
import re
import glob


def main():
    for fn in sorted(glob.glob('Admin*Panel.vue')):
        if fn == 'AdminPanel.vue':
            continue
        src = open(fn, encoding='utf-8').read()
        if '<template>' not in src:
            continue

        m = re.search(r'^const \{([^}]*)\} = props;$', src, re.M)
        if not m:
            continue

        names = [x.strip() for x in m.group(1).split(',') if x.strip()]
        if not names:
            continue

        head = src[:src.index('<template>')]
        already = 'toRefs' in head

        repl = "const { %s } = toRefs(props);" % ', '.join(names)
        src = src.replace(m.group(0), repl)

        if not already:
            # 插到已有的 vue import 上；没有就插在第一行 import 之前
            mi = re.search(r"^import \{[^}]*\} from 'vue';$", src, re.M)
            if mi:
                assert 'toRefs' in mi.group(0), 'vue import 里已有 toRefs'
                src = src[:mi.start()] + \
                    mi.group(0).replace("from 'vue';", "from 'vue';") + src[mi.start():]
            else:
                idx = src.index('<script setup>') + len('<script setup>\n')
                src = src[:idx] + "import { toRefs } from 'vue';\n" + src[idx:]

        open(fn, 'w', encoding='utf-8', newline='').write(src)
        print('%-30s toRefs %d 个' % (fn, len(names)))


if __name__ == '__main__':
    main()
