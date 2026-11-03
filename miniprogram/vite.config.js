import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import fs from 'fs'
import path from 'path'

// H5 预览时，种子商品图 / 头像等前端静态资源（生产由 nginx 直接喂 /seed-products、/seed-avatars）
// 在本地没有 nginx，故由 vite 直接从 PC 前端 public 目录喂出，避免 H5 里图片 401/裂图。
// 注意：这些图不是 /api，不能走 /api 代理（后端 SecurityConfig 未放行 /api/seed-products → 401）。
const FRONTEND_PUBLIC = path.resolve(__dirname, '../../frontend/public')

function serveFrontendStatic() {
  return {
    name: 'serve-frontend-static',
    configureServer(server) {
      const handler = (req, res, next) => {
        const rel = decodeURIComponent(req.url.split('?')[0])
        const file = path.join(FRONTEND_PUBLIC, rel)
        fs.stat(file, (err, st) => {
          if (err || !st.isFile()) return next()
          const ext = path.extname(file).slice(1).toLowerCase()
          const mime = {
            webp: 'image/webp', jpg: 'image/jpeg', jpeg: 'image/jpeg',
            png: 'image/png', gif: 'image/gif', svg: 'image/svg+xml'
          }[ext] || 'application/octet-stream'
          res.setHeader('Content-Type', mime)
          fs.createReadStream(file).pipe(res)
        })
      }
      // 只接管明确属于前端静态目录的前缀，其余交给 vite 默认流程
      server.middlewares.use('/seed-products', handler)
      server.middlewares.use('/seed-avatars', handler)
    }
  }
}

export default defineConfig({
  plugins: [uni(), serveFrontendStatic()],
  server: {
    // H5 预览时把 /api 代理到本地后端，避免浏览器跨域拦截（小程序本体不受影响）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    },
    // 监听所有网卡：默认只绑 IPv6 回环（::1），部分浏览器按 IPv4 解析 localhost 会连不上；
    // 开全之后 127.0.0.1 / localhost / 局域网 IP（手机同 WiFi 访问）都能打开 H5 预览。
    host: '0.0.0.0',
    port: 5173
  }
})
