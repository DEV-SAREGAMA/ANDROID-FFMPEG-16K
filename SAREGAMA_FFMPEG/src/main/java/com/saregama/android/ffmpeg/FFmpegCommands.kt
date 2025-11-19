package com.saregama.android.ffmpeg

object FFmpegCommands {

    fun buildCustom(argsWithoutBinary: List<String>): List<String> {
        return listOf("ffmpeg") + argsWithoutBinary
    }

    fun buildExtractFrame(
        input: String,
        outputImage: String,
        timeSec: Double = 0.0,
        quality: Int = 2
    ): List<String> {
        return listOf(
            "ffmpeg",
            "-y",
            "-ss", timeSec.toString(),
            "-i", input,
            "-frames:v", "1",
            "-q:v", quality.toString(),
            outputImage
        )
    }

    fun buildMergeAudio(
        minusTrack: String,
        vocalTrack: String,
        output: String,
        minusVolume: Double = 1.0,
        vocalVolume: Double = 1.0
    ): List<String> {
        val filter = "[0:a]volume=$minusVolume[a0];" +
                "[1:a]volume=$vocalVolume[a1];" +
                "[a0][a1]amix=inputs=2:normalize=0[aout]"

        return listOf(
            "ffmpeg",
            "-y",
            "-i", minusTrack,
            "-i", vocalTrack,
            "-filter_complex", filter,
            "-map", "[aout]",
            "-c:a", "aac",
            "-b:a", "192k",
            output
        )
    }

    fun buildConcatVideos(
        concatListFile: String,
        output: String
    ): List<String> {
        return listOf(
            "ffmpeg",
            "-y",
            "-f", "concat",
            "-safe", "0",
            "-i", concatListFile,
            "-c", "copy",
            output
        )
    }

    fun buildKaraokeMix(
        minusTrack: String,
        vocalTrack: String,
        output: String,
        minusVolume: Double = 1.0,
        vocalVolume: Double = 1.0
    ): List<String> = buildMergeAudio(
        minusTrack = minusTrack,
        vocalTrack = vocalTrack,
        output = output,
        minusVolume = minusVolume,
        vocalVolume = vocalVolume
    )
}
