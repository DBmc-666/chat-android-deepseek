package com.yg.dschat.memo

import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

/**
 * 备忘条目。
 *
 * 两种「消失」语义，别混：
 * - [deletedAt] > 0：**用户删除**，进回收站，可恢复、可彻底删。
 * - [abandonedAt] > 0：**助手弃置**。对助手来说等于删除（搜索/读取/列表都看不到），
 *   但用户随时能在「弃置区」找回。助手没有物理删除能力。
 */
data class Note(
    val id: String = newId(),
    val title: String = "",
    val body: String = "",
    val keywords: List<String> = emptyList(),
    val folderId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val sourceUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long = 0L,
    val abandonedAt: Long = 0L,
    val abandonedBy: String = "",
    val rev: Int = 1,
) {
    val isDeleted: Boolean get() = deletedAt > 0L
    val isAbandoned: Boolean get() = abandonedAt > 0L && deletedAt == 0L
    val keywordsText: String get() = keywords.joinToString(", ")
    val preview: String get() = body.replace(Regex("\\s+"), " ").trim()
}

data class Folder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val sort: Int = 0,
)

data class Tag(
    val id: String,
    val name: String,
    val count: Int = 0,
)

/** 检索结果：带命中片段与排序依据。 */
data class NoteHit(
    val note: Note,
    val snippet: String = "",
    val titleHit: Boolean = false,
)

data class Revision(
    val id: String,
    val noteId: String,
    val rev: Int,
    val title: String,
    val body: String,
    val origin: String,
    val createdAt: Long,
)

enum class TimeGroup(val label: String) {
    Today("今天"),
    Yesterday("昨天"),
    Week("7 天内"),
    Month("30 天内"),
    Earlier("更早"),
}
