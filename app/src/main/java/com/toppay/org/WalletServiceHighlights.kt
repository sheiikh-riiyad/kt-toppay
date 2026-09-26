package com.toppay.org

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image

private data class HighlightedWalletService(
    val name: String,
    val message: String,
    val logo: Int,
    val tint: Color
)

private val highlightedWalletServices = listOf(
    HighlightedWalletService("bKash", "সহজ পেমেন্ট", R.drawable.bkash_logo, Color(0xFFE2136E)),
    HighlightedWalletService("Nagad", "দ্রুত লেনদেন", R.drawable.nagad_logo, Color(0xFFF6921E)),
    HighlightedWalletService("Rocket", "নিরাপদ সেবা", R.drawable.rocket_logo, Color(0xFF8A2A8B))
)

@Composable
fun WalletServiceHighlights(modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF5F9),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFFD5E6))
    ) {
        Column(
            Modifier.padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 9.dp else 13.dp)
        ) {
            if (!compact) {
                Text(
                    "জনপ্রিয় মোবাইল ফাইন্যান্স সেবা",
                    color = Color(0xFF3B3036),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "TopPay থেকে আপনার পছন্দের সেবায় সহজে লেনদেন করুন",
                    color = Color(0xFF887780),
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                highlightedWalletServices.forEach { service ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFF0E5EA))
                    ) {
                        Column(
                            Modifier.padding(horizontal = 5.dp, vertical = if (compact) 7.dp else 9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(if (compact) 28.dp else 34.dp)
                                    .background(service.tint.copy(alpha = .08f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(service.logo),
                                    contentDescription = "${service.name} লোগো",
                                    modifier = Modifier.size(if (compact) 24.dp else 30.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                service.name,
                                color = service.tint,
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )
                            if (!compact) {
                                Text(
                                    service.message,
                                    color = Color(0xFF8B7D84),
                                    fontSize = 8.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            if (!compact) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "সেবাগুলোর নাম ও লোগো তাদের নিজ নিজ মালিকের ট্রেডমার্ক।",
                    color = Color(0xFF9A8E94),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
