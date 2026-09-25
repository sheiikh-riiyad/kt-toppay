package com.toppay.org

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.DateFormat

private val AddPink = Color(0xFFE50973)
private val AddInk = Color(0xFF302B30)
private val AddMuted = Color(0xFF756D72)

private data class DepositRequest(
    val id: String,
    val method: String,
    val action: String,
    val amount: Double,
    val proof: String,
    val status: String,
    val submittedAt: Timestamp?
)
private data class BankDestination(val bankName: String, val accountName: String, val accountNumber: String, val branch: String)
private data class SavedFundingCard(val id: String, val brand: String, val holderName: String, val expiry: String, val last4: String, val topPayCode: String)

@Composable
fun AddMoneyScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val firestore = remember { FirebaseFirestore.getInstance() }
    val collection = remember(uid) { uid?.let { firestore.collection("users/$it/depositRequests") } }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var method by rememberSaveable { mutableStateOf("Bank Transfer") }
    var walletAction by rememberSaveable { mutableStateOf("Send Money") }
    var source by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var proof by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var requests by remember { mutableStateOf(emptyList<DepositRequest>()) }
    var showSuccess by remember { mutableStateOf(false) }
    var walletAccounts by remember { mutableStateOf(emptyMap<String, String>()) }
    var bankAccounts by remember { mutableStateOf(emptyMap<String, BankDestination>()) }
    var selectedBank by rememberSaveable { mutableStateOf("") }
    var accountConfigLoading by remember { mutableStateOf(true) }
    var savedCards by remember { mutableStateOf(emptyList<SavedFundingCard>()) }
    var selectedCardId by rememberSaveable { mutableStateOf("") }
    var cardCvv by rememberSaveable { mutableStateOf("") }
    var showCardPin by remember { mutableStateOf(false) }
    val amountValue = amount.toDoubleOrNull()
    val mobileWallet = method in listOf("bKash", "Nagad", "Rocket")
    val cardPayment = method == "Pay by Card"
    val selectedCard = savedCards.firstOrNull { it.id == selectedCardId }
    val sourceValid = when { mobileWallet -> source.length == 11 && source.startsWith("01"); cardPayment -> selectedCard != null && selectedCard.topPayCode.isNotBlank() && cardCvv == selectedCard.topPayCode; else -> source.length >= 4 }
    val formValid = amountValue != null && amountValue in 10.0..100_000.0 && sourceValid && (cardPayment || proof.length >= 5)

    fun loadRequests() {
        val requestCollection = collection ?: run { loading = false; return }
        scope.launch {
            loading = true
            try {
                requests = requestCollection.get().await().documents.mapNotNull { doc ->
                    DepositRequest(doc.id, doc.getString("method") ?: return@mapNotNull null, doc.getString("action") ?: "Transfer", doc.getDouble("amount") ?: 0.0, doc.getString("proofReference").orEmpty(), doc.getString("status") ?: "pending", doc.getTimestamp("submittedAt"))
                }.sortedByDescending { it.submittedAt?.seconds ?: 0 }
            } catch (_: Exception) { snackbar.showSnackbar("Could not load add-money requests") }
            finally { loading = false }
        }
    }
    LaunchedEffect(uid) {
        loadRequests()
        accountConfigLoading = true
        try {
            if (uid != null) {
                val userSnapshot = firestore.document("users/$uid").get().await()
                savedCards = (userSnapshot.get("cards") as? List<*>)?.mapNotNull { raw ->
                    val card = raw as? Map<*, *> ?: return@mapNotNull null
                    SavedFundingCard(card["id"]?.toString() ?: return@mapNotNull null, card["brand"]?.toString() ?: "Card", card["holderName"]?.toString().orEmpty(), card["expiry"]?.toString().orEmpty(), card["last4"]?.toString().orEmpty(), card["topPayCode"]?.toString().orEmpty())
                }.orEmpty()
                if (selectedCardId.isBlank()) selectedCardId = savedCards.firstOrNull()?.id.orEmpty()
            }
            val walletSnapshot = firestore.document("addmoney/account").get().await()
            walletAccounts = walletSnapshot.data.orEmpty().mapNotNull { (key, value) ->
                val account = value?.toString()?.trim().orEmpty()
                if (account.isBlank()) null else key.lowercase() to account
            }.toMap()
            val bankIndex = firestore.document("addmoney/bank").get().await()
            val configuredBankNames = (bankIndex.get("bankNames") as? List<*>)
                ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
            // BRAC Bank is the default path used by the current Firestore setup.
            // bankNames remains supported as an index when more banks are added.
            val bankNames = (configuredBankNames + listOf("BRAC Bank", "IBBPLC")).distinct()
            bankAccounts = bankNames.flatMap { bankName ->
                firestore.collection("addmoney/bank/$bankName").get().await().documents.mapNotNull { accountDoc ->
                    val accountName = accountDoc.getString("AccountName").orEmpty()
                    val accountNumber = accountDoc.getString("AccountNumber").orEmpty()
                    val branch = accountDoc.getString("Branch").orEmpty()
                    if (accountNumber.isBlank()) null else {
                        val displayName = if (accountName.isBlank()) bankName else "$bankName · $accountName"
                        displayName to BankDestination(bankName, accountName, accountNumber, branch)
                    }
                }
            }.toMap()
            if (selectedBank.isBlank()) selectedBank = bankAccounts.keys.firstOrNull().orEmpty()
        } catch (_: Exception) {
            snackbar.showSnackbar("Could not load receiving-account details")
        } finally { accountConfigLoading = false }
    }

    val destinationAccount = when (method) {
        "bKash" -> walletAccounts[if (walletAction == "Cash Out") "bkashagent" else "bkashpersonal"]
        "Nagad" -> walletAccounts[if (walletAction == "Cash Out") "nagatagent" else "nagatpersonal"]
            ?: walletAccounts[if (walletAction == "Cash Out") "nagadagent" else "nagadpersonal"]
        "Rocket" -> walletAccounts[if (walletAction == "Cash Out") "rocketagent" else "rocketpersonal"]
        else -> null
    }

    fun submitRequest() {
        val requestCollection = collection ?: return
        sending = true
        scope.launch {
            try {
                requestCollection.add(mapOf("userId" to uid, "method" to method, "action" to if (mobileWallet) walletAction else "Transfer", "sourceReference" to (selectedCard?.last4 ?: source.takeLast(4)), "cardId" to (selectedCard?.id ?: ""), "cardBrand" to (selectedCard?.brand ?: ""), "amount" to amountValue, "proofReference" to if (cardPayment) "PIN_AUTHORIZED_CARD" else proof.trim(), "note" to note.trim(), "status" to "pending", "submittedAt" to FieldValue.serverTimestamp())).await()
                amount = ""; source = ""; cardCvv = ""; proof = ""; note = ""; submitted = false; showSuccess = true; loadRequests()
            } catch (_: Exception) { snackbar.showSnackbar("Could not submit request. Check rules and connection.") }
            finally { sending = false }
        }
    }

    Box(modifier.fillMaxSize().background(Color(0xFFF8F6F7))) {
        Column(Modifier.fillMaxSize()) {
            AddMoneyHeader(onBack)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(color = Color(0xFFFFE8F2), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text("+৳", color = AddPink, fontWeight = FontWeight.Black, fontSize = 18.sp) }
                        Spacer(Modifier.width(13.dp)); Column { Text("Add money request", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text("Submit payment proof for administrator approval", color = AddMuted, fontSize = 11.sp) }
                    }
                }
                Text("Choose payment method", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                MethodGrid(method) { method = it; source = ""; cardCvv = "" }
                if (cardPayment) SavedCardSelector(savedCards, selectedCardId) { selectedCardId = it; cardCvv = "" }
                if (method == "Bank Transfer" && bankAccounts.isNotEmpty()) {
                    Text("Select receiving bank", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        bankAccounts.values.map { it.bankName }.distinct().forEach { bankName ->
                            val selected = bankAccounts[selectedBank]?.bankName == bankName
                            FilterChip(selected = selected, onClick = { selectedBank = bankAccounts.entries.first { it.value.bankName == bankName }.key }, label = { Text(bankName) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AddPink, selectedLabelColor = Color.White))
                        }
                    }
                }
                if (mobileWallet) {
                    Text("Transfer type", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("Send Money", "Cash Out").forEach { item -> FilterChip(selected = walletAction == item, onClick = { walletAction = item }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AddPink, selectedLabelColor = Color.White)) }
                    }
                    PaymentInstruction(method, walletAction, destinationAccount, null, accountConfigLoading)
                } else PaymentInstruction(method, "Transfer", null, bankAccounts[selectedBank], accountConfigLoading)
                OutlinedTextField(value = amount, onValueChange = { value -> if (value.length <= 9 && value.all { it.isDigit() || it == '.' } && value.count { it == '.' } <= 1) amount = value }, label = { Text("Amount") }, prefix = { Text("৳ ", color = AddPink, fontWeight = FontWeight.Bold) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), isError = submitted && (amountValue == null || amountValue !in 10.0..100_000.0), supportingText = { if (submitted && (amountValue == null || amountValue !in 10.0..100_000.0)) Text("Enter an amount from ৳10 to ৳100,000") })
                if (cardPayment) {
                    OutlinedTextField(value = cardCvv, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) cardCvv = it }, label = { Text("CVV") }, placeholder = { Text("Code for selected card") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), isError = submitted && !sourceValid, supportingText = { Text(when { selectedCard?.topPayCode.isNullOrBlank() -> "This older card has no CVV. Remove it and save it again."; submitted && cardCvv != selectedCard?.topPayCode -> "Incorrect code for the selected card"; else -> "Enter the app security code created when this card was saved" }) })
                } else {
                    OutlinedTextField(value = source, onValueChange = { value -> if (value.length <= (if (mobileWallet) 11 else 30) && (!mobileWallet || value.all(Char::isDigit))) source = value }, label = { Text(if (mobileWallet) "Sender mobile number" else "Sender bank account last 4 digits") }, keyboardOptions = KeyboardOptions(keyboardType = if (mobileWallet) KeyboardType.Phone else KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), isError = submitted && !sourceValid, supportingText = { if (submitted && !sourceValid) Text("Enter valid sender information") })
                }
                if (!cardPayment) OutlinedTextField(value = proof, onValueChange = { proof = it.take(60) }, label = { Text("Payment proof / transaction ID") }, placeholder = { Text("Enter the transaction reference") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), isError = submitted && proof.length < 5, supportingText = { Text(if (submitted && proof.length < 5) "Enter a valid payment reference" else "Required for administrator verification") })
                OutlinedTextField(value = note, onValueChange = { note = it.take(150) }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3, shape = RoundedCornerShape(13.dp))
                Surface(color = Color(0xFFFFF5D9), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFF1D98E))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) { Text("!", color = Color(0xFFA66B00), fontWeight = FontWeight.Black); Spacer(Modifier.width(10.dp)); Text("Your balance will not change immediately. The request remains Pending until an administrator verifies the payment and approves it.", color = Color(0xFF695328), fontSize = 11.sp, lineHeight = 17.sp) }
                }
                Button(onClick = {
                    submitted = true
                    if (!formValid || sending || collection == null) return@Button
                    if (cardPayment) showCardPin = true else submitRequest()
                }, enabled = !sending, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = AddPink), shape = RoundedCornerShape(14.dp)) { if (sending) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp) else Text("Submit for approval", fontWeight = FontWeight.Bold) }
                RequestHistory(requests, loading)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    if (showSuccess) AlertDialog(onDismissRequest = { showSuccess = false }, icon = { Box(Modifier.size(52.dp).background(Color(0xFFFFE2EF), CircleShape), contentAlignment = Alignment.Center) { Text("✓", color = AddPink, fontSize = 27.sp, fontWeight = FontWeight.Bold) } }, title = { Text("Request submitted") }, text = { Text("Your add-money request is Pending. Your balance will update only after administrator approval.") }, confirmButton = { TextButton(onClick = { showSuccess = false }) { Text("View status", color = AddPink) } })
    if (showCardPin && uid != null) CardPaymentPinDialog(uid, firestore, { showCardPin = false }) { showCardPin = false; submitRequest() }
}

@Composable
private fun AddMoneyHeader(onBack: () -> Unit) { Surface(color = AddPink, shadowElevation = 3.dp) { Row(Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }; Text("Add Money", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f)); Box(Modifier.size(38.dp).background(Color.White.copy(.17f), CircleShape), contentAlignment = Alignment.Center) { Text("+৳", color = Color.White, fontWeight = FontWeight.Bold) } } } }

@Composable
private fun CardPaymentPinDialog(uid: String, firestore: FirebaseFirestore, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        icon = { Box(Modifier.size(52.dp).background(Color(0xFFFFE5F0), CircleShape), contentAlignment = Alignment.Center) { Text("PIN", color = AddPink, fontWeight = FontWeight.Black) } },
        title = { Text("Confirm card payment", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter your 4-digit TopPay PIN to authorize this saved card.", color = AddMuted, fontSize = 12.sp, lineHeight = 18.sp)
                OutlinedTextField(value = pin, onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) { pin = it; error = null } }, label = { Text("TopPay PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), isError = error != null, supportingText = { error?.let { Text(it) } }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Text("Your account PIN and CVV are never included in the add-money request.", color = AddMuted, fontSize = 9.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (pin.length != 4 || checking) return@Button
                checking = true; error = null
                scope.launch {
                    try {
                        val storedPin = firestore.document("users/$uid/private/pin").get().await().getString("pin")
                        if (storedPin == pin) onVerified() else { pin = ""; error = "Incorrect PIN. Try again." }
                    } catch (_: Exception) { error = "Could not verify PIN. Check your connection." }
                    finally { checking = false }
                }
            }, enabled = pin.length == 4 && !checking, colors = ButtonDefaults.buttonColors(containerColor = AddPink), shape = RoundedCornerShape(11.dp)) { if (checking) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp) else Text("Confirm", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !checking) { Text("Cancel", color = AddMuted) } }
    )
}

@Composable
private fun MethodGrid(selected: String, onSelect: (String) -> Unit) {
    val methods = listOf(
        Triple("Bank Transfer", "B", null),
        Triple("Pay by Card", "C", null),
        Triple("bKash", "", R.drawable.bkash_logo),
        Triple("Nagad", "", R.drawable.nagad_logo),
        Triple("Rocket", "", R.drawable.rocket_logo)
    )
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        methods.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) { row.forEach { (name, icon, logo) -> Surface(onClick = { onSelect(name) }, modifier = Modifier.weight(1f), color = if (selected == name) Color(0xFFFFE5F0) else Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(if (selected == name) 1.5.dp else 1.dp, if (selected == name) AddPink else Color(0xFFE5DFE2))) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(38.dp).background(if (logo == null && selected == name) AddPink else Color.White, CircleShape), contentAlignment = Alignment.Center) { if (logo != null) Image(painter = painterResource(logo), contentDescription = "$name logo", modifier = Modifier.size(34.dp), contentScale = ContentScale.Fit) else Text(icon, color = if (selected == name) Color.White else AddInk, fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(8.dp)); Text(name, color = AddInk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }; if (row.size == 1) Spacer(Modifier.weight(1f)) } }
    }
}

@Composable
private fun SavedCardSelector(cards: List<SavedFundingCard>, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Select a saved card", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (cards.isEmpty()) {
            Surface(color = Color(0xFFFFF3D8), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFF0D48A))) {
                Text("No saved cards found. Add a card from My TopPay → Payment Cards first.", Modifier.fillMaxWidth().padding(15.dp), color = Color(0xFF6D5420), fontSize = 11.sp, lineHeight = 17.sp)
            }
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                cards.forEach { card ->
                    val selected = card.id == selectedId
                    val colors = cardColors(card.brand)
                    Surface(
                        modifier = Modifier.width(238.dp).height(132.dp).clickable { onSelect(card.id) },
                        color = colors.first,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) AddPink else colors.second.copy(alpha = .35f)),
                        shadowElevation = if (selected) 5.dp else 1.dp
                    ) {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CardNetworkBadge(card.brand)
                                Spacer(Modifier.weight(1f))
                                Box(Modifier.size(22.dp).background(if (selected) AddPink else Color.White.copy(.22f), CircleShape), contentAlignment = Alignment.Center) { if (selected) Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            }
                            Spacer(Modifier.weight(1f))
                            Text("••••  ••••  ••••  ${card.last4}", color = colors.second, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(Modifier.height(8.dp))
                            Row { Column(Modifier.weight(1f)) { Text("CARD HOLDER", color = colors.second.copy(.7f), fontSize = 7.sp); Text(card.holderName.ifBlank { "TOPPAY MEMBER" }.uppercase(), color = colors.second, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Column(horizontalAlignment = Alignment.End) { Text("EXPIRES", color = colors.second.copy(.7f), fontSize = 7.sp); Text(card.expiry, color = colors.second, fontSize = 9.sp, fontWeight = FontWeight.SemiBold) } }
                        }
                    }
                }
            }
            Text("Swipe sideways to see all saved cards.", color = AddMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun CardNetworkBadge(brand: String) {
    val label = when (brand) { "American Express" -> "AMEX"; "Mastercard" -> "MC"; "Diners Club" -> "DINERS"; else -> brand.uppercase() }
    Surface(color = Color.White, shape = RoundedCornerShape(7.dp)) { Text(label, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = when (brand) { "Visa" -> Color(0xFF1434CB); "American Express" -> Color(0xFF2E77BC); "Mastercard" -> Color(0xFFEB001B); else -> Color(0xFF34343A) }, fontSize = 9.sp, fontWeight = FontWeight.Black) }
}

private fun cardColors(brand: String): Pair<Color, Color> = when (brand) {
    "Visa" -> Color(0xFF172B85) to Color.White
    "Mastercard" -> Color(0xFF242126) to Color.White
    "American Express" -> Color(0xFF1976B9) to Color.White
    "Discover" -> Color(0xFFF2F2F2) to Color(0xFF252525)
    "JCB" -> Color(0xFF075D4D) to Color.White
    "UnionPay" -> Color(0xFF0A6FAD) to Color.White
    else -> Color(0xFF3A3037) to Color.White
}

@Composable
private fun PaymentInstruction(method: String, action: String, walletAccount: String?, bank: BankDestination?, loading: Boolean) {
    val context = LocalContext.current
    val destination = when {
        loading -> "Loading receiving account…"
        method == "Bank Transfer" -> "No receiving bank has been configured"
        method == "Pay by Card" -> "Complete the card payment, then enter its reference"
        walletAccount != null -> "$action to $walletAccount"
        else -> "No $method $action account has been configured"
    }
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFE9E3E6))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PAYMENT INSTRUCTION", color = AddPink, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                if (method == "Bank Transfer" && bank != null) {
                    OutlinedButton(
                        onClick = {
                            val details = "Bank: ${bank.bankName}\nAccount Name: ${bank.accountName}\nAccount Number: ${bank.accountNumber}\nBranch: ${bank.branch}"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("TopPay bank details", details))
                        },
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, AddPink)
                    ) { Text("Copy", color = AddPink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
            }
            if (method == "Bank Transfer" && bank != null) {
                Text(bank.bankName, color = AddInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                BankDetailLine("Account name", bank.accountName.ifBlank { "Not provided" })
                BankDetailLine("Account number", bank.accountNumber, important = true)
                BankDetailLine("Branch", bank.branch.ifBlank { "Not provided" })
            } else {
                Text(destination, color = AddInk, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = Color(0xFFF0EAED))
            Text("Keep the transaction ID as your payment proof.", color = AddMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun BankDetailLine(label: String, value: String, important: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AddMuted, fontSize = 10.sp, modifier = Modifier.width(96.dp))
        Text(value, color = if (important) AddPink else AddInk, fontSize = if (important) 14.sp else 12.sp, fontWeight = if (important) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
private fun RequestHistory(requests: List<DepositRequest>, loading: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Recent requests", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AddPink, trackColor = Color(0xFFFFDDEB))
        if (!loading && requests.isEmpty()) Surface(color = Color.White, shape = RoundedCornerShape(15.dp)) { Text("No add-money requests yet.", Modifier.fillMaxWidth().padding(22.dp), color = AddMuted, textAlign = TextAlign.Center, fontSize = 11.sp) }
        requests.take(10).forEach { request ->
            val statusColor = when (request.status.lowercase()) { "approved" -> Color(0xFF16864A); "rejected" -> Color(0xFFC43D57); else -> Color(0xFFA66B00) }
            val statusBg = when (request.status.lowercase()) { "approved" -> Color(0xFFE3F6EA); "rejected" -> Color(0xFFFFE8EC); else -> Color(0xFFFFF3D1) }
            Surface(color = Color.White, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFEDE7EA))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(Color(0xFFFFE7F1), CircleShape), contentAlignment = Alignment.Center) { Text("+", color = AddPink, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text("${request.method} · ${request.action}", color = AddInk, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("Proof: ${request.proof}", color = AddMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); request.submittedAt?.let { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()), color = AddMuted, fontSize = 8.sp) } }
                    Column(horizontalAlignment = Alignment.End) { Text("৳ ${"%,.2f".format(request.amount)}", color = AddInk, fontWeight = FontWeight.Bold, fontSize = 12.sp); Surface(color = statusBg, shape = RoundedCornerShape(50)) { Text(request.status.replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }
                }
            }
        }
    }
}
