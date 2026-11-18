package com.yg.dschat

import android.content.Context
import java.util.Calendar
import java.util.Locale

/**
 * Token 用量与花费统计。
 *
 * 价格取自官方定价（元 / 百万 tokens，deepseek-flash）：
 *  输入（缓存命中）高峰 0.04 / 空闲 0.02
 *  输入（未命中）  高峰 2    / 空闲 1
 *  输出            高峰 8    / 空闲 4
 * 高峰时段：北京时间周一至周五 9:00-12:00、14:00-18:00（不含法定节假日）。
 * 这里不判断节假日，只要落在工作日的这两个区间就按高峰算，属于偏保守的估算。
 */
class UsageStore(context: Context) {

    /**
     * input  = 未命中的输入 token（Anthropic 的 input_tokens 不含缓存部分）
     * cacheRead = 命中缓存的输入 token
     */
    data class Stat(
        val input: Long = 0,
        val cacheRead: Long = 0,
        val output: Long = 0,
        val searches: Long = 0,
        val fetches: Long = 0,
        val cost: Double = 0.0,
    ) {
        val totalInput: Long get() = input + cacheRead
        val total: Long get() = totalInput + output
        val cacheHitRate: Double
            get() = if (totalInput <= 0) 0.0 else cacheRead.toDouble() / totalInput
    }

    private val prefs = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)

    fun total(): Stat = Stat(
        input = prefs.getLong("t_input", 0),
        cacheRead = prefs.getLong("t_cache", 0),
        output = prefs.getLong("t_output", 0),
        searches = prefs.getLong("t_search", 0),
        fetches = prefs.getLong("t_fetch", 0),
        cost = prefs.getLong("t_cost_micro", 0) / 1_000_000.0,
    )

    fun today(): Stat {
        rollDay()
        return Stat(
            input = prefs.getLong("d_input", 0),
            cacheRead = prefs.getLong("d_cache", 0),
            output = prefs.getLong("d_output", 0),
            searches = prefs.getLong("d_search", 0),
            fetches = prefs.getLong("d_fetch", 0),
            cost = prefs.getLong("d_cost_micro", 0) / 1_000_000.0,
        )
    }

    fun add(input: Int, output: Int, cacheRead: Int, searches: Int = 0, fetches: Int = 0) {
        if (input <= 0 && output <= 0 && searches <= 0 && fetches <= 0) return
        rollDay()
        val cost = estimateCost(input, output, cacheRead, searches, fetches)
        val micro = (cost * 1_000_000).toLong()
        prefs.edit()
            .putLong("t_input", prefs.getLong("t_input", 0) + input)
            .putLong("t_cache", prefs.getLong("t_cache", 0) + cacheRead)
            .putLong("t_output", prefs.getLong("t_output", 0) + output)
            .putLong("t_search", prefs.getLong("t_search", 0) + searches)
            .putLong("t_fetch", prefs.getLong("t_fetch", 0) + fetches)
            .putLong("t_cost_micro", prefs.getLong("t_cost_micro", 0) + micro)
            .putLong("d_input", prefs.getLong("d_input", 0) + input)
            .putLong("d_cache", prefs.getLong("d_cache", 0) + cacheRead)
            .putLong("d_output", prefs.getLong("d_output", 0) + output)
            .putLong("d_search", prefs.getLong("d_search", 0) + searches)
            .putLong("d_fetch", prefs.getLong("d_fetch", 0) + fetches)
            .putLong("d_cost_micro", prefs.getLong("d_cost_micro", 0) + micro)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    /** 跨天时清零「今日」，累计值不动 */
    private fun rollDay() {
        val today = todayKey()
        if (prefs.getString("d_day", "") != today) {
            prefs.edit()
                .putString("d_day", today)
                .putLong("d_input", 0)
                .putLong("d_cache", 0)
                .putLong("d_output", 0)
                .putLong("d_search", 0)
                .putLong("d_fetch", 0)
                .putLong("d_cost_micro", 0)
                .apply()
        }
    }

    private fun todayKey(): String {
        val c = Calendar.getInstance()
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
        )
    }

    companion object {
        /**
         * 官方规则：自北京时间 2026-11-18 12:00 起，模型调用触发的服务端搜索与网页抓取不再单独计费，
         * 只按 token 计费。这个时间点之前的调用按 ¥5 / 1000 次估算。
         */
        val TOOL_FREE_SINCE: Long =
            runCatching {
                java.time.OffsetDateTime.parse("2026-11-18T12:00:00+08:00").toInstant().toEpochMilli()
            }.getOrDefault(Long.MAX_VALUE)

        fun isPeakNow(now: Calendar = Calendar.getInstance()): Boolean {
            val dow = now.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false
            val h = now.get(Calendar.HOUR_OF_DAY)
            return h in 9..11 || h in 14..17
        }

        /**
         * 元。Anthropic 语义下 input_tokens 只统计未命中部分，
         * cache_read_input_tokens 是命中部分，两者相加才是总输入，不能相减。
         */
        fun estimateCost(
            input: Int,
            output: Int,
            cacheRead: Int,
            searches: Int = 0,
            fetches: Int = 0,
            peak: Boolean = isPeakNow(),
            now: Long = System.currentTimeMillis(),
        ): Double {
            val pCache = if (peak) 0.04 else 0.02
            val pIn = if (peak) 2.0 else 1.0
            val pOut = if (peak) 8.0 else 4.0
            val miss = input.coerceAtLeast(0)
            val hit = cacheRead.coerceAtLeast(0)
            val modelCost = (miss * pIn + hit * pCache + output * pOut) / 1_000_000.0
            val toolCalls = searches.coerceAtLeast(0) + fetches.coerceAtLeast(0)
            val toolCost = if (now < TOOL_FREE_SINCE) toolCalls * 5.0 / 1000.0 else 0.0
            return modelCost + toolCost
        }

        fun formatTokens(n: Long): String = when {
            n >= 1_000_000 -> String.format(Locale.US, "%.2fM", n / 1_000_000.0)
            n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
            else -> n.toString()
        }

        fun formatCost(yuan: Double): String = when {
            yuan <= 0.0 -> "¥0"
            yuan < 0.01 -> "¥" + String.format(Locale.US, "%.4f", yuan)
            else -> "¥" + String.format(Locale.US, "%.2f", yuan)
        }
    }
}
