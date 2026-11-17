<script>
import { syncCartBadge } from '@/utils/cartBadge'
import { syncCouponDot } from '@/utils/couponBadge'
import { isLoggedIn } from '@/store/auth'

export default {
  onLaunch() {
    // 启动时可在此尝试用本地 token 静默刷新登录态
  },
  onShow() {
    // 每次小程序回到前台都对齐一次购物车角标：
    // 用户可能在别的小程序/网页里加过购，也可能只是切出去又回来，
    // tabBar 角标是"当前事实"而不是"上次操作的残留"。
    // 延时一点：冷启动时 tabBar 还没渲染完，setTabBarBadge 会失败。
    if (!isLoggedIn()) return
    setTimeout(() => {
      syncCartBadge(true)
      // 「有券可领」红点同理：运营随时可能在后台加券，用户回到前台就该看到提示。
      // syncCouponDot 内部有 5s 节流，和页面自己的 onShow 不会重复打接口。
      syncCouponDot(true)
    }, 300)
  }
}
</script>

<style>
page {
  background: #FAF8F3;
  font-size: 28rpx;
  color: #333;
}

/* H5 预览时 uni-app 会把 scroll-view 渲染成可滚动 div，右侧会多出一条灰滚动条；
   小程序端本来就不显示滚动条，这里统一隐藏，避免网页预览显得脏。 */
::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}
</style>
