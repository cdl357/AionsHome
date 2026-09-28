# 信息架构 — 页面、导航与数据来源

> 2026-09-28 第三次更新：按《布局定稿》v3——朋友圈（方案 A 微信流式）、记忆库（方案 C+D）、留言板（方案 A 便利贴）三功能落地；聊天页记入「分条消息保留 + 气泡库（方案 B）」待 P4 处理。
> 对象：移动端新界面（`static/mobile/`）。旧页面全部保留作回退，新旧并行。

## 1. 路由与文件

| 页面 | 路由 | 文件 | 状态 |
|---|---|---|---|
| 回家（首页） | `/m/home`（`/m` 重定向） | `static/mobile/home.html` | 布局定稿版 |
| 朋友圈 | `/m/moments` | `static/mobile/moments.html` | 接现有 moments API，可发可评可赞 |
| 我们（日历时光机） | `/m/us` | `static/mobile/us.html` | 骨架（留言/钉纪念日随后端接入） |
| 相册（照片墙+故事） | `/m/album` | `static/mobile/album.html` | 接现有相册 API |
| 记忆库 | `/m/memories` | `static/mobile/memories.html` | 接现有 memories API，可增改删 |
| 更多 | `/m/more` | `static/mobile/more.html` | 含主题切换器 |
| 留言板 | `/m/board` | `static/mobile/board.html` | 便利贴墙骨架（接口未接态） |
| 旧首页（回退） | `/` | `static/home.html` | 不动 |
| 聊天 | `/chat` | `static/chat.html` + `chat.js` | 布局先不动（定稿 §2） |
| 旧版朋友圈 | `/moments` | `static/moments.html` | 保留，「更多→其他」可达 |
| 设置（模型与服务商） | `/settings` | `static/settings.html` | 本阶段不动 |
| 旧版相册（上传/管理） | `/album` | `static/album.html` | 保留 |
| 记忆库（旧工作台） | `/memory` | `static/memory.html` | 保留 |
| 日记 `/diary` ｜ 世界书 `/worldbook` ｜ 点歌台 `/music-station` ｜ 阅读 `/reading` ｜ 小剧场 `/theater` ｜ 娱乐室 `/playground` ｜ 基金 `/fund` ｜ 语音试听 `/tts-test` | 已有 | 「更多」分类直链 |

## 2. 底部导航（布局定稿，顺序固定）

```
聊天 ｜ 朋友圈 ｜ 回家(中心) ｜ 我们 ｜ 更多
```

- 聊天：`/chat`（布局照现成前端先不动；**分条消息**（一次回复拆多条小气泡陆续冒出）保留现有行为；**气泡库（方案 B：整套气泡皮肤可换）** 列入 P4 聊天移动化范围）
- 朋友圈：`/m/moments`（方案 A 双向）——微信流式：头像+名字+文字+图，底下一栏点赞评论可展开；右下角 + 发布（写字+配图，走 /api/upload 后拼 attachments）；Yuri / Sean 都能发、能互相评论点赞；删自己的动态/评论；独立页面不参与日历
- 回家（中心，抬高强调）：`/m/home`
- 我们：`/m/us` 日历时光机——整月格子 + 左右滑切换月份；点天新建（写日记 / 钉纪念日）与查看（三张摘要卡，点开才展开，顺序 ①Sean 的日记 ②两人的留言 ③重要记忆摘要，后期可调）；**没日记 = Sean 偷懒，如实提醒不显示空白**；今日情话在本页；不显示在一起天数；朋友圈不进日历
- 更多：`/m/more` 按类翻全部——相册 / 留言板 / 记忆库 + 模型与连接 + 管理 + 休闲 + 主题切换

## 3. 记忆库（方案 C 权限 + 方案 D 浏览）

- 权限（后端已支持）：Sean 聊天中自动记重要的事；Yuri 能看、能手动加（+ → POST /api/memories）、能改（纠正记错的，PUT）、能删（DELETE）
- 浏览：分类 chips（全部 / 长期重要 / 日常，kind 过滤）+ 搜索框（q 关键词）+ 按时间列表（新的在上，before 游标分页）——「关于你的 / 我们的约定 / 重要日子」等细分标签需后端分类字段，列入 P6
- 数据：GET /api/memories?kind=&q=&before=（含 memory_kind / source_*_ts）

## 4. 留言板（方案 A：双向便利贴 + 点开可对话）

- 双向：Yuri 和 Sean 都能贴，互相留话
- 墙面：两列便利贴平铺按时间排；点开某张 → 该贴底下的回复平铺 + 输入框继续对话
- 当前为骨架：墙面空态与发布按钮置灰均为诚实状态，结构已按方案 A 定好，后端接入即用（P6）

## 5. 相册（方案 C：照片墙 + 故事）

- 列表：照片墙（三列方格缩略图，懒加载，分页「再看一些」）
- 点开一张照片：上方大图 + 「Sean 存这张时在想什么」（note 字段）+ 摄于/存于日期 + 相册归属
- 私心话与备注（标题、拍摄日期）可事后补/改（PUT /api/album/photos/{id}）
- 上传与批量管理仍在旧版相册页；「存照片时可写私心话」入口列 P6

## 6. 回家页骨架（布局定稿，从上到下）

1. 顶部：Yuri / Sean 头像（真实图片，点头像随时换，存 localStorage）+ 名字 + 中间连接符号（待定，暂用「×」占位）
2. 在一起 XX 天（固定从 2026.07.09 起算）
3. 今日情话（本地短句按日轮换）
4. 继续聊天：大按钮 + 线路 + 连接状态
5. 快捷入口方块：相册 ｜ 留言板 ｜ 记忆库
6. 最近动态：最近日记 / 留言 / 照片（朋友圈不进这里）

## 7. 主题系统（布局定稿·方案 C）

- 现成主题一键换色：樱粉 / 雾蓝 / 墨绿 / 藕荷 / 奶茶（无橘色，奶橘/落日已删）
- 调色盘：只调一个主色，背景 / 卡片 / 文字由系统自动派生（暖橙色相自动压饱和防翻车）
- 实现：`static/mobile/theme.js` + `theme.css` 变量源；入口在「更多」

## 8. 数据来源（全部现有 API）

| 区块 | 数据 | API |
|---|---|---|
| 朋友圈 | 动态流/发帖/配图/赞/评论/删 | GET/POST/DELETE /api/moments、/react、/comments、POST /api/upload、POST /api/moments/mark-read |
| 记忆库 | 分类/搜索/列表/增改删 | GET/POST/PUT/DELETE /api/memories（kind、q、before） |
| 继续聊天 | 最近会话/模型/连接状态 | GET /api/conversations + /api/models + /api/codex/status |
| 最近动态 | 日记 + 相册照片 | GET /api/diaries + /api/album/photos |
| 我们·日历 | 按天日记 / 记忆 | GET /api/diaries（author=aion=Sean）+ /api/memories?kind=long_term |
| 相册 | 照片墙 / 详情 / 私心话 | GET /api/album/photos /thumbnail /download + PUT /api/album/photos/{id} |
| 头像 | 两人自选图片 | localStorage（aion_avatar_yuri / aion_avatar_sean），无仓库素材 |

朋友圈的 AI 侧行为（Sean 发动态、回应评论）由现有后端驱动：聊天中的 [MOMENT:] 指令与 expect_reply 触发 AI 回复，等价于教程中的「双向 + 惰性生成」，无需新后端。

连接状态显示规则（订阅线路）：`未登录` / `未安装` / `登录态存在` / `启动失败` / `可用`；API Key 线路显示线路名，永不标注「订阅」。

## 9. 导航技术约定

- 新页面共用 `static/mobile/theme.css`（变量源）+ `theme.js`（主题 boot 防闪烁）
- 聊天壳常驻 + 子页 iframe 浮层为聊天页既有机制；新首页直接链接独立页；Android 返回键由聊天壳 `handleNativeBack` 兼容
- 所有请求失败静默降级为空状态，不白屏；后端未接入的功能显示诚实的「未接入」空态，不放假数据

## 10. 后续阶段对齐

- P4 聊天页移动化：沿用 chat.js 逻辑层；落实**分条消息**确认与**气泡库（方案 B：整套皮肤可换）**
- P5 新设置页：模型与服务商 + GPT/Codex 连接状态独立页
- P6 留言板后端（便利贴+对话）、纪念日钉日历后端、上传时写私心话入口、记忆细分分类（关于你的/我们的约定/重要日子）、共影（已移出导航，是否新建待定）
