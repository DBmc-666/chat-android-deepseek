package com.yg.dschat

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ChatViewModel = viewModel()
            DsTheme(
                themeMode = vm.settings.theme,
                fontScale = vm.settings.fontScale,
                followSystemFont = vm.settings.followSystemFont,
            ) {
                ChatApp(vm)
            }
        }
    }
}

@Composable
fun ChatApp(vm: ChatViewModel) {
    val colors = Ds.colors
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val ctx = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var showExperimental by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var searchHits by remember { mutableStateOf<List<SearchHit>?>(null) }

    // 搜索结果面板打开时，返回键先关面板
    androidx.activity.compose.BackHandler(enabled = searchHits != null) { searchHits = null }

    // 附件上传失败要明确告知，不能静默丢掉
    LaunchedEffect(vm.attachError) {
        vm.attachError?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            vm.attachError = null
        }
    }

    val session = vm.current
    val tick = vm.version
    val messages = session?.messages ?: emptyList()
    val isHome = messages.isEmpty()

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount == 0 || last >= info.totalItemsCount - 2
        }
    }

    

    Box(Modifier.fillMaxSize()) {
        ModalNavigationDrawer(
            modifier = Modifier.fillMaxSize(),
            drawerState = drawerState,
            drawerContent = {
                DrawerPanel(
                    vm = vm,
                    onClose = { scope.launch { drawerState.close() } },
                    onSettings = { showSettings = true; scope.launch { drawerState.close() } },
                    onExperimental = { showExperimental = true; scope.launch { drawerState.close() } },
                )
            },
        ) {
            Column(Modifier.fillMaxSize().background(colors.bg)) {
                SessionHeader(
                    isHome = isHome,
                    title = session?.title?.ifBlank { "新对话" } ?: "新对话",
                    onMenu = { scope.launch { drawerState.open() } },
                    onNew = { vm.newSession() },
                )

    // 流式生成时，如果用户停在底部就自动跟随；一旦上滚就不再打扰。
    // 注意目标是 LazyList 的 item 下标（assistant 消息被拆成多个 item），不能用 messages.lastIndex
    LaunchedEffect(tick, isHome) {
        if (isHome) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && atBottom) {
            runCatching { listState.scrollToItem(total - 1) }
        }
    }

    Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (isHome) {
                        Welcome()
                    } else {
                        MessageList(
                            listState = listState,
                            messages = messages,
                            tick = tick,
                            showUsage = vm.settings.showUsage,
                            onOpenSearch = { searchHits = it },
                            onRetry = { vm.retryLast() },
                            onSwitchVersion = { id, delta -> vm.switchVersion(id, delta) },
                        )
                        if (!atBottom) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 16.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(colors.layer3)
                                        .clickable {
                                            scope.launch {
                                                runCatching { listState.animateScrollToItem(messages.lastIndex) }
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        "回到底部",
                                        tint = colors.labelPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                ChatInputBar(
                    value = input,
                    onValueChange = { input = it },
                    attachments = attachments,
                    onAttachmentsChange = { attachments = it },
                    settings = vm.settings,
                    streaming = vm.streaming,
                    showCaveat = !isHome,
                    webSearch = vm.settings.webSearch,
                    thinking = vm.settings.deepThinking,
                    onToggleSearch = { vm.toggleWebSearch() },
                    onToggleThinking = { vm.toggleThinking() },
                    onSend = {
                        if (input.isNotBlank() || attachments.isNotEmpty()) {
                            vm.send(input, attachments)
                            input = ""
                            attachments = emptyList()
                        }
                    },
                    onStop = { vm.stop() },
                    onSendText = { t ->
                        vm.send(t, attachments)
                        input = ""
                        attachments = emptyList()
                    },
                )
            }
        }

        if (showSettings) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                SettingsScreen(vm) { showSettings = false }
            }
        }

        if (showExperimental) {
            Box(Modifier.fillMaxSize().background(colors.bg)) {
                ExperimentalScreen(vm) { showExperimental = false }
            }
        }
    }

    searchHits?.let { hits ->
        SearchSheet(hits) { searchHits = null }
    }
}

// ---------------- 侧栏 ----------------


internal fun bucketOf(ts: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
    val diff = java.time.temporal.ChronoUnit.DAYS.between(day, java.time.LocalDate.now(zone))
    return when {
        diff <= 0L -> "今天"
        diff == 1L -> "昨天"
        diff <= 7L -> "7 天内"
        diff <= 30L -> "30 天内"
        else -> "更早"
    }
}

// ---------------- 顶部 ----------------

@Composable
private fun SessionHeader(
    isHome: Boolean,
    title: String,
    onMenu: () -> Unit,
    onNew: () -> Unit,
) {
    val colors = Ds.colors
    Row(
        Modifier.fillMaxWidth().height(Ds.headerHeight).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(painterResource(R.drawable.ic_menu), "会话", tint = colors.labelPrimary)
        }
        if (!isHome) {
            Text(
                title,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = colors.labelPrimary,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onNew) {
            Icon(painterResource(R.drawable.ic_new_chat), "新对话", tint = colors.labelPrimary)
        }
    }
}

// ---------------- 欢迎页 ----------------

private fun greetingPrefix(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        h in 5..10 -> "早上好"
        h in 11..12 -> "中午好"
        h in 13..17 -> "下午好"
        h in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}

@Composable
private fun Welcome() {
    val colors = Ds.colors
    val prefix = remember { greetingPrefix() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$prefix，有什么我能帮你的吗？",
            fontSize = 24.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.labelPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------- 消息列表 ----------------

@Composable
private fun MessageList(
    listState: androidx.compose.foundation.lazy.LazyListState,
    messages: List<Msg>,
    tick: Int,
    showUsage: Boolean,
    onOpenSearch: (List<SearchHit>) -> Unit,
    onRetry: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = Ds.messageMaxWidth).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            messages.forEachIndexed { index, m ->
                if (m.role == "user") {
                    item(key = m.id) { UserBubble(m, first = index == 0) }
                } else {
                    val parts = splitParts(m.blockSnapshot())
                    if (parts.isEmpty() && m.status == "streaming") {
                        item(key = "${m.id}#wait") {
                            Box(Modifier.padding(vertical = 8.dp)) { WaitingDots() }
                        }
                    }
                    parts.forEachIndexed { pi, p ->
                        item(key = "${m.id}#$pi") {
                            PartView(p, "${m.id}#$pi", tick, m.status == "streaming", onOpenSearch)
                        }
                    }
                    if (m.status == "error") {
                        item(key = "${m.id}#err") {
                            Text(
                                m.error ?: "请求失败",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = Ds.colors.error,
                            )
                        }
                    }
                    if (m.status == "refusal") {
                        item(key = "${m.id}#ref") {
                            Text(
                                "这个问题我暂时无法回答，可以换个话题试试。",
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = Ds.colors.labelSecondary,
                            )
                        }
                    }
                    item(key = "${m.id}#act") {
                        MessageFooter(
                            m = m,
                            isLast = index == messages.lastIndex,
                            showUsage = showUsage,
                            onRetry = onRetry,
                            onSwitchVersion = onSwitchVersion,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserBubble(m: Msg, first: Boolean) {
    val colors = Ds.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = if (first) 24.dp else 0.dp, bottom = 16.dp, start = 56.dp),
        horizontalAlignment = Alignment.End,
    ) {
        // 附件在气泡上方，和官方一致：图片直接显示缩略图，文档显示文件名卡片
        m.attachments.forEach { a ->
            if (a.isImage) {
                AsyncImage(
                    model = a.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .heightIn(max = 180.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            } else {
                Row(
                    Modifier
                        .widthIn(max = 240.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.layer3)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        null,
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        a.name,
                        fontSize = 13.sp,
                        color = colors.labelPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (m.plainText().isNotBlank()) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(Ds.radiusBubble))
                    .background(colors.bubble)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    m.plainText(),
                    fontSize = Ds.fsMd,
                    lineHeight = 26.sp,
                    color = colors.labelPrimary,
                )
            }
        }
    }
}

/** 思考块内部按原始顺序排列的片段 */
private sealed interface ThinkItem {
    data class Txt(val text: String) : ThinkItem
    data class Src(val search: Block.Search) : ThinkItem
    data class Fch(val fetch: Block.Fetch) : ThinkItem
}

/** 把一条 assistant 消息切成多个 List item，避免单个 item 高度持续增长把滚动卡死。 */
private sealed interface Part {
    data class Think(
        val items: List<ThinkItem>,
        val startedAt: Long,
        val endedAt: Long,
    ) : Part

    data class Txt(val t: Block.Text) : Part
    data class Src(val s: Block.Search) : Part
    data class Fetch(val f: Block.Fetch) : Part
}

private fun splitParts(blocks: List<Block>): List<Part> {
    val out = mutableListOf<Part>()
    var i = 0
    while (i < blocks.size) {
        when (val b = blocks[i]) {
            is Block.Thinking -> {
                // 思考 → 搜索 → 继续思考 → 浏览 → 继续思考 都属于同一个思考块，
                // 不能每遇到一个 Thinking 就另起一块
                val items = mutableListOf<ThinkItem>()
                var started = b.startedAt
                var ended = b.endedAt
                if (b.text.isNotBlank()) items.add(ThinkItem.Txt(b.text))
                var j = i + 1
                while (j < blocks.size) {
                    when (val nb = blocks[j]) {
                        is Block.Thinking -> {
                            if (nb.text.isNotBlank()) items.add(ThinkItem.Txt(nb.text))
                            if (started == 0L) started = nb.startedAt
                            if (nb.endedAt > ended) ended = nb.endedAt
                            j++
                        }
                        is Block.Search -> {
                            items.add(ThinkItem.Src(nb))
                            j++
                        }
                        is Block.Fetch -> {
                            items.add(ThinkItem.Fch(nb))
                            j++
                        }
                        else -> break
                    }
                }
                out.add(Part.Think(items, started, ended))
                i = j
            }
            is Block.Search -> {
                out.add(Part.Src(b))
                i++
            }
            is Block.Fetch -> {
                out.add(Part.Fetch(b))
                i++
            }
            is Block.Text -> {
                if (b.text.isNotBlank()) out.add(Part.Txt(b))
                i++
            }
        }
    }
    return out
}

@Composable
private fun PartView(
    p: Part,
    itemKey: String,
    tick: Int,
    streaming: Boolean,
    onOpenSearch: (List<SearchHit>) -> Unit,
) {
    Box(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        when (p) {
            is Part.Think -> ThinkingGroup(p.items, p.startedAt, p.endedAt, itemKey, tick, onOpenSearch)
            is Part.Txt -> TextBody(p.t, onOpenSearch, streaming)
            is Part.Src -> SearchLine(p.s, onOpenSearch)
            is Part.Fetch -> FetchLine(listOf(p.f), onOpenSearch)
        }
    }
}

@Composable
private fun MessageFooter(
    m: Msg,
    isLast: Boolean,
    showUsage: Boolean,
    onRetry: () -> Unit,
    onSwitchVersion: (String, Int) -> Unit,
) {
    val colors = Ds.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (showUsage) {
            m.usage?.let { u ->
                Text(
                    "输入 " + UsageStore.formatTokens(u.input.toLong()) +
                        " · 输出 " + UsageStore.formatTokens(u.output.toLong()) +
                        " · " + UsageStore.formatCost(
                        UsageStore.estimateCost(u.input, u.output, u.cacheRead)
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                    fontSize = 11.sp,
                    color = colors.labelTertiary,
                )
            }
        }
        if (m.versions.size > 1) {
            VersionSwitcher(m.versionIndex, m.versions.size) { delta ->
                onSwitchVersion(m.id, delta)
            }
        }
        if (m.status == "done" && m.plainText().isNotBlank()) {
            // 重新生成只在最后一条出现：中间轮次重跑会作废后面的上下文
            MessageActions(m.plainText(), showRetry = isLast, onRetry = onRetry)
        }
    }
}

@Composable
private fun VersionSwitcher(index: Int, total: Int, onDelta: (Int) -> Unit) {
    val colors = Ds.colors
    Row(
        Modifier.padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Default.KeyboardArrowLeft,
            "上一版本",
            tint = colors.labelSecondary,
            modifier = Modifier.size(20.dp).clickable { onDelta(-1) },
        )
        Text(
            "${index + 1}/$total",
            fontSize = 13.sp,
            color = colors.labelSecondary,
        )
        Icon(
            Icons.Default.KeyboardArrowRight,
            "下一版本",
            tint = colors.labelSecondary,
            modifier = Modifier.size(20.dp).clickable { onDelta(1) },
        )
    }
}

@Composable
private fun MessageActions(text: String, showRetry: Boolean, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current

    Row(
        Modifier.padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.Default.ContentCopy, "复制") {
            clip.setText(AnnotatedString(text))
            Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
        }
        if (showRetry) {
            ActionIcon(Icons.Default.Refresh, "重新生成") { onRetry() }
        }
    }
}

@Composable
private fun ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    val colors = Ds.colors
    Icon(
        icon,
        desc,
        tint = colors.labelTertiary,
        modifier = Modifier.size(19.dp).clickable { onClick() },
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun ThinkingGroup(
    items: List<ThinkItem>,
    startedAt: Long,
    endedAt: Long,
    key: String,
    tick: Int,
    onOpenSearch: (List<SearchHit>) -> Unit,
) {
    val colors = Ds.colors
    var open by remember(key) { mutableStateOf(false) }
    val secs = if (startedAt > 0L && endedAt > startedAt) {
        ((endedAt - startedAt) / 1000L).coerceAtLeast(1L).toInt()
    } else 0

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier
                .testTag("thinking-$key")
                .clickable { open = !open }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    secs > 0 -> "已思考（用时 $secs 秒）"
                    else -> "已思考"
                },
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = colors.labelSecondary,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                painterResource(if (open) R.drawable.ic_chevron_down else R.drawable.ic_chevron_right),
                null,
                tint = colors.labelTertiary,
                modifier = Modifier.size(16.dp),
            )
        }

        if (open) {
            Spacer(Modifier.height(6.dp))
            // 竖线用 drawBehind 直接画在内容列上：fillMaxHeight 在高度由内容决定的 Box 里
            // 拿不到约束，会导致线消失
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = colors.borderL2,
                            start = androidx.compose.ui.geometry.Offset(0f, 0f),
                            end = androidx.compose.ui.geometry.Offset(0f, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                    .padding(start = 13.5.dp)
            ) {
                // 按原始顺序交替渲染：思考段落 → 搜索行 → 思考段落 → 浏览行 …
                var idx = 0
                while (idx < items.size) {
                    when (val item = items[idx]) {
                        is ThinkItem.Txt -> {
                            Text(
                                item.text,
                                fontSize = 15.sp,
                                lineHeight = 28.sp,
                                color = colors.labelTertiary,
                            )
                            idx++
                        }
                        is ThinkItem.Src -> {
                            SearchLine(item.search, onOpenSearch)
                            idx++
                        }
                        is ThinkItem.Fch -> {
                            // 连续的浏览结果合并成一行「已浏览 N 个页面」
                            val group = mutableListOf(item.fetch)
                            var k = idx + 1
                            while (k < items.size && items[k] is ThinkItem.Fch) {
                                group.add((items[k] as ThinkItem.Fch).fetch)
                                k++
                            }
                            FetchLine(group, onOpenSearch)
                            idx = k
                        }
                    }
                    if (idx < items.size) Spacer(Modifier.height(12.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
@Composable
private fun SearchLine(b: Block.Search, onOpenSearch: (List<SearchHit>) -> Unit) {
    val colors = Ds.colors
    Row(
        Modifier
            .testTag("search-${b.toolUseId}")
            .clickable { onOpenSearch(b.results) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_search),
            null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "搜索到 ${b.results.size} 个网页",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = colors.labelTertiary,
        )
        Spacer(Modifier.width(6.dp))
        b.results.take(4).forEach { h ->
            SiteIcon(h, 18.dp)
            Spacer(Modifier.width(3.dp))
        }
    }
}

@Composable
private fun FetchLine(list: List<Block.Fetch>, onOpenSearch: (List<SearchHit>) -> Unit) {
    val colors = Ds.colors
    Row(
        Modifier
            .clickable {
                onOpenSearch(
                    list.map {
                        SearchHit(
                            title = it.title.ifBlank { it.url },
                            url = it.url,
                            site = runCatching { java.net.URI(it.url).host }.getOrNull().orEmpty(),
                            snippet = "",
                            date = it.publishedAt.take(10),
                        )
                    }
                )
            }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_browse),
            null,
            tint = colors.labelTertiary,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "已浏览 ${list.size} 个页面",
            fontSize = 15.sp,
            lineHeight = 24.sp,
            color = colors.labelTertiary,
        )
    }
}

@Composable
private fun WaitingDots() {
    val colors = Ds.colors
    val transition = rememberInfiniteTransition(label = "dots")
    Row(
        Modifier.height(34.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 100),
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.labelTertiary.copy(alpha = alpha))
            )
        }
    }
}

/** 1→①、2→②…超出 20 用 (n) */
private fun circled(n: Int): String =
    if (n in 1..20) "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"[n - 1].toString() else "($n)"

@Composable
private fun TextBody(b: Block.Text, onOpenSearch: (List<SearchHit>) -> Unit, streaming: Boolean) {
    val openCitations: () -> Unit = {
        onOpenSearch(
            b.citations.map {
                SearchHit(
                    title = it.title.ifBlank { it.url },
                    url = it.url,
                    site = runCatching { java.net.URI(it.url).host }.getOrNull().orEmpty(),
                    snippet = it.cited,
                    date = "",
                )
            }
        )
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        MarkdownView(
            b.text,
            streaming = streaming,
            trailing = if (b.citations.isNotEmpty()) {
                { CitationBadge(b.citations.size, openCitations) }
            } else {
                null
            },
        )
    }
}

/** 官方样式：灰底小圈，贴在正文最后一句的末尾 */
@Composable
private fun CitationBadge(count: Int, onClick: () -> Unit) {
    val colors = Ds.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.layer3)
            .clickable { onClick() }
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            circled(count),
            fontSize = 9.sp,
            lineHeight = 12.sp,
            color = colors.labelSecondary,
        )
    }
}

// ---------------- 输入区 ----------------

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    attachments: List<Attachment>,
    onAttachmentsChange: (List<Attachment>) -> Unit,
    settings: AppSettings,
    streaming: Boolean,
    showCaveat: Boolean,
    webSearch: Boolean,
    thinking: Boolean,
    onToggleSearch: () -> Unit,
    onToggleThinking: () -> Unit,
    onSend: () -> Unit,
    onSendText: (String) -> Unit,
    onStop: () -> Unit,
) {
    val colors = Ds.colors
    val ctx = LocalContext.current
    var showAttach by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }

    var voiceMode by remember { mutableStateOf(false) }

    fun hasAudioPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            ctx,
            android.Manifest.permission.RECORD_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun finishRecording() {
        val wav = AudioIo.stopRecording() ?: return
        Toast.makeText(ctx, "正在转写…", Toast.LENGTH_SHORT).show()
        Thread {
            val text = AudioIo.transcribe(settings, wav)
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (text.isNullOrBlank()) {
                    Toast.makeText(ctx, "没听清，再试一次", Toast.LENGTH_SHORT).show()
                } else {
                    val merged = if (value.isBlank()) text else "$value $text"
                    if (settings.voiceAutoSend) {
                        // 直接发送，保持语音模式方便连续说
                        onSendText(merged)
                    } else {
                        // 填进输入框并切回键盘，让用户先改再发
                        onValueChange(merged)
                        voiceMode = false
                    }
                }
            }
        }.start()
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceMode = true
        else Toast.makeText(ctx, "需要麦克风权限", Toast.LENGTH_SHORT).show()
    }

    fun addUri(uri: android.net.Uri?) {
        if (uri == null) return
        val mime = runCatching { ctx.contentResolver.getType(uri) }.getOrNull()
            ?: "application/octet-stream"
        val name = uri.lastPathSegment ?: "file"
        onAttachmentsChange(
            attachments + Attachment(
                uri = uri.toString(),
                name = name,
                mime = mime,
                isImage = mime.startsWith("image/"),
            )
        )
        showAttach = false
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        addUri(uri)
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        addUri(uri)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            runCatching {
                val f = java.io.File(ctx.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
                addUri(android.net.Uri.fromFile(f))
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments) { idx ->
                onAttachmentsChange(attachments.filterIndexed { i, _ -> i != idx })
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showAttach) {
            AttachPanel(
                onPhoto = { takePhoto.launch(null) },
                onGallery = {
                    pickImage.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onFile = { pickFile.launch(arrayOf("*/*")) },
            )
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Ds.radiusInput))
                .background(colors.input)
                .border(1.dp, colors.borderL2, RoundedCornerShape(Ds.radiusInput))
        ) {
            if (voiceMode) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (!AudioIo.startRecording()) {
                                        Toast.makeText(ctx, "无法启动录音", Toast.LENGTH_SHORT).show()
                                        return@detectTapGestures
                                    }
                                    recording = true
                                    AudioIo.onTimeout = {
                                        // 回调已在主线程；到点上自动结束并提示
                                        if (recording) {
                                            recording = false
                                            Toast.makeText(
                                                ctx,
                                                "已到 2 分钟上限，自动结束录音",
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                            finishRecording()
                                        }
                                    }
                                    tryAwaitRelease()
                                    if (recording) {
                                        recording = false
                                        finishRecording()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (recording) "松开 结束" else "按住 说话",
                        fontSize = Ds.fsMd,
                        color = if (recording) colors.error else colors.labelSecondary,
                    )
                }
            } else {
                BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat-input")
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp),
                textStyle = TextStyle(fontSize = Ds.fsMd, lineHeight = 26.sp, color = colors.labelPrimary),
                cursorBrush = SolidColor(colors.brand),
                maxLines = 8,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "发消息…",
                                fontSize = Ds.fsMd,
                                lineHeight = 26.sp,
                                color = colors.labelCaption,
                            )
                        }
                        inner()
                    }
                },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleChip("深度思考", painterResource(R.drawable.ic_think), thinking, onToggleThinking)
                Spacer(Modifier.width(10.dp))
                ToggleChip("智能搜索", painterResource(R.drawable.ic_globe), webSearch, onToggleSearch)
                Spacer(Modifier.weight(1f))

                if (value.isNotBlank() || streaming) {
                    SendButton(streaming, true, onSend, onStop)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showAttach) {
                            Icon(
                                Icons.Default.Close,
                                "收起",
                                tint = colors.labelPrimary,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { showAttach = false },
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_add26),
                                "添加",
                                tint = colors.labelPrimary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable { showAttach = true },
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        if (voiceMode) {
                            Icon(
                                Icons.Default.Keyboard,
                                "切换到文字输入",
                                tint = colors.labelPrimary,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clickable { voiceMode = false },
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_voice),
                                "语音输入",
                                tint = colors.labelPrimary,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        if (hasAudioPermission()) voiceMode = true
                                        else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                                    },
                            )
                        }
                    }
                }
            }
        }
        if (showCaveat) {
            Text(
                "回答由 AI 生成，仅供参考",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                fontSize = Ds.fsCaption,
                lineHeight = Ds.lhCaption,
                color = colors.labelTertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AttachmentStrip(list: List<Attachment>, onRemove: (Int) -> Unit) {
    val colors = Ds.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        list.forEachIndexed { i, a ->
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.layer3)
            ) {
                if (a.isImage) {
                    AsyncImage(
                        model = a.uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().padding(6.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            tint = colors.labelSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            a.name.take(12),
                            fontSize = 9.sp,
                            color = colors.labelTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { onRemove(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        "移除",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachPanel(onPhoto: () -> Unit, onGallery: () -> Unit, onFile: () -> Unit) {
    val colors = Ds.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.layer3)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AttachButton("拍照", Icons.Default.PhotoCamera, onPhoto, Modifier.weight(1f))
        AttachButton("相册", Icons.Default.PhotoLibrary, onGallery, Modifier.weight(1f))
        AttachButton("文件", Icons.Default.AttachFile, onFile, Modifier.weight(1f))
    }
}

@Composable
private fun AttachButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = Ds.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.layer2)
            .clickable { onClick() }
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = colors.labelPrimary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(text, fontSize = 13.sp, color = colors.labelPrimary)
    }
}

@Composable
private fun ToggleChip(
    text: String,
    icon: Painter,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Ds.colors
    Box(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) colors.chipActiveBg else Color.Transparent)
            .border(
                1.dp,
                if (selected) colors.chipActiveBorder else colors.borderL2,
                RoundedCornerShape(15.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                null,
                tint = if (selected) colors.brandText else colors.labelSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = if (selected) colors.brandText else colors.labelPrimary,
            )
        }
    }
}

@Composable
private fun SendButton(streaming: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val colors = Ds.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (streaming) Color.Transparent else if (enabled) colors.brand else colors.brand.copy(alpha = 0.4f))
            .clickable(enabled = enabled) { if (streaming) onStop() else onSend() },
        contentAlignment = Alignment.Center,
    ) {
        if (streaming) {
            Icon(
                Icons.Default.Close,
                "停止",
                tint = colors.labelPrimary,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                "发送",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
