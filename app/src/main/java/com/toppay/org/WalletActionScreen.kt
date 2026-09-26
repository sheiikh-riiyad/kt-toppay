package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.tasks.await

enum class WalletActionType { SendMoney, MobileRecharge, CashOut, Payment, PayBill }
private enum class TransactionStep { Form, Review, Authorize, Complete }

private val ActionPink = Color(0xFFE50973)
private val ActionInk = Color(0xFF302B30)
private val PageBackground = Color(0xFFF8F7F8)

private data class ActionCopy(val title: String, val subtitle: String, val accountLabel: String, val icon: String)

@Composable
fun WalletActionScreen(
    type: WalletActionType,
    modifier: Modifier = Modifier,
    initialProvider: String? = null,
    providerLocked: Boolean = false,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val copy = when (type) {
        WalletActionType.SendMoney -> ActionCopy("সেন্ড মানি", "বিকাশ, নগদ বা রকেটে সহজে টাকা পাঠান", "প্রাপকের নম্বর", "S")
        WalletActionType.MobileRecharge -> ActionCopy("মোবাইল রিচার্জ", "যেকোনো বাংলাদেশি মোবাইল নম্বরে রিচার্জ করুন", "মোবাইল নম্বর", "R")
        WalletActionType.CashOut -> ActionCopy("ক্যাশ আউট", "আপনার পছন্দের সেবার মাধ্যমে টাকা তুলুন", "এজেন্ট নম্বর", "C")
        WalletActionType.Payment -> ActionCopy("পেমেন্ট", "মার্চেন্ট নম্বরে পেমেন্ট করুন", "মার্চেন্ট নম্বর", "P")
        WalletActionType.PayBill -> ActionCopy("বিল পরিশোধ", "বিদ্যুৎ, ইন্টারনেট, গ্যাস ও পানির বিল দিন", "বিল অ্যাকাউন্ট নম্বর", "B")
    }
    var account by rememberSaveable(type) { mutableStateOf("") }
    var amount by rememberSaveable(type) { mutableStateOf("") }
    var reference by rememberSaveable(type) { mutableStateOf("") }
    var operator by rememberSaveable(type) { mutableStateOf("Grameenphone") }
    var rechargeType by rememberSaveable(type) { mutableStateOf("Prepaid") }
    var sendProvider by rememberSaveable(type, initialProvider) { mutableStateOf(initialProvider ?: "bKash") }
    var cashOutProvider by rememberSaveable(type, initialProvider) { mutableStateOf(initialProvider ?: "bKash") }
    var paymentProvider by rememberSaveable(type, initialProvider) { mutableStateOf(initialProvider ?: "bKash") }
    var billProvider by rememberSaveable(type) { mutableStateOf("NESCO (Postpaid)") }
    var step by rememberSaveable(type) { mutableStateOf(TransactionStep.Form) }
    var submitted by remember { mutableStateOf(false) }
    var transactionError by remember { mutableStateOf<String?>(null) }
    var accountBalance by remember { mutableDoubleStateOf(0.0) }
    val amountValue = amount.toDoubleOrNull()
    val validAccount = if (type == WalletActionType.PayBill) account.length in 6..24 else account.length == 11 && account.startsWith("01")
    val validAmount = amountValue != null && amountValue > 0 && amountValue <= 25_000
    val fee = if (type == WalletActionType.CashOut && amountValue != null) amountValue * .0185 else 0.0
    val total = (amountValue ?: 0.0) + fee

    DisposableEffect(uid) {
        if (uid == null) return@DisposableEffect onDispose { }
        val registration = FirebaseFirestore.getInstance().document("users/$uid")
            .addSnapshotListener { snapshot, _ -> accountBalance = snapshot?.getDouble("balance") ?: 0.0 }
        onDispose { registration.remove() }
    }

    when (step) {
        TransactionStep.Form -> TransactionForm(copy, type, modifier, account, { account = it }, amount, { amount = it }, reference, { reference = it }, operator, { operator = it }, rechargeType, { rechargeType = it }, sendProvider, { sendProvider = it }, cashOutProvider, { cashOutProvider = it }, paymentProvider, { paymentProvider = it }, billProvider, { billProvider = it }, providerLocked, submitted, validAccount, validAmount, fee, total, accountBalance, onBack) {
            submitted = true
            if (validAccount && validAmount) step = TransactionStep.Review
        }
        TransactionStep.Review -> ReviewScreen(copy, type, account, amountValue ?: 0.0, fee, total, reference, operator, rechargeType, sendProvider, cashOutProvider, paymentProvider, billProvider, { step = TransactionStep.Form }, { step = TransactionStep.Authorize })
        TransactionStep.Authorize -> PinAuthorizationScreen(copy, account, total, { step = TransactionStep.Review }, {
            val requiresApproval = true
            if (!requiresApproval) {
                step = TransactionStep.Complete
            } else if (uid == null) {
                transactionError = "আপনার সেশনের মেয়াদ শেষ হয়েছে। আবার সাইন ইন করুন।"
                step = TransactionStep.Review
            } else {
                scope.launch {
                    try {
                        FirebaseFirestore.getInstance().collection("users/$uid/depositRequests").add(
                            mapOf(
                                "userId" to uid,
                                "transactionType" to when (type) { WalletActionType.SendMoney -> "send_money"; WalletActionType.MobileRecharge -> "mobile_recharge"; WalletActionType.CashOut -> "cash_out"; WalletActionType.Payment -> "payment"; WalletActionType.PayBill -> "pay_bill" },
                                "method" to when (type) { WalletActionType.SendMoney -> "Send Money"; WalletActionType.MobileRecharge -> "Mobile Recharge"; WalletActionType.CashOut -> "Cash Out"; WalletActionType.Payment -> "Payment"; WalletActionType.PayBill -> "Pay Bill" },
                                "action" to when (type) { WalletActionType.SendMoney -> sendProvider; WalletActionType.MobileRecharge -> "$operator · $rechargeType"; WalletActionType.CashOut -> cashOutProvider; WalletActionType.Payment -> paymentProvider; WalletActionType.PayBill -> billProvider },
                                "provider" to when (type) { WalletActionType.SendMoney -> sendProvider; WalletActionType.MobileRecharge -> operator; WalletActionType.CashOut -> cashOutProvider; WalletActionType.Payment -> paymentProvider; WalletActionType.PayBill -> billProvider },
                                "rechargeType" to if (type == WalletActionType.MobileRecharge) rechargeType else "",
                                "sourceReference" to account,
                                "recipientNumber" to account,
                                "merchantNumber" to if (type == WalletActionType.Payment) account else "",
                                "agentNumber" to if (type == WalletActionType.CashOut) account else "",
                                "billAccountNumber" to if (type == WalletActionType.PayBill) account else "",
                                "amount" to (amountValue ?: 0.0),
                                "fee" to fee,
                                "total" to total,
                                "proofReference" to reference.ifBlank {
                                    when (type) {
                                        WalletActionType.SendMoney -> "PIN_AUTHORIZED_SEND_MONEY"
                                        WalletActionType.MobileRecharge -> "PIN_AUTHORIZED_RECHARGE"
                                        WalletActionType.CashOut -> "PIN_AUTHORIZED_CASH_OUT"
                                        WalletActionType.Payment -> "PIN_AUTHORIZED_PAYMENT"
                                        WalletActionType.PayBill -> "PIN_AUTHORIZED_PAY_BILL"
                                    }
                                },
                                "note" to reference,
                                "status" to "pending",
                                "submittedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                        step = TransactionStep.Complete
                    } catch (_: Exception) {
                        transactionError = "${copy.title} লেনদেনটি সংরক্ষণ করা যায়নি। সংযোগ ও Firestore rules পরীক্ষা করুন।"
                        step = TransactionStep.Review
                    }
                }
            }
        })
        TransactionStep.Complete -> SuccessScreen(copy, account, total, pendingApproval = true, onBack)
    }

    transactionError?.let { message ->
        AlertDialog(
            onDismissRequest = { transactionError = null },
            title = { Text("লেনদেন সংরক্ষণ হয়নি") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { transactionError = null }) { Text("আবার চেষ্টা করুন", color = ActionPink) } }
        )
    }
}

@Composable
private fun TransactionForm(
    copy: ActionCopy, type: WalletActionType, modifier: Modifier,
    account: String, onAccountChange: (String) -> Unit,
    amount: String, onAmountChange: (String) -> Unit,
    reference: String, onReferenceChange: (String) -> Unit,
    operator: String, onOperatorChange: (String) -> Unit,
    rechargeType: String, onRechargeTypeChange: (String) -> Unit,
    sendProvider: String, onSendProviderChange: (String) -> Unit,
    cashOutProvider: String, onCashOutProviderChange: (String) -> Unit,
    paymentProvider: String, onPaymentProviderChange: (String) -> Unit,
    billProvider: String, onBillProviderChange: (String) -> Unit,
    providerLocked: Boolean,
    submitted: Boolean, validAccount: Boolean, validAmount: Boolean,
    fee: Double, total: Double, accountBalance: Double, onBack: () -> Unit, onContinue: () -> Unit
) {
    Column(modifier.fillMaxSize().background(PageBackground)) {
        ActionHeader(copy.title, copy.icon, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(copy.icon, color = ActionPink, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(14.dp))
                    Column { Text(copy.title, color = ActionInk, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(copy.subtitle, color = Color(0xFF7C7379), fontSize = 12.sp) }
                }
            }
            if (type == WalletActionType.SendMoney) {
                Text("কোথায় টাকা পাঠাবেন", color = ActionInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (providerLocked) SelectedProviderCard(sendProvider) else SendProviderSelector(sendProvider, onSendProviderChange)
            }
            if (type == WalletActionType.CashOut) {
                Text("ক্যাশ আউট সেবা নির্বাচন করুন", color = ActionInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (providerLocked) SelectedProviderCard(cashOutProvider) else SendProviderSelector(cashOutProvider, onCashOutProviderChange)
            }
            if (type == WalletActionType.Payment) {
                Text("পেমেন্ট সেবা নির্বাচন করুন", color = ActionInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (providerLocked) SelectedProviderCard(paymentProvider) else SendProviderSelector(paymentProvider, onPaymentProviderChange, includeRocket = false)
            }
            if (type == WalletActionType.PayBill) {
                Text("বিল প্রদানকারী নির্বাচন করুন", color = ActionInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                PayBillProviderSelector(billProvider, onBillProviderChange)
            }
            if (type == WalletActionType.MobileRecharge) {
                Text("অপারেটর নির্বাচন করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Grameenphone", "Robi", "Banglalink", "Airtel").forEach { item ->
                        FilterChip(selected = operator == item, onClick = { onOperatorChange(item) }, label = { Text(item.take(2), fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFD8E9), selectedLabelColor = ActionPink))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Prepaid", "Postpaid").forEach { item -> FilterChip(selected = rechargeType == item, onClick = { onRechargeTypeChange(item) }, label = { Text(if (item == "Prepaid") "প্রিপেইড" else "পোস্টপেইড") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ActionPink, selectedLabelColor = Color.White)) }
                }
            }
            OutlinedTextField(value = account, onValueChange = { if (it.length <= (if (type == WalletActionType.PayBill) 24 else 11) && it.all(Char::isDigit)) onAccountChange(it) }, label = { Text(copy.accountLabel) }, placeholder = { Text(if (type == WalletActionType.PayBill) "গ্রাহক/অ্যাকাউন্ট নম্বর লিখুন" else "01XXXXXXXXX") }, singleLine = true, isError = submitted && !validAccount, supportingText = { if (submitted && !validAccount) Text(if (type == WalletActionType.PayBill) "৬–২৪ সংখ্যার সঠিক বিল অ্যাকাউন্ট নম্বর লিখুন" else "সঠিক ১১ সংখ্যার মোবাইল নম্বর লিখুন") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(value = amount, onValueChange = { value -> if (value.length <= 7 && value.all { it.isDigit() || it == '.' } && value.count { it == '.' } <= 1) onAmountChange(value) }, label = { Text("পরিমাণ") }, prefix = { Text("৳ ", color = ActionPink, fontWeight = FontWeight.Bold) }, singleLine = true, isError = submitted && !validAmount, supportingText = { if (submitted && !validAmount) Text("৳১ থেকে ৳২৫,০০০-এর মধ্যে পরিমাণ লিখুন") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            if (type != WalletActionType.MobileRecharge) OutlinedTextField(value = reference, onValueChange = { if (it.length <= 40) onReferenceChange(it) }, label = { Text("রেফারেন্স (ঐচ্ছিক)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            SummaryCard(type, total, fee, operator, rechargeType, accountBalance)
            Text("পরবর্তী ধাপে তথ্য যাচাই করে পিন দিয়ে অনুমোদন করুন।", color = Color(0xFF8B8288), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        BottomButton("লেনদেন যাচাই করুন", onContinue)
    }
}

@Composable
private fun ReviewScreen(copy: ActionCopy, type: WalletActionType, account: String, amount: Double, fee: Double, total: Double, reference: String, operator: String, rechargeType: String, sendProvider: String, cashOutProvider: String, paymentProvider: String, billProvider: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        ActionHeader("লেনদেন যাচাই", copy.icon, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StepIndicator(0)
            Text("সব তথ্য ভালোভাবে দেখুন", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ActionInk)
            Text("অনুমোদনের পরে এই লেনদেন সম্পাদনা করা যাবে না।", color = Color(0xFF756D72), fontSize = 13.sp)
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFEAE4E7))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryLine("সেবা", copy.title); SummaryLine(copy.accountLabel, account)
                    if (type == WalletActionType.SendMoney) SummaryLine("সেবাদাতা", sendProvider)
                    if (type == WalletActionType.CashOut) SummaryLine("সেবাদাতা", cashOutProvider)
                    if (type == WalletActionType.Payment) SummaryLine("সেবাদাতা", paymentProvider)
                    if (type == WalletActionType.PayBill) SummaryLine("বিল প্রদানকারী", billProvider)
                    if (type == WalletActionType.MobileRecharge) SummaryLine("প্যাকেজ", "$operator · ${if (rechargeType == "Prepaid") "প্রিপেইড" else "পোস্টপেইড"}")
                    if (reference.isNotBlank()) SummaryLine("রেফারেন্স", reference)
                    HorizontalDivider(color = Color(0xFFEDE8EA)); SummaryLine("পরিমাণ", "৳ ${"%.2f".format(amount)}")
                    if (fee > 0) SummaryLine("ফি", "৳ ${"%.2f".format(fee)}")
                    SummaryLine("মোট", "৳ ${"%.2f".format(total)}", true)
                }
            }
            Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(14.dp)) { Text("পরবর্তী ধাপে পরিচয় যাচাই করতে ৪ সংখ্যার টপপে পিন লিখুন।", Modifier.padding(15.dp), color = ActionInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        BottomButton("পিন ধাপে যান", onContinue)
    }
}

@Composable
private fun PinAuthorizationScreen(copy: ActionCopy, account: String, total: Double, onBack: () -> Unit, onComplete: () -> Unit) {
    val scope = rememberCoroutineScope()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var pin by rememberSaveable { mutableStateOf("") }
    var verified by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun verifyPin() {
        if (pin.length != 4 || busy || uid == null) return
        busy = true; error = null
        scope.launch {
            try {
                val savedPin = FirebaseFirestore.getInstance().document("users/$uid/private/pin").get().await().getString("pin")
                if (savedPin == pin) verified = true else { pin = ""; error = "পিন সঠিক নয়। আবার চেষ্টা করুন।" }
            } catch (_: Exception) { error = "পিন যাচাই করা যায়নি। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        ActionHeader("অনুমোদন", copy.icon, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StepIndicator(if (verified) 2 else 1)
            Box(Modifier.size(70.dp).background(Color(0xFFFFE2EF), CircleShape), contentAlignment = Alignment.Center) { Text(if (verified) "✓" else "PIN", color = ActionPink, fontSize = if (verified) 30.sp else 17.sp, fontWeight = FontWeight.Bold) }
            Text(if (verified) "পিন যাচাই হয়েছে" else "আপনার পিন লিখুন", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ActionInk)
            Text(if (verified) "লেনদেন অনুমোদন করতে নিচের বোতামটি চেপে ধরে রাখুন।" else "আপনার টপপে অ্যাকাউন্টের ৪ সংখ্যার পিন লিখুন।", color = Color(0xFF756D72), fontSize = 13.sp, textAlign = TextAlign.Center)
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFEAE4E7))) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SummaryLine(copy.accountLabel, account); SummaryLine("মোট", "৳ ${"%.2f".format(total)}", true) } }
            if (!verified) {
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = null } }, label = { Text("4-digit PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), isError = error != null, supportingText = { error?.let { Text(it) } })
                Button(onClick = ::verifyPin, enabled = pin.length == 4 && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text("পিন যাচাই করুন", fontWeight = FontWeight.Bold) }
            } else {
                HoldToConfirmButton(onComplete)
                Text("১.৫ সেকেন্ড ধরে রাখুন। আগে ছেড়ে দিলে নিশ্চিতকরণ বাতিল হবে।", color = Color(0xFF8B8288), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HoldToConfirmButton(onConfirmed: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    var confirmed by remember { mutableStateOf(false) }
    Surface(color = if (holding) Color(0xFFB3085B) else ActionPink, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(58.dp).pointerInput(Unit) {
        detectTapGestures(onPress = {
            holding = true
            val completed = try {
                coroutineScope {
                    val released = async { tryAwaitRelease() }
                    val timer = async { delay(1_500); true }
                    val result = select {
                        released.onAwait { false }
                        timer.onAwait { true }
                    }
                    released.cancel()
                    timer.cancel()
                    result
                }
            } finally { holding = false }
            if (completed && !confirmed) { confirmed = true; onConfirmed() }
        })
    }) { Box(contentAlignment = Alignment.Center) { Text(if (holding) "ধরে রাখুন…" else "নিশ্চিত করতে ধরে রাখুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun SuccessScreen(copy: ActionCopy, account: String, total: Double, pendingApproval: Boolean, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(92.dp).background(if (pendingApproval) Color(0xFFFFF2D4) else Color(0xFFE2F6E9), CircleShape), contentAlignment = Alignment.Center) { Text(if (pendingApproval) "…" else "✓", color = if (pendingApproval) Color(0xFFA66B00) else Color(0xFF16864A), fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp)); Text(if (pendingApproval) "অনুমোদনের জন্য জমা হয়েছে" else "লেনদেন সফল হয়েছে", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = ActionInk, textAlign = TextAlign.Center); Spacer(Modifier.height(8.dp)); Text(if (pendingApproval) "আপনার ${copy.title} অনুরোধটি অ্যাডমিনের অনুমোদনের অপেক্ষায় আছে।" else "${copy.title} সফলভাবে সম্পন্ন হয়েছে", color = Color(0xFF756D72), textAlign = TextAlign.Center); Spacer(Modifier.height(28.dp))
        Surface(color = PageBackground, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { SummaryLine(copy.accountLabel, account); SummaryLine("মোট", "৳ ${"%.2f".format(total)}", true); SummaryLine("অবস্থা", if (pendingApproval) "অপেক্ষমাণ" else "সফল") } }
        Spacer(Modifier.height(28.dp)); Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { Text("হোমে ফিরে যান", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun SendProviderSelector(selected: String, onSelect: (String) -> Unit, includeRocket: Boolean = true) {
    val providers = listOf(
        "bKash" to R.drawable.bkash_logo,
        "Nagad" to R.drawable.nagad_logo,
        "Rocket" to R.drawable.rocket_logo
    ).let { if (includeRocket) it else it.take(2) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        providers.forEach { (name, logo) ->
            Surface(
                onClick = { onSelect(name) },
                modifier = Modifier.weight(1f),
                color = if (selected == name) Color(0xFFFFE4F0) else Color.White,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(if (selected == name) 1.5.dp else 1.dp, if (selected == name) ActionPink else Color(0xFFE7E1E4))
            ) {
                Column(Modifier.padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(38.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(logo), contentDescription = "$name logo", modifier = Modifier.size(34.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(name, color = if (selected == name) ActionPink else ActionInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SelectedProviderCard(provider: String) {
    val logo = when (provider) {
        "Nagad" -> R.drawable.nagad_logo
        "Rocket" -> R.drawable.rocket_logo
        else -> R.drawable.bkash_logo
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFFEAF3),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, ActionPink)
    ) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(logo), contentDescription = "$provider লোগো", modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(provider, color = ActionInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("নির্বাচিত পেমেন্ট সেবা", color = Color(0xFF83767C), fontSize = 10.sp)
            }
            Surface(color = ActionPink, shape = RoundedCornerShape(50)) {
                Text("নির্বাচিত", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PayBillProviderSelector(selected: String, onSelect: (String) -> Unit) {
    val providers = listOf(
        "NESCO (Postpaid)", "NESCO (Prepaid)",
        "DESCO", "DPDC", "Palli Bidyut",
        "Titas Gas", "Dhaka WASA", "Internet"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        providers.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { provider ->
                    val active = selected == provider
                    Surface(
                        onClick = { onSelect(provider) },
                        modifier = Modifier.weight(1f),
                        color = if (active) Color(0xFFFFE4F0) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(if (active) 1.5.dp else 1.dp, if (active) ActionPink else Color(0xFFE7E1E4))
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp).background(if (active) ActionPink else Color(0xFFF2ECEF), CircleShape), contentAlignment = Alignment.Center) {
                                Text(when { "Internet" in provider -> "I"; "Gas" in provider -> "G"; "WASA" in provider -> "W"; else -> "E" }, color = if (active) Color.White else ActionInk, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.width(7.dp))
                            Text(provider, color = if (active) ActionPink else ActionInk, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionHeader(title: String, icon: String, onBack: () -> Unit) { Surface(color = ActionPink, shadowElevation = 3.dp) { Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }; Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(Modifier.size(38.dp).background(Color.White.copy(alpha = .17f), CircleShape), contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontWeight = FontWeight.Bold) } } } }

@Composable
private fun BottomButton(text: String, onClick: () -> Unit) { Surface(color = Color.White, shadowElevation = 8.dp) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) } } }

@Composable
private fun StepIndicator(active: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { listOf("যাচাই", "পিন", "নিশ্চিত").forEachIndexed { index, label -> if (index > 0) HorizontalDivider(Modifier.width(34.dp), color = if (index <= active) ActionPink else Color(0xFFDAD4D7)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(27.dp).background(if (index <= active) ActionPink else Color(0xFFE4DFE1), CircleShape), contentAlignment = Alignment.Center) { Text("${index + 1}", color = if (index <= active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Text(label, fontSize = 9.sp, color = if (index <= active) ActionPink else Color.Gray) } } } }

@Composable
private fun SummaryCard(type: WalletActionType, total: Double, fee: Double, operator: String, rechargeType: String, accountBalance: Double) { Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFECE7EA))) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { SummaryLine("বর্তমান ব্যালেন্স", "৳ ${"%,.2f".format(accountBalance)}"); if (type == WalletActionType.CashOut) SummaryLine("ক্যাশ আউট ফি", "৳ ${"%.2f".format(fee)}"); if (type == WalletActionType.MobileRecharge) SummaryLine("রিচার্জের ধরন", "$operator · ${if (rechargeType == "Prepaid") "প্রিপেইড" else "পোস্টপেইড"}"); HorizontalDivider(color = Color(0xFFF0ECEE)); SummaryLine("মোট", "৳ ${"%.2f".format(total)}", true) } } }

@Composable
private fun SummaryLine(label: String, value: String, strong: Boolean = false) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = if (strong) ActionInk else Color(0xFF766F74), fontSize = if (strong) 14.sp else 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal); Spacer(Modifier.width(12.dp)); Text(value, color = if (strong) ActionPink else ActionInk, fontSize = if (strong) 15.sp else 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.End) } }
