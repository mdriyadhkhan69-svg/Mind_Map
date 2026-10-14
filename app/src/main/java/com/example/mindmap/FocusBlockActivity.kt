package com.example.mindmap

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class FocusBlockActivity : ComponentActivity() {
    private val pkgState = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pkgState.value = intent.getStringExtra("pkg").orEmpty()
        onBackPressedDispatcher.addCallback(this) { goHome() }
        setContent {
            FocusBlockScreen(pkg = pkgState.value, onHome = { goHome() }, onExpired = { finish() })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkgState.value = intent.getStringExtra("pkg").orEmpty()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}

@Composable
private fun FocusBlockScreen(pkg: String, onHome: () -> Unit, onExpired: () -> Unit) {
    val context = LocalContext.current
    FocusStore.ensureLoaded(context)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val cfg = FocusStore.configs.firstOrNull { it.packageName == pkg }
    val remaining = (cfg?.expiresAt ?: 0L) - now
    val finished = cfg == null || !cfg.isActive || remaining <= 0L
    LaunchedEffect(finished) {
        if (finished) {
            FocusStore.refreshExpired(context)
            onExpired()
        }
    }

    Surface(color = Color(0xFF0B0B12), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFF64FFDA), modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(18.dp))
            Text(cfg?.appName.orEmpty(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                FocusStore.formatRemaining(remaining),
                color = Color(0xFF64FFDA), fontSize = 48.sp, fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Stay focused. This app is blocked until the timer ends.",
                color = Color(0xFFB7B7C6), fontSize = 15.sp, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onHome() }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("Go Home", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}