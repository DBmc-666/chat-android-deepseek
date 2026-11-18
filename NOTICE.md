# 第三方组件与授权

本项目在运行时和构建时使用下列第三方组件。清单按组件类型分组，逐条列出授权与用途。

## 打包进 APK 的资源

### KaTeX（数学公式渲染）

- 文件：`app/src/main/assets/katex/katex.min.js`、`katex.min.css`、`fonts/*.woff2`
- 版本：0.16.11
- 用途：在 WebView 中渲染 `$$…$$` 行间公式
- 授权：
  - 代码（JS/CSS）：**MIT License**，Copyright (c) 2013-2020 Khan Academy and other contributors
  - 字体（woff2）：**SIL Open Font License 1.1**
- 说明：SIL OFL 1.1 允许免费使用、修改、再分发（包括商业与闭源产品），要求保留版权声明，且不得单独售卖字体本身。本项目原样打包，未做修改。

### Mermaid

- 文件：`app/src/main/assets/mermaid.min.js`
- 版本：11.4.1
- 用途：在 WebView 中渲染 ```` ```mermaid ```` 图表
- 授权：**MIT License**，Copyright (c) 2014-2022 Knut Sveidqvist

## 构建依赖（Maven）

| 组件 | 授权 | 用途 |
|---|---|---|
| AndroidX Core KTX | Apache-2.0 | Android 基础扩展 |
| AndroidX Activity Compose | Apache-2.0 | Compose 宿主与 ActivityResult |
| AndroidX Lifecycle ViewModel Compose | Apache-2.0 | 状态持有 |
| Jetpack Compose (BOM / UI / Material3) | Apache-2.0 | 全部界面 |
| Material Icons Extended | Apache-2.0 | 图标 |
| OkHttp | Apache-2.0 | 网络请求与 SSE 流式解析 |
| Coil Compose | Apache-2.0 | 图片与站点图标加载 |

## 测试依赖（不进入 APK）

| 组件 | 授权 |
|---|---|
| JUnit 4 | Eclipse Public License 1.0 |
| Robolectric | Apache-2.0 |
| Roborazzi | Apache-2.0 |
| AndroidX Test | Apache-2.0 |

## 说明

界面参照 DeepSeek 官方 App 制作，目的是尽量对齐操作手感与视觉习惯，让人不用重新学习一套交互。

本项目不是官方产品，与 DeepSeek 没有隶属关系。应用名称「Chat」、包名 `com.yg.dschat` 都与官方无关。
