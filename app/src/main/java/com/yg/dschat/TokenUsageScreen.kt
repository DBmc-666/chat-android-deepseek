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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenUsageScreen(vm: ChatViewModel, onBack: () -> Unit) {
    val colors = Ds.colors
    var total by remember { mutableStateOf(vm.usageTotal()) }
    var today by remember { mutableStateOf(vm.usageToday()) }

    androidx.activity.compose.BackHandler { onBack() }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Token 用量",
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
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            StatCard(
                title = "今日",
                stat = today,
            )
            Spacer(Modifier.height(12.dp))
            StatCard(
                title = "总计",
                stat = total,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                "价格按官方 deepseek-flash 定价估算：输入（未命中）¥1/¥2、输入（命中缓存）¥0.02/¥0.04、" +
                    "输出 ¥4/¥8 每百万 tokens（空闲/高峰）。\n\n" +
                    "搜索与抓取自 2026-11-18 12:00 起不再单独计费，只按 token 计费；" +
                    "此前的调用按 ¥5/1000 次计入。因此这里的金额只是估算，实际以官方账单为准。",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.labelTertiary,
            )

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = {
                vm.resetUsage()
                total = vm.usageTotal()
                today = vm.usageToday()
            }) {
                Text("清空统计", color = colors.error, fontSize = 14.sp)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun StatCard(title: String, stat: UsageStore.Stat) {
    val colors = Ds.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.layer2)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, fontSize = 13.sp, color = colors.labelTertiary, modifier = Modifier.weight(1f))
            Text(
                UsageStore.formatCost(stat.cost),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.labelPrimary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "共 " + UsageStore.formatTokens(stat.total) + " tokens",
            fontSize = 12.sp,
            color = colors.labelSecondary,
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = colors.borderL1)
        Spacer(Modifier.height(10.dp))

        DetailRow("输入", UsageStore.formatTokens(stat.totalInput) + "（未命中 " + UsageStore.formatTokens(stat.input) + "）")
        DetailRow(
            "其中缓存命中",
            UsageStore.formatTokens(stat.cacheRead) +
                if (stat.totalInput > 0) String.format(Locale.US, "（%.0f%%）", stat.cacheHitRate * 100) else "",
        )
        DetailRow("输出", UsageStore.formatTokens(stat.output))
        DetailRow("工具调用", "搜索 " + stat.searches + " 次 · 抓取 " + stat.fetches + " 次")
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = Ds.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = colors.labelSecondary)
        Text(value, fontSize = 13.sp, color = colors.labelPrimary)
    }
}
