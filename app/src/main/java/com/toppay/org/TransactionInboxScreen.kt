package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DateFormat

private val InboxPink = Color(0xFFE50973)
private val InboxInk = Color(0xFF302B30)
private val InboxMuted = Color(0xFF756D72)

private data class InboxTransaction(
    val id: String,
    val method: String,
    val action: String,
    val amount: Double,
    val status: String,
    val source: String,
    val proof: String,
    val submittedAt: Timestamp?
)

private enum class InboxFilter(val label: String) { All("সব"), Pending("অপেক্ষমাণ"), Success("সফল") }

@Composable
fun TransactionInboxScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    var filter by rememberSaveable { mutableStateOf(InboxFilter.All) }
    var transactions by remember { mutableStateOf(emptyList<InboxTransaction>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(uid) {
        if (uid == null) { loading = false; return@DisposableEffect onDispose { } }
        val registration = FirebaseFirestore.getInstance().collection("users/$uid/depositRequests")
            .addSnapshotListener { snapshot, failure ->
                if (failure != null) { error = "লেনদেনগুলো লোড করা যায়নি। ইন্টারনেট সংযোগ পরীক্ষা করুন।"; loading = false; return@addSnapshotListener }
                transactions = snapshot?.documents?.mapNotNull { doc ->
                    InboxTransaction(doc.id, doc.getString("method") ?: return@mapNotNull null, doc.getString("action") ?: "Transfer", doc.getDouble("amount") ?: 0.0, doc.getString("status") ?: "pending", doc.getString("sourceReference").orEmpty(), doc.getString("proofReference").orEmpty(), doc.getTimestamp("submittedAt"))
                }?.sortedByDescending { it.submittedAt?.seconds ?: 0 } ?: emptyList()
                error = null; loading = false
            }
        onDispose { registration.remove() }
    }

    val filtered = transactions.filter { transaction ->
        when (filter) {
            InboxFilter.All -> true
            InboxFilter.Pending -> transaction.status.equals("pending", true)
            InboxFilter.Success -> transaction.status.equals("approved", true) || transaction.status.equals("success", true)
        }
    }
    val pendingCount = transactions.count { it.status.equals("pending", true) }
    val successCount = transactions.count { it.status.equals("approved", true) || it.status.equals("success", true) }

    Column(modifier.fillMaxSize().background(Color(0xFFF8F6F7))) {
        Surface(color = InboxPink, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }
                Column(Modifier.weight(1f)) { Text("লেনদেন ইনবক্স", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text("আপডেট ও পেমেন্টের অবস্থা", color = Color.White.copy(.78f), fontSize = 9.sp) }
                Box(Modifier.size(38.dp).background(Color.White.copy(.17f), CircleShape), contentAlignment = Alignment.Center) { Text("${transactions.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusSummary("অপেক্ষমাণ", pendingCount, Color(0xFFFFF2D4), Color(0xFFA66B00), Modifier.weight(1f))
                StatusSummary("সফল", successCount, Color(0xFFE2F6E9), Color(0xFF16864A), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InboxFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item.label) }, modifier = Modifier.weight(1f), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = InboxPink, selectedLabelColor = Color.White)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = InboxPink, trackColor = Color(0xFFFFDCEB))
            error?.let { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) { Text(it, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 11.sp) } }
            if (!loading && error == null && filtered.isEmpty()) EmptyInbox(filter)
            filtered.forEach { TransactionCard(it) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StatusSummary(label: String, count: Int, background: Color, foreground: Color, modifier: Modifier) {
    Surface(modifier = modifier, color = background, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).background(Color.White.copy(.72f), CircleShape), contentAlignment = Alignment.Center) { Text(count.toString(), color = foreground, fontWeight = FontWeight.Black, fontSize = 16.sp) }
            Spacer(Modifier.width(10.dp)); Text(label, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TransactionCard(transaction: InboxTransaction) {
    val success = transaction.status.equals("approved", true) || transaction.status.equals("success", true)
    val pending = transaction.status.equals("pending", true)
    val statusLabel = when { success -> "সফল"; pending -> "অপেক্ষমাণ"; else -> transaction.status }
    val statusColor = when { success -> Color(0xFF16864A); pending -> Color(0xFFA66B00); else -> Color(0xFFC43D57) }
    val statusBackground = when { success -> Color(0xFFE2F6E9); pending -> Color(0xFFFFF2D4); else -> Color(0xFFFFE6EB) }
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFECE6E9))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFFFFE6F1), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text("+৳", color = InboxPink, fontWeight = FontWeight.Black) }
                Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(transaction.method, color = InboxInk, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(transaction.action, color = InboxMuted, fontSize = 10.sp) }
                Column(horizontalAlignment = Alignment.End) { Text("৳ ${"%,.2f".format(transaction.amount)}", color = InboxInk, fontSize = 14.sp, fontWeight = FontWeight.Bold); Surface(color = statusBackground, shape = RoundedCornerShape(50)) { Text(statusLabel, Modifier.padding(horizontal = 9.dp, vertical = 4.dp), color = statusColor, fontSize = 8.sp, fontWeight = FontWeight.Bold) } }
            }
            HorizontalDivider(color = Color(0xFFF0EBED))
            Row { Column(Modifier.weight(1f)) { Text("রেফারেন্স", color = InboxMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold); Text(transaction.proof.takeIf { it.isNotBlank() && it != "PIN_AUTHORIZED_CARD" } ?: "কার্ড পিন অনুমোদিত", color = InboxInk, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Column(horizontalAlignment = Alignment.End) { Text("উৎস", color = InboxMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold); Text(if (transaction.source.isBlank()) "—" else "•••• ${transaction.source}", color = InboxInk, fontSize = 10.sp) } }
            transaction.submittedAt?.let { Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it.toDate()), color = InboxMuted, fontSize = 9.sp) }
            if (pending) Text("অ্যাডমিন যাচাইয়ের অপেক্ষায় আছে। আপনার ব্যালেন্স এখনো পরিবর্তন হয়নি।", color = Color(0xFF85661F), fontSize = 9.sp, lineHeight = 14.sp)
            if (success) Text("অনুমোদিত হয়েছে এবং আপনার TopPay ব্যালেন্সে যোগ হয়েছে।", color = Color(0xFF16864A), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyInbox(filter: InboxFilter) {
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFFECE6E9))) {
        Column(Modifier.fillMaxWidth().padding(vertical = 42.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(58.dp).background(Color(0xFFFFE6F1), CircleShape), contentAlignment = Alignment.Center) { Text("✉", color = InboxPink, fontSize = 25.sp) }
            Spacer(Modifier.height(12.dp)); Text("কোনো ${filter.label} লেনদেন নেই", color = InboxInk, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text("আপনার লেনদেনের আপডেট এখানে দেখা যাবে।", color = InboxMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}
