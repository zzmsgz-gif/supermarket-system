/**
 * v-reveal —— 滚动入场（淡入 + 上浮）。
 *
 * 用法：
 *   <div v-reveal>…</div>           元素整体入场
 *   <div v-reveal.stagger>…</div>   它的**直接子元素**逐张错开入场（商品网格用这个）
 *
 * ⚠️ 关键设计：**`reveal` 类是这里加的，模板里不写**。
 *    初始的 `opacity:0` 只挂在 `.reveal` 上，所以：
 *    · JS 没跑 / 指令没注册 / 浏览器不支持 IntersectionObserver → **类不存在 → 元素照常显示**；
 *    · 反过来若把 `.reveal` 写死在模板里，一旦指令失效，内容就**永久隐形**（这类事故很难查）。
 *
 * ⚠️ `prefers-reduced-motion: reduce` 时**直接不介入**（不加类、不观察）——
 *    前庭功能障碍的用户对位移敏感，入场动效必须能让路（CSS 里另有一层同样的媒体查询兜底）。
 */
const REDUCED = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
  : false;

const SUPPORTED = typeof window !== 'undefined' && typeof window.IntersectionObserver === 'function';

const REVEAL_CLASS = 'reveal';
const STAGGER_CLASS = 'reveal-stagger';
const IN_CLASS = 'is-in';

export const reveal = {
  mounted(el, binding) {
    if (REDUCED || !SUPPORTED) return;

    const stagger = !!binding.modifiers.stagger;
    el.classList.add(stagger ? STAGGER_CLASS : REVEAL_CLASS);

    // 触发时机：元素顶边进入视口（下方只留 32px 余量）。
    // ⚠️ 这里**踩过一次**：最初写成 rootMargin 底部 -12%，想着"等元素进得深一点再动更好看"，
    //    结果是**视口最底部约 12%（≈108px）那条带里的内容会停在 opacity:0** ——
    //    用户滚到某一行刚好卡在底边时，看到的是一片空白，像布局漏了。
    //    实测数据：y=600 时有 5 张卡在 top=825 处（排除带从 792 开始）且容器未入场。
    //    现在只留 32px 余量：底部几乎不会再出现空白，而动画也不会早到看不见。
    const observer = new IntersectionObserver((entries) => {
      entries.forEach((entry) => {
        if (!entry.isIntersecting) return;
        entry.target.classList.add(IN_CLASS);
        observer.unobserve(entry.target);   // 入场是一次性的，别让滚动反复重放
      });
    }, { threshold: 0, rootMargin: '0px 0px -32px 0px' });

    observer.observe(el);
    el.__revealObserver = observer;
  },

  unmounted(el) {
    if (el.__revealObserver) {
      el.__revealObserver.disconnect();
      delete el.__revealObserver;
    }
  },
};

export default reveal;
