package com.example.mindmap

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class FocusBlockerService : AccessibilityService() {
    private var lastEnforcedAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val cfg = FocusStore.activeBlock(applicationContext, pkg) ?: return

        val tick = SystemClock.elapsedRealtime()
        if (tick - lastEnforcedAt < 700L) return
        lastEnforcedAt = tick

        val remaining = cfg.expiresAt - System.currentTimeMillis()
        if (cfg.priority == FocusStore.PRIORITY_HIGH) {
            // Strict mode: send the user home immediately + warning
            performGlobalAction(GLOBAL_ACTION_HOME)
            Toast.makeText(
                this,
                "${cfg.appName} is blocked · ${FocusStore.formatRemaining(remaining)} left",
                Toast.LENGTH_LONG
            ).show()
        } else {
            // Block-screen mode
            val intent = Intent(this, FocusBlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("pkg", pkg)
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
}