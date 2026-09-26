package com.toppay.org

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private val LogPink = Color(0xFFE50973)
private val LogInk = Color(0xFF302B30)

@Composable
fun NotificationLogScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember { mutableStateOf(isNotificationAccessEnabled(context)) }
    var contents by remember { mutableStateOf(NotificationLogStorage.read(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        enabled = isNotificationAccessEnabled(context)
        contents = NotificationLogStorage.read(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier.fillMaxSize().background(Color(0xFFF8F6F7))) {
        Surface(color = LogPink, shadowElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }
                Text("নোটিফিকেশন লগ", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = if (enabled) Color(0xFFE8F7EE) else Color(0xFFFFE7F1), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (enabled) "● নোটিফিকেশন অ্যাক্সেস চালু" else "● নোটিফিকেশন অ্যাক্সেস বন্ধ", color = if (enabled) Color(0xFF16864A) else LogPink, fontWeight = FontWeight.Bold)
                    Text("অনুমতি দেওয়ার পর নতুন নোটিফিকেশনের অ্যাপ, শিরোনাম ও বার্তা লোকাল ফাইলে থাকবে এবং সাইন-ইন থাকলে Firestore-এ স্বয়ংক্রিয় ব্যাকআপ হবে।", color = Color(0xFF756D72), fontSize = 12.sp, lineHeight = 18.sp)
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = LogPink),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text(if (enabled) "অ্যাক্সেস সেটিংস" else "অ্যাক্সেস চালু করুন") }
                }
            }

            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("লোকাল ফাইল", color = LogInk, fontWeight = FontWeight.Bold)
                    Text(NotificationLogStorage.file(context).absolutePath, color = Color(0xFF756D72), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("অ্যাপ আনইনস্টল করলে লোকাল ফাইলটি মুছে যাবে। সাইন-ইন থাকা অবস্থায় সর্বশেষ ৫০টি লগ স্বয়ংক্রিয়ভাবে Firestore-এ ব্যাকআপ হবে।", color = Color(0xFF756D72), fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = ::refresh, shape = RoundedCornerShape(11.dp)) { Text("রিফ্রেশ", color = LogPink) }
                        OutlinedButton(onClick = { confirmClear = true }, enabled = contents.isNotBlank(), shape = RoundedCornerShape(11.dp)) { Text("সব মুছুন", color = LogPink) }
                    }
                }
            }

            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("স্বয়ংক্রিয় Firestore ব্যাকআপ", color = LogInk, fontWeight = FontWeight.Bold)
                    Text("users/{uid}/private/notification", color = Color(0xFF756D72), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text("নতুন লগ আসলে এবং ব্যবহারকারী সাইন-ইন করলে ব্যাকআপ নিজে থেকে আপডেট হয়।", color = Color(0xFF756D72), fontSize = 11.sp)
                    backupMessage?.let { Text(it, color = if (it.contains("হয়নি")) MaterialTheme.colorScheme.error else Color(0xFF16864A), fontSize = 11.sp) }
                }
            }

            Text("সংরক্ষিত নোটিফিকেশন", color = LogInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp), shadowElevation = 1.dp) {
                Text(
                    text = contents.ifBlank { "এখনো কোনো নোটিফিকেশন সংরক্ষিত হয়নি।" },
                    modifier = Modifier.fillMaxWidth().padding(15.dp),
                    color = if (contents.isBlank()) Color(0xFF8B8288) else LogInk,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    fontFamily = if (contents.isBlank()) FontFamily.Default else FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("সব লগ মুছবেন?") },
            text = { Text("সংরক্ষিত নোটিফিকেশনগুলো স্থায়ীভাবে মুছে যাবে।") },
            confirmButton = {
                TextButton(onClick = {
                    NotificationLogStorage.clear(context)
                    contents = ""
                    NotificationBackupManager.sync(context) { result ->
                        backupMessage = if (result.isSuccess) "লোকাল ও Firestore লগ মুছে দেওয়া হয়েছে" else "লোকাল লগ মুছেছে, কিন্তু Firestore আপডেট হয়নি"
                    }
                    confirmClear = false
                }) { Text("মুছুন", color = LogPink) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("বাতিল") } }
        )
    }
}

private fun isNotificationAccessEnabled(context: Context): Boolean {
    val component = ComponentName(context, TopPayNotificationListenerService::class.java)
    return Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        ?.split(':')
        ?.any { ComponentName.unflattenFromString(it) == component } == true
}
