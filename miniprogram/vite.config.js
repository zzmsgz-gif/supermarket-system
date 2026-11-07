import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'
import fs from 'fs'
import path from 'path'

// ⚠️ 不要用 `path.resolve(__dirname, '../../frontend/public')`：vite 执行 config 时
// __dirname 指向仓库根而不是本文件所在目录，会算成 `D:\frontend\public`（少一层），
// 于是所有 /seed-products 图片静默 404 → 落到 SPA 兜底返回 text/html（2026-11-03 修）。
// 这里按 cwd 探测多种可能，取第一个真实存在的目录。
function resolveFrontendPublic() {
  const candidates = [
    path.resolve(process.cwd(), '../frontend/public'),
    path.resolve(process.cwd(), 'frontend/public'),
    path.resolve(__dirname, '../../frontend/public'),
    path.resolve(__dirname, '../frontend/public')
  ]
  for (const c of candidates) {
    try {
      if (fs.statSync(c).isDirectory()) return c
    } catch (e) {
      // 继续试下一个
    }
  }
  return candidates[0]
}

const FRONTEND_PUBLIC = resolveFrontendPublic()

function serveFrontendStatic() {
  return {
    name: 'serve-frontend-static',
    configureServer(server) {
      // ⚠️ connect 的 `use(base, fn)` 会在调用 handler 前把 req.url 里的 base 前缀**剥掉**
      // （/seed-products/sx-tomato.webp → req.url = '/sx-tomato.webp'）。
      // 早期写法直接 path.join(FRONTEND_PUBLIC, req.url)，等于去 public 根目录找文件 → 永远 stat 失败
      // → next() 落到 SPA 兜底 → 返回 index.html（状态码 200 但 content-type: text/html），
      // 于是所有 /seed-products 商品图静默裂图，且只看状态码完全查不出来（2026-11-03 修）。
      // 所以这里必须把 base 拼回去。
      const mount = (base) => {
        server.middlewares.use(base, (req, res, next) => {
          const rel = decodeURIComponent(req.url.split('?')[0])
          const file = path.join(FRONTEND_PUBLIC, base, rel)
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
        })
      }
      // 只接管明确属于前端静态目录的前缀，其余交给 vite 默认流程
      mount('/seed-products')
      mount('/seed-avatars')
    }
  }
}

// H5 预览的后端目标：默认打本地 8080；要连线上用环境变量切（见下方 server.proxy 注释）。
// 为什么 H5 不能像小程序那样直连 https://haomart.xyz：浏览器会判跨域，而线上 nginx 没给
// 我们开 CORS 头。走 vite 同源代理（/api → 目标站）就没有跨域问题，H5 代码一行都不用改。
const API_TARGET = process.env.MINI_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [uni(), serveFrontendStatic()],
  server: {
    // H5 预览时把 /api 代理到后端，避免浏览器跨域拦截（小程序本体不受影响）
    //   本地后端：      NODE_OPTIONS= npm run dev:h5
    //   连线上后端：    MINI_API_TARGET=https://haomart.xyz NODE_OPTIONS= npm run dev:h5
    proxy: {
      '/api': {
        target: API_TARGET,
        changeOrigin: true
      }
    },
    // 监听所有网卡：默认只绑 IPv6 回环（::1），部分浏览器按 IPv4 解析 localhost 会连不上；
    // 开全之后 127.0.0.1 / localhost / 局域网 IP（手机同 WiFi 访问）都能打开 H5 预览。
    host: '0.0.0.0',
    port: 5173
  }
})
