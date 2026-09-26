package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ProviderPink = Color(0xFFE50973)
private val ProviderInk = Color(0xFF302B30)

private data class ProviderTransactionChoice(
    val type: WalletActionType,
    val title: String,
    val subtitle: String,
    val symbol: String,
    val color: Color
)

@Composable
fun ProviderTransactionScreen(
    provider: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSelect: (WalletActionType) -> Unit
) {
    val logo = when (provider) {
        "Nagad" -> R.drawable.nagad_logo
        "Rocket" -> R.drawable.rocket_logo
        else -> R.drawable.bkash_logo
    }
    val choices = listOf(
        ProviderTransactionChoice(WalletActionType.SendMoney, "সেন্ড মানি", "$provider নম্বরে টাকা পাঠান", "S", Color(0xFFD96E8C)),
        ProviderTransactionChoice(WalletActionType.CashOut, "ক্যাশ আউট", "$provider এজেন্টের মাধ্যমে টাকা তুলুন", "C", Color(0xFF199B9B)),
        ProviderTransactionChoice(WalletActionType.Payment, "পেমেন্ট", "$provider মার্চেন্টে পেমেন্ট করুন", "P", Color(0xFFE38C5C))
    )

    Column(modifier.fillMaxSize().background(Color(0xFFF8F7F8))) {
        Surface(color = ProviderPink, shadowElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().height(62.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 34.sp) }
                Text("$provider সেবা", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = Color(0xFFFFEAF3), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Image(painter = painterResource(logo), contentDescription = "$provider লোগো", modifier = Modifier.size(54.dp), contentScale = ContentScale.Fit)
                    }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(provider, color = ProviderInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text("কোন ধরনের লেনদেন করতে চান?", color = Color(0xFF776C72), fontSize = 12.sp)
                    }
                }
            }

            Text("লেনদেনের ধরন নির্বাচন করুন", color = ProviderInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            choices.forEach { choice ->
                Surface(
                    onClick = { onSelect(choice.type) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, Color(0xFFE9E3E6)),
                    shadowElevation = 1.dp
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(50.dp).background(choice.color.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(choice.symbol, color = choice.color, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.size(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(choice.title, color = ProviderInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(choice.subtitle, color = Color(0xFF81777C), fontSize = 11.sp)
                        }
                        Text("›", color = ProviderPink, fontSize = 28.sp, fontWeight = FontWeight.Light)
                    }
                }
            }

            Surface(color = Color(0xFFFFF2F7), shape = RoundedCornerShape(14.dp)) {
                Text(
                    "লেনদেন সম্পন্ন করতে তথ্য যাচাই এবং ৪ সংখ্যার TopPay পিন প্রয়োজন হবে।",
                    Modifier.fillMaxWidth().padding(14.dp),
                    color = Color(0xFF725D67),
                    fontSize = 11.sp
                )
            }
        }
    }
}
