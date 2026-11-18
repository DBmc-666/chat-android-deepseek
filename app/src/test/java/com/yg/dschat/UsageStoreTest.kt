package com.yg.dschat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageStoreTest {

    private fun store(): UsageStore =
        UsageStore(ApplicationProvider.getApplicationContext<Context>())

    // ---- 价格估算 ----

    @Test
    fun free_period_price_for_flash() {
        // 空闲：未命中输入 ¥1/M、输出 ¥4/M
        val cost = UsageStore.estimateCost(input = 1_000_000, output = 1_000_000, cacheRead = 0, peak = false)
        assertEquals(5.0, cost, 1e-9)
    }

    @Test
    fun peak_price_is_double() {
        val cost = UsageStore.estimateCost(input = 1_000_000, output = 1_000_000, cacheRead = 0, peak = true)
        assertEquals(10.0, cost, 1e-9)
    }

    @Test
    fun input_and_cache_are_additive_not_subtractive() {
        // Anthropic 语义：input_tokens 是未命中部分，cache_read 是命中部分，两者相加才是总输入
        val cost = UsageStore.estimateCost(input = 1_000_000, output = 0, cacheRead = 1_000_000, peak = false)
        assertEquals(1.0 + 0.02, cost, 1e-9)
    }

    @Test
    fun pure_cache_hit_is_cheap() {
        // 全部命中：input=0、cacheRead=1M，只花缓存价
        val cost = UsageStore.estimateCost(input = 0, output = 0, cacheRead = 1_000_000, peak = false)
        assertEquals(0.02, cost, 1e-9)
    }

    @Test
    fun pure_miss_is_full_price() {
        val cost = UsageStore.estimateCost(input = 1_000_000, output = 0, cacheRead = 0, peak = false)
        assertEquals(1.0, cost, 1e-9)
    }

    @Test
    fun negative_input_does_not_produce_negative_cost() {
        val cost = UsageStore.estimateCost(input = -100, output = 0, cacheRead = 0, peak = false)
        assertTrue(cost >= 0.0)
    }

    @Test
    fun peak_window_detection() {
        fun at(dayOfWeek: Int, hour: Int): Boolean {
            val c = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                set(Calendar.HOUR_OF_DAY, hour)
            }
            return UsageStore.isPeakNow(c)
        }
        assertTrue(at(Calendar.MONDAY, 10))
        assertTrue(at(Calendar.FRIDAY, 15))
        assertTrue(!at(Calendar.MONDAY, 8))
        assertTrue(!at(Calendar.MONDAY, 13))
        assertTrue(!at(Calendar.SATURDAY, 10))
        assertTrue(!at(Calendar.SUNDAY, 15))
    }

    // ---- 累计 ----

    @Test
    fun add_accumulates_into_total_and_today() {
        val s = store()
        s.reset()
        s.add(input = 1000, output = 500, cacheRead = 200)
        s.add(input = 2000, output = 1000, cacheRead = 0)

        val total = s.total()
        assertEquals(3000L, total.input)
        assertEquals(1500L, total.output)
        assertEquals(200L, total.cacheRead)
        assertEquals(3000L + 200L + 1500L, total.total)

        val today = s.today()
        assertEquals(3000L, today.input)
        assertEquals(1500L, today.output)
        assertTrue(today.cost > 0.0)
    }

    @Test
    fun empty_add_is_ignored() {
        val s = store()
        s.reset()
        s.add(input = 0, output = 0, cacheRead = 0)
        assertEquals(0L, s.total().input)
        assertEquals(0L, s.total().output)
    }

    @Test
    fun reset_clears_everything() {
        val s = store()
        s.add(input = 5000, output = 3000, cacheRead = 0)
        s.reset()
        assertEquals(0L, s.total().input)
        assertEquals(0.0, s.today().cost, 1e-9)
    }

    @Test
    fun cache_hit_rate_is_computed() {
        val s = store()
        s.reset()
        s.add(input = 750, output = 100, cacheRead = 250)
        // 命中率 = 命中 / 总输入 = 250 / 1000
        assertEquals(0.25, s.total().cacheHitRate, 1e-9)
    }

    @Test
    fun touch_tool_calls_are_free_after_nov_18_2026() {
        val afterFree = UsageStore.TOOL_FREE_SINCE + 60_000
        val cost = UsageStore.estimateCost(
            input = 0, output = 0, cacheRead = 0,
            searches = 10, fetches = 10,
            peak = false, now = afterFree,
        )
        assertEquals(0.0, cost, 1e-9)
    }

    @Test
    fun tool_calls_billed_before_nov_18_2026() {
        val beforeFree = UsageStore.TOOL_FREE_SINCE - 60_000
        val cost = UsageStore.estimateCost(
            input = 0, output = 0, cacheRead = 0,
            searches = 500, fetches = 500,
            peak = false, now = beforeFree,
        )
        // 1000 次 × ¥5/1000 = ¥5
        assertEquals(5.0, cost, 1e-9)
    }

    @Test
    fun search_and_fetch_counts_are_stored() {
        val s = store()
        s.reset()
        s.add(input = 100, output = 50, cacheRead = 0, searches = 3, fetches = 2)
        assertEquals(3L, s.total().searches)
        assertEquals(2L, s.total().fetches)
        assertEquals(3L, s.today().searches)
    }

    @Test
    fun formatting() {
        assertEquals("999", UsageStore.formatTokens(999))
        assertEquals("1.5k", UsageStore.formatTokens(1500))
        assertEquals("2.00M", UsageStore.formatTokens(2_000_000))
        assertEquals("¥0", UsageStore.formatCost(0.0))
        assertEquals("¥2.00", UsageStore.formatCost(2.0))
    }
}
