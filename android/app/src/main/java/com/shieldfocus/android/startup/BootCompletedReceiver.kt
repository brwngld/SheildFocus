package com.shieldfocus.android.startup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.shieldfocus.android.data.ProtectionStore
import com.shieldfocus.android.vpn.ShieldFocusVpnService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val store = ProtectionStore(context)
        val settings = store.loadSettings()
        if (!settings.autoStartOnBoot || !settings.enabled) return

        val serviceIntent = Intent(context, ShieldFocusVpnService::class.java).apply {
            action = ShieldFocusVpnService.ACTION_START
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}
