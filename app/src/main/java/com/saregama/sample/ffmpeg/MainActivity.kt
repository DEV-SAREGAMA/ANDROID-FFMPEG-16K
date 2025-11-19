package com.saregama.sample.ffmpeg

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.saregama.android.ffmpeg.FFmpegExecutor
import com.saregama.android.ffmpeg.FFmpegNative
import com.saregama.sample.ffmpeg.ui.theme.SAREGAMAFFMPEGTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SAREGAMAFFMPEGTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        val version = FFmpegExecutor.getVersion()
        Log.d("FFmpeg SAREGAMA", "FFmpeg version = $version")
        val codecs= FFmpegExecutor.getSupportedCodecs()
        //print number of codecs
        Log.d("FFmpeg SAREGAMA", "Number of codecs = ${codecs.size}")

        for (codec in codecs) {
            Log.d("FFmpeg SAREGAMA", "codec = $codec")
        }

    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SAREGAMAFFMPEGTheme {
        Greeting("Android")
    }
}