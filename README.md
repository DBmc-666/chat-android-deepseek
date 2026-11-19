# Chat

一个**纯本地**的 DeepSeek 对话客户端。所有会话、API Key 和请求都在你自己的手机上，不经过任何中转服务器。

界面参照 DeepSeek 官方 App 制作，为了对齐操作手感和视觉习惯。不是官方产品，与 DeepSeek 没有隶属关系。使用前需要自备 DeepSeek 官方 API Key。

## 它做什么

直接把请求发到 DeepSeek 官方 API，把返回的流式内容渲染成对话界面。没有账号体系，没有云端同步，没有埋点。

## 功能

**对话**

- 流式输出，支持随时停止
- 深度思考：折叠/展开，显示「已思考（用时 N 秒）」，思考过程与正文分开滚动
- 智能搜索：服务端联网搜索，结果可在底部面板查看并跳转
- 阅读链接：发送含 URL 的消息时自动抓取网页正文，「已浏览 N 个页面」
- 附件：图片、文档上传后随消息发送
- 消息版本：重新生成时保留旧回答，可 `‹ 1/2 ›` 来回切换
- Markdown 渲染：标题、列表、粗体/斜体/行内码、代码块（含语法高亮与一键复制）
- 公式与图表：`$$…$$` 走 KaTeX，```` ```mermaid ```` 走 Mermaid（本地打包，不联网加载）

**语音**

- 输入框点麦克风切换为「按住说话」，松手自动转写
- 转写后默认填进输入框并切回键盘，方便先改再发；设置里可改成「转写后直接发送」
- 单次录音上限 110 秒（服务端限制 120 秒），到点自动结束并提示

**会话管理**

- 侧栏分组（今天 / 昨天 / 7 天内 / 30 天内 / 更早）
- 置顶、重命名、删除
- 多选模式批量置顶/删除
- 导入官方导出的历史对话 ZIP

**设置**

- API Key、接口地址、模型
- 外观：跟随系统 / 浅色 / 深色
- 字体大小：跟随系统，或用滑块在 0.8–1.2 之间调整
- 语音自动发送：转写后直接发出，或回到键盘先改再发
- 数据：导入历史对话、删除所有对话

**实验性功能**

- 每条回答下方显示 token 消耗
- Token 用量页：今日 / 总计的输入、缓存命中率、输出，以及按官方定价估算的花费（自动区分高峰与空闲时段）

## 构建

需要 JDK 17 与 Android SDK（compileSdk 34）。

```bash
git clone <repo>
cd dschat
./gradlew assembleDebug
```

Windows 用 `gradlew.bat`。产物在 `app/build/outputs/apk/debug/`。

> **注意**：SDK 路径不要包含中文或非 ASCII 字符。AAPT2 在处理中文路径时会失败（`AssetsProvider.cpp:114 Failed to stat file`）。`local.properties` 里的 `sdk.dir` 指向纯 ASCII 路径即可。

### 正式签名

仓库默认用 debug 签名出 release 包，只为方便试装。要正式分发，在根目录创建 `keystore.properties`（已在 `.gitignore` 中）：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

存在这个文件时，`assembleRelease` 会自动切到正式签名；不存在则回落到 debug 签名。

> ### ⚠️ 关于当前发布包的签名
>
> **`dist/` 里的 APK 使用 Android debug 证书签名。**
>
> - debug 证书的私钥是公开的（就在 Android SDK 里），任何人都能签出同包名、同证书的 APK
> - 因此它**只适合手动安装试用**，不要当作可信分发渠道
> - 如果你要自己发布，请按上面的方式配置自己的 keystore，否则无法覆盖安装已装的 debug 签名版本
> - 上架应用商店必须使用自己的正式签名

### 界面快照测试
项目用 Robolectric + Roborazzi 在 JVM 上渲染界面快照，不需要模拟器：

```bash
./gradlew testDebugUnitTest
```

截图输出到 `app/build/screenshots/`，覆盖首页、对话、思考展开、搜索结果、设置、侧栏、字体大小等 20 余个场景。

## 发布流程

### 第一步：先和远端对比，不要凭记忆

每次提交前先跑这一组，看清远端到底处在什么状态：

```bash
git fetch origin --tags --prune
git status -sb                  # 本地是否落后或领先
git log origin/main..HEAD       # 还没推上去的提交
git log HEAD..origin/main       # 远端有、本地没有的提交
git status --porcelain          # 工作区未提交的改动
git tag -l                      # 本地 tag
git ls-remote --tags origin     # 远端 tag
ls dist/                        # 是否残留旧版本包
```

确认这四件事之后再动手：

1. **远端有没有别人推的提交**需要先合并
2. **版本号要和远端 tag 衔接**——用 `git tag -l` 的结果决定下一个号，不要凭印象跳号或回退
3. **CHANGELOG 的条目要从提交里取**，不要凭回忆写：

   ```bash
   git log v0.10.2..HEAD --oneline
   ```

   逐条对照，已经发布过的内容不要重复写进新版本
4. **`dist/` 里只保留当前版本的包**，临时测试包（`-memo`、`-mixed` 之类）发版前清掉

> `git fetch` 失败时不要继续判断对比结果——此时 `origin/main` 是上次的缓存，
> 显示"一致"并不代表真的同步。重试到成功为止。

### 第二步：改版本号并核对

`app/build.gradle.kts` 里的 `versionCode` 要递增，`versionName` 与即将打的 tag 一致：

```bash
./gradlew testDebugUnitTest                    # 测试必须全绿
./gradlew assembleDebug assembleRelease
aapt2 dump badging dist/Chat-X.Y.Z-release.apk | head -1   # 核对版本号写进去了
```

### 第三步：提交、打 tag、推送

```bash
git add -A
git commit -m "feat: vX.Y.Z ..."
git tag -a vX.Y.Z -m "vX.Y.Z"
git push origin main
git push origin vX.Y.Z        # tag 不会跟着 main 自动推
```

推送后核对远端确实收到了：

```bash
git ls-remote --heads origin
git ls-remote --tags origin
```

### 第四步：GitHub Release

在 Releases 页面选择刚推的 tag，上传 `dist/Chat-X.Y.Z-release.apk`，说明直接用 CHANGELOG 对应段落，并附上 SHA-256 与签名警示。

## 项目结构

```
app/src/main/java/com/yg/dschat/
├── MainActivity.kt        # 入口 + 对话界面（消息列表、输入栏、顶栏）
├── Sidebar.kt             # 侧栏：分组、多选、置顶
├── SettingsDialog.kt      # 设置页
├── FontSizeScreen.kt      # 字体大小页
├── ExperimentalScreen.kt  # 实验性功能（占位）
├── SearchSheet.kt         # 搜索结果底部面板
├── MarkdownView.kt        # Markdown 解析与渲染
├── SyntaxHighlight.kt     # 代码语法着色
├── WebRender.kt           # KaTeX / Mermaid 的 WebView 渲染
├── AnthropicClient.kt     # Messages 协议客户端（SSE 解析、历史构造）
├── ChatViewModel.kt       # 会话状态
├── Models.kt              # 数据模型与 JSON 序列化
├── SessionStore.kt        # 会话落盘（原子写入）
├── SettingsStore.kt       # 设置存储
├── AudioIo.kt             # 录音、ASR、TTS
├── ZipImporter.kt         # 官方历史对话导入
└── Theme.kt               # 配色与尺寸
```

## 协议

用的是 DeepSeek 的 Messages 协议（Anthropic 兼容）：

- `POST {base}/anthropic/v1/messages`，`x-api-key` 鉴权，SSE 流式
- 服务端工具：`deepseek_web_search`、`deepseek_read_url`
- 文件：`POST {base}/anthropic/v1/files`，需带 `anthropic-beta: files-api-2025-04-14`
- 语音：`{base}/v1/tools/asr`、`{base}/v1/tools/tts`，`Authorization: Bearer` 鉴权

**没有注入任何系统提示词**，模型人格来自模型本身。每轮会附带一条 `latest_reminder` 消息（日期、星期、地区、平台、语言），这是协议要求的格式。

## 已知限制

- 只支持 DeepSeek 官方 API，未适配任何第三方中转站
- 界面仅简体中文（`values/strings.xml` 里留了扩展位）
- 语音转录、公式与图表渲染需要真机验证，JVM 快照测试覆盖不到
- 只做输入侧语音（ASR），不提供朗读（TTS）
- 消息版本会随会话持久化，但当前停留在第几个版本不回传模型（上下文只用最新版本）

## 授权

代码以 MIT 授权发布，见 [LICENSE](LICENSE)。

第三方组件（KaTeX、Mermaid、AndroidX、OkHttp、Coil 等）的授权见 [NOTICE.md](NOTICE.md)。
