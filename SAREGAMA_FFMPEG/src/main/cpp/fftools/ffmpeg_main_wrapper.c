// ffmpeg_main_wrapper.c

#include "ffmpeg.h"   // or header that declares ffmpeg_main_internal / cleanup / globals
#include "cmdutils.h"

// Implemented in ffmpeg.c
extern int  ffmpeg_main_internal(int argc, char **argv);
extern void ffmpeg_cleanup(int ret);
extern void ffmpeg_reset_state(void);

/**
 * Safe entry point for JNI.
 * - runs the CLI once
 * - cleans up
 * - resets globals so it can be called again
 */
int ffmpeg_main(int argc, char **argv)
{
    int ret = ffmpeg_main_internal(argc, argv);

    // If ffmpeg_main_internal already calls ffmpeg_cleanup(ret),
    // you can remove this call; otherwise keep it.
    // Calling cleanup twice is *not* safe, so don't duplicate it.
    // If unsure, comment this out and keep it only in ffmpeg.c.
    // ffmpeg_cleanup(ret);

    ffmpeg_reset_state();

    return ret;
}
