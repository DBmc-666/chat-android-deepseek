package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MIN_SCALE = 0.8f
private const val MAX_SCALE = 1.2f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSizeScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val colors = Ds.colors
    var s by remember { mutableStateOf(vm.settings) }

    androidx.activity.compose.BackHandler { onBack() }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "字体大小",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.labelPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        Column(Modifier.padding(pad).fillMaxSize()) {
            // 预览区
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("font-preview"),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.layer3)
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Text("预览文字大小", fontSize = 16.sp, color = colors.labelPrimary)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "你可以拖动滑块来调整字体大小。",
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                    color = colors.labelPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "调整后会应用到所有对话的正文、标题和输入框。",
                    fontSize = 16.sp,
                    lineHeight = 27.sp,
                    color = colors.labelPrimary,
                )
            }

            // 底部面板
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(colors.layer2)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "跟随系统",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = colors.labelPrimary,
                    )
                    Switch(
                        checked = s.followSystemFont,
                        onCheckedChange = {
                            s = s.copy(followSystemFont = it)
                            vm.saveSettings(s)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.bg,
                            checkedTrackColor = colors.labelPrimary,
                            uncheckedThumbColor = colors.labelSecondary,
                            uncheckedTrackColor = colors.layer3,
                        ),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "标准",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    color = colors.labelTertiary,
                )

                Spacer(Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", fontSize = 13.sp, color = colors.labelSecondary)
                    Slider(
                        value = s.fontScale.coerceIn(MIN_SCALE, MAX_SCALE),
                        onValueChange = {
                            s = s.copy(fontScale = it, followSystemFont = false)
                        },
                        onValueChangeFinished = { vm.saveSettings(s) },
                        valueRange = MIN_SCALE..MAX_SCALE,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.labelPrimary,
                            activeTrackColor = colors.labelPrimary,
                            inactiveTrackColor = colors.borderL3,
                            activeTickColor = colors.labelPrimary,
                            inactiveTickColor = colors.borderL3,
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                    )
                    Text("A", fontSize = 21.sp, color = colors.labelSecondary)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
