<template>
  <div class="notfound-wrap">
    <div class="nf-card">
      <!-- ===== 左：Q 版店员插画（纯内联 SVG，无外部图片请求，可跟随文字缩放） ===== -->
      <div class="nf-art" aria-hidden="true">
        <svg viewBox="0 0 360 320" fill="none" xmlns="http://www.w3.org/2000/svg">
          <!-- 背景光斑 + 地面阴影 -->
          <circle cx="180" cy="168" r="122" fill="#e6f7f1" />
          <circle cx="180" cy="168" r="88" fill="#d3efe3" opacity=".5" />
          <ellipse cx="180" cy="288" rx="88" ry="12" fill="#0f1721" opacity=".08" />

          <!-- 掉在地上的购物袋：暗示「东西不见了」 -->
          <g class="nf-bag" transform="rotate(-16 90 264)">
            <path d="M70 248h40l-4.5 36a6 6 0 0 1-6 5.2H80.5a6 6 0 0 1-6-5.2L70 248Z" fill="#ffd9ab" stroke="#eab887" stroke-width="2.5" />
            <path d="M79 248c0-8.5 5-14 11-14s11 5.5 11 14" stroke="#eab887" stroke-width="2.5" stroke-linecap="round" />
            <circle cx="90" cy="266" r="8.5" fill="#fff" opacity=".72" />
            <path d="M82 260l16 10" stroke="#eab887" stroke-width="2" opacity=".45" />
          </g>

          <!-- 腿 + 鞋 -->
          <rect x="157" y="204" width="20" height="54" rx="10" fill="#e4ece8" />
          <rect x="184" y="204" width="20" height="54" rx="10" fill="#e4ece8" />
          <path d="M149 258c0-7 5.5-11 12.5-11h9.5c3.5 0 5.8 2.3 5.8 5.8v5.6c0 4.2-3.2 6.6-7.6 6.6H155c-3.8 0-6-2.5-6-6V258Z" fill="#10382c" />
          <path d="M180 258c0-7 5.5-11 12.5-11h9.5c3.5 0 5.8 2.3 5.8 5.8v5.6c0 4.2-3.2 6.6-7.6 6.6H186c-3.8 0-6-2.5-6-6V258Z" fill="#10382c" />

          <!-- 围裙身体 + 白领 + 口袋 -->
          <path d="M146 168c0-10.5 8.5-19 19-19h30c10.5 0 19 8.5 19 19v40c0 9-7 16-16 16h-36c-9 0-16-7-16-16v-40Z" fill="#00a870" />
          <path d="M165 149h30l-6.6 14c-2.8 6-5.4 9-8.4 9s-5.6-3-8.4-9L165 149Z" fill="#ffffff" opacity=".95" />
          <rect x="186" y="184" width="20" height="17" rx="5" fill="#00895c" />
          <rect x="172" y="128" width="16" height="24" rx="4" fill="#f2cbad" />

          <!-- 两臂向外摊手 -->
          <path d="M152 168C138 176 126 184 116 194" stroke="#00a870" stroke-width="16" stroke-linecap="round" />
          <g transform="translate(110 200) rotate(-38)">
            <ellipse cx="0" cy="0" rx="13" ry="9.5" fill="#ffe3c4" />
            <path d="M9-4c3-1 5 1 5 3.5" stroke="#f0c3a2" stroke-width="2.4" stroke-linecap="round" />
            <path d="M-3 1.5h-.1M1 3h-.1M5 2h-.1" stroke="#f0c3a2" stroke-width="2.6" stroke-linecap="round" />
          </g>
          <path d="M208 168C222 176 234 184 244 194" stroke="#00a870" stroke-width="16" stroke-linecap="round" />
          <g transform="translate(250 200) rotate(38)">
            <ellipse cx="0" cy="0" rx="13" ry="9.5" fill="#ffe3c4" />
            <path d="M-9-4c-3-1-5 1-5 3.5" stroke="#f0c3a2" stroke-width="2.4" stroke-linecap="round" />
            <path d="M3 1.5h.1M-1 3h.1M-5 2h.1" stroke="#f0c3a2" stroke-width="2.6" stroke-linecap="round" />
          </g>

          <!-- 头 + 耳 -->
          <circle cx="134" cy="106" r="9.5" fill="#ffe3c4" />
          <circle cx="226" cy="106" r="9.5" fill="#ffe3c4" />
          <circle cx="180" cy="98" r="46" fill="#ffe3c4" />

          <!-- 员工帽 -->
          <path d="M132 62c0-20 21-32 48-32s48 12 48 32H132Z" fill="#00a870" />
          <path d="M140 50c8-8 22-13 40-13s32 5 40 13" stroke="#12c48b" stroke-width="4" stroke-linecap="round" opacity=".6" />
          <rect x="126" y="58" width="108" height="14" rx="7" fill="#00895c" />
          <circle cx="180" cy="30" r="7" fill="#e6f7f1" />

          <!-- 眉毛（一高一低）+ 眼睛 + 腮红 + 嘴 -->
          <path d="M157 88c5-4.6 12.5-4.6 17.5-1" stroke="#3b2f28" stroke-width="4.2" stroke-linecap="round" />
          <path d="M187 87.5c5-3.6 12.5-3.2 17 1.5" stroke="#3b2f28" stroke-width="4.2" stroke-linecap="round" />
          <ellipse class="nf-eye" cx="166" cy="102" rx="5.8" ry="6.8" fill="#2b2320" />
          <ellipse class="nf-eye" cx="196" cy="102" rx="5.8" ry="6.8" fill="#2b2320" />
          <circle cx="168" cy="99.2" r="2.1" fill="#fff" />
          <circle cx="198" cy="99.2" r="2.1" fill="#fff" />
          <ellipse cx="150" cy="116" rx="9.5" ry="5.8" fill="#ff9d9d" opacity=".5" />
          <ellipse cx="212" cy="116" rx="9.5" ry="5.8" fill="#ff9d9d" opacity=".5" />
          <ellipse cx="181" cy="121" rx="4.8" ry="3.8" fill="#c9744f" />

          <!-- 问号气泡 -->
          <g class="nf-q">
            <circle cx="288" cy="54" r="22" fill="#fff" stroke="#00a870" stroke-width="3" />
            <path d="M281 47c0-4.2 3.2-7.4 7.4-7.4s7.4 3.2 7.4 7.4c0 3.2-3 4.4-5.2 6.2-1.2 1-2 2.1-2 4" stroke="#00a870" stroke-width="3.6" stroke-linecap="round" />
            <circle cx="288" cy="63" r="2.3" fill="#00a870" />
            <path d="M266 72l-7 9" stroke="#00a870" stroke-width="3" stroke-linecap="round" />
          </g>

          <!-- 漂浮装饰 -->
          <circle cx="76" cy="130" r="5" fill="#00a870" opacity=".35" />
          <circle cx="302" cy="152" r="7.5" fill="#ffb27a" opacity=".5" />
          <circle cx="106" cy="206" r="4" fill="#00a870" opacity=".28" />
          <circle cx="274" cy="240" r="5.5" fill="#00a870" opacity=".26" />
          <path d="M94 82l3.4 7 7 3.4-7 3.4-3.4 7-3.4-7-7-3.4 7-3.4 3.4-7Z" fill="#00a870" opacity=".22" />
        </svg>
      </div>

      <!-- ===== 右：文案 ===== -->
      <div class="nf-body">
        <!-- 404 用渐变字 + 极淡的重影，比纯色大数字更有层次；aria-hidden 因为标题已说明语义 -->
        <div class="nf-code" aria-hidden="true">404</div>
        <h1 class="nf-title">页面走丢了</h1>
        <p class="nf-desc">
          我们翻遍了整个货架，也没找到这个页面。<br />
          可能是链接输错了，或者它已经被下架、挪到了别处。
        </p>

        <div class="nf-actions">
          <button class="nf-btn-primary" @click="goShop">回到首页</button>
          <button class="ghost" @click="goBack">返回上一页</button>
        </div>

        <div class="nf-links">
          <span class="nf-links-label">也可以去看看</span>
          <a href="/orders" @click.prevent="go('/orders')">我的订单</a>
          <span class="nf-dot">·</span>
          <a href="/cart" @click.prevent="go('/cart')">购物车</a>
          <span class="nf-dot">·</span>
          <a href="/coupons" @click.prevent="go('/coupons')">领券中心</a>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
const router = useRouter();

function goShop() {
  router.push('/shop');
}
function go(path) {
  router.push(path);
}
/** 返回上一页；直接输地址进来的（history 里没有上一条）就退回首页，避免点了没反应 */
function goBack() {
  if (window.history.length > 1) router.back();
  else router.push('/shop');
}
</script>

<style scoped>
.notfound-wrap {
  min-height: min(70vh, 640px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(24px, 5vh, 56px) 16px;
}

.nf-card {
  width: 100%;
  max-width: 840px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-xl);
  box-shadow: var(--shadow);
  padding: 34px clamp(24px, 5vw, 56px);
  display: flex;
  align-items: center;
  gap: clamp(16px, 4vw, 48px);
}

/* ---------- 插画 ---------- */
.nf-art {
  flex: 0 0 auto;
  width: clamp(240px, 38%, 340px);
}
.nf-art svg {
  display: block;
  width: 100%;
  height: auto;
  overflow: visible;
}

/* 人物整体轻微上下浮动；问号与袋子各自小幅度摆动，三者周期错开，避免「一起动」的木偶感 */
.nf-art svg {
  animation: nf-float 4.2s ease-in-out infinite;
}
.nf-q {
  transform-box: fill-box;
  transform-origin: center;
  animation: nf-tilt 3.4s ease-in-out infinite;
}
.nf-bag {
  transform-box: fill-box;
  transform-origin: 60% 20%;
  animation: nf-swing 2.8s ease-in-out infinite;
}
.nf-eye {
  transform-box: fill-box;
  transform-origin: center;
  animation: nf-blink 5.6s ease-in-out infinite;
}

@keyframes nf-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-7px); }
}
@keyframes nf-tilt {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(-7deg) translateY(-3px); }
}
@keyframes nf-swing {
  0%, 100% { transform: rotate(0deg); }
  50% { transform: rotate(4deg); }
}
@keyframes nf-blink {
  0%, 94%, 100% { transform: scaleY(1); }
  96%, 98% { transform: scaleY(0.12); }
}

/* ---------- 文案 ---------- */
.nf-body {
  flex: 1 1 auto;
  min-width: 0;
}
.nf-code {
  font-size: clamp(56px, 9vw, 82px);
  font-weight: 800;
  line-height: 1;
  letter-spacing: 2px;
  background: linear-gradient(180deg, #12c48b, var(--brand-dark));
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  /* 极淡的同色重影，制造一点厚度，比纯渐变字更「立」 */
  filter: drop-shadow(0 6px 12px rgba(0, 168, 112, 0.18));
}
.nf-title {
  margin: 10px 0 10px;
  font-size: clamp(20px, 2.4vw, 26px);
  font-weight: 800;
  color: var(--ink);
  letter-spacing: 0.5px;
}
.nf-desc {
  margin: 0 0 26px;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.85;
}

.nf-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.nf-actions button {
  min-width: 124px;
}
.nf-btn-primary {
  /* 全局 button 已是品牌绿渐变，这里只补一点悬浮反馈 */
  box-shadow: 0 8px 16px rgba(0, 168, 112, 0.22);
}

.nf-links {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px dashed var(--line);
  font-size: 13px;
  color: var(--muted);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.nf-links-label {
  color: #98a2ad;
}
.nf-links a {
  color: var(--brand-dark);
  text-decoration: none;
  font-weight: 600;
}
.nf-links a:hover {
  color: var(--brand-deep);
  text-decoration: none;
}
.nf-dot {
  color: #cfd6dc;
}

/* ---------- 窄屏：插画在上、文案在下 ---------- */
@media (max-width: 720px) {
  .nf-card {
    flex-direction: column;
    text-align: center;
    padding: 26px 20px 30px;
    gap: 4px;
  }
  .nf-art {
    width: clamp(180px, 58%, 240px);
  }
  .nf-desc {
    margin-bottom: 22px;
  }
  .nf-actions {
    justify-content: center;
  }
  .nf-links {
    justify-content: center;
  }
}

/* 尊重系统的「减少动态效果」偏好 */
@media (prefers-reduced-motion: reduce) {
  .nf-art svg,
  .nf-q,
  .nf-bag,
  .nf-eye {
    animation: none;
  }
}
</style>
