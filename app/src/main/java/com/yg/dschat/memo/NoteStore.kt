package com.yg.dschat.memo

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 笔记库：SQLite 主存储 + FTS4 检索索引（见 docs/03-数据模型.md）。
 *
 * 所有方法都是阻塞的，调用方负责放到 Dispatchers.IO。
 * 索引与数据在同一个事务里更新，避免搜索索引和正文不一致。
 *
 * 删除语义（重要）：
 * - softDelete / purge 是**用户**的动作；
 * - abandon 是**AI** 的动作，只写 abandonedAt，用户仍可在弃置区找回；
 *   工具层的所有读取都会过滤掉弃置笔记，所以对 AI 而言等同于删除。
 */
class NoteStore(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, SCHEMA_VERSION) {

    companion object {
        private const val DB_NAME = "memo.db"
        private const val SCHEMA_VERSION = 2

        private val NOTE_COLS = listOf(
            "id", "title", "body", "keywords", "folder_id", "pinned", "archived",
            "source_url", "created_at", "updated_at", "deleted_at",
            "abandoned_at", "abandoned_by", "rev",
        )

        private fun cols(prefix: String) = NOTE_COLS.joinToString(", ") { "$prefix.$it" }
        private fun plainCols() = NOTE_COLS.joinToString(", ")

        private const val ACTIVE = "n.deleted_at = 0 AND n.abandoned_at = 0"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE note (
              id           TEXT PRIMARY KEY,
              title        TEXT NOT NULL DEFAULT '',
              body         TEXT NOT NULL DEFAULT '',
              keywords     TEXT NOT NULL DEFAULT '',
              search_norm  TEXT NOT NULL DEFAULT '',
              folder_id    TEXT,
              pinned       INTEGER NOT NULL DEFAULT 0,
              archived     INTEGER NOT NULL DEFAULT 0,
              source_url   TEXT NOT NULL DEFAULT '',
              created_at   INTEGER NOT NULL,
              updated_at   INTEGER NOT NULL,
              deleted_at   INTEGER NOT NULL DEFAULT 0,
              abandoned_at INTEGER NOT NULL DEFAULT 0,
              abandoned_by TEXT NOT NULL DEFAULT '',
              rev          INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_note_list ON note(deleted_at, abandoned_at, archived, pinned DESC, updated_at DESC)")
        db.execSQL("CREATE INDEX idx_note_folder ON note(folder_id, deleted_at, updated_at DESC)")

        db.execSQL(
            """
            CREATE TABLE folder (
              id         TEXT PRIMARY KEY,
              name       TEXT NOT NULL,
              parent_id  TEXT,
              sort       INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE tag (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE)")
        db.execSQL(
            """
            CREATE TABLE note_tag (
              note_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
              tag_id  TEXT NOT NULL REFERENCES tag(id) ON DELETE CASCADE,
              PRIMARY KEY (note_id, tag_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_note_tag_tag ON note_tag(tag_id)")

        db.execSQL(
            """
            CREATE TABLE note_revision (
              id         TEXT PRIMARY KEY,
              note_id    TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
              rev        INTEGER NOT NULL,
              title      TEXT NOT NULL DEFAULT '',
              body       TEXT NOT NULL,
              origin     TEXT NOT NULL DEFAULT '',
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_rev_note ON note_revision(note_id, created_at DESC)")

        db.execSQL(
            """
            CREATE TABLE attachment (
              id         TEXT PRIMARY KEY,
              note_id    TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
              rel_path   TEXT NOT NULL,
              mime       TEXT NOT NULL DEFAULT '',
              size       INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE tool_run (
              id         TEXT PRIMARY KEY,
              session_id TEXT NOT NULL DEFAULT '',
              name       TEXT NOT NULL,
              args_json  TEXT NOT NULL DEFAULT '{}',
              ok         INTEGER NOT NULL DEFAULT 1,
              digest     TEXT NOT NULL DEFAULT '',
              ms         INTEGER NOT NULL DEFAULT 0,
              created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT NOT NULL DEFAULT '')")

        // 预分词 bigram 索引；simple 分词器只按空白/标点切，行为完全可预测
        db.execSQL(
            """
            CREATE VIRTUAL TABLE note_fts USING fts4(
              note_id,
              title_gram,
              body_gram,
              tokenize=simple
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 → v2：关键词与 AI 弃置
            db.execSQL("ALTER TABLE note ADD COLUMN keywords TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE note ADD COLUMN abandoned_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE note ADD COLUMN abandoned_by TEXT NOT NULL DEFAULT ''")
            db.execSQL("DROP INDEX IF EXISTS idx_note_list")
            db.execSQL("CREATE INDEX idx_note_list ON note(deleted_at, abandoned_at, archived, pinned DESC, updated_at DESC)")
            db.execSQL("DELETE FROM note_fts")
            db.rawQuery("SELECT ${plainCols()} FROM note", null).use { c ->
                while (c.moveToNext()) reindex(db, c.toNote())
            }
        }
    }

    // ── 内部工具 ────────────────────────────────────────────────────────────

    private fun normText(title: String, keywords: List<String>, body: String): String =
        TextIndex.normalize("$title\n${keywords.joinToString(" ")}\n$body")

    private fun <T> tx(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block(db)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.toNote(): Note = Note(
        id = getString(0),
        title = getString(1),
        body = getString(2),
        keywords = getString(3).split(',', '，').map { it.trim() }.filter { it.isNotEmpty() },
        folderId = if (isNull(4)) null else getString(4),
        pinned = getInt(5) != 0,
        archived = getInt(6) != 0,
        sourceUrl = getString(7),
        createdAt = getLong(8),
        updatedAt = getLong(9),
        deletedAt = getLong(10),
        abandonedAt = getLong(11),
        abandonedBy = getString(12),
        rev = getInt(13),
    )

    private fun noteById(db: SQLiteDatabase, id: String): Note? =
        db.rawQuery("SELECT ${plainCols()} FROM note WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.toNote() else null
        }

    private fun reindex(db: SQLiteDatabase, note: Note) {
        db.execSQL("DELETE FROM note_fts WHERE note_id = ?", arrayOf<Any?>(note.id))
        // 关键词并入标题 gram，让关键词命中获得「标题级」权重
        val titleGrams = TextIndex.gramsOf(note.title + " " + note.keywords.joinToString(" "))
        db.execSQL(
            "INSERT INTO note_fts(note_id, title_gram, body_gram) VALUES(?, ?, ?)",
            arrayOf<Any?>(note.id, titleGrams, TextIndex.gramsOf(note.body)),
        )
    }

    private fun kw(list: List<String>): String =
        list.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().joinToString(", ")

    // ── meta ───────────────────────────────────────────────────────────────

    fun meta(key: String): String? =
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun setMeta(key: String, value: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO meta(k, v) VALUES(?, ?)",
            arrayOf<Any?>(key, value),
        )
    }

    // ── 文件夹 / 标签 ───────────────────────────────────────────────────────

    fun folders(): List<Folder> =
        readableDatabase.rawQuery(
            "SELECT id, name, parent_id, sort FROM folder ORDER BY sort, name",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Folder(
                            id = c.getString(0),
                            name = c.getString(1),
                            parentId = if (c.isNull(2)) null else c.getString(2),
                            sort = c.getInt(3),
                        )
                    )
                }
            }
        }

    fun folderByName(name: String): Folder? =
        readableDatabase.rawQuery(
            "SELECT id, name, parent_id, sort FROM folder WHERE name = ? COLLATE NOCASE",
            arrayOf(name),
        ).use { c ->
            if (c.moveToFirst()) Folder(c.getString(0), c.getString(1)) else null
        }

    fun ensureFolder(name: String): String = tx { db ->
        db.rawQuery("SELECT id FROM folder WHERE name = ? COLLATE NOCASE", arrayOf(name)).use { c ->
            if (c.moveToFirst()) return@tx c.getString(0)
        }
        val id = newId()
        db.execSQL(
            "INSERT INTO folder(id, name, parent_id, sort, created_at) VALUES(?, ?, NULL, 0, ?)",
            arrayOf<Any?>(id, name.trim(), System.currentTimeMillis()),
        )
        id
    }

    fun tags(): List<Tag> =
        readableDatabase.rawQuery(
            """
            SELECT t.id, t.name, COUNT(nt.note_id) AS c
            FROM tag t LEFT JOIN note_tag nt ON nt.tag_id = t.id
            GROUP BY t.id, t.name
            ORDER BY c DESC, t.name
            """.trimIndent(),
            null,
        ).use { c ->
            buildList { while (c.moveToNext()) add(Tag(c.getString(0), c.getString(1), c.getInt(2))) }
        }

    private fun ensureTags(db: SQLiteDatabase, names: List<String>): List<String> =
        names.map { raw ->
            val name = raw.trim().removePrefix("#")
            if (name.isEmpty()) return@map ""
            val found = db.rawQuery("SELECT id FROM tag WHERE name = ?", arrayOf(name)).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            found ?: newId().also { id ->
                db.execSQL("INSERT INTO tag(id, name) VALUES(?, ?)", arrayOf<Any?>(id, name))
            }
        }.filter { it.isNotEmpty() }

    fun setTags(noteId: String, names: List<String>) = tx { db ->
        db.execSQL("DELETE FROM note_tag WHERE note_id = ?", arrayOf<Any?>(noteId))
        ensureTags(db, names).distinct().forEach { tagId ->
            db.execSQL(
                "INSERT OR IGNORE INTO note_tag(note_id, tag_id) VALUES(?, ?)",
                arrayOf<Any?>(noteId, tagId),
            )
        }
    }

    fun addTags(noteId: String, names: List<String>) = tx { db ->
        ensureTags(db, names).distinct().forEach { tagId ->
            db.execSQL(
                "INSERT OR IGNORE INTO note_tag(note_id, tag_id) VALUES(?, ?)",
                arrayOf<Any?>(noteId, tagId),
            )
        }
    }

    fun removeTags(noteId: String, names: List<String>) = tx { db ->
        names.forEach { raw ->
            val name = raw.trim().removePrefix("#")
            db.execSQL(
                "DELETE FROM note_tag WHERE note_id = ? AND tag_id IN (SELECT id FROM tag WHERE name = ?)",
                arrayOf<Any?>(noteId, name),
            )
        }
    }

    fun tagsOf(noteId: String): List<String> =
        readableDatabase.rawQuery(
            "SELECT t.name FROM tag t JOIN note_tag nt ON nt.tag_id = t.id WHERE nt.note_id = ? ORDER BY t.name",
            arrayOf(noteId),
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    // ── 笔记读写 ────────────────────────────────────────────────────────────

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM note n WHERE $ACTIVE", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }

    fun note(id: String): Note? = noteById(readableDatabase, id)

    fun existsTitle(title: String, folderId: String?): Note? =
        readableDatabase.rawQuery(
            "SELECT ${plainCols()} FROM note WHERE title = ? AND IFNULL(folder_id, '') = ? AND deleted_at = 0",
            arrayOf(title, folderId ?: ""),
        ).use { c -> if (c.moveToFirst()) c.toNote() else null }

    fun createNote(
        title: String,
        body: String,
        keywords: List<String> = emptyList(),
        folderId: String? = null,
        tags: List<String> = emptyList(),
        sourceUrl: String = "",
    ): Note {
        val stamp = System.currentTimeMillis()
        val note = Note(
            title = title,
            body = body,
            keywords = keywords,
            folderId = folderId,
            sourceUrl = sourceUrl,
            createdAt = stamp,
            updatedAt = stamp,
        )
        tx { db ->
            db.execSQL(
                """
                INSERT INTO note(id, title, body, keywords, search_norm, folder_id, pinned, archived,
                                 source_url, created_at, updated_at, deleted_at, abandoned_at, abandoned_by, rev)
                VALUES(?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, 0, 0, '', 1)
                """.trimIndent(),
                arrayOf<Any?>(
                    note.id, title, body, kw(keywords), normText(title, keywords, body),
                    folderId, sourceUrl, stamp, stamp,
                ),
            )
            reindex(db, note)
            ensureTags(db, tags).distinct().forEach { tagId ->
                db.execSQL(
                    "INSERT OR IGNORE INTO note_tag(note_id, tag_id) VALUES(?, ?)",
                    arrayOf<Any?>(note.id, tagId),
                )
            }
        }
        return note
    }

    /**
     * 保存正文/标题/关键词。origin 记录改动来源（user / ai:<工具> / import / restore）。
     * keywords 传 null 表示不改。
     */
    fun saveContent(
        id: String,
        title: String,
        body: String,
        keywords: List<String>? = null,
        origin: String = "user",
    ): Note? = tx { db ->
        val old = noteById(db, id) ?: return@tx null
        val newKeywords = keywords ?: old.keywords
        if (old.title != title || old.body != body || old.keywords != newKeywords) {
            snapshot(db, old, origin)
        }
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE note SET title = ?, body = ?, keywords = ?, search_norm = ?, updated_at = ?, rev = rev + 1 WHERE id = ?",
            arrayOf<Any?>(title, body, kw(newKeywords), normText(title, newKeywords, body), now, id),
        )
        reindex(db, Note(id = id, title = title, body = body, keywords = newKeywords))
        noteById(db, id)
    }

    fun appendBody(id: String, text: String, origin: String = "ai:append"): Note? {
        val note = noteById(readableDatabase, id) ?: return null
        val merged = if (note.body.isBlank()) text else note.body.trimEnd() + "\n\n" + text
        return saveContent(id, note.title, merged, note.keywords, origin)
    }

    fun setFlags(
        id: String,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        folderId: String? = null,
    ) = tx { db ->
        if (pinned != null) {
            db.execSQL("UPDATE note SET pinned = ? WHERE id = ?", arrayOf<Any?>(if (pinned) 1 else 0, id))
        }
        if (archived != null) {
            db.execSQL("UPDATE note SET archived = ? WHERE id = ?", arrayOf<Any?>(if (archived) 1 else 0, id))
        }
        if (folderId != null) {
            db.execSQL("UPDATE note SET folder_id = ? WHERE id = ?", arrayOf<Any?>(folderId, id))
        }
    }

    // ── 用户删除（回收站）────────────────────────────────────────────────────

    fun softDelete(id: String) {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            "UPDATE note SET deleted_at = ?, updated_at = ? WHERE id = ?",
            arrayOf<Any?>(now, now, id),
        )
    }

    fun restore(id: String) {
        writableDatabase.execSQL(
            "UPDATE note SET deleted_at = 0, updated_at = ? WHERE id = ?",
            arrayOf<Any?>(System.currentTimeMillis(), id),
        )
    }

    fun purge(id: String) = tx { db ->
        db.execSQL("DELETE FROM note_fts WHERE note_id = ?", arrayOf<Any?>(id))
        db.execSQL("DELETE FROM note WHERE id = ?", arrayOf<Any?>(id))
    }

    fun emptyTrash() = tx { db ->
        db.rawQuery("SELECT id FROM note WHERE deleted_at > 0", null).use { c ->
            while (c.moveToNext()) {
                db.execSQL("DELETE FROM note_fts WHERE note_id = ?", arrayOf<Any?>(c.getString(0)))
            }
        }
        db.execSQL("DELETE FROM note WHERE deleted_at > 0")
    }

    fun deleted(): List<Note> =
        readableDatabase.rawQuery(
            "SELECT ${plainCols()} FROM note WHERE deleted_at > 0 ORDER BY deleted_at DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    // ── AI 弃置（对 AI 等于删除）──────────────────────────────────────────────

    fun abandon(id: String, by: String = "ai"): Note? = tx { db ->
        val now = System.currentTimeMillis()
        db.execSQL(
            "UPDATE note SET abandoned_at = ?, abandoned_by = ?, updated_at = ? WHERE id = ? AND deleted_at = 0",
            arrayOf<Any?>(now, by, now, id),
        )
        noteById(db, id)
    }

    fun restoreAbandoned(id: String) {
        writableDatabase.execSQL(
            "UPDATE note SET abandoned_at = 0, abandoned_by = '', updated_at = ? WHERE id = ?",
            arrayOf<Any?>(System.currentTimeMillis(), id),
        )
    }

    fun abandonedNotes(): List<Note> =
        readableDatabase.rawQuery(
            "SELECT ${plainCols()} FROM note WHERE abandoned_at > 0 AND deleted_at = 0 ORDER BY abandoned_at DESC",
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    // ── 版本快照 ────────────────────────────────────────────────────────────

    private fun snapshot(db: SQLiteDatabase, note: Note, origin: String) {
        db.execSQL(
            """
            INSERT INTO note_revision(id, note_id, rev, title, body, origin, created_at)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(newId(), note.id, note.rev, note.title, note.body, origin, System.currentTimeMillis()),
        )
        db.execSQL(
            """
            DELETE FROM note_revision WHERE note_id = ? AND id NOT IN (
              SELECT id FROM note_revision WHERE note_id = ? ORDER BY created_at DESC LIMIT 20
            )
            """.trimIndent(),
            arrayOf<Any?>(note.id, note.id),
        )
    }

    fun revisions(noteId: String): List<Revision> =
        readableDatabase.rawQuery(
            """
            SELECT id, note_id, rev, title, body, origin, created_at
            FROM note_revision WHERE note_id = ? ORDER BY created_at DESC
            """.trimIndent(),
            arrayOf(noteId),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Revision(
                            id = c.getString(0),
                            noteId = c.getString(1),
                            rev = c.getInt(2),
                            title = c.getString(3),
                            body = c.getString(4),
                            origin = c.getString(5),
                            createdAt = c.getLong(6),
                        )
                    )
                }
            }
        }

    fun restoreRevision(revisionId: String): Note? {
        val rev = readableDatabase.rawQuery(
            "SELECT note_id, title, body FROM note_revision WHERE id = ?",
            arrayOf(revisionId),
        ).use { c ->
            if (c.moveToFirst()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null
        } ?: return null
        return saveContent(rev.first, rev.second, rev.third, origin = "restore")
    }

    // ── 检索 ───────────────────────────────────────────────────────────────

    /**
     * 统一检索入口。needle 为空时按置顶 + 更新时间列出。
     * 默认排除 AI 弃置与用户删除；中文走 FTS4 短语查询，单字/纯符号降级 LIKE。
     */
    fun queryNotes(
        needle: String,
        folderId: String? = null,
        tagName: String? = null,
        includeAbandoned: Boolean = false,
        limit: Int = 300,
    ): List<NoteHit> {
        val db = readableDatabase
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        where.add("n.deleted_at = 0")
        if (!includeAbandoned) where.add("n.abandoned_at = 0")
        where.add("n.archived = 0")
        if (folderId != null) {
            where.add("n.folder_id = ?")
            args.add(folderId)
        }
        if (tagName != null) {
            where.add(
                "EXISTS (SELECT 1 FROM note_tag nt JOIN tag t ON t.id = nt.tag_id " +
                    "WHERE nt.note_id = n.id AND t.name = ? COLLATE NOCASE)"
            )
            args.add(tagName)
        }
        val whereSql = where.joinToString(" AND ")

        val norm = TextIndex.normalize(needle).trim()
        val raw: List<Note> = when {
            norm.isEmpty() -> listBy(db, whereSql, args, limit)
            else -> {
                val expr = TextIndex.queryExpr(needle)
                val viaFts = if (expr != null) {
                    runCatching { listByFts(db, expr, whereSql, args) }.getOrElse { emptyList() }
                } else {
                    emptyList()
                }
                if (viaFts.isNotEmpty()) viaFts else listByLike(db, norm, whereSql, args, limit)
            }
        }

        if (norm.isEmpty()) return raw.map { NoteHit(it) }

        val words = norm.split(' ', '\n').filter { it.isNotBlank() }
        return raw.map { note ->
            val titleNorm = TextIndex.normalize(note.title + " " + note.keywords.joinToString(" "))
            NoteHit(
                note = note,
                snippet = TextIndex.snippet(note.body, needle),
                titleHit = words.any { titleNorm.contains(it) },
            )
        }.sortedWith(
            compareByDescending<NoteHit> { it.titleHit }
                .thenByDescending { it.note.pinned }
                .thenByDescending { it.note.updatedAt }
        ).take(limit)
    }

    private fun listBy(db: SQLiteDatabase, whereSql: String, args: List<String>, limit: Int): List<Note> =
        db.rawQuery(
            "SELECT ${cols("n")} FROM note n WHERE $whereSql ORDER BY n.pinned DESC, n.updated_at DESC LIMIT ?",
            (args + limit.toString()).toTypedArray(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    private fun listByFts(
        db: SQLiteDatabase,
        expr: String,
        whereSql: String,
        args: List<String>,
    ): List<Note> =
        db.rawQuery(
            """
            SELECT ${cols("n")} FROM note_fts JOIN note n ON n.id = note_fts.note_id
            WHERE note_fts MATCH ? AND $whereSql
            ORDER BY n.pinned DESC, n.updated_at DESC
            LIMIT 500
            """.trimIndent(),
            (listOf(expr) + args).toTypedArray(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    private fun listByLike(
        db: SQLiteDatabase,
        normNeedle: String,
        whereSql: String,
        args: List<String>,
        limit: Int,
    ): List<Note> =
        db.rawQuery(
            """
            SELECT ${cols("n")} FROM note n
            WHERE $whereSql AND n.search_norm LIKE ?
            ORDER BY n.pinned DESC, n.updated_at DESC LIMIT ?
            """.trimIndent(),
            (args + "%$normNeedle%" + limit.toString()).toTypedArray(),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toNote()) } }

    // ── 索引维护 ────────────────────────────────────────────────────────────

    fun rebuildIndex() = tx { db ->
        db.execSQL("DELETE FROM note_fts")
        db.rawQuery("SELECT ${plainCols()} FROM note", null).use { c ->
            while (c.moveToNext()) reindex(db, c.toNote())
        }
        db.execSQL(
            "INSERT OR REPLACE INTO meta(k, v) VALUES('fts_last_sync', ?)",
            arrayOf<Any?>(System.currentTimeMillis().toString()),
        )
    }

    fun reindexSweep(): Int = tx { db ->
        val last = db.rawQuery("SELECT v FROM meta WHERE k = 'fts_last_sync'", null).use { c ->
            if (c.moveToFirst()) c.getString(0).toLongOrNull() ?: 0L else 0L
        }
        var n = 0
        db.rawQuery("SELECT ${plainCols()} FROM note WHERE updated_at > ?", arrayOf(last.toString())).use { c ->
            while (c.moveToNext()) {
                reindex(db, c.toNote())
                n++
            }
        }
        db.execSQL(
            "INSERT OR REPLACE INTO meta(k, v) VALUES('fts_last_sync', ?)",
            arrayOf<Any?>(System.currentTimeMillis().toString()),
        )
        n
    }

    // ── 种子数据 ────────────────────────────────────────────────────────────

    fun seedIfEmpty(): Boolean {
        if (count() > 0) return false
        val work = ensureFolder("工作")
        val life = ensureFolder("生活")
        val read = ensureFolder("读书")

        createNote(
            title = "部署踩坑记录",
            body = """
                nginx 反向代理偶发 502，最后定位到 upstream keepalive 连接被后端提前断开。
                设置 proxy_read_timeout 与 upstream keepalive_timeout 后稳定。

                - 检查点：upstream 是否复用连接
                - 监控：5xx 比例、连接重置次数
            """.trimIndent(),
            keywords = listOf("nginx", "502", "keepalive"),
            folderId = work,
            tags = listOf("部署", "nginx"),
        )
        createNote(
            title = "发布流程备忘",
            body = """
                1. 合并到 release 分支，打 tag
                2. 构建镜像，推送到私有仓库
                3. 灰度 10% 流量观察 30 分钟
                4. 全量发布，回滚脚本保留两小时
            """.trimIndent(),
            keywords = listOf("发布", "灰度", "回滚"),
            folderId = work,
            tags = listOf("部署", "流程"),
        )
        createNote(
            title = "服务器清单",
            body = "web-01 / web-02 承载前端；api-01、api-02 承载接口；db-01 主库，db-02 只读从库。",
            keywords = listOf("服务器", "清单"),
            folderId = work,
            tags = listOf("部署"),
        )
        createNote(
            title = "读书笔记：深度工作",
            body = "把注意力当成稀缺资源管理。上午留给需要连续思考的任务，下午处理沟通与事务。",
            keywords = listOf("注意力", "深度工作"),
            folderId = read,
            tags = listOf("读书"),
        )
        createNote(
            title = "本周待办",
            body = "整理上周的部署碎片笔记；把 nginx 那篇补上复现步骤；清理回收站。",
            keywords = listOf("待办"),
            folderId = life,
            tags = listOf("待办"),
        )
        createNote(
            title = "咖啡冲煮参数",
            body = "手冲：水温 92 度，粉水比 1:15，闷蒸 30 秒。研磨中细。",
            keywords = listOf("咖啡", "手冲"),
            folderId = life,
            tags = listOf("生活"),
        )
        createNote(
            title = "读书笔记：系统之美",
            body = "存量、流量、反馈回路。延迟是系统震荡的常见原因，先找延迟再调参数。",
            keywords = listOf("系统", "反馈回路"),
            folderId = read,
            tags = listOf("读书", "系统"),
        )
        createNote(
            title = "关于备忘的备忘",
            body = "这条笔记是给检索测试用的：标题和正文里都有「备忘」与「备忘录」，用来验证 bigram 双侧命中。",
            keywords = listOf("检索", "测试"),
            folderId = life,
            tags = listOf("测试"),
        )
        createNote(
            title = "サーバー移行メモ",
            body = "日本語のメモ：サーバー移行の手順をまとめる。假名折叠测试：さーばー で検索できること。",
            keywords = listOf("移行"),
            folderId = work,
            tags = listOf("部署"),
        )
        createNote(
            title = "API 与 PI 的区别",
            body = "写接口文档时要区分 PI（进程内接口）和 API（对外接口）。整词匹配测试用。",
            keywords = listOf("接口", "术语"),
            folderId = work,
            tags = listOf("测试"),
        )
        return true
    }
}
