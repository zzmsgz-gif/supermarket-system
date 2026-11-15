#!/usr/bin/env node
/**
 * 小程序产物自检。
 *
 * 为什么需要它：vite 编译时会先清空输出目录再写入。如果中途失败（最常见是被微信开发者工具
 * 占着文件 → EPERM），会留下一个「看着完整、跑起来报错」的残缺产物：
 * 页面 js/wxml 是新的，但 static/ 还没复制过去。
 * 开发者工具打开后只会甩一句「模拟器启动失败：app.json tabBar iconPath 未找到」，
 * 很容易误以为是代码里图标路径写错了（2026-11-15 就是这么被坑的）。
 *
 * 用法：
 *   node scripts/verify-build.js                    # 检查默认 dist/build/mp-weixin
 *   node scripts/verify-build.js ../dist/build/mp-v8  # 检查指定目录
 *
 * 退出码 0 = 完整，1 = 有问题（可直接接在 CI / 发布脚本里）
 */
const fs = require('fs')
const path = require('path')

const root = process.argv[2]
  ? path.resolve(process.argv[2])
  : path.resolve(__dirname, '..', 'dist', 'build', 'mp-weixin')

const problems = []
const ok = []

function rel(p) {
  return path.relative(root, p).split(path.sep).join('/')
}

if (!fs.existsSync(root)) {
  console.error(`✗ 产物目录不存在：${root}`)
  process.exit(1)
}

// 1) app.json 必须能解析
const appJsonPath = path.join(root, 'app.json')
if (!fs.existsSync(appJsonPath)) {
  console.error(`✗ 缺 app.json：${root} 不是小程序产物目录`)
  process.exit(1)
}
let app
try {
  app = JSON.parse(fs.readFileSync(appJsonPath, 'utf8'))
} catch (e) {
  console.error(`✗ app.json 解析失败：${e.message}`)
  process.exit(1)
}
ok.push('app.json 可解析')

// 2) static 目录 —— 这次踩的坑就在这
if (fs.existsSync(path.join(root, 'static'))) {
  ok.push('static/ 存在')
} else {
  problems.push('缺 static/ 目录（静态图全丢，tabBar 图标必然报未找到）')
}

// 3) 每个页面的四件套
const pages = app.pages || []
const pageMissing = []
for (const p of pages) {
  for (const ext of ['.js', '.json', '.wxml', '.wxss']) {
    if (!fs.existsSync(path.join(root, p + ext))) pageMissing.push(p + ext)
  }
}
if (pageMissing.length === 0) {
  ok.push(`${pages.length} 个页面四件套齐全`)
} else {
  problems.push(`页面文件缺失 ${pageMissing.length} 个：${pageMissing.slice(0, 5).join(', ')}${pageMissing.length > 5 ? ' …' : ''}`)
}

// 4) tabBar 图标
const tabList = (app.tabBar && app.tabBar.list) || []
const iconMissing = []
for (let i = 0; i < tabList.length; i++) {
  for (const k of ['iconPath', 'selectedIconPath']) {
    const v = tabList[i][k]
    if (v && !fs.existsSync(path.join(root, v))) iconMissing.push(`tabBar[${i}].${k} → ${v}`)
  }
}
if (iconMissing.length === 0) {
  ok.push(`tabBar ${tabList.length} 项图标齐全`)
} else {
  problems.push(`tabBar 图标缺失 ${iconMissing.length} 个：${iconMissing.join('; ')}`)
}

// 5) 0 字节文件（复制/写入被中断的典型痕迹）
const zero = []
;(function walk(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(fp)
    else if (entry.size === 0) zero.push(rel(fp))
  }
})(root)
if (zero.length === 0) {
  ok.push('无 0 字节文件')
} else {
  problems.push(`${zero.length} 个文件是 0 字节（写入被中断）：${zero.slice(0, 5).join(', ')}`)
}

// 6) project.config.json 的 appid（防止误用别人的产物）
const pcPath = path.join(root, 'project.config.json')
if (fs.existsSync(pcPath)) {
  try {
    const pc = JSON.parse(fs.readFileSync(pcPath, 'utf8'))
    if (pc.appid) ok.push(`appid = ${pc.appid}`)
    else problems.push('project.config.json 里没有 appid（开发者工具导入时会让你手填）')
  } catch (e) {
    problems.push(`project.config.json 解析失败：${e.message}`)
  }
} else {
  problems.push('缺 project.config.json（导入时 AppID 要手填，且可能是选错目录）')
}

// 输出
console.log(`产物目录：${root}`)
for (const s of ok) console.log(`  ✓ ${s}`)
for (const p of problems) console.log(`  ✗ ${p}`)
console.log(problems.length === 0 ? '\n结果：完整，可以导入开发者工具 ✔' : `\n结果：${problems.length} 项问题，先别导入/上传 ✗`)
process.exit(problems.length === 0 ? 0 : 1)
