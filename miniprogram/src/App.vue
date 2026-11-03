<script>
export default {
  onLaunch() {
    // 启动时可在此尝试用本地 token 静默刷新登录态
  },
  onShow() {}
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
