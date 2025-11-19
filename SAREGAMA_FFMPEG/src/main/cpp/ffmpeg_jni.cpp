#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cstdarg>
#include <cstdio>
#include <mutex>

extern "C" {
#include <libavutil/avutil.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>

// From fftools/ffmpeg.c
int ffmpeg_main(int argc, char **argv);
}

#define LOG_TAG "FFmpegJNI"
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

// ---- Global JNI state ----
static JavaVM *g_vm = nullptr;
static jobject g_logCallback = nullptr;   // GlobalRef to FFmpegLogCallback
static jmethodID g_onLogMethod = nullptr; // void onLog(int level, String msg)
static int g_logLevel = AV_LOG_INFO;
static std::mutex g_logMutex;

// Soft cancel flag (you can wire this into fftools/ffmpeg.c later if needed)
static volatile int g_cancelRequested = 0;
extern "C" int ffmpeg_android_is_cancel_requested() {
    return g_cancelRequested;
}
static std::string safeStr(const char *s) {
    return s ? std::string(s) : std::string();
}

static std::string jsonEscape(const std::string &s) {
    std::string out;
    out.reserve(s.size() + 8);
    for (char c : s) {
        switch (c) {
            case '\"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) >= 0x20) {
                    out += c;
                }
                break;
        }
    }
    return out;
}

// ---- AV log callback ----
static void ffmpeg_android_log_callback(void *ptr, int level, const char *fmt, va_list vl) {
    std::lock_guard<std::mutex> lock(g_logMutex);

    if (!g_logCallback || level > g_logLevel) {
        return;
    }

    char msg[1024];
    vsnprintf(msg, sizeof(msg), fmt, vl);
    msg[sizeof(msg) - 1] = '\0';

    JNIEnv *env = nullptr;
    bool attached = false;

    if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    }

    jstring jMsg = env->NewStringUTF(msg);
    if (jMsg == nullptr) {
        if (attached) g_vm->DetachCurrentThread();
        return;
    }

    env->CallVoidMethod(g_logCallback, g_onLogMethod, (jint) level, jMsg);

    env->DeleteLocalRef(jMsg);

    if (attached) {
        g_vm->DetachCurrentThread();
    }
}

// ---- Java helpers ----

static void convertJavaArgs(JNIEnv *env, jobjectArray argsArray,
                            std::vector<std::string> &argsStr,
                            std::vector<char *> &argv) {
    jint argc = env->GetArrayLength(argsArray);
    argsStr.reserve(argc);
    argv.reserve(argc);

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) env->GetObjectArrayElement(argsArray, i);
        const char *raw = env->GetStringUTFChars(arg, nullptr);
        argsStr.emplace_back(raw);
        env->ReleaseStringUTFChars(arg, raw);
        env->DeleteLocalRef(arg);
    }

    for (auto &s : argsStr) {
        argv.push_back(const_cast<char *>(s.c_str()));
    }
}

// ---- JNI OnLoad ----

jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    av_log_set_level(AV_LOG_INFO);
    return JNI_VERSION_1_6;
}

// ---- getVersion() ----
extern "C"
JNIEXPORT jstring JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_getVersion(
        JNIEnv *env,
        jobject /*thiz*/) {
    const char *version = av_version_info();
    if (!version) version = "unknown";
    return env->NewStringUTF(version);
}

// ---- nativeGetMediaInfo(path) -> JSON or null ----
extern "C"
JNIEXPORT jstring JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_nativeGetMediaInfo(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring filePath_) {

    if (filePath_ == nullptr) return nullptr;

    const char *path = env->GetStringUTFChars(filePath_, nullptr);
    if (!path) return nullptr;

    avformat_network_init();

    AVFormatContext *fmtCtx = nullptr;
    int ret = avformat_open_input(&fmtCtx, path, nullptr, nullptr);
    env->ReleaseStringUTFChars(filePath_, path);

    if (ret < 0 || !fmtCtx) {
        return nullptr;
    }

    ret = avformat_find_stream_info(fmtCtx, nullptr);
    if (ret < 0) {
        avformat_close_input(&fmtCtx);
        return nullptr;
    }

    int64_t durationMs = -1;
    if (fmtCtx->duration > 0) {
        durationMs = fmtCtx->duration * 1000 / AV_TIME_BASE;
    }

    int64_t bitRate = fmtCtx->bit_rate;

    int videoStreamIndex = -1;
    int audioStreamIndex = -1;
    AVCodecParameters *videoPar = nullptr;
    AVCodecParameters *audioPar = nullptr;

    for (unsigned int i = 0; i < fmtCtx->nb_streams; ++i) {
        AVStream *st = fmtCtx->streams[i];
        AVCodecParameters *par = st->codecpar;
        if (!par) continue;

        if (par->codec_type == AVMEDIA_TYPE_VIDEO && videoStreamIndex < 0) {
            videoStreamIndex = (int) i;
            videoPar = par;
        } else if (par->codec_type == AVMEDIA_TYPE_AUDIO && audioStreamIndex < 0) {
            audioStreamIndex = (int) i;
            audioPar = par;
        }
    }

    int width = 0;
    int height = 0;
    std::string videoCodecName;
    std::string audioCodecName;

    if (videoPar) {
        width = videoPar->width;
        height = videoPar->height;
        const AVCodec *vCodec = avcodec_find_decoder(videoPar->codec_id);
        if (vCodec) videoCodecName = safeStr(vCodec->name);
    }

    if (audioPar) {
        const AVCodec *aCodec = avcodec_find_decoder(audioPar->codec_id);
        if (aCodec) audioCodecName = safeStr(aCodec->name);
    }

    std::string json = "{";
    json += "\"durationMs\":" + std::to_string(durationMs) + ",";
    json += "\"bitRate\":" + std::to_string(bitRate) + ",";
    json += "\"width\":" + std::to_string(width) + ",";
    json += "\"height\":" + std::to_string(height) + ",";
    json += "\"videoCodec\":\"" + jsonEscape(videoCodecName) + "\",";
    json += "\"audioCodec\":\"" + jsonEscape(audioCodecName) + "\"";
    json += "}";

    avformat_close_input(&fmtCtx);

    return env->NewStringUTF(json.c_str());
}

// ---- nativeSetLogCallback(callback: FFmpegLogCallback?) ----
extern "C"
JNIEXPORT void JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_nativeSetLogCallback(
        JNIEnv *env,
        jobject /*thiz*/,
        jobject callback) {

    std::lock_guard<std::mutex> lock(g_logMutex);

    if (g_logCallback) {
        env->DeleteGlobalRef(g_logCallback);
        g_logCallback = nullptr;
        g_onLogMethod = nullptr;
    }

    if (callback == nullptr) {
        // Restore default
        av_log_set_callback(nullptr);
        return;
    }

    jclass cbClass = env->GetObjectClass(callback);
    if (!cbClass) {
        ALOGE("Failed to get callback class");
        return;
    }

    // FFmpegLogCallback.onLog(int level, String message)
    jmethodID onLog = env->GetMethodID(cbClass, "onLog", "(ILjava/lang/String;)V");
    env->DeleteLocalRef(cbClass);

    if (!onLog) {
        ALOGE("Failed to get onLog method");
        return;
    }

    g_logCallback = env->NewGlobalRef(callback);
    g_onLogMethod = onLog;

    av_log_set_callback(ffmpeg_android_log_callback);
}

// ---- nativeSetLogLevel(level: Int) ----
extern "C"
JNIEXPORT void JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_nativeSetLogLevel(
        JNIEnv * /*env*/,
        jobject /*thiz*/,
        jint level) {

    g_logLevel = level;
    av_log_set_level(level);
}

// ---- nativeRequestCancel() ----
extern "C"
JNIEXPORT void JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_nativeRequestCancel(
        JNIEnv * /*env*/,
        jobject /*thiz*/) {
    g_cancelRequested = 1;
    // NOTE: To truly abort FFmpeg, you’d need to use this in
    // fftools/ffmpeg.c’s decode_interrupt_cb(), etc.
}

// ---- runCommand(args: Array<String>) ----
extern "C"
JNIEXPORT jint JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_runCommand(
        JNIEnv *env,
        jobject /*thiz*/,
        jobjectArray argsArray) {

    if (argsArray == nullptr) {
        ALOGE("runCommand: argsArray is null");
        return -1;
    }

    g_cancelRequested = 0; // reset soft flag

    std::vector<std::string> argsStr;
    std::vector<char *> argv;
    convertJavaArgs(env, argsArray, argsStr, argv);

    int argc = (int) argv.size();

    ALOGI("Running FFmpeg with %d args", argc);

    // CLI main
    int result = ffmpeg_main(argc, argv.data());

    ALOGI("FFmpeg finished with code %d", result);

    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_getBuildConfig(
        JNIEnv *env,
        jobject /*thiz*/) {
    const char *cfg = avcodec_configuration();
    if (!cfg) cfg = "";
    return env->NewStringUTF(cfg);
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_getSupportedCodecsRaw(
        JNIEnv *env,
        jobject /*thiz*/) {

    std::string out;

    void *opaque = nullptr;
    const AVCodec *codec = nullptr;

    while ((codec = av_codec_iterate(&opaque))) {
        char typeChar = '?';
        if (codec->type == AVMEDIA_TYPE_VIDEO) typeChar = 'V';
        else if (codec->type == AVMEDIA_TYPE_AUDIO) typeChar = 'A';
        else if (codec->type == AVMEDIA_TYPE_SUBTITLE) typeChar = 'S';

        char dirChar = av_codec_is_encoder(codec) ? 'E' : 'D';

        out += dirChar;
        out += "/";
        out += typeChar;
        out += " ";
        out += codec->name ? codec->name : "unknown";
        if (codec->long_name) {
            out += " : ";
            out += codec->long_name;
        }
        out += "\n";
    }

    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_getSupportedFormatsRaw(
        JNIEnv *env,
        jobject /*thiz*/) {

    std::string out;

    // Input formats (demuxers)
    void *d_opaque = nullptr;
    const AVInputFormat *ifmt = nullptr;
    while ((ifmt = av_demuxer_iterate(&d_opaque))) {
        out += "D ";
        out += ifmt->name ? ifmt->name : "unknown";
        if (ifmt->long_name) {
            out += " : ";
            out += ifmt->long_name;
        }
        out += "\n";
    }

    // Output formats (muxers)
    void *m_opaque = nullptr;
    const AVOutputFormat *ofmt = nullptr;
    while ((ofmt = av_muxer_iterate(&m_opaque))) {
        out += "M ";
        out += ofmt->name ? ofmt->name : "unknown";
        if (ofmt->long_name) {
            out += " : ";
            out += ofmt->long_name;
        }
        out += "\n";
    }

    return env->NewStringUTF(out.c_str());
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_saregama_android_ffmpeg_FFmpegNative_isHardwareCodecSupported(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring codecName_) {

    if (codecName_ == nullptr) return JNI_FALSE;

    const char *name = env->GetStringUTFChars(codecName_, nullptr);
    if (!name) return JNI_FALSE;

    const AVCodec *codec = avcodec_find_decoder_by_name(name);
    if (!codec) {
        codec = avcodec_find_encoder_by_name(name);
    }

    env->ReleaseStringUTFChars(codecName_, name);

    if (!codec) {
        // Codec not present in this build at all
        return JNI_FALSE;
    }

    // Check if this codec has any hardware configs compiled in
    for (int i = 0;; ++i) {
        const AVCodecHWConfig *cfg = avcodec_get_hw_config(codec, i);
        if (!cfg) break;
        // At least one HW config exists
        return JNI_TRUE;
    }

    return JNI_FALSE;
}


