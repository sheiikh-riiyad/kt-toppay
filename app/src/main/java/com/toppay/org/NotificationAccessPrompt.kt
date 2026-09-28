package com.toppay.org

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val ACCESS_PROMPT_PREFERENCES = "notification_access_prompt"
private const val ACCESS_PROMPT_ANSWERED = "answered"

@Composable
fun NotificationAccessFirstLaunchPrompt() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(ACCESS_PROMPT_PREFERENCES, Context.MODE_PRIVATE) }
    var stage by remember {
        mutableIntStateOf(
            if (!preferences.getBoolean(ACCESS_PROMPT_ANSWERED, false) && !hasNotificationListenerAccess(context)) 1 else 0
        )
    }
    val notificationAccessLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { stage = 0 }
    val appInfoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { stage = 3 }

    fun finishPrompt() {
        preferences.edit().putBoolean(ACCESS_PROMPT_ANSWERED, true).apply()
        stage = 0
    }

    when (stage) {
        1 -> AlertDialog(
            onDismissRequest = { finishPrompt() },
            title = { Text("নোটিফিকেশন অ্যাক্সেস", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("TopPay কি নতুন নোটিফিকেশন লোকাল লগে রাখতে এবং আপনার অ্যাকাউন্টের Firestore-এ স্বয়ংক্রিয় ব্যাকআপ করতে পারবে?")
                    Spacer(Modifier.height(10.dp))
                    Text("Android নিরাপত্তার কারণে পরের দুইটি ছোট ধাপ আপনাকে নিজে সম্পন্ন করতে হবে।", color = Color(0xFF756D72))
                }
            },
            confirmButton = { TextButton(onClick = { stage = 2 }) { Text("হ্যাঁ, শুরু করুন", color = Color(0xFFE50973), fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { finishPrompt() }) { Text("না", color = Color(0xFF756D72)) } }
        )
        2 -> AlertDialog(
            onDismissRequest = { finishPrompt() },
            title = { Text("ধাপ ১: Restricted settings চালু করুন", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("TopPay-এর App info খুললে উপরের ডান পাশের ⋮ চাপুন।")
                    Spacer(Modifier.height(8.dp))
                    Text("তারপর ‘Allow restricted settings’ চাপুন এবং PIN বা fingerprint দিয়ে নিশ্চিত করুন।", color = Color(0xFFE50973), fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit().putBoolean(ACCESS_PROMPT_ANSWERED, true).apply()
                    appInfoLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }) { Text("App info খুলুন", color = Color(0xFFE50973), fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { finishPrompt() }) { Text("পরে করব") } }
        )
        3 -> AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text("ধাপ ২: Notification access চালু করুন", fontWeight = FontWeight.Bold) },
            text = { Text("এখন Notification access খুলে TopPay-এর পাশের switch চালু করুন।") },
            confirmButton = {
                TextButton(onClick = { notificationAccessLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                    Text("Notification access খুলুন", color = Color(0xFFE50973), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("বন্ধ করুন") } }
        )
    }
}

private fun hasNotificationListenerAccess(context: Context): Boolean {
    val component = ComponentName(context, TopPayNotificationListenerService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')
        ?.any { ComponentName.unflattenFromString(it) == component } == true
}
