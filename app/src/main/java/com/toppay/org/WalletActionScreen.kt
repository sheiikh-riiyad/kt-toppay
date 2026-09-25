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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.tasks.await

enum class WalletActionType { SendMoney, MobileRecharge, CashOut, Payment }
private enum class TransactionStep { Form, Review, Authorize, Complete }

private val ActionPink = Color(0xFFE50973)
private val ActionInk = Color(0xFF302B30)
private val PageBackground = Color(0xFFF8F7F8)

private data class ActionCopy(val title: String, val subtitle: String, val accountLabel: String, val icon: String)

@Composable
fun WalletActionScreen(type: WalletActionType, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val copy = when (type) {
        WalletActionType.SendMoney -> ActionCopy("Send Money", "Send money instantly to a TopPay account", "Recipient number", "S")
        WalletActionType.MobileRecharge -> ActionCopy("Mobile Recharge", "Recharge any Bangladeshi mobile number", "Mobile number", "R")
        WalletActionType.CashOut -> ActionCopy("Cash Out", "Withdraw money through a TopPay agent", "Agent number", "C")
        WalletActionType.Payment -> ActionCopy("Payment", "Pay a merchant using their TopPay number", "Merchant number", "P")
    }
    var account by rememberSaveable(type) { mutableStateOf("") }
    var amount by rememberSaveable(type) { mutableStateOf("") }
    var reference by rememberSaveable(type) { mutableStateOf("") }
    var operator by rememberSaveable(type) { mutableStateOf("Grameenphone") }
    var rechargeType by rememberSaveable(type) { mutableStateOf("Prepaid") }
    var step by rememberSaveable(type) { mutableStateOf(TransactionStep.Form) }
    var submitted by remember { mutableStateOf(false) }
    val amountValue = amount.toDoubleOrNull()
    val validAccount = account.length == 11 && account.startsWith("01")
    val validAmount = amountValue != null && amountValue > 0 && amountValue <= 25_000
    val fee = if (type == WalletActionType.CashOut && amountValue != null) amountValue * .0185 else 0.0
    val total = (amountValue ?: 0.0) + fee

    when (step) {
        TransactionStep.Form -> TransactionForm(copy, type, modifier, account, { account = it }, amount, { amount = it }, reference, { reference = it }, operator, { operator = it }, rechargeType, { rechargeType = it }, submitted, validAccount, validAmount, fee, total, onBack) {
            submitted = true
            if (validAccount && validAmount) step = TransactionStep.Review
        }
        TransactionStep.Review -> ReviewScreen(copy, type, account, amountValue ?: 0.0, fee, total, reference, operator, rechargeType, { step = TransactionStep.Form }, { step = TransactionStep.Authorize })
        TransactionStep.Authorize -> PinAuthorizationScreen(copy, account, total, { step = TransactionStep.Review }, { step = TransactionStep.Complete })
        TransactionStep.Complete -> SuccessScreen(copy, account, total, onBack)
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
    submitted: Boolean, validAccount: Boolean, validAmount: Boolean,
    fee: Double, total: Double, onBack: () -> Unit, onContinue: () -> Unit
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
            if (type == WalletActionType.MobileRecharge) {
                Text("Select operator", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("Grameenphone", "Robi", "Banglalink", "Airtel").forEach { item ->
                        FilterChip(selected = operator == item, onClick = { onOperatorChange(item) }, label = { Text(item.take(2), fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFD8E9), selectedLabelColor = ActionPink))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Prepaid", "Postpaid").forEach { item -> FilterChip(selected = rechargeType == item, onClick = { onRechargeTypeChange(item) }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ActionPink, selectedLabelColor = Color.White)) }
                }
            }
            OutlinedTextField(value = account, onValueChange = { if (it.length <= 11 && it.all(Char::isDigit)) onAccountChange(it) }, label = { Text(copy.accountLabel) }, placeholder = { Text("01XXXXXXXXX") }, singleLine = true, isError = submitted && !validAccount, supportingText = { if (submitted && !validAccount) Text("Enter a valid 11-digit mobile number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            OutlinedTextField(value = amount, onValueChange = { value -> if (value.length <= 7 && value.all { it.isDigit() || it == '.' } && value.count { it == '.' } <= 1) onAmountChange(value) }, label = { Text("Amount") }, prefix = { Text("৳ ", color = ActionPink, fontWeight = FontWeight.Bold) }, singleLine = true, isError = submitted && !validAmount, supportingText = { if (submitted && !validAmount) Text("Enter an amount between ৳1 and ৳25,000") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            if (type != WalletActionType.MobileRecharge) OutlinedTextField(value = reference, onValueChange = { if (it.length <= 40) onReferenceChange(it) }, label = { Text("Reference (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            SummaryCard(type, total, fee, operator, rechargeType)
            Text("You will review and authorize this transaction with your PIN.", color = Color(0xFF8B8288), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        BottomButton("Review transaction", onContinue)
    }
}

@Composable
private fun ReviewScreen(copy: ActionCopy, type: WalletActionType, account: String, amount: Double, fee: Double, total: Double, reference: String, operator: String, rechargeType: String, onBack: () -> Unit, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        ActionHeader("Review transaction", copy.icon, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StepIndicator(0)
            Text("Check everything carefully", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ActionInk)
            Text("Your transaction cannot be edited after authorization.", color = Color(0xFF756D72), fontSize = 13.sp)
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFEAE4E7))) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SummaryLine("Service", copy.title); SummaryLine(copy.accountLabel, account)
                    if (type == WalletActionType.MobileRecharge) SummaryLine("Package", "$operator · $rechargeType")
                    if (reference.isNotBlank()) SummaryLine("Reference", reference)
                    HorizontalDivider(color = Color(0xFFEDE8EA)); SummaryLine("Amount", "৳ ${"%.2f".format(amount)}")
                    if (fee > 0) SummaryLine("Fee", "৳ ${"%.2f".format(fee)}")
                    SummaryLine("Total", "৳ ${"%.2f".format(total)}", true)
                }
            }
            Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(14.dp)) { Text("Next, enter your 4-digit TopPay PIN to verify your identity.", Modifier.padding(15.dp), color = ActionInk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        }
        BottomButton("Continue to PIN", onContinue)
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
                if (savedPin == pin) verified = true else { pin = ""; error = "Incorrect PIN. Please try again." }
            } catch (_: Exception) { error = "Could not verify your PIN. Check your connection and retry." }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().background(PageBackground)) {
        ActionHeader("Authorize", copy.icon, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            StepIndicator(if (verified) 2 else 1)
            Box(Modifier.size(70.dp).background(Color(0xFFFFE2EF), CircleShape), contentAlignment = Alignment.Center) { Text(if (verified) "✓" else "PIN", color = ActionPink, fontSize = if (verified) 30.sp else 17.sp, fontWeight = FontWeight.Bold) }
            Text(if (verified) "PIN verified" else "Enter your PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = ActionInk)
            Text(if (verified) "Press and hold below to authorize the transaction." else "Enter the 4-digit PIN linked to your TopPay account.", color = Color(0xFF756D72), fontSize = 13.sp, textAlign = TextAlign.Center)
            Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFEAE4E7))) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { SummaryLine(copy.accountLabel, account); SummaryLine("Total", "৳ ${"%.2f".format(total)}", true) } }
            if (!verified) {
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = null } }, label = { Text("4-digit PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), isError = error != null, supportingText = { error?.let { Text(it) } })
                Button(onClick = ::verifyPin, enabled = pin.length == 4 && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text("Verify PIN", fontWeight = FontWeight.Bold) }
            } else {
                HoldToConfirmButton(onComplete)
                Text("Keep holding for 1.5 seconds. Releasing early cancels confirmation.", color = Color(0xFF8B8288), fontSize = 11.sp, textAlign = TextAlign.Center)
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
    }) { Box(contentAlignment = Alignment.Center) { Text(if (holding) "Keep holding…" else "Hold to confirm", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun SuccessScreen(copy: ActionCopy, account: String, total: Double, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(92.dp).background(Color(0xFFE2F6E9), CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = Color(0xFF16864A), fontSize = 48.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(24.dp)); Text("Transaction successful", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = ActionInk); Spacer(Modifier.height(8.dp)); Text("${copy.title} completed successfully", color = Color(0xFF756D72)); Spacer(Modifier.height(28.dp))
        Surface(color = PageBackground, shape = RoundedCornerShape(18.dp)) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { SummaryLine(copy.accountLabel, account); SummaryLine("Total", "৳ ${"%.2f".format(total)}", true); SummaryLine("Status", "Successful") } }
        Spacer(Modifier.height(28.dp)); Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { Text("Back to home", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ActionHeader(title: String, icon: String, onBack: () -> Unit) { Surface(color = ActionPink, shadowElevation = 3.dp) { Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }; Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Box(Modifier.size(38.dp).background(Color.White.copy(alpha = .17f), CircleShape), contentAlignment = Alignment.Center) { Text(icon, color = Color.White, fontWeight = FontWeight.Bold) } } } }

@Composable
private fun BottomButton(text: String, onClick: () -> Unit) { Surface(color = Color.White, shadowElevation = 8.dp) { Button(onClick = onClick, modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(18.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = ActionPink), shape = RoundedCornerShape(14.dp)) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold) } } }

@Composable
private fun StepIndicator(active: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { listOf("Review", "PIN", "Confirm").forEachIndexed { index, label -> if (index > 0) HorizontalDivider(Modifier.width(34.dp), color = if (index <= active) ActionPink else Color(0xFFDAD4D7)); Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(27.dp).background(if (index <= active) ActionPink else Color(0xFFE4DFE1), CircleShape), contentAlignment = Alignment.Center) { Text("${index + 1}", color = if (index <= active) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold) }; Text(label, fontSize = 9.sp, color = if (index <= active) ActionPink else Color.Gray) } } } }

@Composable
private fun SummaryCard(type: WalletActionType, total: Double, fee: Double, operator: String, rechargeType: String) { Surface(color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFECE7EA))) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { SummaryLine("Available balance", "৳ 24,850.00"); if (type == WalletActionType.CashOut) SummaryLine("Cash-out fee", "৳ ${"%.2f".format(fee)}"); if (type == WalletActionType.MobileRecharge) SummaryLine("Recharge type", "$operator · $rechargeType"); HorizontalDivider(color = Color(0xFFF0ECEE)); SummaryLine("Total", "৳ ${"%.2f".format(total)}", true) } } }

@Composable
private fun SummaryLine(label: String, value: String, strong: Boolean = false) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = if (strong) ActionInk else Color(0xFF766F74), fontSize = if (strong) 14.sp else 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal); Spacer(Modifier.width(12.dp)); Text(value, color = if (strong) ActionPink else ActionInk, fontSize = if (strong) 15.sp else 12.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.End) } }
