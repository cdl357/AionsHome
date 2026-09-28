# 信息架构 — 页面、导航与数据来源

> 2026-09-28 更新：按《布局定稿》（docs/布局定稿.md）修订导航与首页骨架；此前的「首页｜聊天｜记忆｜我们｜共影」方案被替代，共影移出导航。
> 对象：移动端新界面（`static/mobile/`）。旧页面全部保留作回退，新旧并行。

## 1. 路由与文件

| 页面 | 路由 | 文件 | 状态 |
|---|---|---|---|
| 回家（首页） | `/m/home`（`/m` 重定向） | `static/mobile/home.html` | 布局定稿版 |
| 我们（日历时光机） | `/m/us` | `static/mobile/us.html` | 骨架（留言/钉纪念日随后端接入） |
| 更多 | `/m/more` | `static/mobile/more.html` | 含主题切换器 |
| 留言板 | `/m/board` | `static/mobile/board.html` | 骨架（接口未接态） |
| 旧首页（回退） | `/` | `static/home.html` | 不动 |
| 聊天 | `/chat` | `static/chat.html` + `chat.js` | 本阶段不动 |
| 朋友圈 | `/moments` | `static/moments.html` | 已有，导航直链（不参与日历） |
| 设置（模型与服务商） | `/settings` | `static/settings.html` | 本阶段不动 |
| 记忆库 | `/memory` ｜ 相册 `/album` ｜ 日记 `/diary` ｜ 世界书 `/worldbook` ｜ 点歌台 `/music-station` ｜ 阅读 `/reading` ｜ 小剧场 `/theater` ｜ 娱乐室 `/playground` ｜ 基金 `/fund` ｜ 语音试听 `/tts-test` | 已有 | 「更多」分类直链 |

## 2. 底部导航（布局定稿，顺序固定）

```
聊天 ｜ 朋友圈 ｜ 回家(中心) ｜ 我们 ｜ 更多
```

- 聊天：`/chat`（`?conv={id}` 直达会话）
- 朋友圈：`/moments`，独立页面，不参与日历
- 回家（中心，抬高强调）：`/m/home`
- 我们：`/m/us` 日历时光机——点任意一天可新建（写日记 / 钉纪念日）、查看（Sean 的日记 / 两人的留言 / 重要记忆摘要）；纪念日 = 钉在日历上的日程；今日情话在本页；**不显示在一起天数**（首页已有）；朋友圈不进日历
- 更多：`/m/more` 按类翻全部的总入口——相册 / 留言板 / 记忆库（日记已并入日历，这里保留按类翻全部）+ 模型与连接 + 管理 + 休闲 + 主题切换

## 3. 回家页骨架（布局定稿，从上到下）

1. 顶部：Yuri / Sean 头像（真实图片，点头像随时换，存 localStorage）+ 名字 + 中间连接符号（待定，暂用「×」占位）
2. 在一起 XX 天（固定从 2026.07.09 起算）
3. 今日情话（本地短句按日轮换）
4. 继续聊天：大按钮 + 线路 + 连接状态
5. 快捷入口方块：相册 ｜ 留言板 ｜ 记忆库
6. 最近动态：最近日记 / 留言 / 照片（留言接口接入后自动并入；朋友圈不进这里）

## 4. 主题系统（布局定稿·方案 C）

- 现成主题一键换色：樱粉 / 雾蓝 / 墨绿 / 藕荷 / 奶茶（无橘色，奶橘/落日已删）
- 调色盘：只调一个主色，背景 / 卡片 / 文字由系统自动派生（暖橙色相自动压饱和防翻车）
- 实现：`static/mobile/theme.js`（预设+派生+localStorage 持久化）+ `theme.css` 变量源；入口在「更多」

## 5. 首页数据来源（全部现有 API）

| 区块 | 数据 | API |
|---|---|---|
| 继续聊天 | 最近会话/模型/连接状态 | `GET /api/conversations` + `GET /api/models` + `GET /api/codex/status` |
| 最近动态 | 日记 + 相册照片 | `GET /api/diaries` + `GET /api/album/photos` |
| 我们·日历 | 按天日记 / 记忆 | `GET /api/diaries`（author=aion=Sean）+ `GET /api/memories?kind=long_term`，客户端按天过滤 |
| 头像 | 两人自选图片 | localStorage（`aion_avatar_yuri` / `aion_avatar_sean`），无仓库素材 |

连接状态显示规则（订阅线路）：`未登录` / `未安装` / `登录态存在` / `启动失败` / `可用`；API Key 线路显示线路名，永不标注「订阅」。

## 6. 导航技术约定

- 新页面共用 `static/mobile/theme.css`（变量源）+ `theme.js`（主题 boot 防闪烁）
- 聊天壳常驻 + 子页 iframe 浮层为聊天页既有机制；新首页直接链接独立页；Android 返回键由聊天壳 `handleNativeBack` 兼容
- 所有请求失败静默降级为空状态，不白屏；后端未接入的功能显示诚实的「未接入」空态，不放假数据

## 7. 后续阶段对齐

- P4 聊天页移动化：沿用 `chat.js` 逻辑层（API 契约不变）
- P5 新设置页：模型与服务商 + GPT/Codex 连接状态独立页
- P6 留言板后端、纪念日钉日历后端、共影（已移出导航，是否新建待定）
