package com.saregama.android.ffmpeg

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun interface FFmpegLogCallback {
    fun onLog(level: Int, message: String)
}

data class MediaInfo(
    val durationMs: Long,
    val bitRate: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String?,
    val audioCodec: String?
)

object FFmpegNative {

    // libavutil log levels
    const val LOG_QUIET   = -8
    const val LOG_PANIC   = 0
    const val LOG_FATAL   = 8
    const val LOG_ERROR   = 16
    const val LOG_WARNING = 24
    const val LOG_INFO    = 32
    const val LOG_VERBOSE = 40
    const val LOG_DEBUG   = 48
    const val LOG_TRACE   = 56

    init {
        System.loadLibrary("avutil")
        System.loadLibrary("swresample")
        System.loadLibrary("swscale")
        System.loadLibrary("avcodec")
        System.loadLibrary("avformat")
        System.loadLibrary("avfilter")
        System.loadLibrary("ffmpeg_wrapper")
    }

    // ---------- Native JNI functions ----------

    external fun getVersion(): String
    external fun runCommand(args: Array<String>): Int

    private external fun nativeGetMediaInfo(filePath: String): String?

    private external fun nativeSetLogCallback(callback: FFmpegLogCallback?)
    private external fun nativeSetLogLevel(level: Int)
    private external fun nativeRequestCancel()

    // ---------- Public sync helpers ----------

    fun setLogCallback(callback: FFmpegLogCallback?, level: Int = LOG_INFO) {
        nativeSetLogLevel(level)
        nativeSetLogCallback(callback)
    }

    fun getMediaInfo(filePath: String): MediaInfo? {
        val json = nativeGetMediaInfo(filePath) ?: return null
        val obj = JSONObject(json)

        return MediaInfo(
            durationMs = obj.optLong("durationMs", -1L),
            bitRate    = obj.optLong("bitRate", -1L),
            width      = obj.optInt("width", 0),
            height     = obj.optInt("height", 0),
            videoCodec = obj.optString("videoCodec", null),
            audioCodec = obj.optString("audioCodec", null)
        )
    }

    // ---------- Core async primitive (suspend) ----------

    suspend fun runCommandAsync(
        args: List<String>,
        logCallback: FFmpegLogCallback? = null,
        logLevel: Int = LOG_INFO
    ): Int = withContext(Dispatchers.IO) {
        if (logCallback != null) {
            nativeSetLogLevel(logLevel)
            nativeSetLogCallback(logCallback)
        }

        try {
            runCommand(args.toTypedArray())
        } catch (ce: CancellationException) {
            nativeRequestCancel()
            throw ce
        } finally {
            if (logCallback != null) {
                nativeSetLogCallback(null)
            }
        }
    }
    // --- Introspection native functions ---
    external fun getBuildConfig(): String
    external fun getSupportedCodecsRaw(): String
    external fun getSupportedFormatsRaw(): String
    external fun isHardwareCodecSupported(codecName: String): Boolean

}
