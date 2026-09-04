import { defineStore } from 'pinia'

// 用户全局状态：登录信息、角色（USER / ADMIN）、登录态。
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: null,
    role: '',
  }),
  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
  },
  actions: {
    setAuth(token, user) {
      this.token = token
      this.user = user
      this.role = user?.role || ''
      if (token) localStorage.setItem('token', token)
    },
    logout() {
      this.token = ''
      this.user = null
      this.role = ''
      localStorage.removeItem('token')
    },
  },
})
