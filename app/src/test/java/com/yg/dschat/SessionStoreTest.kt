package com.yg.dschat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStoreTest {

    private fun store(): SessionStore =
        SessionStore(ApplicationProvider.getApplicationContext<Context>())

    private fun demoSession(title: String = "测试会话"): Session = Session(
        title = title,
        messages = mutableListOf(
            Msg(role = "user", blocks = mutableListOf(Block.Text("你好"))),
            Msg(role = "assistant", blocks = mutableListOf(Block.Text("你好，有什么可以帮你？"))),
        ),
    )

    @Test
    fun save_then_load_returns_session() {
        val s = store()
        val session = demoSession()
        s.save(session)

        val loaded = s.loadAll()
        assertEquals(1, loaded.size)
        assertEquals(session.id, loaded[0].id)
        assertEquals("测试会话", loaded[0].title)
        assertEquals(2, loaded[0].messages.size)
        assertEquals("你好", loaded[0].messages[0].plainText())
    }

    @Test
    fun repeated_save_does_not_duplicate_index_entries() {
        val s = store()
        val session = demoSession()
        s.save(session)
        s.save(session)
        s.save(session)

        assertEquals(1, s.loadAll().size)
    }

    @Test
    fun multiple_sessions_all_survive() {
        val s = store()
        val a = demoSession("会话 A")
        val b = demoSession("会话 B")
        s.save(a)
        s.save(b)

        val loaded = s.loadAll()
        assertEquals(2, loaded.size)
        assertEquals(setOf("会话 A", "会话 B"), loaded.map { it.title }.toSet())
    }

    @Test
    fun delete_removes_from_index() {
        val s = store()
        val a = demoSession("会话 A")
        val b = demoSession("会话 B")
        s.save(a)
        s.save(b)
        s.delete(a.id)

        val loaded = s.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("会话 B", loaded[0].title)
    }

    @Test
    fun message_versions_survive_roundtrip() {
        val s = store()
        val session = demoSession()
        session.messages[1].versions.add(listOf(Block.Text("旧版本回答")))
        session.messages[1].versions.add(listOf(Block.Text("新版本回答")))
        session.messages[1].versionIndex = 1
        s.save(session)

        val loaded = s.loadAll()
        val m = loaded[0].messages[1]
        assertEquals(2, m.versions.size)
        assertEquals(1, m.versionIndex)
        assertTrue(m.versions[0].filterIsInstance<Block.Text>().first().text.contains("旧版本"))
    }

    @Test
    fun thinking_and_search_blocks_survive_roundtrip() {
        val s = store()
        val session = Session(
            title = "带工具",
            messages = mutableListOf(
                Msg(
                    role = "assistant",
                    blocks = mutableListOf(
                        Block.Thinking("思考内容", "", startedAt = 1000L, endedAt = 4000L),
                        Block.Search("toolu_9", mutableListOf(SearchHit("标题", "https://a.com", "A", "摘要"))),
                        Block.Text("正文"),
                    ),
                ),
            ),
        )
        s.save(session)

        val m = s.loadAll()[0].messages[0]
        assertEquals(3, m.blocks.size)
        assertTrue(m.blocks[0] is Block.Thinking)
        assertEquals("思考内容", (m.blocks[0] as Block.Thinking).text)
        assertEquals(1, (m.blocks[1] as Block.Search).results.size)
        assertEquals("正文", (m.blocks[2] as Block.Text).text)
    }
}
