package com.yg.dschat

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 历史构造是最容易写错又最难发现的部分：
 * 传错了模型会“忘记”上一轮，传多了会 400。这里把协议规则钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuildMessagesTest {

    private fun settings(reminder: Boolean = false) = AppSettings(
        apiKey = "sk-test",
        reminderOn = reminder,
    )

    private fun user(text: String) = Msg(role = "user", blocks = mutableListOf(Block.Text(text)))
    private fun assistant(text: String, inputTokens: Int = 0) =
        Msg(role = "assistant", blocks = mutableListOf(Block.Text(text))).also {
            if (inputTokens > 0) it.usage = com.yg.dschat.Usage(input = inputTokens)
        }

    private fun roles(arr: JSONArray): List<String> =
        (0 until arr.length()).map { arr.getJSONObject(it).optString("role") }

    @Test
    fun pairs_are_mapped_user_then_assistant() {
        val history = listOf(user("你好"), assistant("你好呀"))
        val out = AnthropicClient.buildMessages(history, "继续", emptyList(), settings())
        assertEquals(listOf("user", "assistant", "user"), roles(out))
    }

    @Test
    fun refusal_round_is_dropped() {
        val refused = Msg(
            role = "assistant",
            blocks = mutableListOf(Block.Text("我不能回答")),
            status = "refusal",
        )
        val out = AnthropicClient.buildMessages(
            listOf(user("敏感问题"), refused),
            "换个问题",
            emptyList(),
            settings(),
        )
        // 被拒绝的整轮不进上下文，只剩当前提问
        assertEquals(listOf("user"), roles(out))
    }

    @Test
    fun empty_assistant_is_skipped() {
        val empty = Msg(role = "assistant", blocks = mutableListOf(), status = "stopped")
        val out = AnthropicClient.buildMessages(
            listOf(user("在吗"), empty),
            "再问一次",
            emptyList(),
            settings(),
        )
        assertEquals(listOf("user"), roles(out))
    }

    @Test
    fun thinking_blocks_are_not_resent() {
        val withThinking = Msg(
            role = "assistant",
            blocks = mutableListOf(
                Block.Thinking("内部推理", "", startedAt = 1L, endedAt = 2L),
                Block.Text("最终回答"),
            ),
        )
        val out = AnthropicClient.buildMessages(
            listOf(user("问题"), withThinking),
            "下一个",
            emptyList(),
            settings(),
        )
        val assistantContent = out.getJSONObject(1).optString("content")
        assertEquals("最终回答", assistantContent)
        assertTrue(!assistantContent.contains("内部推理"))
    }

    @Test
    fun reminder_inserted_once_and_before_user() {
        val out = AnthropicClient.buildMessages(
            listOf(user("第一问"), assistant("第一答")),
            "第二问",
            emptyList(),
            settings(reminder = true),
        )
        val r = roles(out)
        assertEquals(1, r.count { it == "latest_reminder" })
        val at = r.indexOf("latest_reminder")
        assertEquals("user", r[at + 1])
    }

    @Test
    fun reminder_has_five_fields() {
        val out = AnthropicClient.buildMessages(emptyList(), "你好", emptyList(), settings(reminder = true))
        val reminder = out.getJSONObject(0).optString("content")
        assertEquals(5, reminder.split(",").size)
        assertTrue(reminder.contains("App"))
    }

    @Test
    fun plain_question_is_a_string() {
        val out = AnthropicClient.buildMessages(emptyList(), "纯文本问题", emptyList(), settings())
        val last = out.getJSONObject(out.length() - 1)
        assertTrue(last.opt("content") is String)
    }

    @Test
    fun attachment_turns_content_into_block_array() {
        val att = Attachment("content://x/1.jpg", "cat.jpg", "image/jpeg", true, "file-api-1")
        val out = AnthropicClient.buildMessages(emptyList(), "看图", listOf(att), settings())
        val content = out.getJSONObject(out.length() - 1).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).optString("type"))
        assertEquals(
            "file-api-1",
            content.getJSONObject(0).getJSONObject("source").optString("file_id"),
        )
        assertEquals("text", content.getJSONObject(1).optString("type"))
    }

    @Test
    fun attachment_without_file_id_is_ignored() {
        val att = Attachment("content://x/1.jpg", "cat.jpg", "image/jpeg", true, null)
        val out = AnthropicClient.buildMessages(emptyList(), "看图", listOf(att), settings())
        assertTrue(out.getJSONObject(out.length() - 1).opt("content") is String)
    }

    @Test
    fun read_link_result_is_resent_with_signature() {
        val fetched = Msg(
            role = "assistant",
            blocks = mutableListOf(
                Block.Fetch("toolu_1", "https://a.com", "标题", "", "sig-abc"),
                Block.Text("读完了"),
            ),
        )
        val out = AnthropicClient.buildMessages(
            listOf(user("看看这个链接"), fetched),
            "总结一下",
            emptyList(),
            settings(),
        )
        val content = out.getJSONObject(1).getJSONArray("content")
        assertEquals("server_tool_use", content.getJSONObject(0).optString("type"))
        assertEquals("web_fetch_tool_result", content.getJSONObject(1).optString("type"))
        assertEquals("sig-abc", content.getJSONObject(1).optString("signature"))
        assertEquals("text", content.getJSONObject(2).optString("type"))
    }

    @Test
    fun search_results_are_not_resent() {
        val searched = Msg(
            role = "assistant",
            blocks = mutableListOf(
                Block.Search("toolu_1", mutableListOf(SearchHit("t", "https://a.com", "A", "s"))),
                Block.Text("答案"),
            ),
        )
        val out = AnthropicClient.buildMessages(
            listOf(user("搜索一下"), searched),
            "再问",
            emptyList(),
            settings(),
        )
        assertTrue(out.getJSONObject(1).opt("content") is String)
        assertEquals("答案", out.getJSONObject(1).optString("content"))
    }

    @Test
    fun dangling_user_without_answer_is_dropped() {
        // 只有 user 没有 assistant（生成失败/中断），不能成对回传
        val out = AnthropicClient.buildMessages(
            listOf(user("上一次没答完")),
            "重新问",
            emptyList(),
            settings(),
        )
        assertEquals(listOf("user"), roles(out))
        assertEquals("重新问", out.getJSONObject(0).optString("content"))
    }

    @Test
    fun consecutive_questions_all_sent() {
        val out = AnthropicClient.buildMessages(
            listOf(user("一问"), assistant("一答"), user("二问"), assistant("二答")),
            "三问",
            emptyList(),
            settings(),
        )
        assertEquals(5, out.length())
        assertNotEquals(0, out.length())
    }
    // ---------------- 上下文压缩（按真实 input token 判断） ----------------

    /** 造一段历史：每轮回答 300 字，最后一条带指定的 input_tokens */
    private fun longHistory(n: Int, lastInput: Int): List<Msg> = (0 until n).flatMap { i ->
        listOf(
            user("问题 $i"),
            assistant("答".repeat(300), inputTokens = if (i == n - 1) lastInput else 100),
        )
    }

    @Test
    fun under_token_threshold_keeps_all_turns() {
        val s = AppSettings(apiKey = "sk-test", reminderOn = false, compressThreshold = 10_000)
        val out = AnthropicClient.buildMessages(longHistory(3, lastInput = 500), "继续", emptyList(), s)
        assertEquals(7, out.length())
        assertTrue(!out.toString().contains("已省略"))
    }

    @Test
    fun over_token_threshold_drops_whole_oldest_turns() {
        val s = AppSettings(apiKey = "sk-test", reminderOn = false, compressThreshold = 1_000)
        val out = AnthropicClient.buildMessages(longHistory(10, lastInput = 10_000), "继续", emptyList(), s)
        val text = out.toString()
        assertTrue("应注明省略了几轮", text.contains("已省略"))
        assertTrue("应保留部分历史", out.length() > 3)
        assertTrue("不能全部发出", out.length() < 21)
        // 关键：保留的回答必须完整，不能被截断成残句
        assertTrue("回答内容不应被截断", text.contains("答".repeat(300)))
    }

    @Test
    fun disabled_compression_ignores_tokens() {
        val s = AppSettings(
            apiKey = "sk-test",
            reminderOn = false,
            compressEnabled = false,
            compressThreshold = 100,
        )
        val out = AnthropicClient.buildMessages(longHistory(5, lastInput = 99_999), "继续", emptyList(), s)
        assertEquals(11, out.length())
        assertTrue(!out.toString().contains("已省略"))
    }

    @Test
    fun last_input_tokens_reads_latest_usage() {
        val h = longHistory(3, lastInput = 4321)
        assertEquals(4321, AnthropicClient.lastInputTokens(h))
    }
}
