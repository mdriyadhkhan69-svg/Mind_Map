package com.example.mindmap

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mindmap.ui.screens.OccasionSeparator

// Calendar timer alarm baje uthle ei Activity full-screen e open hoy —
// phone lock/home screen/onno app jekhaneই thakuk na keno, upore chole ase
class AlarmActivity : ComponentActivity() {
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen er upore dekhano ebong screen jagano-r jonno flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val rawText = intent.getStringExtra("text").orEmpty()
        val occasionText = rawText.split(OccasionSeparator).map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
        val dateKey = intent.getStringExtra("date_key").orEmpty()

        startAlarmSound()

        setContent {
            AlarmScreen(
                dateKey = dateKey,
                occasionText = occasionText.ifBlank { "Reminder" },
                onDismiss = { stopAlarmSound(); finish() }
            )
        }
    }

    private fun startAlarmSound() {
        runCatching {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, Uri.parse("android.resource://$packageName/${R.raw.calendar_alarm}"))
                isLooping = true
                prepare()
                start()
            }
        }
    }

    private fun stopAlarmSound() {
        runCatching {
            player?.let { if (it.isPlaying) it.stop(); it.release() }
        }
        player = null
    }

    override fun onDestroy() {
        stopAlarmSound()
        super.onDestroy()
    }
}

@Composable
private fun AlarmScreen(dateKey: String, occasionText: String, onDismiss: () -> Unit) {
    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(24.dp)
                    .offset(y = (-72).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(dateKey, color = Color(0xFF64FFDA), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(
                    occasionText,
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5252))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, contentDescription = "Stop alarm", tint = Color.White, modifier = Modifier.size(44.dp))
            }
        }
    }
}