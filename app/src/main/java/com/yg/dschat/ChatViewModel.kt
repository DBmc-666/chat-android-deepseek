package com.yg.dschat

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsStore = SettingsStore(app)
    private val store = SessionStore(app)
    private val usageStore = UsageStore(app)

    /** 备忘库（独立数据库 memo.db，与对话数据无关） */
    val memoStore = com.yg.dschat.memo.NoteStore(app)

    /** 客户端工具模式下的联网能力 */
    val webClient = com.yg.dschat.memo.WebClient(app.cacheDir)

    fun toggleMemo() {
        settings = settings.copy(memoActive = !settings.memoActive)
        settingsStore.save(settings)
        version++
    }

    fun usageTotal() = usageStore.total()
    fun usageToday() = usageStore.today()
    fun resetUsage() = usageStore.reset()
    private val client = AnthropicClient()
    private val main = Handler(Looper.getMainLooper())

    var settings by mutableStateOf(settingsStore.load())
        private set

    init {
        // 声明顺序必须在 settings 之后，否则读不到初始值
    }

    val sessions = mutableStateListOf<Session>()
    var currentId by mutableStateOf<String?>(null)
        private set
    var streaming by mutableStateOf(false)

    /** 附件上传失败提示，UI 取出后置空 */
    var attachError by mutableStateOf<String?>(null)

    /** 每次发起请求递增；停止或新请求会让排队中的旧请求作废 */
    private var runId = 0
    private var inflightSession: Session? = null
    private var inflightMsg: Msg? = null
        private set
    /** 流式期间靠版本号触发重组，配合 40ms 合批，避免每个 token 都重组 */
    var version by mutableIntStateOf(0)
        private set

    private var pendingBump = false

    init {
        sessions.addAll(store.loadAll())
        // 清掉历史遗留的重复空会话，只保留最新那个
        val empties = sessions.filter { it.messages.isEmpty() }
        if (empties.size > 1) {
            empties.drop(1).forEach {
                store.delete(it.id)
                sessions.removeAll { s -> s.id == it.id }
            }
        }
        if (sessions.isEmpty()) sessions.add(Session().also { store.save(it) })
        currentId = sessions.first().id
    }

    val current: Session?
        get() = sessions.firstOrNull { it.id == currentId }

    private fun bump() {
        if (pendingBump) return
        pendingBump = true
        main.postDelayed({ pendingBump = false; version++ }, 40)
    }

    fun newSession() {
        // 当前对话还是空的就留在原地，别点几次堆几个空会话
        current?.let {
            if (it.messages.isEmpty()) {
                version++
                return
            }
        }
        val s = Session()
        store.save(s)
        sessions.add(0, s)
        currentId = s.id
        version++
    }

    fun select(id: String) {
        val prev = sessions.firstOrNull { it.id == currentId }
        // 切走时丢掉空的新会话，避免列表里堆一串“新对话”
        if (prev != null && prev.id != id && prev.messages.isEmpty() && sessions.size > 1) {
            store.delete(prev.id)
            sessions.remove(prev)
        }
        currentId = id
        version++
    }

    fun delete(id: String) {
        if (streaming && currentId == id) stop()
        store.delete(id)
        sessions.removeAll { it.id == id }
        if (sessions.isEmpty()) sessions.add(Session().also { store.save(it) })
        if (currentId == id) currentId = sessions.first().id
        version++
    }

    fun rename(id: String, title: String) {
        sessions.firstOrNull { it.id == id }?.let {
            it.title = title.ifBlank { "新对话" }
            store.save(it)
            version++
        }
    }

    fun togglePin(id: String) {
        sessions.firstOrNull { it.id == id }?.let {
            it.pinned = !it.pinned
            store.save(it)
            resort()
            version++
        }
    }

    fun pinAll(ids: List<String>, pinned: Boolean) {
        ids.forEach { id ->
            sessions.firstOrNull { it.id == id }?.let {
                it.pinned = pinned
                store.save(it)
            }
        }
        resort()
        version++
    }

    fun deleteAll(ids: List<String>) {
        ids.forEach { store.delete(it) }
        sessions.removeAll { it.id in ids }
        if (sessions.isEmpty()) sessions.add(Session().also { store.save(it) })
        if (currentId in ids) currentId = sessions.first().id
        version++
    }

    fun clearAllSessions() {
        sessions.forEach { store.delete(it.id) }
        sessions.clear()
        val fresh = Session()
        store.save(fresh)
        sessions.add(fresh)
        currentId = fresh.id
        version++
    }

    fun importSessions(list: List<Session>) {
        if (list.isEmpty()) return
        val backup = sessions.toList()
        // 先写新会话；任何一步失败都保持旧数据不动
        val ok = runCatching { list.forEach { store.save(it) } }.isSuccess
        if (!ok) return
        backup.forEach { store.delete(it.id) }
        sessions.clear()
        sessions.addAll(list.sortedWith(compareByDescending<Session> { it.pinned }.thenByDescending { it.updated }))
        currentId = sessions.firstOrNull()?.id
        version++
    }

    private fun resort() {
        val sorted = sessions.sortedWith(
            compareByDescending<Session> { it.pinned }.thenByDescending { it.updated }
        )
        sessions.clear()
        sessions.addAll(sorted)
    }

    fun toggleWebSearch() {
        settings = settings.copy(webSearch = !settings.webSearch)
        settingsStore.save(settings)
        version++
    }

    fun toggleThinking() {
        settings = settings.copy(deepThinking = !settings.deepThinking)
        settingsStore.save(settings)
        version++
    }

    fun saveSettings(s: AppSettings) {
        settings = s
        settingsStore.save(s)
        version++
    }

    fun stop() {
        // runId++ 让「已排队但还没发出」的请求作废，否则点了停止请求照样发出去
        runId++
        client.cancel()
        // 用 inflight 定位，而不是 current：用户可能已经切到别的会话了
        inflightMsg?.let { m ->
            if (m.status == "streaming") m.status = "stopped"
        }
        inflightSession?.let { runCatching { store.save(it) } }
        inflightMsg = null
        inflightSession = null
        streaming = false
        version++
    }

    fun send(text: String, attachments: List<Attachment> = emptyList()) {
        val q = text.trim()
        if ((q.isEmpty() && attachments.isEmpty()) || streaming) return
        val s = current ?: return

        val userMsg = Msg(
            role = "user",
            blocks = mutableListOf<Block>().also { if (q.isNotBlank()) it.add(Block.Text(q)) },
            attachments = attachments.toMutableList(),
        )
        val assistant = Msg(role = "assistant", status = "streaming")
        s.messages.add(userMsg)
        s.messages.add(assistant)
        streaming = true
        version++
        // 先落盘一次：即使用户在生成过程中杀掉进程，提问和占位回答也还在
        store.save(s)

        val history = s.messages.dropLast(2).toList()
        val myRun = ++runId
        inflightSession = s
        inflightMsg = assistant

        Thread {
            val (uploaded, failed) = uploadAll(attachments)
            main.post {
                // 排队期间用户可能已经点了停止或发了新消息，这次就别发了
                if (myRun != runId) return@post
                // 回填 file_id，便于多轮回传
                uploaded.forEachIndexed { i, a ->
                    if (i < userMsg.attachments.size) userMsg.attachments[i] = a
                }
                if (failed > 0) {
                    attachError = if (uploaded.none { it.fileId != null } && attachments.isNotEmpty()) {
                        "附件上传失败，本条消息只发送了文字"
                    } else {
                        "有 $failed 个附件上传失败，已跳过"
                    }
                }
                client.streamWithTools(
                    settings = settings,
                    history = history,
                    question = q,
                    attachments = uploaded,
                    into = assistant,
                    memoEnabled = settings.memoEnabled,
                    memoStore = memoStore,
                    web = webClient,
                    onUpdate = { main.post { bump() } },
                    onTitle = { t ->
                        main.post {
                            if (s.title == "新对话" || s.title.isBlank()) {
                                s.title = t
                                store.save(s)
                                version++
                            }
                        }
                    },
                    onError = { e ->
                        main.post {
                            assistant.status = "error"
                            assistant.error = e
                            streaming = false
                            inflightMsg = null
                            inflightSession = null
                            s.updated = System.currentTimeMillis()
                            store.save(s)
                            version++
                        }
                    },
                    onDone = {
                        main.post {
                            streaming = false
                            inflightMsg = null
                            inflightSession = null
                            val done = assistant.blockSnapshot()
                            val u = assistant.usage ?: Usage()
                            usageStore.add(
                                input = u.input,
                                output = u.output,
                                cacheRead = u.cacheRead,
                                searches = done.count { it is Block.Search },
                                fetches = done.count { it is Block.Fetch },
                            )
                            s.updated = System.currentTimeMillis()
                            store.save(s)
                            version++
                        }
                    },
                )
            }
        }.start()
    }

    /** 把还没上传的附件传到 Files API；返回成功列表和失败数量。 */
    private fun uploadAll(list: List<Attachment>): Pair<List<Attachment>, Int> {
        if (list.isEmpty()) return list to 0
        val ctx = getApplication<Application>()
        var failed = 0
        val out = list.map { a ->
            if (!a.fileId.isNullOrBlank()) return@map a
            val bytes = runCatching {
                ctx.contentResolver.openInputStream(android.net.Uri.parse(a.uri))?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                failed++
                return@map a
            }
            val id = client.uploadFile(settings, bytes, a.name, a.mime, a.isImage)
            if (id != null) a.copy(fileId = id) else {
                failed++
                a
            }
        }
        return out to failed
    }

    /** 仅供 UI 快照测试注入状态，不参与业务逻辑 */
    fun seedForPreview(title: String, messages: List<Msg>) {
        val s = current ?: return
        s.title = title
        s.messages.clear()
        s.messages.addAll(messages)
        version++
    }

    /** 重新生成最后一条回答：旧内容存入版本快照，然后原地重跑。 */
    fun retryLast() {
        val s = current ?: return
        if (streaming) return
        val assistantIdx = s.messages.indexOfLast { it.role == "assistant" }
        val userIdx = s.messages.indexOfLast { it.role == "user" }
        if (assistantIdx < 0 || userIdx < 0 || userIdx > assistantIdx) return

        val assistant = s.messages[assistantIdx]
        val user = s.messages[userIdx]
        val q = user.plainText()
        val atts = user.attachments.toList()

        if (assistant.blocks.isNotEmpty()) {
            assistant.versions.clear()
            assistant.versions.add(assistant.blocks.toList())
            assistant.versions.add(mutableListOf())
            assistant.versionIndex = 1
        }
        assistant.blocks.clear()
        assistant.status = "streaming"
        assistant.error = null
        streaming = true
        version++

        val history = s.messages.take(userIdx)
        val myRun = ++runId
        inflightSession = s
        inflightMsg = assistant

        Thread {
            val (uploaded, failed) = uploadAll(atts)
            main.post {
                if (myRun != runId) return@post
                if (failed > 0) attachError = "有 $failed 个附件上传失败，已跳过"
                client.streamWithTools(
                    settings = settings,
                    history = history,
                    question = q,
                    attachments = uploaded,
                    into = assistant,
                    memoEnabled = settings.memoEnabled,
                    memoStore = memoStore,
                    web = webClient,
                    onUpdate = { main.post { bump() } },
                    onTitle = { },
                    onError = { e ->
                        main.post {
                            assistant.status = "error"
                            assistant.error = e
                            streaming = false
                            inflightMsg = null
                            inflightSession = null
                            store.save(s)
                            version++
                        }
                    },
                    onDone = {
                        main.post {
                            streaming = false
                            inflightMsg = null
                            inflightSession = null
                            val done = assistant.blockSnapshot()
                            val u = assistant.usage ?: Usage()
                            usageStore.add(
                                input = u.input,
                                output = u.output,
                                cacheRead = u.cacheRead,
                                searches = done.count { it is Block.Search },
                                fetches = done.count { it is Block.Fetch },
                            )
                            s.updated = System.currentTimeMillis()
                            store.save(s)
                            version++
                        }
                    },
                )
            }
        }.start()
    }

    /** 在已产生的回答版本之间切换。delta = -1 上一个，+1 下一个。 */
    fun switchVersion(msgId: String, delta: Int) {
        val s = current ?: return
        val m = s.messages.firstOrNull { it.id == msgId } ?: return
        if (m.versions.isEmpty()) return
        // 当前内容写回快照，再切到目标
        m.versions[m.versionIndex] = m.blocks.toList()
        val total = m.versions.size
        val next = ((m.versionIndex + delta) % total + total) % total
        m.blocks.clear()
        m.blocks.addAll(m.versions[next])
        m.versionIndex = next
        version++
    }
}
