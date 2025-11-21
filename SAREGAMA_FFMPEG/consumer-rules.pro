# Keep the public API of the FFmpeg wrapper library
-keep class com.saregama.android.ffmpeg.** { *; }

# Keep all native methods – important for JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Optional: keep log callback interface / listener
-keep interface com.saregama.android.ffmpeg.FFmpegLogCallback
-keep interface com.saregama.android.ffmpeg.FFmpegCommandListener