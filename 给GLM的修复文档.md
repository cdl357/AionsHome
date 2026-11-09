# AionsHome 朋友圈 + 打包问题修复说明

> 整理人：沈聿淮（经手排查 2026-11-09 晚）
> 给 GLM：请按这份文档修，不要再盲改几十版了，病根已经定位清楚。

---

## 一、先搞清楚仓库里有两个 App 工程（关键！）

仓库 `cdl357/aionshome` 里有**两个不同的 Android 工程**，打出两个不同的包：

| 工程目录 | 产出 APK | 大小 | 说明 |
|---|---|---|---|
| `AionApp/` | AionChat-x.x.x.apk | ~32MB | 另一个App，能正常打包成功 |
| **（AionsHome工程）** | AionsHome-0.3.138.apk | ~55MB | **小鑫手机在用的就是这个，一直打包失败** |

**小鑫手机上装的是 AionsHome（55MB 那个）。所有问题都针对这个工程，不要动 AionApp。**

GLM 第一步：确认 AionsHome 工程的实际目录路径（打出 55MB 包的那个 build.gradle 在哪）。

---

## 二、打包失败的真凶：gradlew 换行符是 CRLF

### 病症
GitHub Actions 里 `Build APK` 这步失败，日志报：
```
./gradlew: /bin/sh^M: bad interpreter: No such file or directory
```

### 病根
那个 `^M` = Windows 换行符（CRLF）混进了 `gradlew` 脚本。
Linux 打包机把 `#!/bin/sh\r` 当成解释器路径，找不到，直接拒绝执行。
**代码写得再对都没用，因为卡在脚本跑不起来这一步。GLM 改的几十版代码全被这个挡住了。**

### 修法（AionsHome 工程的 gradlew）
把该工程的 `gradlew` 换行符从 CRLF 转成 LF：

```bash
# 在 AionsHome 工程根目录
sed -i 's/\r$//' gradlew
# 或
dos2unix gradlew
```

并在 `.gitattributes` 里钉死，防止以后又被 Windows 编辑器改回去：
```
gradlew text eol=lf
*.sh text eol=lf
```

提交时确认 gradlew 是 LF（`file gradlew` 或 `cat -A gradlew | head -1` 应是 `#!/bin/sh$` 不带 `^M`）。

> 注：AionApp 工程的 gradlew 现在是正常 LF，所以它能打包成功。照着它来即可。

---

## 三、朋友圈数据源问题（看不到内容的根因）

### 现状
- 橘瓣（orangechat）的朋友圈走**网关 Supabase**（表 `moments`），云端数据，所以正常。
- AionsHome 之前读的是**本地 SQLite**，和云端不是同一份数据，所以小鑫看不到沈聿淮发的朋友圈。

### Supabase moments 表字段（云端真实结构）
```
id, author, content, context_note, reply_status, liked,
reply_content, replied_at, yuri_liked, created_at(ISO字符串), reply_due_at, images
```
- `created_at` 是 ISO 时间字符串，如 `2026-11-08T19:31:54.681599+00:00`
- `author` 是中文名："沈聿淮" / "李雨鑫"
- `images` 是数组
- 点赞：`liked`(沈发的被李赞) / `yuri_liked`
- 回复：`reply_content` + `replied_at`（单条，不是多评论结构）

### 已经做好的参考实现
我已经把 **aion-chat（Python后端）** 的 `routes/moments.py` 里 `list_moments` 改成了读 Supabase，
并提交到 main 分支（commit dc365bc）。里面有完整的字段映射函数 `_sb_map_moment` / `_sb_list_moments`，
**AionsHome 要连 Supabase 可以直接抄那套映射逻辑**，重点看：
- ISO 时间字符串 ↔ Unix 时间戳 的互转（前端游标用时间戳）
- images → attachments
- reply_content → comments 数组
- liked/yuri_liked → reactions 数组

### AionsHome 要做的
确认 AionsHome 的朋友圈是：
1. App 内嵌 WebView 加载某个 Python 后端的网页？→ 那后端 `/api/moments` 照 aion-chat 那样改读 Supabase。
2. App 原生代码直接请求？→ 在原生层直接调 Supabase REST：
   `GET {SUPABASE_URL}/rest/v1/moments?select=*&order=created_at.desc&limit=N`
   headers: `apikey` + `Authorization: Bearer <key>`

### Supabase 连接信息
- URL / KEY 在网关的 `/opt/services/gateway/code/.env` 里（`SUPABASE_URL` / `SUPABASE_KEY`）
- 项目 id：`byqqwypdfiwvalozihgs`
- App 端要把这两个值配进去（local.properties 或 BuildConfig），不要硬编码进源码提交。

---

## 四、顺序建议

1. **先修 gradlew 换行符** → 让 AionsHome 能打包成功（这是地基，不修这个改啥都看不到）。
2. 打包成功、小鑫能装上能跑之后，再处理朋友圈数据源。
3. 朋友圈改 Supabase，抄 commit dc365bc 的映射逻辑。

---

## 五、安全提醒
- 那个 `ghp_` 开头的 GitHub token 在聊天和语料里暴露多处，**修完请让小鑫去 GitHub 作废重置**。
- Supabase key 不要硬编码进源码提交到仓库。
