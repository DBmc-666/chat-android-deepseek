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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DataUsage
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

    androidx.activity.compose.BackHandler { onBack() }

    if (sub == "usage") {
        TokenUsageScreen(vm) { sub = null }
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
