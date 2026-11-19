package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val colors = Ds.colors
    var sub by remember { mutableStateOf<String?>(null) }
    var showLastRequest by remember { mutableStateOf(false) }
    var showModeDialog by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val ctx = androidx.compose.ui.platform.LocalContext.current

    androidx.activity.compose.BackHandler { onBack() }

    if (showModeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showModeDialog = false },
            title = { Text("备忘工具模式") },
            text = {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    ModeOption(
                        title = "官方搜索（默认）",
                        selected = vm.settings.memoToolMode != "client",
                        body = "联网走 DeepSeek 官方搜索，质量最好，带引用，支持深度思考搜索。\n\n" +
                            "限制：官方不允许自定义工具与联网工具共存，所以助手**不能自己查备忘**。" +
                            "发消息时会自动检索相关备忘并附在问题后面；想要的回答需要手动点" +
                            "「存为备忘」保存。",
                    ) { vm.saveSettings(vm.settings.copy(memoToolMode = "official")) }
                    Spacer(Modifier.height(12.dp))
                    ModeOption(
                        title = "客户端工具",
                        selected = vm.settings.memoToolMode == "client",
                        body = "助手能自己调用 web_search、web_fetch、memo_search、memo_read、" +
                            "memo_save，可以主动检索和写入备忘，无需手动点按钮。\n\n" +
                            "限制：**不走官方搜索**，联网改用 DuckDuckGo 或自建 SearXNG 等外部源，" +
                            "没有官方引用，结果质量明显下降；DuckDuckGo 容易限流，" +
                            "长期使用建议配 SearXNG。",
                    ) { vm.saveSettings(vm.settings.copy(memoToolMode = "client")) }
                    Spacer(Modifier.height(12.dp))
                    ModeOption(
                        title = "互斥模式",
                        selected = vm.settings.memoToolMode == "exclusive",
                        body = "备忘工具与联网搜索**互斥**：在输入框打开备忘会自动关掉搜索，" +
                            "打开搜索会自动关掉备忘。\n\n" +
                            "适合既想助手能自己查备忘、又不想牺牲官方搜索质量的场景：" +
                            "查资料时开搜索，整理笔记时开备忘。\n\n" +
                            "代价：**每次都要手动切换**才能达到理想效果，忘记切换就会用错工具集" +
                            "（开着搜索时助手查不了备忘，开着备忘时搜不到网）。",
                    ) {
                        // 切到互斥时立刻收敛到单一状态，否则两个 chip 会同时亮
                        vm.saveSettings(
                            vm.settings.copy(
                                memoToolMode = "exclusive",
                                memoActive = false,
                                webSearch = true,
                            )
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showModeDialog = false }) { Text("关闭") }
            },
        )
    }

    if (showLastRequest) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLastRequest = false },
            title = { Text("最近一次请求体") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 一眼看出这次请求到底声明了哪些工具，排查"模型看不到工具"最直接
                    val toolNames = runCatching {
                        val o = org.json.JSONObject(AnthropicClient.lastPayload)
                        val arr = o.optJSONArray("tools")
                        (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                            arr?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
                        }
                    }.getOrDefault(emptyList())
                    Text(
                        "工具（${toolNames.size}）：" + toolNames.joinToString(", ").ifBlank { "无" },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.labelPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        AnthropicClient.lastPayload.ifBlank { "还没有发送过请求" },
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = colors.labelSecondary,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(AnthropicClient.lastPayload))
                    android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("复制") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showLastRequest = false }) { Text("关闭") }
            },
        )
    }

    if (sub == "usage") {
        TokenUsageScreen(vm) { sub = null }
        return
    }

    if (sub == "memo") {
        com.yg.dschat.memo.MemoListScreen(vm.memoStore) { sub = null }
        return
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "实验性功能",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.labelPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Box(
                            Modifier.size(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.layer3),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                "返回",
                                tint = colors.labelPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Text(
                "这些功能还在打磨，行为可能变化。",
                modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp),
                fontSize = 13.sp,
                color = colors.labelTertiary,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.layer2)
            ) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "每条回答显示 token 消耗",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.labelPrimary,
                    )
                    Switch(
                        checked = vm.settings.showUsage,
                        onCheckedChange = { vm.saveSettings(vm.settings.copy(showUsage = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.bg,
                            checkedTrackColor = colors.labelPrimary,
                            uncheckedThumbColor = colors.labelSecondary,
                            uncheckedTrackColor = colors.layer3,
                        ),
                    )
                }
                HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(start = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { sub = "usage" }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.DataUsage,
                        null,
                        tint = colors.labelPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text("Token 用量", modifier = Modifier.weight(1f), fontSize = 15.sp, color = colors.labelPrimary)
                    Text("查看", fontSize = 14.sp, color = colors.labelTertiary)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(start = 16.dp))
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "启用备忘工具",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.labelPrimary,
                    )
                    Switch(
                        checked = vm.settings.memoEnabled,
                        onCheckedChange = {
                            vm.saveSettings(vm.settings.copy(memoEnabled = it))
                            android.widget.Toast.makeText(
                                ctx,
                                "已" + (if (it) "开启" else "关闭") + "备忘工具。" +
                                    "模型对工具的记忆跟着会话走，如果它还按旧工具集回答，开个新会话即可。",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.bg,
                            checkedTrackColor = colors.labelPrimary,
                            uncheckedThumbColor = colors.labelSecondary,
                            uncheckedTrackColor = colors.layer3,
                        ),
                    )
                }
                HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(start = 16.dp))
                if (vm.settings.memoEnabled) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { showModeDialog = true }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "备忘工具模式",
                            modifier = Modifier.weight(1f),
                            fontSize = 15.sp,
                            color = colors.labelPrimary,
                        )
                        Text(
                            if (vm.settings.memoToolMode == "client") "客户端工具" else "官方搜索",
                            fontSize = 14.sp,
                            color = colors.labelTertiary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            null,
                            tint = colors.labelTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(start = 16.dp))
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { sub = "memo" }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.EditNote,
                        null,
                        tint = colors.labelPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "备忘录",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.labelPrimary,
                    )
                    Text(
                        "查看",
                        fontSize = 14.sp,
                        color = colors.labelTertiary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                HorizontalDivider(color = colors.borderL1, modifier = Modifier.padding(start = 16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showLastRequest = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.BugReport,
                        null,
                        tint = colors.labelPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        "最近一次请求体",
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        color = colors.labelPrimary,
                    )
                    Text(
                        if (AnthropicClient.lastPayload.isBlank()) "暂无" else "${AnthropicClient.lastPayload.length} 字符",
                        fontSize = 14.sp,
                        color = colors.labelTertiary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                "消耗来自服务端返回的 usage，金额按官方定价估算（高峰/空闲自动区分），仅作参考。",
                modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.labelTertiary,
            )
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    selected: Boolean,
    body: String,
    onClick: () -> Unit,
) {
    val colors = Ds.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.chipActiveBg else colors.layer3)
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = colors.labelPrimary,
            )
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = colors.brandText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 12.sp, lineHeight = 19.sp, color = colors.labelSecondary)
    }
}
