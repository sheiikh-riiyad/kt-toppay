package com.toppay.org

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val LoginPink = Color(0xFFE50973)
private val LoginInk = Color(0xFF343034)

@Composable
fun PinGate(uid: String, onSignOut: () -> Unit, content: @Composable () -> Unit) {
    key(uid) { AccountPinGate(uid, onSignOut, content) }
}

@Composable
private fun AccountPinGate(uid: String, onSignOut: () -> Unit, content: @Composable () -> Unit) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val pinDocument = remember(uid) { firestore.document("users/$uid/private/pin") }
    val profileDocument = remember(uid) { firestore.document("users/$uid") }
    val user = FirebaseAuth.getInstance().currentUser
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val activity = LocalContext.current as? Activity
    var storedPin by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var enteredPin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    DisposableEffect(owner, activity) {
        val wasSecure = activity?.window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
                enteredPin = ""
                firstPin = null
                storedPin = null
                checked = false
                busy = false
                generation++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (!wasSecure) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(generation, uid) {
        error = null
        try {
            storedPin = pinDocument.get().await().getString("pin")
            checked = true
            try {
                profileDocument.set(
                    mapOf(
                        "uid" to uid,
                        "displayName" to (user?.displayName ?: ""),
                        "email" to (user?.email ?: ""),
                        "photoUrl" to (user?.photoUrl?.toString() ?: ""),
                        "emailVerified" to (user?.isEmailVerified ?: false),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ), SetOptions.merge()
                ).await()
            } catch (_: Exception) {
                // Profile sync does not block PIN login or setup.
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = firestorePinError(failure)
        }
    }

    if (unlocked) {
        content()
        return
    }

    val hasPin = checked && storedPin != null
    val isConfirming = !hasPin && firstPin != null
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = if (isConfirming) ({ firstPin = null; enteredPin = ""; error = null }) else onSignOut) {
                Text("‹", color = LoginPink, fontSize = 32.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, LoginPink),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 3.dp)
            ) { Text("লগ আউট", color = LoginPink, fontSize = 10.sp) }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("➤", color = LoginPink, fontSize = 30.sp)
                Box(Modifier.size(34.dp).background(Color(0xFFFFD1E5), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text("▦", color = LoginPink, fontSize = 22.sp)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                when {
                    !checked -> "অ্যাকাউন্ট যাচাই হচ্ছে"
                    hasPin -> "লগ ইন"
                    isConfirming -> "পিন নিশ্চিত করুন"
                    else -> "পিন সেট করুন"
                },
                color = if (checked && !hasPin) LoginPink else LoginInk,
                fontSize = 29.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    hasPin -> "আপনার TopPay অ্যাকাউন্টে"
                    isConfirming -> "একই পিন আবার লিখুন"
                    else -> "আপনার TopPay অ্যাকাউন্ট সুরক্ষিত রাখতে"
                },
                color = Color(0xFF777277), fontSize = 16.sp
            )
            if (checked && !hasPin) {
                Spacer(Modifier.height(16.dp))
                Surface(color = Color(0xFFFFEDF5), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(
                            if (isConfirming) "আপনার পিন নিশ্চিত করুন" else "গুরুত্বপূর্ণ",
                            color = LoginPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (isConfirming) "আগে লেখা একই ৪টি সংখ্যা আবার লিখুন।"
                            else "মনে রাখা সহজ এমন ৪টি সংখ্যা বেছে নিন। প্রতিবার লগ ইন করতে এই পিন লাগবে।",
                            color = LoginInk,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Spacer(Modifier.height(if (checked && !hasPin) 20.dp else 30.dp))
            Text("অ্যাকাউন্ট", color = LoginPink, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                user?.email ?: user?.displayName ?: uid,
                color = LoginInk, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(25.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (hasPin) "পিন লিখুন" else if (isConfirming) "৪ সংখ্যার পিন নিশ্চিত করুন" else "৪ সংখ্যার পিন তৈরি করুন",
                        color = LoginPink,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = .6.sp
                    )
                    Spacer(Modifier.height(9.dp))
                    PinDots(enteredPin.length)
                }
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Text("◌", color = LoginPink, fontSize = 33.sp)
                }
            }
            if (hasPin) {
                Spacer(Modifier.height(20.dp))
                Text("পিন ভুলে গেছেন? লগ আউট করে অন্য অ্যাকাউন্ট ব্যবহার করুন", color = LoginPink, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onSignOut))
            }
            notice?.let {
                Spacer(Modifier.height(12.dp))
                Surface(color = Color(0xFFFFEDF5), shape = RoundedCornerShape(9.dp)) {
                    Text(it, Modifier.fillMaxWidth().padding(11.dp), color = LoginPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(9.dp)) {
                    Text(it, Modifier.fillMaxWidth().padding(11.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!checked && error == null) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(Modifier.size(25.dp), color = LoginPink, strokeWidth = 2.dp)
            }
            if (!checked && error != null) {
                TextButton(onClick = { generation++ }) { Text("আবার চেষ্টা করুন", color = LoginPink) }
            }
        }

        Surface(color = Color(0xFFF0F4F4), shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Surface(
                    onClick = {
                        if (!busy && checked && enteredPin.length == 4) {
                            error = null
                            when {
                                hasPin -> {
                                    if (enteredPin == storedPin) {
                                        enteredPin = ""
                                        unlocked = true
                                    } else {
                                        enteredPin = ""
                                        error = "পিন সঠিক নয়। আবার চেষ্টা করুন।"
                                    }
                                }
                                firstPin == null -> {
                                    firstPin = enteredPin
                                    enteredPin = ""
                                    notice = "নিশ্চিত করতে পিনটি আরেকবার লিখুন।"
                                }
                                firstPin != enteredPin -> {
                                    firstPin = null
                                    enteredPin = ""
                                    notice = null
                                    error = "পিন দুটি মেলেনি। আবার পিন তৈরি করুন।"
                                }
                                else -> {
                                    busy = true
                                    val newPin = enteredPin
                                    enteredPin = ""
                                    scope.launch {
                                        try {
                                            pinDocument.set(mapOf("pin" to newPin, "createdAt" to FieldValue.serverTimestamp())).await()
                                            storedPin = newPin
                                            firstPin = null
                                            notice = "পিন সংরক্ষিত হয়েছে। লগ ইন করতে এখন পিনটি লিখুন।"
                                            try {
                                                profileDocument.set(mapOf("pinConfigured" to true), SetOptions.merge()).await()
                                            } catch (_: Exception) {
                                                // The PIN is saved; metadata can sync later.
                                            }
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (failure: Exception) {
                                            error = firestorePinError(failure)
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = checked && enteredPin.length == 4 && !busy,
                    color = if (checked && enteredPin.length == 4 && !busy) LoginPink else Color(0xFFB8BABB),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (busy) "সংরক্ষণ হচ্ছে…" else "পরবর্তী", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("➜", color = Color.White, fontSize = 23.sp)
                    }
                }
                NumberPad(
                    enabled = checked && !busy,
                    onDigit = { digit -> if (enteredPin.length < 4) enteredPin += digit },
                    onDelete = { if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1) },
                    onClear = { enteredPin = "" }
                )
            }
        }
    }
}

private fun firestorePinError(failure: Exception): String {
    val firestoreFailure = failure as? FirebaseFirestoreException
        ?: failure.cause as? FirebaseFirestoreException
    return when (firestoreFailure?.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Firestore পিন অ্যাক্সেসের অনুমতি দেয়নি। firestore.rules deploy করে আবার চেষ্টা করুন।"
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Firebase-এ default Firestore database তৈরি করে আবার চেষ্টা করুন।"
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Firestore এখন পাওয়া যাচ্ছে না। ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
        else -> "Firestore থেকে পিন পাওয়া যায়নি। ডাটাবেস পরীক্ষা করে আবার চেষ্টা করুন।"
    }
}

@Composable
private fun PinDots(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        repeat(4) { index ->
            Box(
                Modifier.size(13.dp).background(
                    if (index < count) LoginPink else Color.Transparent,
                    CircleShape
                ).then(
                    if (index >= count) Modifier.border(1.5.dp, Color(0xFFAAA6AA), CircleShape) else Modifier
                )
            )
        }
    }
}

@Composable
private fun NumberPad(enabled: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "●"))
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(CircleShape).clickable(enabled = enabled) {
                            when (key) {
                                "⌫" -> onDelete()
                                "●" -> onClear()
                                else -> onDigit(key)
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫" || key == "●") {
                            Box(Modifier.size(28.dp).background(Color(0xFF8B9396), CircleShape), contentAlignment = Alignment.Center) {
                                Text(if (key == "⌫") "×" else "←", color = Color.White, fontSize = 16.sp)
                            }
                        } else {
                            Text(key, color = Color(0xFF656B6D), fontSize = 26.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
