package com.yg.dschat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DrawerPanel(
    vm: ChatViewModel,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onExperimental: () -> Unit,
) {
    val colors = Ds.colors
    var query by remember { mutableStateOf("") }
    var userMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Session?>(null) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    ModalDrawerSheet(
        modifier = Modifier.width(Ds.sidebarWidth),
        drawerContainerColor = colors.bg,
        drawerContentColor = colors.labelPrimary,
        drawerShape = RectangleShape,
        drawerTonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(12.dp))

            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().height(40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "选择对话",
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.labelPrimary,
                    )
                    IconButton(
                        onClick = { selecting = false; selected.clear() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.layer3),
                    ) {
                        Icon(Icons.Default.Close, "退出选择", tint = colors.labelSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(colors.layer3)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        null,
                        tint = colors.labelTertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.labelPrimary),
                        cursorBrush = SolidColor(colors.brand),
                        singleLine = true,
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) {
                                    Text("搜索对话内容…", fontSize = 14.sp, color = colors.labelTertiary)
                                }
                                inner()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val filtered = vm.sessions.filter {
                query.isBlank() || it.title.contains(query, ignoreCase = true)
            }
            val pinnedList = filtered.filter { it.pinned }
            val normal = filtered.filterNot { it.pinned }
            val groups = normal.groupBy { bucketOf(it.updated) }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                if (pinnedList.isNotEmpty()) {
                    item(key = "header-pinned") { SectionHeader("置顶") }
                    items(pinnedList, key = { "pin-" + it.id }) { s ->
                        SessionRow(
                            session = s,
                            active = s.id == vm.currentId,
                            selecting = selecting,
                            checked = s.id in selected,
                            onOpen = { if (selecting) toggle(selected, s.id) else { vm.select(s.id); onClose() } },
                            menuOpen = menuFor == s.id,
                            onMenu = { menuFor = s.id },
                            onDismissMenu = { menuFor = null },
                            onRename = { menuFor = null; renaming = s },
                            onPin = { menuFor = null; vm.togglePin(s.id) },
                            onDelete = { menuFor = null; vm.delete(s.id) },
                        )
                    }
                }

                val labels = listOf("今天", "昨天", "7 天内", "30 天内", "更早")
                val firstNonEmpty = labels.firstOrNull { !groups[it].isNullOrEmpty() }
                labels.forEach { label ->
                    val list = groups[label]
                    if (list.isNullOrEmpty()) return@forEach
                    item(key = "header-$label") {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                color = colors.labelTertiary,
                            )
                            if (pinnedList.isEmpty() && label == firstNonEmpty && !selecting) {
                                IconButton(
                                    onClick = { selecting = true },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_ellipsis),
                                        "选择对话",
                                        tint = colors.labelTertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                    items(list, key = { it.id }) { s ->
                        SessionRow(
                            session = s,
                            active = s.id == vm.currentId,
                            selecting = selecting,
                            checked = s.id in selected,
                            onOpen = { if (selecting) toggle(selected, s.id) else { vm.select(s.id); onClose() } },
                            menuOpen = menuFor == s.id,
                            onMenu = { menuFor = s.id },
                            onDismissMenu = { menuFor = null },
                            onRename = { menuFor = null; renaming = s },
                            onPin = { menuFor = null; vm.togglePin(s.id) },
                            onDelete = { menuFor = null; vm.delete(s.id) },
                        )
                    }
                }
            }

            if (selecting) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            vm.pinAll(selected.toList(), true)
                            selecting = false
                            selected.clear()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            null,
                            tint = if (selected.isEmpty()) colors.labelTertiary else colors.brandText,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("置顶", color = if (selected.isEmpty()) colors.labelTertiary else colors.brandText)
                    }
                    Box(Modifier.width(1.dp).height(20.dp).background(colors.borderL2))
                    TextButton(
                        onClick = {
                            vm.deleteAll(selected.toList())
                            selecting = false
                            selected.clear()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty(),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_trash),
                            null,
                            tint = if (selected.isEmpty()) colors.labelTertiary else colors.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("删除", color = if (selected.isEmpty()) colors.labelTertiary else colors.error)
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("本地会话", fontSize = 15.sp, color = colors.labelPrimary)
                        Text("API Key 仅存本机", fontSize = 11.sp, color = colors.labelTertiary)
                    }
                    Box {
                        IconButton(onClick = { userMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                painterResource(R.drawable.ic_ellipsis),
                                "更多",
                                tint = colors.labelSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        DropdownMenu(expanded = userMenu, onDismissRequest = { userMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = { userMenu = false; onSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text("实验性功能") },
                                onClick = { userMenu = false; onExperimental() },
                            )
                        }
                    }
                }
            }
        }

        renaming?.let { target ->
            var text by remember(target.id) { mutableStateOf(target.title) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("重命名对话") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.rename(target.id, text); renaming = null }) { Text("保存") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消") } },
            )
        }
    }
}

private fun toggle(list: MutableList<String>, id: String) {
    if (id in list) list.remove(id) else list.add(id)
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = Ds.colors.labelTertiary,
    )
}

@Composable
private fun SessionRow(
    session: Session,
    active: Boolean,
    selecting: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Ds.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active && !selecting) colors.navActive else Color.Transparent)
            .clickable { onOpen() }
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.brand else Color.Transparent)
                    .then(
                        if (checked) Modifier else Modifier.border(1.5.dp, colors.borderL3, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            session.title.ifBlank { "新对话" },
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = when {
                selecting -> colors.labelPrimary
                active -> colors.navActiveText
                else -> colors.labelPrimary
            },
        )
        if (!selecting) {
            Box {
                IconButton(onClick = onMenu, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_ellipsis),
                        "更多",
                        tint = colors.labelSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = onRename)
                    DropdownMenuItem(
                        text = { Text(if (session.pinned) "取消置顶" else "置顶") },
                        onClick = onPin,
                    )
                    DropdownMenuItem(text = { Text("删除") }, onClick = onDelete)
                }
            }
        }
    }
}
