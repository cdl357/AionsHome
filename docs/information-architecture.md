# 信息架构 — 页面、导航与数据来源

> 对象：移动端新界面（`static/mobile/`）。旧页面全部保留作回退，新旧并行。

## 1. 路由与文件

| 页面 | 路由 | 文件 | 状态 |
|---|---|---|---|
| 移动首页 | `/m/home`（`/m` 重定向） | `static/mobile/home.html` | 本阶段新建，纯 UI |
| 旧首页（回退） | `/` | `static/home.html` | 不动 |
| 聊天 | `/chat` | `static/chat.html` + `chat.js` | 本阶段不动 |
| 设置（模型与服务商） | `/settings` | `static/settings.html` | 本阶段不动 |
| 记忆 | `/memory` | `static/memory.html` | 已有，导航直链 |
| 相册 | `/album` | `static/album.html` | 已有 |
| 朋友圈 | `/moments` | `static/moments.html` | 已有 |
| 日记 | `/diary` | `static/diary.html` | 已有 |
| 世界书 | `/worldbook` ｜ 阅读 `/reading` ｜ 小剧场 `/theater` ｜ 娱乐室 `/playground` ｜ 基金 `/fund` ｜ 点歌台 `/music-station` ｜ 语音试听 `/tts-test` 等 | 已有 | 「更多」抽屉直链 |

## 2. 底部导航（五项固定）

```
首页 ｜ 聊天 ｜ 记忆 ｜ 我们 ｜ 共影
```

- 首页：`/m/home`（当前页）
- 聊天：`/chat`（保留聊天壳常驻逻辑；`?conv={id}` 直达会话）
- 记忆：`/memory`；记忆页内含：长期记忆、记忆搜索、相册（`/album`）、记忆总结、重要事实
- 我们：聚合页后续阶段新建；当前过渡方案直链 `/moments`，聚合页将含：朋友圈、日记、梦境（新建）、留言板
- 共影：新建页（后续阶段）；当前点击给出「即将开放」提示，不放假页面

「更多」入口在首页顶部（与设置并列），以底部抽屉列出低频功能；不做图标墙、不进首页中央。

## 3. 首页区块与数据来源（全部现有 API）

| 区块 | 数据 | API |
|---|---|---|
| 头部状态 | 陪伴状态/今日情话 | `GET /api/chat_status`（空则用本地情话库按日轮换） |
| 在一起天数 | 纪念日 | `GET /api/idle-autonomy`（roles[].config.relationship_started_on）+ 复用 `static/relationship-days.js` |
| 继续聊天 | 最近会话标题/时间/模型/连接状态 | `GET /api/conversations` + `GET /api/models` + `GET /api/codex/status` |
| 最近历史 | 最近会话 3-5 条 | `GET /api/conversations` |
| 最近的我们 | 朋友圈/日记/相册混合时间线 | `GET /api/moments` + `GET /api/diaries` + `GET /api/album/photos`，前端按时间合并取前几条 |

连接状态显示规则（订阅线路）：`未登录` / `未安装` / `登录态存在` / `启动失败` / `可用`，来自 `/api/codex/status`；API Key 线路显示线路名，永不标注「订阅」。

## 4. 导航技术约定

- 新页面共用 `static/mobile/theme.css`（唯一变量源）
- 页面跳转用现有模式：聊天壳常驻 + 子页 iframe 浮层（`openSubPage`）是聊天页既有机制；新首页直接链接 `/chat` 等独立页，Android 返回键行为由聊天壳的 `handleNativeBack` 兼容
- 所有请求失败静默降级为空状态，首页不因任何接口失败白屏

## 5. 后续阶段对齐

- P4 聊天页移动化：沿用 `chat.js` 逻辑层（发送/流式/停止/重试/历史 API 契约不变）
- P5 新设置页：「模型与服务商」+「GPT/Codex 连接状态」（订阅与 Key 线路分组展示）
- P6 记忆页聚合、我们页聚合（含梦境新建）、共影页、更多页独立成页
