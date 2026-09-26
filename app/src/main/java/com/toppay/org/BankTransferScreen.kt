package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private val BankPink = Color(0xFFE50973)
private val BankInk = Color(0xFF302B30)
private val BankBackground = Color(0xFFF8F7F8)

private enum class BankTransferStep { Form, Review, Pin, Complete }

private val bangladeshBanks = listOf(
    "Agrani Bank", "Al-Arafah Islami Bank", "Bangladesh Commerce Bank", "Bangladesh Development Bank",
    "Bangladesh Krishi Bank", "Bank Asia", "BASIC Bank", "BRAC Bank", "City Bank", "Community Bank Bangladesh",
    "Dhaka Bank", "Dutch-Bangla Bank", "Eastern Bank", "EXIM Bank", "First Security Islami Bank",
    "Global Islami Bank", "IFIC Bank", "Islami Bank Bangladesh", "Jamuna Bank", "Janata Bank",
    "Meghna Bank", "Mercantile Bank", "Midland Bank", "Modhumoti Bank", "Mutual Trust Bank",
    "National Bank", "NCC Bank", "NRB Bank", "NRB Commercial Bank", "One Bank", "Padma Bank",
    "Prime Bank", "Pubali Bank", "Rupali Bank", "Shahjalal Islami Bank", "Social Islami Bank",
    "Sonali Bank", "Southeast Bank", "Standard Bank", "The Premier Bank", "Trust Bank",
    "United Commercial Bank", "Uttara Bank", "অন্যান্য ব্যাংক"
)

@Composable
fun BankTransferScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val firestore = remember { FirebaseFirestore.getInstance() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(BankTransferStep.Form) }
    var bank by rememberSaveable { mutableStateOf("") }
    var customBank by rememberSaveable { mutableStateOf("") }
    var accountName by rememberSaveable { mutableStateOf("") }
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var branch by rememberSaveable { mutableStateOf("") }
    var routing by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var reference by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var balance by remember { mutableDoubleStateOf(0.0) }

    val finalBank = if (bank == "অন্যান্য ব্যাংক") customBank.trim() else bank
    val amountValue = amount.toDoubleOrNull()
    val valid = finalBank.isNotBlank() && accountName.trim().length >= 3 && accountNumber.length in 6..24 &&
        branch.trim().isNotBlank() && (routing.isBlank() || routing.length == 9) &&
        amountValue != null && amountValue > 0 && amountValue <= 25000 && amountValue <= balance

    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val listener = firestore.document("users/$uid").addSnapshotListener { snapshot, _ ->
            balance = snapshot?.getDouble("balance") ?: 0.0
        }
        onDispose { listener.remove() }
    }

    when (step) {
        BankTransferStep.Form -> BankTransferForm(
            modifier, bank, { bank = it; if (it != "অন্যান্য ব্যাংক") customBank = "" }, customBank, { customBank = it },
            accountName, { accountName = it }, accountNumber, { accountNumber = it }, branch, { branch = it },
            routing, { routing = it }, amount, { amount = it }, reference, { reference = it }, balance,
            submitted, valid, onBack
        ) { submitted = true; if (valid) step = BankTransferStep.Review }

        BankTransferStep.Review -> BankTransferReview(finalBank, accountName, accountNumber, branch, routing, amountValue ?: 0.0, reference, { step = BankTransferStep.Form }) { step = BankTransferStep.Pin }

        BankTransferStep.Pin -> BankTransferPin(
            finalBank, accountNumber, amountValue ?: 0.0, pin, { pin = it; error = null }, error, busy,
            { step = BankTransferStep.Review }
        ) {
            if (uid == null) { error = "আপনার সেশনের মেয়াদ শেষ হয়েছে। আবার লগ ইন করুন।"; return@BankTransferPin }
            busy = true
            scope.launch {
                try {
                    val storedPin = firestore.document("users/$uid/private/pin").get().await().getString("pin")
                    if (storedPin != pin) {
                        pin = ""; error = "পিন সঠিক নয়। আবার চেষ্টা করুন।"; return@launch
                    }
                    firestore.collection("users/$uid/depositRequests").add(
                        mapOf(
                            "userId" to uid,
                            "transactionType" to "bank_transfer",
                            "method" to "Bank Transfer",
                            "action" to finalBank,
                            "provider" to finalBank,
                            "bankName" to finalBank,
                            "accountName" to accountName.trim(),
                            "accountNumber" to accountNumber,
                            "branch" to branch.trim(),
                            "routingNumber" to routing,
                            "sourceReference" to accountNumber.takeLast(4),
                            "amount" to (amountValue ?: 0.0),
                            "fee" to 0.0,
                            "total" to (amountValue ?: 0.0),
                            "proofReference" to "PIN_AUTHORIZED_BANK_TRANSFER",
                            "note" to reference.trim(),
                            "status" to "pending",
                            "submittedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    step = BankTransferStep.Complete
                } catch (_: Exception) {
                    error = "ব্যাংক ট্রান্সফার অনুরোধ সংরক্ষণ করা যায়নি। সংযোগ পরীক্ষা করুন।"
                } finally { busy = false }
            }
        }

        BankTransferStep.Complete -> BankTransferComplete(finalBank, accountNumber, amountValue ?: 0.0, onBack)
    }
}

@Composable
private fun BankTransferForm(
    modifier: Modifier, bank: String, onBank: (String) -> Unit, customBank: String, onCustomBank: (String) -> Unit,
    accountName: String, onAccountName: (String) -> Unit, accountNumber: String, onAccountNumber: (String) -> Unit,
    branch: String, onBranch: (String) -> Unit, routing: String, onRouting: (String) -> Unit,
    amount: String, onAmount: (String) -> Unit, reference: String, onReference: (String) -> Unit,
    balance: Double, submitted: Boolean, valid: Boolean, onBack: () -> Unit, onContinue: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(BankBackground)) {
        BankHeader("ব্যাংক ট্রান্সফার", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Color(0xFFE8F4F1), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(50.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("৳", color = Color(0xFF397A6A), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp)); Column { Text("বাংলাদেশের যেকোনো ব্যাংকে", color = BankInk, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("প্রাপকের সঠিক ব্যাংক তথ্য দিন", color = Color(0xFF746D71), fontSize = 11.sp) }
                }
            }
            Text("প্রাপকের ব্যাংক নির্বাচন করুন", color = BankInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(13.dp)) {
                    Text(bank.ifBlank { "ব্যাংক নির্বাচন করুন" }, Modifier.weight(1f), textAlign = TextAlign.Start, color = if (bank.isBlank()) Color.Gray else BankInk); Text("⌄")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, modifier = Modifier.heightIn(max = 330.dp)) {
                    bangladeshBanks.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onBank(item); menuOpen = false }) }
                }
            }
            if (bank == "অন্যান্য ব্যাংক") BankField(customBank, onCustomBank, "ব্যাংকের নাম", submitted && customBank.isBlank())
            BankField(accountName, { onAccountName(it.take(60)) }, "অ্যাকাউন্টধারীর নাম", submitted && accountName.trim().length < 3)
            BankField(accountNumber, { if (it.length <= 24 && it.all(Char::isDigit)) onAccountNumber(it) }, "অ্যাকাউন্ট নম্বর", submitted && accountNumber.length !in 6..24, KeyboardType.Number)
            BankField(branch, { onBranch(it.take(50)) }, "শাখার নাম", submitted && branch.isBlank())
            BankField(routing, { if (it.length <= 9 && it.all(Char::isDigit)) onRouting(it) }, "রাউটিং নম্বর (ঐচ্ছিক)", submitted && routing.isNotBlank() && routing.length != 9, KeyboardType.Number)
            BankField(amount, { if (it.length <= 8 && it.all { c -> c.isDigit() || c == '.' } && it.count { c -> c == '.' } <= 1) onAmount(it) }, "পরিমাণ", submitted && !valid, KeyboardType.Decimal, prefix = "৳ ")
            BankField(reference, { onReference(it.take(50)) }, "রেফারেন্স (ঐচ্ছিক)", false)
            Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE8E3E5))) {
                Row(Modifier.fillMaxWidth().padding(15.dp)) { Text("বর্তমান ব্যালেন্স", color = Color.Gray); Spacer(Modifier.weight(1f)); Text("৳ ${"%,.2f".format(balance)}", color = BankInk, fontWeight = FontWeight.Bold) }
            }
            if (submitted && !valid) Text("সব প্রয়োজনীয় তথ্য সঠিকভাবে পূরণ করুন এবং ব্যালেন্সের মধ্যে পরিমাণ দিন।", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = BankPink), shape = RoundedCornerShape(14.dp)) { Text("লেনদেন যাচাই করুন", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BankTransferReview(bank: String, name: String, number: String, branch: String, routing: String, amount: Double, reference: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BankBackground)) {
        BankHeader("তথ্য যাচাই", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("ব্যাংক ট্রান্সফার নিশ্চিত করুন", color = BankInk, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("অনুমোদনের আগে প্রতিটি তথ্য ভালোভাবে পরীক্ষা করুন।", color = Color.Gray, fontSize = 12.sp)
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFE8E3E5))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    BankSummary("ব্যাংক", bank); BankSummary("অ্যাকাউন্টধারী", name); BankSummary("অ্যাকাউন্ট নম্বর", number); BankSummary("শাখা", branch)
                    if (routing.isNotBlank()) BankSummary("রাউটিং নম্বর", routing)
                    if (reference.isNotBlank()) BankSummary("রেফারেন্স", reference)
                    HorizontalDivider(); BankSummary("মোট", "৳ ${"%,.2f".format(amount)}", true)
                }
            }
            Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(14.dp)) { Text("পরবর্তী ধাপে ৪ সংখ্যার TopPay পিন দিয়ে অনুমোদন করুন।", Modifier.padding(14.dp), color = BankInk, fontSize = 11.sp) }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = BankPink), shape = RoundedCornerShape(14.dp)) { Text("পিন দিয়ে অনুমোদন করুন", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BankTransferPin(bank: String, number: String, amount: Double, pin: String, onPin: (String) -> Unit, error: String?, busy: Boolean, onBack: () -> Unit, onConfirm: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BankBackground)) {
        BankHeader("পিন অনুমোদন", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Box(Modifier.size(72.dp).background(Color(0xFFFFE4F0), CircleShape), contentAlignment = Alignment.Center) { Text("PIN", color = BankPink, fontWeight = FontWeight.Black) }
            Text("আপনার TopPay পিন লিখুন", color = BankInk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("$bank · •••• ${number.takeLast(4)}", color = Color.Gray)
            OutlinedTextField(value = pin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) onPin(it) }, label = { Text("৪ সংখ্যার পিন") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), isError = error != null, supportingText = { error?.let { Text(it) } })
            BankSummary("ট্রান্সফারের পরিমাণ", "৳ ${"%,.2f".format(amount)}", true)
            Text("পিন যাচাইয়ের পরে নিচের বোতামটি চেপে ধরে রাখুন।", color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        BankHoldButton(enabled = pin.length == 4 && !busy, busy = busy, onConfirmed = onConfirm)
    }
}

@Composable
private fun BankHoldButton(enabled: Boolean, busy: Boolean, onConfirmed: () -> Unit) {
    val scope = rememberCoroutineScope(); var holding by remember { mutableStateOf(false) }; var done by remember { mutableStateOf(false) }
    Surface(color = if (enabled) BankPink else Color(0xFFB9B4B7), modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).pointerInput(enabled, busy) {
        detectTapGestures(onPress = {
            if (!enabled || busy) return@detectTapGestures
            holding = true; done = false
            val job = scope.launch { delay(1300); if (holding) { done = true; onConfirmed() } }
            tryAwaitRelease(); holding = false; if (!done) job.cancel()
        })
    }, shape = RoundedCornerShape(14.dp)) { Box(contentAlignment = Alignment.Center) { Text(if (busy) "জমা হচ্ছে…" else if (holding) "ধরে রাখুন…" else "নিশ্চিত করতে ধরে রাখুন", color = Color.White, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun BankTransferComplete(bank: String, number: String, amount: Double, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(90.dp).background(Color(0xFFFFF2D4), CircleShape), contentAlignment = Alignment.Center) { Text("…", color = Color(0xFFA66B00), fontSize = 45.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(22.dp)); Text("অনুমোদনের জন্য জমা হয়েছে", color = BankInk, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("আপনার ব্যাংক ট্রান্সফার অনুরোধটি অ্যাডমিনের অনুমোদনের অপেক্ষায় আছে।", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Surface(color = BankBackground, shape = RoundedCornerShape(17.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) { BankSummary("ব্যাংক", bank); BankSummary("অ্যাকাউন্ট", "•••• ${number.takeLast(4)}"); BankSummary("পরিমাণ", "৳ ${"%,.2f".format(amount)}", true); BankSummary("অবস্থা", "অপেক্ষমাণ") } }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 25.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = BankPink), shape = RoundedCornerShape(14.dp)) { Text("হোমে ফিরে যান", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun BankHeader(title: String, onBack: () -> Unit) { Surface(color = BankPink) { Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }; Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) } } }

@Composable
private fun BankField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean, keyboard: KeyboardType = KeyboardType.Text, prefix: String? = null) { OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, prefix = prefix?.let { { Text(it, color = BankPink, fontWeight = FontWeight.Bold) } }, singleLine = true, isError = isError, keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) }

@Composable
private fun BankSummary(label: String, value: String, important: Boolean = false) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(value, color = if (important) BankPink else BankInk, fontSize = if (important) 15.sp else 12.sp, fontWeight = if (important) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.4f)) } }
