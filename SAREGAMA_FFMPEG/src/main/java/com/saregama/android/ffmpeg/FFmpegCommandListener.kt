package com.saregama.android.ffmpeg

/**
 * Callback-style listener for FFmpeg command execution.
 *
 * All callbacks are invoked on the Main thread.
 */
interface FFmpegCommandListener {

    /** Called right before the command starts. */
    fun onStart() {}

    /** Called for each log line (if you want to show progress text / debug). */
    fun onLog(level: Int, message: String) {}

    /**
     * Called when we can estimate progress (0f..100f).
     * Not guaranteed for all commands (best-effort).
     */
    fun onProgress(percent: Float) {}

    /** Called when FFmpeg finishes with exitCode == 0. */
    fun onSuccess()

    /**
     * Called when FFmpeg fails (exitCode != 0) or gets cancelled.
     *
     * @param exitCode FFmpeg exit code or:
     *                 -1: internal error before calling ffmpeg
     *                 -2: coroutine cancelled
     * @param reason   Human-readable reason (last error log or generic message).
     */
    fun onFailure(exitCode: Int, reason: String)
}
