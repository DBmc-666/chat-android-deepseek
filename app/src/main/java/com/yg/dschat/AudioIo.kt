package com.yg.dschat

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 语音能力：AudioRecord 采 16k/16bit/mono PCM → 包 WAV → /v1/tools/asr；
 * 朗读走 /v1/tools/tts（wav）→ 落盘 → MediaPlayer。
 * 这两个端点用 Authorization: Bearer，和对话的 x-api-key 不同。
 */
object AudioIo {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()

    private const val SAMPLE_RATE = 16000

    /** ASR 单次上限 120 秒，留 10 秒余量给编码和上传 */
    private const val MAX_RECORD_MS = 110_000L

    private var recStartAt = 0L

    /** 本次录音是否因为超时被自动结束 */
    @Volatile
    var timedOut = false
        private set

    /** 超时回调，已在主线程 */
    var onTimeout: (() -> Unit)? = null

    // ---------------- 录音 ----------------

    private var record: AudioRecord? = null
    private var recThread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    @Volatile
    private var recording = false

    fun startRecording(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf, SAMPLE_RATE)
        val rec = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            runCatching { rec.release() }
            return false
        }
        synchronized(pcm) { pcm.reset() }
        return runCatching {
            rec.startRecording()
            record = rec
            recording = true
            timedOut = false
            recStartAt = System.currentTimeMillis()
            recThread = Thread {
                val buf = ByteArray(4096)
                while (recording) {
                    val n = runCatching { rec.read(buf, 0, buf.size) }.getOrDefault(-1)
                    if (n > 0) {
                        synchronized(pcm) { pcm.write(buf, 0, n) }
                    } else {
                        // read 返回 0/负值不能让循环空转，否则满核跑
                        runCatching { Thread.sleep(5) }
                    }
                    if (System.currentTimeMillis() - recStartAt > MAX_RECORD_MS) {
                        timedOut = true
                        recording = false
                        Handler(Looper.getMainLooper()).post { onTimeout?.invoke() }
                    }
                }
            }.also { it.start() }
            true
        }.getOrDefault(false)
    }

    fun stopRecording(): ByteArray? {
        if (!recording) return null
        recording = false
        runCatching { recThread?.join(400) }
        runCatching {
            record?.stop()
            record?.release()
        }
        record = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        if (data.isEmpty()) return null
        return wav(data, SAMPLE_RATE)
    }

    private fun wav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val channels = 1
        val bits = 16
        val byteRate = sampleRate * channels * bits / 8

        fun le32(v: Int) = byteArrayOf(
            (v and 0xff).toByte(),
            ((v shr 8) and 0xff).toByte(),
            ((v shr 16) and 0xff).toByte(),
            ((v shr 24) and 0xff).toByte(),
        )

        fun le16(v: Int) = byteArrayOf((v and 0xff).toByte(), ((v shr 8) and 0xff).toByte())

        out.write("RIFF".toByteArray())
        out.write(le32(36 + pcmData.size))
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        out.write(le32(16))
        out.write(le16(1))
        out.write(le16(channels))
        out.write(le32(sampleRate))
        out.write(le32(byteRate))
        out.write(le16(channels * bits / 8))
        out.write(le16(bits))
        out.write("data".toByteArray())
        out.write(le32(pcmData.size))
        out.write(pcmData)
        return out.toByteArray()
    }

    // ---------------- 语音识别 ----------------

    fun transcribe(settings: AppSettings, wav: ByteArray): String? {
        val base = settings.baseUrl.trim().trimEnd('/')
        if (base.isBlank() || settings.apiKey.isBlank()) return null
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "speech.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("$base/v1/tools/asr")
            .addHeader("Authorization", "Bearer ${settings.apiKey}")
            .post(body)
            .build()
        return runCatching {
            http.newCall(request).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) return null
                JSONObject(text).optString("text").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    // 朗读（TTS）功能已移除：本客户端只做输入侧语音，不提供朗读
}
