package com.yg.dschat.memo

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.dschat.Ds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔记编辑页。
 * [noteId] 为 null 表示新建。返回时若有改动会自动保存，避免误触丢失内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    store: NoteStore,
    noteId: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val colors = Ds.colors
    val main = remember { Handler(Looper.getMainLooper()) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    var title by remember(noteId) { mutableStateOf("") }
    var body by remember(noteId) { mutableStateOf("") }
    var updatedAt by remember(noteId) { mutableStateOf(0L) }
    var rev by remember(noteId) { mutableStateOf(0) }
    var loaded by remember(noteId) { mutableStateOf(noteId == null) }
    var dirty by remember(noteId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun persist(close: Boolean) {
        val t = title.trim()
        val b = body.trim()
        if (t.isBlank() && b.isBlank()) {
            if (close) onBack()
            return
        }
        val safeTitle = t.ifBlank { "无标题备忘" }
        Thread {
            runCatching {
                if (noteId == null) {
                    store.createNote(title = safeTitle, body = b)
                } else {
                    store.saveContent(noteId, safeTitle, b, origin = "user")
                }
            }
            main.post {
                dirty = false
                onChanged()
                if (close) onBack()
            }
        }.start()
    }

    LaunchedEffect(noteId) {
        if (noteId == null) return@LaunchedEffect
        Thread {
            val n = runCatching { store.note(noteId) }.getOrNull()
            main.post {
                if (n != null) {
                    title = n.title
                    body = n.body
                    updatedAt = n.updatedAt
                    rev = n.rev
                }
                loaded = true
            }
        }.start()
    }

    androidx.activity.compose.BackHandler {
        if (dirty) persist(close = true) else onBack()
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (noteId == null) "新建备忘" else "编辑备忘",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.labelPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) persist(close = true) else onBack() }) {
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
                actions = {
                    if (noteId != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Default.Delete,
                                "删除",
                                tint = colors.labelSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    TextButton(
                        onClick = { persist(close = false) },
                        enabled = dirty && (title.isNotBlank() || body.isNotBlank()),
                    ) {
                        Text(
                            "保存",
                            fontSize = 15.sp,
                            color = if (dirty) colors.brandText else colors.labelTertiary,
                        )
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
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            if (!loaded) {
                Text("载入中…", modifier = Modifier.padding(top = 20.dp), fontSize = 13.sp, color = colors.labelTertiary)
                return@Column
            }

            // 标题
            BasicTextField(
                value = title,
                onValueChange = {
                    title = it
                    dirty = true
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle = TextStyle(
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.labelPrimary,
                ),
                cursorBrush = SolidColor(colors.brand),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text(
                                "标题",
                                fontSize = 22.sp,
                                lineHeight = 30.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.labelTertiary,
                            )
                        }
                        inner()
                    }
                },
            )

            if (updatedAt > 0L) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "更新于 " + fmt.format(Date(updatedAt)),
                        fontSize = 11.sp,
                        color = colors.labelTertiary,
                    )
                    if (rev > 1) {
                        Spacer(Modifier.width(10.dp))
                        Text("第 $rev 版", fontSize = 11.sp, color = colors.labelTertiary)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.borderL1)
            Spacer(Modifier.height(14.dp))

            // 正文
            BasicTextField(
                value = body,
                onValueChange = {
                    body = it
                    dirty = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                textStyle = TextStyle(fontSize = 15.sp, lineHeight = 26.sp, color = colors.labelPrimary),
                cursorBrush = SolidColor(colors.brand),
                decorationBox = { inner ->
                    Box {
                        if (body.isEmpty()) {
                            Text(
                                "记点什么…",
                                fontSize = 15.sp,
                                lineHeight = 26.sp,
                                color = colors.labelTertiary,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这条备忘？") },
            text = { Text("删除后可以在回收站找回。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val id = noteId ?: return@TextButton
                    Thread {
                        runCatching { store.softDelete(id) }
                        main.post {
                            dirty = false
                            onChanged()
                            onBack()
                        }
                    }.start()
                }) { Text("删除", color = colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }
}
