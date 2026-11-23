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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val colors = Ds.colors
    var s by remember { mutableStateOf(vm.settings) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var subPage by remember { mutableStateOf<String?>(null) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    if (subPage == "font") {
        FontSizeScreen(vm) { subPage = null }
        return
    }

    val leave: () -> Unit = {
        // 字体页是独立页面、改完立即落盘，这里不能拿开页时的旧快照覆盖它
        val cur = vm.settings
        vm.saveSettings(s.copy(fontScale = cur.fontScale, followSystemFont = cur.followSystemFont))
        onBack()
    }

    androidx.activity.compose.BackHandler { leave() }

    val importZip = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = ZipImporter.import(ctx, uri)
            if (result.error != null) {
                android.widget.Toast.makeText(ctx, result.error, android.widget.Toast.LENGTH_LONG).show()
            } else {
                vm.importSessions(result.sessions)
                android.widget.Toast.makeText(
                    ctx,
                    "已导入 ${result.sessions.size} 个对话",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "设置",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.labelPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Box(
                            Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).background(colors.layer3),
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
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .testTag("settings"),
        ) {
            SectionLabel("账户")
            SettingsCard {
                SettingRow(Icons.Default.Person, "API Key", if (s.apiKey.isBlank()) "未填写" else "已填写") {
                    dialog = "key"
                }
                RowDivider()
                SettingRow(Icons.Default.Link, "接口地址", s.baseUrl.removePrefix("https://")) {
                    dialog = "base"
                }
                RowDivider()
                SettingRow(Icons.Default.Memory, "模型", s.model) { dialog = "model" }
            }

            SectionLabel("应用")
            SettingsCard {
                SettingRow(Icons.Default.Language, "语言", "简体中文") { dialog = "lang" }
                RowDivider()
                SettingRow(
                    Icons.Default.DarkMode,
                    "外观",
                    when (s.theme) {
                        "light" -> "浅色"
                        "dark" -> "深色"
                        else -> "系统"
                    },
                ) { dialog = "theme" }
                RowDivider()
                SettingRow(
                    Icons.Default.TextFields,
                    "字体大小",
                    when {
                        s.followSystemFont -> "跟随系统"
                        s.fontScale < 0.95f -> "小"
                        s.fontScale > 1.1f -> "大"
                        else -> "标准"
                    },
                ) { subPage = "font" }
                RowDivider()
                SettingRow(
                    Icons.Default.Keyboard,
                    "语音自动发送",
                    if (s.voiceAutoSend) "转写后直接发送" else "转写后回到键盘",
                ) {
                    s = s.copy(voiceAutoSend = !s.voiceAutoSend)
                    vm.saveSettings(s)
                }
            }

            SectionLabel("上下文")
            SettingsCard {
                SettingRow(
                    Icons.Default.Compress,
                    "自动压缩",
                    if (vm.settings.compressEnabled) {
                        "超过 ${vm.settings.compressThreshold / 1000}k 字符"
                    } else {
                        "已关闭"
                    },
                ) { dialog = "compress" }
            }

            SectionLabel("数据")
            SettingsCard {
                SettingRow(
                    Icons.Default.FileDownload,
                    "导入历史对话",
                    "官方 ZIP",
                ) {
                    importZip.launch(arrayOf("application/zip", "application/octet-stream"))
                }
                RowDivider()
                SettingRow(Icons.Default.Delete, "删除所有对话", "", danger = true) { dialog = "clear" }
            }

            SectionLabel("关于")
            SettingsCard {
                SettingRow(Icons.Default.Info, "版本", com.yg.dschat.BuildConfig.VERSION_NAME) {}
            }

            Text(
                "会话与 API Key 只保存在本机，不经过任何中转服务器。",
                modifier = Modifier.padding(top = 16.dp, bottom = 32.dp),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.labelTertiary,
            )
        }
    }

    when (dialog) {
        "key" -> TextFieldDialog(
            title = "API Key",
            initial = s.apiKey,
            placeholder = "sk-…",
            password = true,
            hint = "仅支持 DeepSeek 官方 API。Key 只保存在本机。",
            onDismiss = { dialog = null },
            onConfirm = {
                s = s.copy(apiKey = it.trim())
                vm.saveSettings(s)
                dialog = null
            },
        )
        "base" -> TextFieldDialog(
            title = "接口地址",
            initial = s.baseUrl,
            placeholder = "https://api.deepseek.com",
            hint = "默认 https://api.deepseek.com，仅自建反向代理时修改。",
            onDismiss = { dialog = null },
            onConfirm = {
                s = s.copy(baseUrl = it.trim().ifBlank { "https://api.deepseek.com" })
                vm.saveSettings(s)
                dialog = null
            },
        )
        "model" -> TextFieldDialog(
            title = "模型",
            initial = s.model,
            placeholder = "deepseek-flash",
            onDismiss = { dialog = null },
            onConfirm = {
                s = s.copy(model = it.trim().ifBlank { "deepseek-flash" })
                vm.saveSettings(s)
                dialog = null
            },
        )
        "lang" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("语言") },
            text = {
                Column {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = true, onClick = { dialog = null })
                        Spacer(Modifier.width(8.dp))
                        Text("简体中文", fontSize = 15.sp, color = Ds.colors.labelPrimary)
                    }
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "其他语言（可自行添加）",
                            fontSize = 13.sp,
                            color = Ds.colors.labelTertiary,
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } },
        )
        "theme" -> ChoiceDialog(
            title = "外观",
            options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
            current = s.theme,
            onDismiss = { dialog = null },
            onPick = {
                s = s.copy(theme = it)
                vm.saveSettings(s)
                dialog = null
            },
        )
        "compress" -> CompressDialog(vm) { dialog = null }
        "clear" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("删除所有对话") },
            text = { Text("所有会话记录都会被清空且无法找回。API Key 和设置会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAllSessions()
                    dialog = null
                }) { Text("删除", color = Ds.colors.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        fontSize = 13.sp,
        color = Ds.colors.labelTertiary,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Ds.colors.layer2)
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = Ds.colors.borderL1,
        modifier = Modifier.padding(start = 52.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    label: String,
    value: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = Ds.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            null,
            tint = if (danger) colors.error else colors.labelPrimary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = if (danger) colors.error else colors.labelPrimary,
        )
        if (value.isNotBlank()) {
            Text(value, fontSize = 14.sp, color = colors.labelTertiary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun TextFieldDialog(
    title: String,
    initial: String,
    placeholder: String,
    hint: String? = null,
    password: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text(placeholder) },
                    visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (hint != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(hint, fontSize = 11.sp, lineHeight = 16.sp, color = Ds.colors.labelTertiary)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    current: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onPick(key) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = key == current, onClick = { onPick(key) })
                        Spacer(Modifier.width(8.dp))
                        Text(label, fontSize = 15.sp, color = Ds.colors.labelPrimary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 上下文压缩设置。
 * 阈值按字符估算（中文约 1 字 = 1 token，英文约 4 字符 = 1 token）。
 */
@Composable
private fun CompressDialog(vm: ChatViewModel, onDismiss: () -> Unit) {
    val colors = Ds.colors
    var enabled by remember { mutableStateOf(vm.settings.compressEnabled) }
    var threshold by remember { mutableStateOf(vm.settings.compressThreshold) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("上下文压缩") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "超过阈值自动压缩",
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        color = colors.labelPrimary,
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.bg,
                            checkedTrackColor = colors.labelPrimary,
                            uncheckedThumbColor = colors.labelSecondary,
                            uncheckedTrackColor = colors.layer3,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "阈值：${threshold / 1000}k 字符（约 ${threshold / 1000}k token）",
                    fontSize = 13.sp,
                    color = colors.labelSecondary,
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { threshold = (it / 10_000).toInt() * 10_000 },
                    valueRange = 50_000f..1_000_000f,
                    enabled = enabled,
                )
                Text(
                    "超过阈值后，最早的回答会被截断成 120 字，仍超出就整轮省略，并在开头注明省略了几轮。" +
                        "只影响发给模型的内容，本地记录不受影响。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.labelTertiary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveSettings(
                    vm.settings.copy(compressEnabled = enabled, compressThreshold = threshold)
                )
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}