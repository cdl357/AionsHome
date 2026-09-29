# 回家前端（Kotlin + Compose）阶段一说明

> 对应《GLM交接文档》：原生 Kotlin + Jetpack Compose 重做情侣陪伴 App 前端，复用 AionsHome 本地数据层，不接外部服务器。
> 分支：`mobile-companion`（阶段一提交：底部导航骨架 + 回家页 + 四页占位）

## 一、工程形态（为什么放在 AionApp 里）

新前端直接加在 **AionApp 现有 Gradle 工程内**（`AionApp/app`），原因：

- homecoming 数据层（`com.aion.chat.homecoming`，50 个 Java 类）对主工程有反向依赖
  （`LauncherActivity` / `AionPushService` / `AionAccessibilityService`），拆独立工程需要先做模块化重构；
- 同模块共存让 Kotlin/Compose 与旧 Java 数据层**零拷贝、零重写**直接互调；
- 旧 WebView 前端**完整保留**（`WebViewActivity` 不再是启动入口，但类与全部功能原样在）。

新入口：`com.aion.chat.compose.MainActivity`（Manifest 中已是 LAUNCHER）。

## 二、目录

```
app/src/main/kotlin/com/aion/chat/compose/
├── MainActivity.kt            # 单 Activity 入口
├── ui/
│   ├── HomecomingApp.kt       # NavHost + 底部导航（聊天|朋友圈|回家(中心凸起)|我们|更多）
│   ├── theme/Theme.kt         # 配色集中管理（冰蓝/白/暖粉点缀，磨砂玻璃 token），后期加樱粉/雾蓝主题
│   ├── home/HomeScreen.kt     # 页面①回家（本阶段全量实现）
│   ├── us/UsScreen.kt         # 页面②我们·日历时光机（阶段一骨架：整月格子+切月+点天抽屉）
│   ├── chat/ChatScreen.kt     # 页面③聊天（骨架；阶段三接 HomecomingChatEngine）
│   ├── moments/MomentsScreen.kt # 页面④朋友圈（骨架；阶段四接线双向动态流）
│   └── more/MoreScreen.kt     # 页面⑤更多（按交接文档 §8 只做七个功能）
└── data/HomecomingData.kt     # 回家页数据装配：在一起天数(2026.07.09)/今日情话/最近动态(读 homecoming.db)
```

## 三、回家页（已按交接文档 §4 实现）

1. 顶部条：Yuri 头像（占位可换）+ `ALREADY TOGETHER` + 大数字天数（衬线、暖粉，从 2026.07.09 起算）+ `since 2026.07.09` + Sean 头像；下方今日情话（本地轮换，后期换 AI 生成）。
2. 一起听歌卡片（播放控件占位）。
3. 今日心情（四个颜文字格，点按提示随后端接线）｜相册（封面占位）。
4. 家里的存粮：模型额度 / 语音字数 / 服务器余额 三条进度（本期占位「待接入」）。
5. 最近动态：读 `homecoming.db` 的 `chat_message`（最近 3 条）与 `memory_local`（最新 1 条），空态柔和。
- 按文档要求：**没有**继续聊天大按钮、**没有**快捷四方块。

## 四、构建

```
cd AionApp
# 需要 JDK 17 + Android SDK (platform 34 / build-tools 34.0.0)
gradlew.bat assembleDebug      # 产物 app/build/outputs/apk/debug/app-debug.apk
```

- `settings.gradle` 已加阿里云镜像（google/public/gradle-plugin），原仓库兜底，网络好可删。
- 数据层未动：`homecoming` 包原样；新功能表（日记/朋友圈/留言/纪念日）阶段二起按需扩展。

## 五、后续阶段

| 阶段 | 内容 |
|---|---|
| 二 | 我们·日历完整版（纪念日专属图标爱心/黑白猫/自定义 + 格子变色 + 新建表单；日记/留言/记忆三卡接线） |
| 三 | 聊天（HomecomingChatEngine/ModelGateway 接线、分条冒泡、气泡皮肤库、背景图可换、加号发图） |
| 四 | 朋友圈（双向动态流、发布配图、点赞评论；独立本地存储，不进日历） |
| 五 | 更多页逐项接线（相册照片墙+私心话、留言板、记忆库、语音通话、陪伴阅读、提醒闹钟、设置）+ 主题/背景切换 |
| 六 | AionPet 桌面小人（AionPet 精灵动画，预留位已有） |

明确不做（交接文档 §8）：世界书、监控、监控日志、密语时刻、小剧场、娱乐室、斗地主、奥罗斯幽林、奥罗斯财团、聊天室、健康心、食谱、备忘录、地图。
