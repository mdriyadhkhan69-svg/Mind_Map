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
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
                    .padding(horizontal = 28.dp)
                    .offset(y = (-72).dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    dateKey,
                    color = Color(0xFFEDE6DA),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    occasionText,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 36.sp,
                    letterSpacing = 0.2.sp,
                    textAlign = TextAlign.Center
                )
            }
            AlarmCloseButton(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun AlarmCloseButton(modifier: Modifier = Modifier, onDismiss: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "alarmCloseScale"
    )
    val backgroundColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (pressed) Color(0xFFFF6B5C) else Color(0xFF2A2A32),
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "alarmCloseColor"
    )
    Box(
        modifier = modifier
            .size(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onDismiss() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Close, contentDescription = "Stop alarm", tint = Color.White, modifier = Modifier.size(40.dp))
    }
}