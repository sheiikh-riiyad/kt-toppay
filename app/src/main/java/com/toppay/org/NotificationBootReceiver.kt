package com.toppay.org

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService

class NotificationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        runCatching {
            NotificationListenerService.requestRebind(
                ComponentName(context, TopPayNotificationListenerService::class.java)
            )
        }
    }
}
