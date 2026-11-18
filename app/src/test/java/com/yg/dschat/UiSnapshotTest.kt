package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 全状态 UI 快照：首页 / 对话 / 侧栏 / 展开态 / 输入态 / 生成 / 错误 / 拒答 / 设置，
 * 深浅两套。输出到 app/build/screenshots。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiSnapshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun newVm(): ChatViewModel =
        ChatViewModel(ApplicationProvider.getApplicationContext())

    private fun now() = System.currentTimeMillis()

    private fun baseMessages(): MutableList<Msg> = mutableListOf(
        Msg(
            role = "user",
            blocks = mutableListOf<Block>(Block.Text("DeepSeek V4.1 有什么更新？顺便搜一下官方公告")),
        ),
        Msg(
            role = "assistant",
            blocks = mutableListOf<Block>(
                Block.Thinking(
                    "用户想知道 V4.1 的更新点，并要官方来源。先搜索官方文档，再挑出与版本变更相关的条目。",
                    "",
                    startedAt = now() - 3400L,
                    endedAt = now(),
                ),
                Block.Search(
                    "toolu_1",
                    mutableListOf(
                        SearchHit(
                            "DeepSeek V4.1 Flash 新版本：许愿式编程一键成真",
                            "https://api-docs.deepseek.com/zh-cn/news/news261017/",
                            "DeepSeek",
                            "V4.1 在推理与代码生成上有明显提升。",
                            "2026/10/17",
                        ),
                        SearchHit(
                            "DeepSeek 发布全家桶应用 API",
                            "https://api-docs.deepseek.com/zh-cn/news/news261113/",
                            "DeepSeek",
                            "开放联网搜索、文件输入等能力。",
                            "2026/11/13",
                        ),
                    ),
                ),
                Block.Thinking(
                    "搜索结果分别来自 2 个域名，都以 api-docs 为主。第一条是官方公告，第二条是版本发布说明，先打开核对。",
                    "",
                    startedAt = now() - 2000L,
                    endedAt = now(),
                ),
                Block.Fetch(
                    "toolu_2",
                    "https://api-docs.deepseek.com/zh-cn/news/news261017/",
                    "DeepSeek V4.1 Flash 新版本",
                    "2026-10-17",
                    "sig-demo",
                ),
                Block.Fetch(
                    "toolu_3",
                    "https://api-docs.deepseek.com/zh-cn/news/news261113/",
                    "DeepSeek 发布全家桶应用 API",
                    "2026-11-13",
                    "sig-demo-2",
                ),
                Block.Thinking(
                    "公告里明确写了推理链路、代码补全和工具调用参数三处变化，可以据此作答了。",
                    "",
                    startedAt = now() - 900L,
                    endedAt = now(),
                ),
                Block.Text(
                    "V4.1 主要有三处变化：\n\n" +
                        "- **推理链路更长**：多步推理更稳，长任务不容易断\n" +
                        "- **代码补全更准**：上下文更长，跨文件补全可用\n" +
                        "- API 支持了新的工具调用参数，例如 `thinking` 与 `safety_check`\n\n" +
                        "```kotlin\nval client = AnthropicClient()\nclient.stream(settings, history, question)\n```",
                    mutableListOf(Citation("https://api-docs.deepseek.com/", "官方公告", "…")),
                ),
            ),
            status = "done",
        ),
    )

    private fun seedChat(vm: ChatViewModel, msgs: List<Msg> = baseMessages()) {
        // 给第一条 assistant 造两个版本，验证版本切换器
        msgs.firstOrNull { it.role == "assistant" }?.let { m ->
            m.versions.clear()
            m.versions.add(listOf(Block.Text("上一版回答：V4.1 的主要变化集中在推理链路与代码补全。")))
            m.versions.add(m.blocks.toList())
            m.versionIndex = 1
        }
        vm.seedForPreview("V4.1 更新", msgs)
    }

    // ---------------- 首页 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun home_dark() {
        compose.setContent { DsTheme { ChatApp(newVm()) } }
        compose.onRoot().captureRoboImage("build/screenshots/home_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun home_light() {
        compose.setContent { DsTheme { ChatApp(newVm()) } }
        compose.onRoot().captureRoboImage("build/screenshots/home_light.png")
    }

    // ---------------- 对话 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun chat_dark() {
        val vm = newVm(); seedChat(vm)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/chat_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun chat_light() {
        val vm = newVm(); seedChat(vm)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/chat_light.png")
    }

    // ---------------- 展开态 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun thinking_expanded_dark() {
        val vm = newVm()
        val msgs = baseMessages()
        seedChat(vm, msgs)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onNodeWithTag("thinking-${msgs[1].id}#0").performClick()
        compose.onRoot().captureRoboImage("build/screenshots/thinking_expanded_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun search_expanded_dark() {
        val vm = newVm()
        val msgs = baseMessages()
        seedChat(vm, msgs)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onNodeWithTag("thinking-${msgs[1].id}#0").performClick()
        compose.onNodeWithTag("search-toolu_1").performClick()
        compose.onRoot().captureRoboImage("build/screenshots/search_expanded_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun search_expanded_light() {
        val vm = newVm()
        val msgs = baseMessages()
        seedChat(vm, msgs)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onNodeWithTag("thinking-${msgs[1].id}#0").performClick()
        compose.onNodeWithTag("search-toolu_1").performClick()
        compose.onRoot().captureRoboImage("build/screenshots/search_expanded_light.png")
    }

    // ---------------- 输入态 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun input_filled_dark() {
        compose.setContent { DsTheme { ChatApp(newVm()) } }
        compose.onNodeWithTag("chat-input").performTextInput("帮我写一段 Kotlin 的协程示例")
        compose.onRoot().captureRoboImage("build/screenshots/input_filled_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun attach_panel_dark() {
        compose.setContent { DsTheme { ChatApp(newVm()) } }
        compose.onNodeWithContentDescription("添加").performClick()
        compose.onRoot().captureRoboImage("build/screenshots/attach_panel_dark.png")
    }

    // ---------------- 生成中 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun streaming_dark() {
        val vm = newVm()
        seedChat(
            vm,
            listOf(
                Msg(role = "user", blocks = mutableListOf<Block>(Block.Text("介绍一下 DeepSeek Harness"))),
                Msg(role = "assistant", blocks = mutableListOf(), status = "streaming"),
            ),
        )
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/streaming_dark.png")
    }

    // ---------------- 错误与拒答 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun error_dark() {
        val vm = newVm()
        seedChat(
            vm,
            listOf(
                Msg(role = "user", blocks = mutableListOf<Block>(Block.Text("帮我看看这个报错"))),
                Msg(
                    role = "assistant",
                    blocks = mutableListOf(),
                    status = "error",
                    error = "账户余额不足，请充值后重试",
                ),
            ),
        )
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/error_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun refusal_dark() {
        val vm = newVm()
        seedChat(
            vm,
            listOf(
                Msg(role = "user", blocks = mutableListOf<Block>(Block.Text("…"))),
                Msg(role = "assistant", blocks = mutableListOf(), status = "refusal"),
            ),
        )
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/refusal_dark.png")
    }

    // ---------------- 长对话 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun long_chat_dark() {
        val vm = newVm()
        val msgs = mutableListOf<Msg>()
        repeat(3) { i ->
            msgs.add(Msg(role = "user", blocks = mutableListOf<Block>(Block.Text("第 ${i + 1} 个问题：这个版本改了哪些地方？"))))
            msgs.add(
                Msg(
                    role = "assistant",
                    blocks = mutableListOf<Block>(
                        Block.Text("这是第 ${i + 1} 个回答。改动主要集中在推理链路、代码补全和工具调用三块。"),
                    ),
                )
            )
        }
        seedChat(vm, msgs)
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/long_chat_dark.png")
    }

    // ---------------- 侧栏 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun drawer_dark() {
        val vm = newVm(); seedChat(vm)
        compose.setContent {
            DsTheme {
                Box(Modifier.fillMaxSize().background(Ds.colors.bg)) {
                    DrawerPanel(vm, onClose = {}, onSettings = {}, onExperimental = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/drawer_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun drawer_light() {
        val vm = newVm(); seedChat(vm)
        compose.setContent {
            DsTheme {
                Box(Modifier.fillMaxSize().background(Ds.colors.bg)) {
                    DrawerPanel(vm, onClose = {}, onSettings = {}, onExperimental = {})
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screenshots/drawer_light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun drawer_select_dark() {
        val vm = newVm(); seedChat(vm)
        compose.setContent {
            DsTheme {
                Box(Modifier.fillMaxSize().background(Ds.colors.bg)) {
                    DrawerPanel(vm, onClose = {}, onSettings = {}, onExperimental = {})
                }
            }
        }
        compose.onNodeWithContentDescription("选择对话").performClick()
        compose.onNodeWithText("V4.1 更新").performClick()
        compose.onRoot().captureRoboImage("build/screenshots/drawer_select_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun math_dark() {
        val vm = newVm()
        vm.seedForPreview(
            "公式",
            listOf(
                Msg(role = "user", blocks = mutableListOf<Block>(Block.Text("帮我推一下质能方程"))),
                Msg(
                    role = "assistant",
                    blocks = mutableListOf<Block>(
                        Block.Text(
                            "推导如下：\n\n$$\nE = mc^2\n$$\n\n所以质量与能量等价。"
                        )
                    ),
                ),
            ),
        )
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/math_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun font_size_dark() {
        val vm = newVm()
        compose.setContent { DsTheme { FontSizeScreen(vm, onBack = {}) } }
        compose.onRoot().captureRoboImage("build/screenshots/font_size_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun user_image_dark() {
        val vm = newVm()
        vm.seedForPreview(
            "看图测试确认",
            listOf(
                Msg(
                    role = "user",
                    blocks = mutableListOf<Block>(Block.Text("这个图是什么")),
                    attachments = mutableListOf(
                        Attachment("file:///nope/cat.jpg", "cat.jpg", "image/jpeg", true)
                    ),
                ),
                Msg(
                    role = "assistant",
                    blocks = mutableListOf<Block>(
                        Block.Text("这是一只卡通风格的猫，手里拿着一个装满蚊子的透明罐子。")
                    ),
                ),
            ),
        )
        compose.setContent { DsTheme { ChatApp(vm) } }
        compose.onRoot().captureRoboImage("build/screenshots/user_image_dark.png")
    }

    // ---------------- 设置 ----------------

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-night-xxhdpi")
    fun settings_dark() {
        compose.setContent { DsTheme { SettingsScreen(newVm()) {} } }
        compose.onRoot().captureRoboImage("build/screenshots/settings_dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun settings_light() {
        compose.setContent { DsTheme { SettingsScreen(newVm()) {} } }
        compose.onRoot().captureRoboImage("build/screenshots/settings_light.png")
    }
}
