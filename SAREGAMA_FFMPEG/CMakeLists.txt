cmake_minimum_required(VERSION 3.22.1)

project(ffmpeg-wrapper)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D__ANDROID__")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -D__ANDROID__")

# ---- Our JNI library ----
add_library(ffmpeg_wrapper SHARED
        # JNI wrapper
        src/main/cpp/ffmpeg_jni.cpp

        # Core FFTools
        src/main/cpp/fftools/ffmpeg.c
        src/main/cpp/fftools/cmdutils.c
        src/main/cpp/fftools/ffmpeg_opt.c
        src/main/cpp/fftools/ffmpeg_filter.c
        src/main/cpp/fftools/opt_common.c
        src/main/cpp/fftools/ffmpeg_sched.c
        src/main/cpp/fftools/ffmpeg_demux.c
        src/main/cpp/fftools/ffmpeg_dec.c
        src/main/cpp/fftools/ffmpeg_hw.c
        src/main/cpp/fftools/graph/graphprint.c
        src/main/cpp/fftools/ffmpeg_mux.c
        src/main/cpp/fftools/ffmpeg_mux_init.c
        src/main/cpp/fftools/thread_queue.c
        src/main/cpp/fftools/sync_queue.c
        src/main/cpp/fftools/ffmpeg_main_wrapper.c
        src/main/cpp/fftools/ffmpeg_enc.c

        # textformat module
        src/main/cpp/fftools/textformat/avtextformat.c
        src/main/cpp/fftools/textformat/tf_compact.c
        src/main/cpp/fftools/textformat/tf_default.c
        src/main/cpp/fftools/textformat/tf_flat.c
        src/main/cpp/fftools/textformat/tf_ini.c
        src/main/cpp/fftools/textformat/tf_json.c
        src/main/cpp/fftools/textformat/tf_mermaid.c
        src/main/cpp/fftools/textformat/tf_xml.c
        src/main/cpp/fftools/textformat/tw_avio.c
        src/main/cpp/fftools/textformat/tw_buffer.c
        src/main/cpp/fftools/textformat/tw_stdout.c

        # resources module
        src/main/cpp/fftools/resources/resman.c
        src/main/cpp/fftools/resources/graph_resources_stub.c
        # Add these too if they exist in your tree:
        # src/main/cpp/fftools/resources/graph_html.c
        # src/main/cpp/fftools/resources/graph_css.c
)

# ---- FFmpeg prebuilt libraries ----
set(FFMPEG_LIB_DIR "${CMAKE_CURRENT_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}")

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libavutil.so")

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libavcodec.so")

add_library(avformat SHARED IMPORTED)
set_target_properties(avformat PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libavformat.so")

add_library(avfilter SHARED IMPORTED)
set_target_properties(avfilter PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libavfilter.so")

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libswresample.so")

add_library(swscale SHARED IMPORTED)
set_target_properties(swscale PROPERTIES IMPORTED_LOCATION
        "${FFMPEG_LIB_DIR}/libswscale.so")

find_library(log-lib log)
find_library(z-lib   z)

# ---- Include FFmpeg headers ----
target_include_directories(ffmpeg_wrapper PRIVATE
        src/main/cpp                      # config.h, stdatomic.h, stdbit.h, compat/, etc.
        src/main/cpp/include              # installed FFmpeg headers (libavcodec/..., etc.)
        src/main/cpp/fftools              # fftools/*.h
        src/main/cpp/fftools/graph        # graphprint.h
        src/main/cpp/fftools/textformat   # avtextformat.h, tf_*.h, tw_*.h
        src/main/cpp/fftools/resources    # resman.h
)

# ---- Link everything together ----
target_link_libraries(ffmpeg_wrapper
        avutil
        avcodec
        avformat
        avfilter
        swresample
        swscale
        ${log-lib}
        ${z-lib}
)
