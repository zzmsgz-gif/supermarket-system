import { defineStore } from 'pinia'

// 购物车全局状态：从接口拉取后存入，避免组件间重复请求。
export const useCartStore = defineStore('cart', {
  state: () => ({
    items: [],
    selectedCount: 0,
    selectedAmount: 0,
  }),
  getters: {
    isEmpty: (state) => state.items.length === 0,
  },
  actions: {
    setCart(payload) {
      this.items = payload.items || []
      this.selectedCount = payload.selectedCount || 0
      this.selectedAmount = payload.selectedAmount || 0
    },
    clear() {
      this.items = []
      this.selectedCount = 0
      this.selectedAmount = 0
    },
  },
})
