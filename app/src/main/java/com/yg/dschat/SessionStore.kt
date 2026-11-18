package com.yg.dschat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 会话落盘：每个会话一个 JSON 文件 + 一个索引文件。
 * 写入走 tmp -> rename，避免进程被杀时留下半截文件。
 */
class SessionStore(context: Context) {

    private val root: File = context.filesDir
    private val dir: File = File(root, "sessions").apply { mkdirs() }
    private val indexFile: File = File(root, "sessions_index.json")

    fun loadAll(): List<Session> {
        if (!indexFile.exists()) return emptyList()
        val text = runCatching { indexFile.readText() }.getOrNull().orEmpty()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<Session>()
        for (i in 0 until arr.length()) {
            val id = arr.optJSONObject(i)?.optString("id").orEmpty()
            if (id.isBlank()) continue
            val f = File(dir, "$id.json")
            if (!f.exists()) continue
            runCatching { sessionFromJson(JSONObject(f.readText())) }.getOrNull()?.let { out.add(it) }
        }
        return out.sortedByDescending { it.updated }
    }

    fun save(session: Session) {
        writeAtomic(File(dir, "${session.id}.json"), session.toJson().toString())
        // 索引只读那个小 JSON，不解析会话正文；以前用 loadMeta() 会把每个会话文件都读一遍
        val entries = readIndexEntries().filterNot { it.optString("id") == session.id }.toMutableList()
        entries.add(
            JSONObject()
                .put("id", session.id)
                .put("title", session.title)
                .put("updated", session.updated)
        )
        writeIndexEntries(entries)
    }

    private fun readIndexEntries(): List<JSONObject> {
        if (!indexFile.exists()) return emptyList()
        val text = runCatching { indexFile.readText() }.getOrNull().orEmpty()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun writeIndexEntries(entries: List<JSONObject>) {
        val arr = JSONArray()
        entries.forEach { arr.put(it) }
        writeAtomic(indexFile, arr.toString())
    }

    fun delete(id: String) {
        runCatching { File(dir, "$id.json").delete() }
        writeIndexEntries(readIndexEntries().filterNot { it.optString("id") == id })
    }

    private fun writeAtomic(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        try {
            java.io.FileOutputStream(tmp).use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
                // 掉电保护：不 fsync 可能留下长度正确但内容为空的文件
                runCatching { out.fd.sync() }
            }
            // Linux 上 rename 覆盖是原子的，不要先 delete 目标文件
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("SessionStore", "写入失败: ${target.name}", e)
            // 保留 tmp，下次启动还有机会人工恢复
        }
    }
}
