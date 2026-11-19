package com.yg.dschat.memo

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.dschat.Ds
import com.yg.dschat.DsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 备忘库管理：用户自己查找、编辑、删除。助手只能通过工具弃置，不能物理删除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoListScreen(store: NoteStore, onBack: () -> Unit) {
    val colors = Ds.colors
    val main = remember { Handler(Looper.getMainLooper()) }

    var notes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    /** null=列表；""=新建；其它=编辑对应 id */
    var editor by remember { mutableStateOf<String?>(null) }

    fun reload() {
        Thread {
            val list = runCatching {
                if (query.isBlank()) {
                    store.queryNotes(needle = "", limit = 200).map { it.note }
                } else {
                    store.queryNotes(needle = query, limit = 200).map { it.note }
                }
            }.getOrDefault(emptyList())
            main.post {
                notes = list
                loaded = true
            }
        }.start()
    }

    LaunchedEffect(query) { reload() }

    androidx.activity.compose.BackHandler { onBack() }

    if (editor != null) {
        NoteEditScreen(
            store = store,
            noteId = editor!!.ifBlank { null },
            onBack = {
                editor = null
                reload()
            },
            onChanged = { },
        )
        return
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "备忘录",
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
                actions = {
                    IconButton(onClick = { editor = "" }) {
                        Icon(Icons.Default.Add, "新建备忘", tint = colors.labelPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.bg),
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            // 搜索框
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(colors.layer3)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                                Text("搜索备忘…", fontSize = 14.sp, color = colors.labelTertiary)
                            }
                            inner()
                        }
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            if (loaded && notes.isEmpty()) {
                Text(
                    if (query.isBlank()) "还没有备忘。对话里打开「备忘」开关，让助手帮你记，或点右上角新建。"
                    else "没有匹配「$query」的备忘。",
                    modifier = Modifier.padding(top = 24.dp),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colors.labelTertiary,
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(notes, key = { it.id }) { n ->
                    NoteRow(n) { editor = n.id }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit) {
    val colors = Ds.colors
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.layer2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                note.title.ifBlank { "（无标题）" },
                modifier = Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                fmt.format(Date(note.updatedAt)),
                fontSize = 11.sp,
                color = colors.labelTertiary,
            )
        }
        if (note.preview.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                note.preview,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.labelSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
