package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class WalletProfile(
    val name: String = "TopPay member",
    val email: String? = null,
    val uid: String = "",
    val emailVerified: Boolean = false,
    val createdAt: Long? = null,
    val lastSignInAt: Long? = null
)

private data class PersonalInfo(val firstName: String = "", val lastName: String = "", val identityType: String = "NID", val identityNumber: String = "", val mobileNumber: String = "")
private data class BankInfo(val id: String, val bankName: String, val holderName: String, val accountType: String, val last4: String)
private data class CardInfo(val id: String, val brand: String, val holderName: String, val expiry: String, val last4: String, val topPayCode: String)

private val ProfilePink = Color(0xFFE50973)
private val ProfileDarkPink = Color(0xFFA7175D)
private val ProfileInk = Color(0xFF302B30)
private val ProfileMuted = Color(0xFF756D72)
private val ProfileBackground = Color(0xFFF8F6F7)

@Composable
fun ProfileScreen(
    profile: WalletProfile,
    onSignOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onNotificationLog: () -> Unit = {}
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val document = remember(profile.uid) { if (profile.uid.isNotBlank()) db.document("users/${profile.uid}") else null }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var personal by remember { mutableStateOf(PersonalInfo(firstName = profile.name.substringBefore(" "), lastName = profile.name.substringAfter(" ", ""))) }
    var banks by remember { mutableStateOf(emptyList<BankInfo>()) }
    var cards by remember { mutableStateOf(emptyList<CardInfo>()) }
    var loading by remember { mutableStateOf(document != null) }
    var showPersonalForm by remember { mutableStateOf(false) }
    var showBankForm by remember { mutableStateOf(false) }
    var showCardForm by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deletingBank by remember { mutableStateOf<BankInfo?>(null) }
    var deletingCard by remember { mutableStateOf<CardInfo?>(null) }

    fun refresh() {
        if (document == null) { loading = false; return }
        scope.launch {
            loading = true
            try {
                val snapshot = document.get().await()
                personal = PersonalInfo(
                    snapshot.getString("firstName") ?: personal.firstName,
                    snapshot.getString("lastName") ?: personal.lastName,
                    snapshot.getString("identityType") ?: "NID",
                    snapshot.getString("identityNumber") ?: "",
                    snapshot.getString("mobileNumber") ?: ""
                )
                banks = (snapshot.get("bankAccounts") as? List<*>)?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { BankInfo(it["id"]?.toString() ?: return@let null, it["bankName"]?.toString().orEmpty(), it["holderName"]?.toString().orEmpty(), it["accountType"]?.toString() ?: "Savings", it["last4"]?.toString().orEmpty()) }
                } ?: emptyList()
                cards = (snapshot.get("cards") as? List<*>)?.mapNotNull { raw ->
                    (raw as? Map<*, *>)?.let { CardInfo(it["id"]?.toString() ?: return@let null, it["brand"]?.toString() ?: "Card", it["holderName"]?.toString().orEmpty(), it["expiry"]?.toString().orEmpty(), it["last4"]?.toString().orEmpty(), it["topPayCode"]?.toString().orEmpty()) }
                } ?: emptyList()
            } catch (_: Exception) { snackbar.showSnackbar("প্রোফাইল লোড করা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।") }
            finally { loading = false }
        }
    }
    LaunchedEffect(profile.uid) { refresh() }

    Box(modifier.fillMaxSize().background(ProfileBackground)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ProfileHero(profile, personal)
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = ProfilePink, trackColor = Color(0xFFFFD8E9))
                CompletionCard(personal, banks, cards)
                ProfileSection("ব্যক্তিগত তথ্য", "আপনার যাচাইকৃত অ্যাকাউন্টের তথ্য", "সম্পাদনা", { showPersonalForm = true }) {
                    ProfileRow("নাম", listOf(personal.firstName, personal.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "আপনার নাম যোগ করুন" })
                    ProfileDivider()
                    ProfileRow("মোবাইল নম্বর", personal.mobileNumber.ifBlank { "যোগ করা হয়নি" })
                    ProfileDivider()
                    ProfileRow(personal.identityType, maskIdentity(personal.identityNumber).ifBlank { "যোগ করা হয়নি" })
                    ProfileDivider()
                    ProfileRow("ইমেইল", profile.email ?: "পাওয়া যায়নি", if (profile.emailVerified) "যাচাইকৃত" else null)
                }
                ProfileSection("ব্যাংক অ্যাকাউন্ট", "TopPay-তে ব্যবহারযোগ্য অ্যাকাউন্ট", "+ ব্যাংক যোগ করুন", { showBankForm = true }) {
                    if (banks.isEmpty()) EmptyMethod("কোনো ব্যাংক অ্যাকাউন্ট সংরক্ষিত নেই", "টাকা জমা ও উত্তোলনের জন্য একটি অ্যাকাউন্ট যোগ করুন।")
                    banks.forEachIndexed { index, bank ->
                        PaymentMethodRow("B", bank.bankName, "${bank.accountType}  •••• ${bank.last4}", bank.holderName, null) { deletingBank = bank }
                        if (index < banks.lastIndex) ProfileDivider()
                    }
                }
                ProfileSection("পেমেন্ট কার্ড", "শনাক্তকরণের জন্য প্রয়োজনীয় তথ্যই শুধু সংরক্ষিত হয়", "+ কার্ড যোগ করুন", { showCardForm = true }) {
                    if (cards.isEmpty()) EmptyMethod("কোনো কার্ড সংরক্ষিত নেই", "পেমেন্টের জন্য একটি ডেবিট বা ক্রেডিট কার্ড যোগ করুন।")
                    cards.forEachIndexed { index, card ->
                        PaymentMethodRow("C", "${card.brand} •••• ${card.last4}", "মেয়াদ ${card.expiry}", card.holderName, card.brand) { deletingCard = card }
                        if (index < cards.lastIndex) ProfileDivider()
                    }
                }
                Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Text("🔒", fontSize = 20.sp); Spacer(Modifier.width(12.dp))
                        Column { Text("আপনার গোপনীয়তা গুরুত্বপূর্ণ", color = ProfileInk, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("TopPay সম্পূর্ণ কার্ড নম্বর সংরক্ষণ করে না। প্রকৃত লেনদেন চালুর আগে নিরাপদ পেমেন্ট সেবা যুক্ত করতে হবে।", color = ProfileMuted, fontSize = 11.sp, lineHeight = 17.sp) }
                    }
                }
                Surface(
                    onClick = onNotificationLog,
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 1.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(Color(0xFFFFE7F1), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("N", color = ProfilePink, fontWeight = FontWeight.Black) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("নোটিফিকেশন লগ", color = ProfileInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("ডিভাইসের নোটিফিকেশন লোকাল ফাইলে রাখুন", color = ProfileMuted, fontSize = 10.sp)
                        }
                        Text("›", color = ProfilePink, fontSize = 26.sp)
                    }
                }
                OutlinedButton(onClick = { confirmSignOut = true }, enabled = onSignOut != null, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, Color(0xFFE8B8CC)), colors = ButtonDefaults.outlinedButtonColors(contentColor = ProfilePink)) { Text("লগ আউট", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (showPersonalForm) PersonalInfoDialog(personal, { showPersonalForm = false }) { updated ->
        document ?: return@PersonalInfoDialog
        scope.launch {
            try {
                document.set(mapOf("firstName" to updated.firstName.trim(), "lastName" to updated.lastName.trim(), "displayName" to "${updated.firstName.trim()} ${updated.lastName.trim()}".trim(), "identityType" to updated.identityType, "identityNumber" to updated.identityNumber.trim(), "mobileNumber" to updated.mobileNumber, "profileUpdatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
                personal = updated; showPersonalForm = false; snackbar.showSnackbar("ব্যক্তিগত তথ্য সংরক্ষিত হয়েছে")
            } catch (_: Exception) { snackbar.showSnackbar("আপনার তথ্য সংরক্ষণ করা যায়নি") }
        }
    }
    if (showBankForm) BankDialog({ showBankForm = false }) { bankName, holder, accountType, accountNumber ->
        document ?: return@BankDialog
        val item = BankInfo(System.currentTimeMillis().toString(), bankName.trim(), holder.trim(), accountType, accountNumber.takeLast(4))
        scope.launch {
            try {
                val map = mapOf("id" to item.id, "bankName" to item.bankName, "holderName" to item.holderName, "accountType" to item.accountType, "last4" to item.last4)
                document.update("bankAccounts", FieldValue.arrayUnion(map)).await(); banks = banks + item; showBankForm = false; snackbar.showSnackbar("ব্যাংক অ্যাকাউন্ট সংরক্ষিত হয়েছে")
            } catch (_: Exception) { snackbar.showSnackbar("ব্যাংক অ্যাকাউন্ট সংরক্ষণ করা যায়নি") }
        }
    }
    if (showCardForm) CardDialog({ showCardForm = false }) { holder, number, expiry, topPayCode ->
        document ?: return@CardDialog
        val item = CardInfo(System.currentTimeMillis().toString(), cardBrand(number), holder.trim(), expiry, number.takeLast(4), topPayCode)
        scope.launch {
            try {
                val map = mapOf("id" to item.id, "brand" to item.brand, "holderName" to item.holderName, "expiry" to item.expiry, "last4" to item.last4, "topPayCode" to item.topPayCode)
                document.update("cards", FieldValue.arrayUnion(map)).await(); cards = cards + item; showCardForm = false; snackbar.showSnackbar("কার্ড নিরাপদে সংরক্ষিত হয়েছে")
            } catch (_: Exception) { snackbar.showSnackbar("কার্ড সংরক্ষণ করা যায়নি") }
        }
    }
    deletingBank?.let { bank -> ConfirmDelete("ব্যাংক অ্যাকাউন্ট মুছবেন?", "${bank.bankName} •••• ${bank.last4}", { deletingBank = null }) {
        scope.launch { try { document?.update("bankAccounts", banks.filter { it.id != bank.id }.map { mapOf("id" to it.id, "bankName" to it.bankName, "holderName" to it.holderName, "accountType" to it.accountType, "last4" to it.last4) })?.await(); banks = banks.filter { it.id != bank.id }; snackbar.showSnackbar("Bank account removed") } catch (_: Exception) { snackbar.showSnackbar("Could not remove account") }; deletingBank = null }
    } }
    deletingCard?.let { card -> ConfirmDelete("কার্ড মুছবেন?", "${card.brand} •••• ${card.last4}", { deletingCard = null }) {
        scope.launch { try { document?.update("cards", cards.filter { it.id != card.id }.map { mapOf("id" to it.id, "brand" to it.brand, "holderName" to it.holderName, "expiry" to it.expiry, "last4" to it.last4, "topPayCode" to it.topPayCode) })?.await(); cards = cards.filter { it.id != card.id }; snackbar.showSnackbar("Card removed") } catch (_: Exception) { snackbar.showSnackbar("Could not remove card") }; deletingCard = null }
    } }
    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false }, title = { Text("TopPay থেকে লগ আউট করবেন?") }, text = { Text("আপনাকে Google অ্যাকাউন্ট ও পিন আবার যাচাই করতে হবে।") }, confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut?.invoke() }) { Text("লগ আউট", color = ProfilePink) } }, dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("বাতিল") } })
}

@Composable
private fun ProfileHero(profile: WalletProfile, personal: PersonalInfo) {
    val name = listOf(personal.firstName, personal.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { profile.name }
    val initials = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "T" }
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(ProfileDarkPink, ProfilePink))).statusBarsPadding().padding(22.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("আমার TopPay", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Surface(color = Color.White.copy(.18f), shape = RoundedCornerShape(50)) { Text("অ্যাকাউন্ট", Modifier.padding(horizontal = 11.dp, vertical = 6.dp), color = Color.White, fontSize = 9.sp, letterSpacing = 1.sp) } }
            Spacer(Modifier.height(22.dp)); Box(Modifier.size(82.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) { Text(initials, color = ProfilePink, fontSize = 28.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp)); Text(name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(profile.email ?: "TopPay member", color = Color.White.copy(.82f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun CompletionCard(personal: PersonalInfo, banks: List<BankInfo>, cards: List<CardInfo>) {
    val steps = listOf(personal.firstName.isNotBlank() && personal.lastName.isNotBlank(), personal.mobileNumber.isNotBlank(), personal.identityNumber.isNotBlank(), banks.isNotEmpty() || cards.isNotEmpty())
    val percent = steps.count { it } / steps.size.toFloat()
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFECE6E9))) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Row { Text("প্রোফাইল সম্পন্ন", color = ProfileInk, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.weight(1f)); Text("${(percent * 100).toInt()}%", color = ProfilePink, fontWeight = FontWeight.Bold) }; LinearProgressIndicator(progress = { percent }, modifier = Modifier.fillMaxWidth().height(7.dp), color = ProfilePink, trackColor = Color(0xFFFFDDEB)); Text(if (percent == 1f) "আপনার অ্যাকাউন্ট প্রোফাইল সম্পূর্ণ।" else "যাচাইয়ের জন্য আপনার সব তথ্য পূরণ করুন।", color = ProfileMuted, fontSize = 11.sp) } }
}

@Composable
private fun ProfileSection(title: String, subtitle: String, action: String, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(9.dp)) { Row(verticalAlignment = Alignment.Bottom) { Column(Modifier.weight(1f)) { Text(title, color = ProfileInk, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .5.sp); Text(subtitle, color = ProfileMuted, fontSize = 10.sp) }; TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text(action, color = ProfilePink, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }; Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFECE6E9))) { Column(Modifier.fillMaxWidth(), content = content) } } }

@Composable
private fun ProfileRow(label: String, value: String, badge: String? = null) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(label, color = ProfileMuted, fontSize = 10.sp); Spacer(Modifier.height(4.dp)); Text(value, color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }; badge?.let { Surface(color = Color(0xFFE3F6EA), shape = RoundedCornerShape(50)) { Text(it, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color(0xFF16864A), fontSize = 9.sp, fontWeight = FontWeight.Bold) } } } }

@Composable
private fun PaymentMethodRow(icon: String, title: String, subtitle: String, holder: String, cardBrand: String?, onRemove: () -> Unit) { Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) { if (cardBrand == null) Box(Modifier.size(48.dp).background(Color(0xFFFFE7F1), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(icon, color = ProfilePink, fontWeight = FontWeight.Bold) } else CardBrandLogo(cardBrand); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, color = ProfileInk, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = ProfileMuted, fontSize = 11.sp); Text(holder, color = ProfileMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text("মুছুন", color = ProfilePink, fontSize = 10.sp, modifier = Modifier.clickable(onClick = onRemove).padding(6.dp)) } }

@Composable
private fun CardBrandLogo(brand: String) {
    val shape = RoundedCornerShape(10.dp)
    when (brand) {
        "Visa" -> Box(Modifier.size(52.dp, 36.dp).background(Color.White, shape), contentAlignment = Alignment.Center) { Text("VISA", color = Color(0xFF1434CB), fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp) }
        "Mastercard" -> Box(Modifier.size(52.dp, 36.dp).background(Color.White, shape), contentAlignment = Alignment.Center) { Box(Modifier.offset(x = (-7).dp).size(23.dp).background(Color(0xFFEB001B), CircleShape)); Box(Modifier.offset(x = 7.dp).size(23.dp).background(Color(0xFFF79E1B).copy(alpha = .9f), CircleShape)) }
        "American Express" -> Box(Modifier.size(52.dp, 36.dp).background(Color(0xFF2E77BC), shape), contentAlignment = Alignment.Center) { Text("AMEX", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        "Discover" -> Box(Modifier.size(52.dp, 36.dp).background(Color.White, shape), contentAlignment = Alignment.Center) { Text("DISC\nOVER", color = Color(0xFF1D1D1D), fontSize = 8.sp, lineHeight = 8.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.ExtraBold) }
        "JCB" -> Row(Modifier.size(52.dp, 36.dp).background(Color.White, shape).padding(7.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { listOf(Color(0xFF1976B9) to "J", Color(0xFFD52B3F) to "C", Color(0xFF159447) to "B").forEach { (color, letter) -> Box(Modifier.weight(1f).fillMaxHeight().background(color, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { Text(letter, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) } } }
        "UnionPay" -> Box(Modifier.size(52.dp, 36.dp).background(Color(0xFF0A6FAD), shape), contentAlignment = Alignment.Center) { Text("UnionPay", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black) }
        "Diners Club" -> Box(Modifier.size(52.dp, 36.dp).background(Color.White, shape), contentAlignment = Alignment.Center) { Text("DC", color = Color(0xFF0079BE), fontSize = 14.sp, fontWeight = FontWeight.Black) }
        "Maestro" -> Box(Modifier.size(52.dp, 36.dp).background(Color.White, shape), contentAlignment = Alignment.Center) { Box(Modifier.offset(x = (-7).dp).size(23.dp).background(Color(0xFF009DDD), CircleShape)); Box(Modifier.offset(x = 7.dp).size(23.dp).background(Color(0xFFED1C2E).copy(alpha = .85f), CircleShape)) }
        else -> Box(Modifier.size(52.dp, 36.dp).background(Color(0xFF34343A), shape), contentAlignment = Alignment.Center) { Text("CARD", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun EmptyMethod(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(title, color = ProfileInk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Spacer(Modifier.height(4.dp)); Text(subtitle, color = ProfileMuted, fontSize = 10.sp, textAlign = TextAlign.Center) } }
@Composable private fun ProfileDivider() { HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Color(0xFFF0ECEE)) }

@Composable
private fun PersonalInfoDialog(current: PersonalInfo, onDismiss: () -> Unit, onSave: (PersonalInfo) -> Unit) {
    var first by rememberSaveable { mutableStateOf(current.firstName) }; var last by rememberSaveable { mutableStateOf(current.lastName) }; var type by rememberSaveable { mutableStateOf(current.identityType) }; var identity by rememberSaveable { mutableStateOf(current.identityNumber) }; var mobile by rememberSaveable { mutableStateOf(current.mobileNumber) }; var submitted by remember { mutableStateOf(false) }
    val valid = first.isNotBlank() && last.isNotBlank() && identity.length in 6..20 && mobile.length == 11 && mobile.startsWith("01")
    FormDialog("ব্যক্তিগত তথ্য", onDismiss, { submitted = true; if (valid) onSave(PersonalInfo(first, last, type, identity, mobile)) }, "তথ্য সংরক্ষণ করুন") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FormField(first, { first = it.take(35) }, "নামের প্রথম অংশ", Modifier.weight(1f), submitted && first.isBlank()); FormField(last, { last = it.take(35) }, "নামের শেষ অংশ", Modifier.weight(1f), submitted && last.isBlank()) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("NID", "Passport").forEach { item -> FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFD8E9), selectedLabelColor = ProfilePink)) } }
        FormField(identity, { if (it.length <= 20) identity = it }, "$type number", Modifier.fillMaxWidth(), submitted && identity.length !in 6..20, KeyboardType.Text)
        FormField(mobile, { if (it.length <= 11 && it.all(Char::isDigit)) mobile = it }, "মোবাইল নম্বর", Modifier.fillMaxWidth(), submitted && (mobile.length != 11 || !mobile.startsWith("01")), KeyboardType.Phone)
    }
}

@Composable
private fun BankDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var bank by rememberSaveable { mutableStateOf("") }; var holder by rememberSaveable { mutableStateOf("") }; var type by rememberSaveable { mutableStateOf("Savings") }; var number by rememberSaveable { mutableStateOf("") }; var submitted by remember { mutableStateOf(false) }
    val valid = bank.isNotBlank() && holder.isNotBlank() && number.length in 6..24
    FormDialog("ব্যাংক অ্যাকাউন্ট যোগ করুন", onDismiss, { submitted = true; if (valid) onSave(bank, holder, type, number) }, "ব্যাংক অ্যাকাউন্ট সংরক্ষণ করুন") {
        FormField(bank, { bank = it.take(50) }, "ব্যাংকের নাম", Modifier.fillMaxWidth(), submitted && bank.isBlank()); FormField(holder, { holder = it.take(60) }, "অ্যাকাউন্টধারীর নাম", Modifier.fillMaxWidth(), submitted && holder.isBlank())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Savings", "Current").forEach { item -> FilterChip(selected = type == item, onClick = { type = item }, label = { Text(if (item == "Savings") "সঞ্চয়ী" else "চলতি") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFD8E9), selectedLabelColor = ProfilePink)) } }
        FormField(number, { if (it.length <= 24 && it.all(Char::isDigit)) number = it }, "অ্যাকাউন্ট নম্বর", Modifier.fillMaxWidth(), submitted && number.length !in 6..24, KeyboardType.Number)
        Text("শুধু শেষ চারটি সংখ্যা দেখানো হবে।", color = ProfileMuted, fontSize = 10.sp)
    }
}

@Composable
private fun CardDialog(onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var holder by rememberSaveable { mutableStateOf("") }; var number by rememberSaveable { mutableStateOf("") }; var expiry by rememberSaveable { mutableStateOf("") }; var cvv by rememberSaveable { mutableStateOf("") }; var submitted by remember { mutableStateOf(false) }
    val valid = holder.isNotBlank() && number.length in 13..19 && luhnValid(number) && expiry.matches(Regex("(0[1-9]|1[0-2])/\\d{2}")) && cvv.length in 3..4
    FormDialog("পেমেন্ট কার্ড যোগ করুন", onDismiss, { submitted = true; if (valid) onSave(holder, number, expiry, cvv) }, "কার্ড সংরক্ষণ করুন") {
        FormField(holder, { holder = it.take(60) }, "কার্ডে থাকা নাম", Modifier.fillMaxWidth(), submitted && holder.isBlank()); FormField(number, { if (it.length <= 19 && it.all(Char::isDigit)) number = it }, "কার্ড নম্বর", Modifier.fillMaxWidth(), submitted && (number.length !in 13..19 || !luhnValid(number)), KeyboardType.Number)
        if (number.isNotBlank()) {
            val detectedBrand = cardBrand(number)
            Surface(color = Color(0xFFF7F4F6), shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardBrandLogo(detectedBrand)
                    Spacer(Modifier.width(10.dp))
                    Column { Text("কার্ড নেটওয়ার্ক", color = ProfileMuted, fontSize = 9.sp); Text(detectedBrand, color = ProfileInk, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        FormField(expiry, { raw -> val digits = raw.filter(Char::isDigit).take(4); expiry = if (digits.length > 2) digits.take(2) + "/" + digits.drop(2) else digits }, "মেয়াদ (MM/YY)", Modifier.fillMaxWidth(), submitted && !expiry.matches(Regex("(0[1-9]|1[0-2])/\\d{2}")), KeyboardType.Number)
        OutlinedTextField(
            value = cvv,
            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) cvv = it },
            label = { Text("CVV") },
            placeholder = { Text("৩ বা ৪ সংখ্যা") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = submitted && cvv.length !in 3..4,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = RoundedCornerShape(12.dp),
            supportingText = { if (submitted && cvv.length !in 3..4) Text("৩ বা ৪ সংখ্যার CVV তৈরি করুন") }
        )
        Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(10.dp)) { Text("TopPay-তে এই কার্ড ব্যবহার করার সময় প্রতিবার এটি লিখতে হবে।", Modifier.padding(11.dp), color = ProfileInk, fontSize = 10.sp) }
    }
}

@Composable
private fun FormDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, saveLabel: String, content: @Composable ColumnScope.() -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title, fontWeight = FontWeight.Bold) }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }, confirmButton = { Button(onClick = onSave, colors = ButtonDefaults.buttonColors(containerColor = ProfilePink), shape = RoundedCornerShape(11.dp)) { Text(saveLabel) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল", color = ProfileMuted) } }) }

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, error: Boolean, keyboard: KeyboardType = KeyboardType.Text) { OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier, singleLine = true, isError = error, keyboardOptions = KeyboardOptions(keyboardType = keyboard), shape = RoundedCornerShape(12.dp), supportingText = { if (error) Text("এই তথ্যটি পরীক্ষা করুন") }) }

@Composable
private fun ConfirmDelete(title: String, item: String, onDismiss: () -> Unit, onConfirm: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(item) }, confirmButton = { TextButton(onClick = onConfirm) { Text("মুছুন", color = Color(0xFFC43D57)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }) }

private fun maskIdentity(value: String): String = if (value.length < 5) value else "•".repeat((value.length - 4).coerceAtMost(8)) + value.takeLast(4)
private fun cardBrand(number: String): String = when {
    number.startsWith("4") -> "Visa"
    number.startsWith("34") || number.startsWith("37") -> "American Express"
    number.startsWith("6011") || number.startsWith("65") || number.take(3).toIntOrNull() in 644..649 -> "Discover"
    number.take(4).toIntOrNull() in 3528..3589 -> "JCB"
    number.startsWith("62") -> "UnionPay"
    number.take(3).toIntOrNull() in 300..305 || number.startsWith("36") || number.startsWith("38") -> "Diners Club"
    number.startsWith("50") || number.take(2).toIntOrNull() in 56..69 -> "Maestro"
    number.take(2).toIntOrNull() in 51..55 || number.take(4).toIntOrNull() in 2221..2720 -> "Mastercard"
    else -> "Card"
}
private fun luhnValid(number: String): Boolean { var sum = 0; var alternate = false; for (digit in number.reversed()) { var value = digit.digitToIntOrNull() ?: return false; if (alternate) { value *= 2; if (value > 9) value -= 9 }; sum += value; alternate = !alternate }; return number.length >= 13 && sum % 10 == 0 }
