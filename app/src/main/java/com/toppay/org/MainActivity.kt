package com.toppay.org

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toppay.org.ui.theme.MyApplicationTheme
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay

private val Pink = Color(0xFFE50973)
private val DarkPink = Color(0xFFA7175D)
private val Ink = Color(0xFF292529)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyApplicationTheme(darkTheme = false, dynamicColor = false) { TopPayApp() } }
    }
}

private data class Service(val title: String, val kind: String, val color: Color)

private val walletServices = listOf(
    Service("সেন্ড মানি", "send", Color(0xFFD96E8C)),
    Service("মোবাইল রিচার্জ", "phone", Color(0xFF68A287)),
    Service("ক্যাশ আউট", "cash", Color(0xFF199B9B)),
    Service("পেমেন্ট", "bag", Color(0xFFE38C5C)),
    Service("অ্যাড মানি", "wallet", Color(0xFF93499E)),
    Service("পে বিল", "bulb", Color(0xFF648679)),
    Service("সেভিংস", "savings", Color(0xFFC44F9B)),
    Service("লোন", "loan", Color(0xFFA67E68)),
    Service("ইন্স্যুরেন্স", "shield", Color(0xFF3EB5D0)),
    Service("টপপে টু ব্যাংক", "bank", Color(0xFFD57598)),
    Service("এডুকেশন ফি", "book", Color(0xFF5B836F)),
    Service("মাইক্রোফাইন্যান্স", "micro", Color(0xFF7D86BA)),
    Service("টোল", "toll", Color(0xFF438DBB)),
    Service("রিকোয়েস্ট মানি", "request", Color(0xFFD67690)),
    Service("রেমিটেন্স", "remit", Color(0xFF69B66A)),
    Service("ডোনেশন", "heart", Color(0xFFD27792))
)

@Composable
fun WalletHome(
    displayName: String = "TopPay",
    onSignOut: (() -> Unit)? = null,
    profile: WalletProfile = WalletProfile()
) {
    var page by rememberSaveable { mutableStateOf("Home") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var balanceVisible by remember { mutableStateOf(false) }
    var selectedAction by remember { mutableStateOf<String?>(null) }
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedProviderAction by rememberSaveable { mutableStateOf<String?>(null) }
    var accountBalance by remember { mutableDoubleStateOf(0.0) }
    var pendingTransactionCount by remember { mutableIntStateOf(0) }
    BackHandler(page != "Home") {
        page = when (page) {
            "ProviderAction" -> "Provider"
            "NotificationLog" -> "Profile"
            else -> "Home"
        }
    }

    fun openService(title: String) {
        page = when (title) {
            walletServices[4].title -> "AddMoney"
            walletServices[5].title -> "PayBill"
            "সেন্ড মানি" -> "SendMoney"
            "মোবাইল রিচার্জ" -> "Recharge"
            "ক্যাশ আউট" -> "CashOut"
            "পেমেন্ট" -> "Payment"
            "ব্যাংক ট্রান্সফার" -> "BankTransfer"
            "bKash", "Nagad", "Rocket" -> {
                selectedProvider = title
                selectedProviderAction = null
                "Provider"
            }
            else -> {
                selectedAction = title
                page
            }
        }
    }

    LaunchedEffect(balanceVisible) {
        if (balanceVisible) {
            delay(7_000)
            balanceVisible = false
        }
    }

    DisposableEffect(profile.uid) {
        if (profile.uid.isBlank()) return@DisposableEffect onDispose { }
        val registration = FirebaseFirestore.getInstance().document("users/${profile.uid}")
            .addSnapshotListener { snapshot, _ -> accountBalance = snapshot?.getDouble("balance") ?: 0.0 }
        onDispose { registration.remove() }
    }

    DisposableEffect(profile.uid) {
        if (profile.uid.isBlank()) return@DisposableEffect onDispose { }
        val registration = FirebaseFirestore.getInstance().collection("users/${profile.uid}/depositRequests")
            .addSnapshotListener { snapshot, _ ->
                pendingTransactionCount = snapshot?.documents?.count { it.getString("status")?.lowercase() == "pending" } ?: 0
            }
        onDispose { registration.remove() }
    }

    Scaffold(
        containerColor = if (page == "Home") Pink else Color(0xFFF5F8F6),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (page in setOf("Home", "Profile", "Scan", "Inbox")) {
            Column(Modifier.background(Pink).navigationBarsPadding()) {
                Surface(color = Color.White.copy(alpha = .98f), shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                        BottomItem("home", "হোম", page == "Home", Modifier.weight(1f)) { page = "Home" }
                        BottomItem("wallet", "আমার টপপে", page == "Profile", Modifier.weight(1f)) { page = "Profile" }
                        BottomItem("scan", "QR স্ক্যান", page == "Scan", Modifier.weight(1f)) { page = "Scan" }
                        BottomItem("inbox", "ইনবক্স", page == "Inbox", Modifier.weight(1f), badge = pendingTransactionCount.takeIf { it > 0 }?.toString()) { page = "Inbox" }
                    }
                }
            }
            }
        }
    ) { padding ->
        when (page) {
            "Profile" -> ProfileScreen(
                profile = profile,
                onSignOut = onSignOut,
                modifier = Modifier.padding(padding).statusBarsPadding(),
                onNotificationLog = { page = "NotificationLog" }
            )
            "NotificationLog" -> NotificationLogScreen(Modifier.padding(padding)) { page = "Profile" }
            "Scan" -> QrScannerScreen(Modifier.padding(padding)) { page = "Home" }
            "SendMoney" -> WalletActionScreen(WalletActionType.SendMoney, Modifier.padding(padding)) { page = "Home" }
            "Recharge" -> WalletActionScreen(WalletActionType.MobileRecharge, Modifier.padding(padding)) { page = "Home" }
            "CashOut" -> WalletActionScreen(WalletActionType.CashOut, Modifier.padding(padding)) { page = "Home" }
            "Payment" -> WalletActionScreen(WalletActionType.Payment, Modifier.padding(padding)) { page = "Home" }
            "AddMoney" -> AddMoneyScreen(Modifier.padding(padding)) { page = "Home" }
            "BankTransfer" -> BankTransferScreen(Modifier.padding(padding)) { page = "Home" }
            "PayBill" -> WalletActionScreen(WalletActionType.PayBill, Modifier.padding(padding)) { page = "Home" }
            "Inbox" -> TransactionInboxScreen(Modifier.padding(padding)) { page = "Home" }
            "Provider" -> ProviderTransactionScreen(
                provider = selectedProvider ?: "bKash",
                modifier = Modifier.padding(padding),
                onBack = { page = "Home" },
                onSelect = { type ->
                    selectedProviderAction = type.name
                    page = "ProviderAction"
                }
            )
            "ProviderAction" -> WalletActionScreen(
                type = selectedProviderAction?.let(WalletActionType::valueOf) ?: WalletActionType.SendMoney,
                modifier = Modifier.padding(padding),
                initialProvider = selectedProvider ?: "bKash",
                providerLocked = true,
                onBack = { page = "Provider" }
            )
            else -> PinkHome(
                displayName = displayName,
                padding = padding,
                expanded = expanded,
                balanceVisible = balanceVisible,
                accountBalance = accountBalance,
                onToggleExpanded = { expanded = !expanded },
                onToggleBalance = { balanceVisible = !balanceVisible },
                onProfile = { page = "Profile" },
                onAction = ::openService
            )
        }
    }

    selectedAction?.let { action ->
        AlertDialog(
            onDismissRequest = { selectedAction = null },
            title = { Text(action) },
            text = { Text("এই সেবাটি এখনো চালু হয়নি। ") },
            confirmButton = { TextButton(onClick = { selectedAction = null }) { Text("ঠিক আছে", color = Pink) } }
        )
    }
}

@Composable
private fun PinkHome(
    displayName: String,
    padding: PaddingValues,
    expanded: Boolean,
    balanceVisible: Boolean,
    accountBalance: Double,
    onToggleExpanded: () -> Unit,
    onToggleBalance: () -> Unit,
    onProfile: () -> Unit,
    onAction: (String) -> Unit
) {
    val balanceIconOffset by animateDpAsState(
        targetValue = if (balanceVisible) 115.dp else 0.dp,
        animationSpec = tween(durationMillis = 520),
        label = "balance icon slide"
    )
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(192.dp)) {
            PinkCity(Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(58.dp).background(Color(0xFFFFD4E5), CircleShape)
                        .border(2.dp, Color.White, CircleShape).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = "Open profile", onClick = onProfile),
                    contentAlignment = Alignment.Center
                ) {
                    Text(displayName.trim().take(1).uppercase(), color = Pink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(displayName, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(7.dp))
                    Surface(
                        onClick = onToggleBalance,
                        modifier = Modifier.height(35.dp).width(150.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(9.dp)
                    ) {
                        Box(Modifier.fillMaxSize().padding(4.dp)) {
                            Text(
                                if (balanceVisible) "৳ ${"%,.2f".format(accountBalance)}" else "ব্যালেন্স দেখুন",
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = if (balanceVisible) 5.dp else 33.dp),
                                color = Ink,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                Modifier.offset(x = balanceIconOffset).size(27.dp).background(Pink, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text("৳", color = Color.White, fontSize = 18.sp) }
                        }
                    }
                }
                RoundHeaderButton("search") { onAction("সার্চ") }
                Spacer(Modifier.width(10.dp))
                RoundHeaderButton("brand", onProfile)
            }
        }

        Column(
            Modifier.fillMaxWidth().offset(y = (-14).dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Color.White)
                .padding(top = 26.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            SupportedPaymentServices(Modifier.padding(horizontal = 16.dp), onAction)

            Column(Modifier.animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                ServiceGrid(walletServices.take(if (expanded) 16 else 8), onAction)
                if (!expanded) {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        walletServices.drop(8).take(4).forEach { service ->
                            ServiceGlyph(service.kind, service.color, Modifier.size(44.dp).alpha(.13f))
                        }
                    }
                }
                OutlinedButton(
                    onClick = onToggleExpanded,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E3E5)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Pink)
                ) { Text(if (expanded) "বন্ধ করুন ⌃" else "আরো দেখুন ⌄") }
            }

            PromoCard(Modifier.padding(horizontal = 20.dp)) { onAction("অফার") }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("কুইক ফিচারসমূহ", color = Color(0xFF555155), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    walletServices.take(3).forEach { service ->
                        Surface(
                            onClick = { onAction(service.title) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE7E3E5)),
                            color = Color.White
                        ) {
                            Column(Modifier.padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                ServiceGlyph(service.kind, service.color, Modifier.size(28.dp))
                                Spacer(Modifier.height(6.dp))
                                Text(service.title, color = Ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Text("", color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SupportedPaymentServices(modifier: Modifier = Modifier, onAction: (String) -> Unit) {
    val services = listOf(
        Triple("bKash", R.drawable.bkash_logo, Color(0xFFE2136E)),
        Triple("Nagad", R.drawable.nagad_logo, Color(0xFFF6921E)),
        Triple("Rocket", R.drawable.rocket_logo, Color(0xFF8A2A8B))
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("সাপোর্টেড পেমেন্ট সেবা", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("আপনার পছন্দের মাধ্যম ব্যবহার করুন", color = Color(0xFF8B8288), fontSize = 10.sp)
            }
            Surface(color = Color(0xFFFFE7F1), shape = RoundedCornerShape(50)) {
                Text("জনপ্রিয়", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = Pink, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            services.forEach { (name, logo, color) ->
                SupportedPaymentItem(name, color, Modifier.weight(1f), onClick = { onAction(name) }) {
                    Image(
                        painter = painterResource(logo),
                        contentDescription = "$name লোগো",
                        modifier = Modifier.size(42.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            SupportedPaymentItem("ব্যাংক", Color(0xFF397A6A), Modifier.weight(1f), onClick = { onAction("ব্যাংক ট্রান্সফার") }) {
                ServiceGlyph("bank", Color(0xFF397A6A), Modifier.size(39.dp))
            }
        }
    }
}

@Composable
private fun SupportedPaymentItem(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    logo: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = Color(0xFFFBFAFB),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEDE7EA))
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(52.dp).background(color.copy(alpha = .08f), CircleShape),
                contentAlignment = Alignment.Center
            ) { logo() }
            Spacer(Modifier.height(6.dp))
            Text(name, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ServiceGrid(items: List<Service>, onAction: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
                row.forEach { service ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button) { onAction(service.title) }.padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(60.dp).background(Color(0xFFF7F6F7), CircleShape), contentAlignment = Alignment.Center) {
                            ServiceGlyph(service.kind, service.color, Modifier.size(43.dp))
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(service.title, color = Ink, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 17.sp, modifier = Modifier.heightIn(min = 32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PromoCard(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF210337), Color(0xFF8A087F), Color(0xFF370044))))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            repeat(8) { index ->
                drawLine(Color.White.copy(alpha = .08f), Offset(size.width * index / 7, 0f), Offset(size.width * (index - 2) / 7, size.height), 3f)
            }
        }
        Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("আপনার প্রতিদিন", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Text("আরও সহজ, আরও রঙিন", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("টপপে!", color = Color(0xFFFFF23D), fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text("২৫.৫% অফার ", Modifier.border(1.dp, Color.White, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp, vertical = 3.dp), color = Color.White, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun PinkCity(modifier: Modifier) {
    Canvas(modifier.background(Brush.verticalGradient(listOf(Color(0xFFE94896), Color(0xFFF5B4D0))))) {
        fun building(x: Float, y: Float, width: Float, height: Float, color: Color) {
            drawRect(color, Offset(x * size.width, y * size.height), Size(width * size.width, height * size.height))
            drawRect(Color(0xFFCF126D), Offset((x - .01f) * size.width, y * size.height), Size((width + .02f) * size.width, .04f * size.height))
        }
        building(-.02f, .42f, .22f, .58f, Color(0xFFBA1266))
        building(.18f, .61f, .15f, .39f, Color(0xFFD7337C))
        building(.74f, .25f, .28f, .75f, Color(0xFFC90D69))
        building(.58f, .58f, .18f, .42f, Color(0xFFDE4085))
        val road = Path().apply {
            moveTo(0f, size.height); quadraticTo(size.width * .5f, size.height * .72f, size.width, size.height); close()
        }
        drawPath(road, Pink)
    }
}

@Composable
private fun RoundHeaderButton(kind: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White) {
        Box(Modifier.size(49.dp), contentAlignment = Alignment.Center) {
            if (kind == "search") SearchMark(Modifier.size(27.dp))
            else Image(
                painter = painterResource(R.drawable.top_pay_logo),
                contentDescription = "TopPay logo",
                modifier = Modifier.size(39.dp).clip(RoundedCornerShape(10.dp))
            )
        }
    }
}

@Composable
private fun BottomItem(kind: String, title: String, selected: Boolean, modifier: Modifier, badge: String? = null, onClick: () -> Unit) {
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Tab, onClick = onClick).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NavGlyph(kind, if (selected) Pink else Color(0xFF555155), Modifier.size(27.dp))
            Spacer(Modifier.height(3.dp))
            Text(title, color = if (selected) Pink else Color(0xFF555155), fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
        badge?.let {
            Box(Modifier.align(Alignment.TopCenter).offset(x = 18.dp, y = (-5).dp).size(21.dp).background(Pink, CircleShape), contentAlignment = Alignment.Center) {
                Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SearchMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color(0xFF3F3B3E), radius = size.minDimension * .34f, center = Offset(size.width * .43f, size.height * .43f), style = Stroke(size.minDimension * .075f))
        drawLine(Color(0xFF3F3B3E), Offset(size.width * .68f, size.height * .68f), Offset(size.width * .94f, size.height * .94f), size.minDimension * .075f)
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val p = Path().apply {
            moveTo(size.width * .08f, size.height * .2f)
            lineTo(size.width * .83f, size.height * .08f)
            lineTo(size.width * .58f, size.height * .52f)
            lineTo(size.width * .88f, size.height * .84f)
            lineTo(size.width * .45f, size.height * .68f)
            lineTo(size.width * .22f, size.height * .96f)
            lineTo(size.width * .24f, size.height * .54f)
            close()
        }
        drawPath(p, Pink)
        drawLine(Color.White, Offset(size.width * .24f, size.height * .54f), Offset(size.width * .83f, size.height * .08f), size.width * .035f)
    }
}

@Composable
private fun ServiceGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 48f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)
        val stroke = Stroke(2.2f * s)
        fun line(x: Float, y: Float, x2: Float, y2: Float, width: Float = 2.2f) = drawLine(color, o(x, y), o(x2, y2), width * s)
        fun rect(x: Float, y: Float, w: Float, h: Float, radius: Float = 3f) {
            drawRoundRect(color.copy(alpha = .08f), o(x, y), Size(w * s, h * s), androidx.compose.ui.geometry.CornerRadius(radius * s))
            drawRoundRect(color, o(x, y), Size(w * s, h * s), androidx.compose.ui.geometry.CornerRadius(radius * s), style = stroke)
        }
        fun circle(x: Float, y: Float, r: Float) = drawCircle(color, r * s, o(x, y), style = stroke)
        fun path(vararg points: Float) {
            val p = Path().apply { moveTo(points[0] * s, points[1] * s); for (i in 2 until points.size step 2) lineTo(points[i] * s, points[i + 1] * s) }
            drawPath(p, color, style = stroke)
        }
        when (kind) {
            "phone" -> { rect(13f, 3f, 22f, 42f); line(19f, 8f, 29f, 8f); circle(24f, 24f, 8f); line(24f, 18f, 24f, 30f); circle(24f, 40f, 1f) }
            "cash" -> { rect(13f, 6f, 31f, 20f); circle(28f, 16f, 6f); path(3f, 29f, 14f, 21f, 24f, 26f, 35f, 25f, 35f, 31f, 17f, 41f, 8f, 42f, 3f, 29f) }
            "bag" -> { rect(8f, 14f, 32f, 30f); drawArc(color, 180f, 180f, false, o(17f, 3f), Size(14f * s, 22f * s), style = stroke) }
            "wallet" -> { rect(5f, 10f, 38f, 29f); rect(28f, 20f, 15f, 12f); circle(34f, 26f, 1f); circle(10f, 36f, 8f); line(10f, 31f, 10f, 41f); line(5f, 36f, 15f, 36f) }
            "bulb" -> { drawOval(color, o(11f, 3f), Size(27f * s, 31f * s), style = stroke); path(25f, 9f, 19f, 23f, 26f, 21f, 22f, 31f); repeat(3) { line(18f, 36f + it * 4, 30f, 36f + it * 4) } }
            "savings", "loan" -> { path(18f, 12f, 15f, 4f, 23f, 7f, 30f, 3f, 31f, 12f); val p = Path().apply { moveTo(17*s, 13*s); cubicTo(-2*s, 27*s, 12*s, 44*s, 18*s, 45*s); lineTo(32*s, 45*s); cubicTo(44*s, 34*s, 44*s, 22*s, 30*s, 13*s); close() }; drawPath(p, color.copy(alpha=.08f)); drawPath(p,color,style=stroke); circle(24f,29f,7f) }
            "shield", "micro" -> { path(24f, 3f, 39f, 8f, 38f, 28f, 33f, 38f, 24f, 44f, 15f, 38f, 10f, 28f, 9f, 8f, 24f, 3f); circle(24f, 23f, 8f); path(20f,23f,23f,27f,30f,18f) }
            "bank" -> { path(4f, 15f, 24f, 4f, 44f, 15f); listOf(9f,21f,33f).forEach { rect(it,19f,6f,19f,1f) }; line(4f,42f,44f,42f) }
            "book" -> { path(24f,10f,17f,7f,5f,7f,5f,40f,17f,40f,24f,44f,31f,40f,43f,40f,43f,7f,31f,7f,24f,10f,24f,44f); repeat(3){line(10f,16f+it*7,19f,18f+it*7);line(29f,18f+it*7,38f,16f+it*7)} }
            "toll" -> { rect(3f,7f,9f,36f); line(9f,14f,31f,3f); rect(20f,29f,24f,12f); path(24f,29f,27f,21f,37f,21f,41f,29f); circle(25f,36f,2f);circle(39f,36f,2f) }
            "request" -> { rect(10f,5f,25f,39f); circle(22f,17f,6f); path(15f,34f,22f,28f,30f,34f); path(38f,14f,46f,22f,38f,30f); line(29f,22f,45f,22f) }
            "remit" -> { val p=Path().apply{moveTo(5*s,29*s);cubicTo(11*s,12*s,31*s,9*s,43*s,19*s);lineTo(37*s,19*s);lineTo(42*s,9*s);lineTo(48*s,20*s);lineTo(43*s,19*s)};drawPath(p,color,style=stroke);circle(24f,29f,9f);line(24f,23f,24f,35f) }
            "heart" -> { val p=Path().apply{moveTo(24*s,41*s);cubicTo(-9*s,19*s,15*s,-1*s,24*s,14*s);cubicTo(35*s,-1*s,58*s,19*s,24*s,41*s)};drawPath(p,color.copy(alpha=.1f));drawPath(p,color,style=stroke) }
            else -> { circle(28f,24f,17f); line(1f,12f,14f,12f); line(1f,22f,16f,22f); line(2f,33f,14f,33f); line(24f,16f,24f,31f); line(20f,21f,30f,21f); path(30f,15f,36f,21f,30f,27f) }
        }
    }
}

@Composable
private fun NavGlyph(kind: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 32f
        val stroke = Stroke(2.2f * s)
        fun o(x: Float,y: Float)=Offset(x*s,y*s)
        when(kind) {
            "home" -> { val p=Path().apply{moveTo(3*s,14*s);lineTo(16*s,3*s);lineTo(29*s,14*s);lineTo(29*s,29*s);lineTo(3*s,29*s);close()};drawPath(p,color);drawLine(Color.White,o(11f,24f),o(21f,24f),2*s) }
            "wallet" -> { drawRoundRect(color,o(4f,6f),Size(24*s,23*s),androidx.compose.ui.geometry.CornerRadius(3*s),style=stroke);drawLine(color,o(8f,13f),o(24f,13f),2*s);drawLine(color,o(10f,24f),o(22f,24f),2*s) }
            "scan" -> { listOf(floatArrayOf(3f,11f,3f,3f,11f,3f),floatArrayOf(21f,3f,29f,3f,29f,11f),floatArrayOf(29f,21f,29f,29f,21f,29f),floatArrayOf(11f,29f,3f,29f,3f,21f)).forEach{a->val p=Path().apply{moveTo(a[0]*s,a[1]*s);lineTo(a[2]*s,a[3]*s);lineTo(a[4]*s,a[5]*s)};drawPath(p,color,style=stroke)} }
            else -> { drawRoundRect(color,o(3f,6f),Size(26*s,21*s),androidx.compose.ui.geometry.CornerRadius(3*s),style=stroke);drawLine(color,o(4f,8f),o(16f,18f),2*s);drawLine(color,o(28f,8f),o(16f,18f),2*s) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WalletHomePreview() {
    MyApplicationTheme(darkTheme = false, dynamicColor = false) { WalletHome("Sheikh Riyad") }
}
