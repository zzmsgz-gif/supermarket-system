<script>
import { syncCartBadge } from '@/utils/cartBadge'
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
    setTimeout(() => { syncCartBadge(true) }, 300)
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
