// Simple bridge so our JNI can call ffmpeg_main(), while the
// real implementation in this FFmpeg version is main() in ffmpeg.c.

int main(int argc, char **argv);  // forward declaration

int ffmpeg_main(int argc, char **argv) {
    return main(argc, argv);
}
