package com.toppay.org

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val ACCESS_PROMPT_PREFERENCES = "notification_access_prompt"
private const val ACCESS_PROMPT_ANSWERED = "answered"

@Composable
fun NotificationAccessFirstLaunchPrompt() {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences(ACCESS_PROMPT_PREFERENCES, Context.MODE_PRIVATE)
    }
    var visible by remember {
        mutableStateOf(
            !preferences.getBoolean(ACCESS_PROMPT_ANSWERED, false) &&
                !hasNotificationListenerAccess(context)
        )
    }

    if (!visible) return

    fun finishPrompt() {
        preferences.edit().putBoolean(ACCESS_PROMPT_ANSWERED, true).apply()
        visible = false
    }

    AlertDialog(
        onDismissRequest = { finishPrompt() },
        title = { Text("নোটিফিকেশন অ্যাক্সেস", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("TopPay কি এই ডিভাইসের নতুন নোটিফিকেশন সংগ্রহ করে লোকাল লগে রাখতে এবং আপনার অ্যাকাউন্টের Firestore-এ স্বয়ংক্রিয় ব্যাকআপ করতে পারবে?")
                Spacer(Modifier.height(10.dp))
                Text("Android-এর নিরাপত্তা নিয়ম অনুযায়ী ‘হ্যাঁ’ চাপার পর সিস্টেমের Notification Access পাতায় TopPay চালু করতে হবে।", color = Color(0xFF756D72))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                finishPrompt()
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Text("হ্যাঁ", color = Color(0xFFE50973), fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = { finishPrompt() }) { Text("না", color = Color(0xFF756D72)) }
        }
    )
}

private fun hasNotificationListenerAccess(context: Context): Boolean {
    val component = ComponentName(context, TopPayNotificationListenerService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')
        ?.any { ComponentName.unflattenFromString(it) == component } == true
}
