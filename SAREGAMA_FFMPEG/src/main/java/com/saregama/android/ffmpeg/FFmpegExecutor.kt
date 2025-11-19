package com.saregama.android.ffmpeg

import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * High-level executor that:
 * - Runs commands on background thread (IO)
 * - Sends callbacks on Main thread
 * - Provides success/failure with "reason" (based on logs/exitCode)
 * - Best-effort progress parsing (from FFmpeg logs)
 * - One FFmpeg command at a time (serialized) to avoid log callback conflicts
 */
object FFmpegExecutor {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // time=00:01:23.45
    private val timeRegex =
        Regex("""time=(\d+):(\d+):(\d+(?:\.\d+)?)""")

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            CoroutineScope(Dispatchers.Main.immediate).launch { block() }
        }
    }

    /**
     * Try to infer total duration from the first "-i <path>" in the args.
     * Only works well for "normal" commands with single main input.
     */
    private fun inferTotalDurationMs(args: List<String>): Long? {
        val idx = args.indexOf("-i")
        if (idx >= 0 && idx + 1 < args.size) {
            val inputPath = args[idx + 1]
            val info = FFmpegNative.getMediaInfo(inputPath)
            if (info != null && info.durationMs > 0) {
                return info.durationMs
            }
        }
        return null
    }

    /**
     * Parse a progress percentage from an FFmpeg log line (best-effort).
     */
    private fun parseProgressPercent(
        line: String,
        totalDurationMs: Long?
    ): Float? {
        if (totalDurationMs == null || totalDurationMs <= 0L) return null

        val match = timeRegex.find(line) ?: return null
        val (hStr, mStr, sStr) = match.destructured

        val hours = hStr.toLongOrNull() ?: return null
        val mins = mStr.toLongOrNull() ?: return null
        val secs = sStr.toDoubleOrNull() ?: return null

        val currentMs =
            ((hours * 3600L + mins * 60L) * 1000L) + (secs * 1000.0).toLong()

        val rawPercent = currentMs.toDouble() / totalDurationMs.toDouble() * 100.0
        val clamped = max(0.0, min(100.0, rawPercent))

        return clamped.toFloat()
    }

    /**
     * Short helper around FFmpegNative.getVersion()
     */
    fun getVersion(): String = FFmpegNative.getVersion()

    /**
     * Raw FFmpeg build configuration (configure flags, etc.).
     */
    fun getBuildConfig(): String = FFmpegNative.getBuildConfig()

    /**
     * List of supported codecs (one entry per line).
     * Each line looks like: "E/V h264 : H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10"
     *
     * - Prefix:
     *   - 'E' encoder, 'D' decoder (approx)
     *   - 'V' video, 'A' audio, 'S' subtitle
     */
    fun getSupportedCodecs(): List<String> {
        return FFmpegNative
            .getSupportedCodecsRaw()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * List of supported formats (demuxers/muxers).
     * Each line starts with:
     *   - 'D ' for demuxer (input format)
     *   - 'M ' for muxer (output format)
     */
    fun getSupportedFormats(): List<String> {
        return FFmpegNative
            .getSupportedFormatsRaw()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Checks if a given codec name (e.g. "h264") has any hardware
     * configurations compiled into this FFmpeg build.
     *
     * NOTE:
     * - Returns false if codec is not present or built only as SW.
     * - "Hardware" here means FFmpeg has some AVCodecHWConfig for it
     *   (e.g. via Vulkan, MediaCodec, etc.), not that Android device
     *   necessarily supports that path at runtime.
     */
    fun isHardwareCodecSupported(codecName: String): Boolean {
        if (codecName.isBlank()) return false
        return FFmpegNative.isHardwareCodecSupported(codecName)
    }


    /**
     * Run an FFmpeg command asynchronously.
     *
     * @return Job you can cancel() if needed (hard cancel now hooked to ffmpeg.c).
     */
    fun runCommand(
        args: List<String>,
        listener: FFmpegCommandListener,
        logLevel: Int = FFmpegNative.LOG_INFO
    ): Job {

        postToMain { listener.onStart() }

        return scope.launch {
            mutex.withLock {
                // Infer total duration (best-effort) *before* starting
                val totalDurationMs = inferTotalDurationMs(args)

                val errorCollector = StringBuilder()

                val logCallback = FFmpegLogCallback { level, msg ->
                    val line = msg.trim()

                    // Collect error logs as potential failure reason
                    if (level <= FFmpegNative.LOG_ERROR) {
                        synchronized(errorCollector) {
                            errorCollector.append(line).append('\n')
                        }
                    }

                    // Per-line callback
                    postToMain {
                        listener.onLog(level, line)
                    }

                    // Progress from time=... if we know duration
                    val percent = parseProgressPercent(line, totalDurationMs)
                    if (percent != null) {
                        postToMain {
                            listener.onProgress(percent)
                        }
                    }
                }

                try {
                    val exitCode = FFmpegNative.runCommandAsync(
                        args = args,
                        logCallback = logCallback,
                        logLevel = logLevel
                    )

                    val reason = synchronized(errorCollector) {
                        val text = errorCollector.toString().trim()
                        text.ifEmpty { "FFmpeg finished with exit code $exitCode" }
                    }

                    if (exitCode == 0) {
                        postToMain { listener.onSuccess() }
                    } else {
                        postToMain { listener.onFailure(exitCode, reason) }
                    }
                } catch (ce: CancellationException) {
                    // nativeRequestCancel() is already invoked in runCommandAsync
                    val reason = "FFmpeg command cancelled"
                    postToMain { listener.onFailure(-2, reason) }
                    throw ce
                } catch (t: Throwable) {
                    val reason = "Internal error: ${t.message ?: t.javaClass.simpleName}"
                    postToMain { listener.onFailure(-1, reason) }
                }
            }
        }
    }

    /**
     * Cancel all running commands (soft/hard combo – we signal native cancel too).
     */
    fun cancelAll() {
        scope.coroutineContext[Job]?.cancelChildren()
    }
}
