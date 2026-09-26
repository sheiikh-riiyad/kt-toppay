package com.toppay.org

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class TopPayNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (posted.packageName == packageName) return

        val notification = posted.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        if (title.isBlank() && body.isBlank()) return

        val appName = runCatching {
            val info = packageManager.getApplicationInfo(posted.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(posted.packageName)

        runCatching {
            NotificationLogStorage.append(this, appName, posted.packageName, title, body)
            if (NotificationBackupManager.isEnabled(this)) {
                NotificationBackupManager.sync(this)
            }
        }
    }
}
