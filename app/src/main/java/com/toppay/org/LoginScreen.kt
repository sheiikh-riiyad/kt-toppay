package com.toppay.org

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun TopPayApp() {
    val context = LocalContext.current
    val auth = remember {
        FirebaseApp.initializeApp(context)?.let { FirebaseAuth.getInstance(it) }
    }
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var user by remember { mutableStateOf(auth?.currentUser) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Firebase only generates this resource after Google sign-in is configured.
    val clientId = remember(context) {
        val resourceId = context.resources.getIdentifier(
            "default_web_client_id", "string", context.packageName
        )
        if (resourceId != 0) context.getString(resourceId) else ""
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }

    if (user != null) {
        PinGate(uid = user!!.uid, onSignOut = { auth?.signOut(); user = null }) {
        WalletHome(
            displayName = user?.displayName ?: "TopPay member",
            profile = WalletProfile(
                name = user?.displayName ?: "TopPay member",
                email = user?.email,
                uid = user?.uid.orEmpty(),
                emailVerified = user?.isEmailVerified == true,
                createdAt = user?.metadata?.creationTimestamp,
                lastSignInAt = user?.metadata?.lastSignInTimestamp
            ),
            onSignOut = {
                // Close the wallet immediately, even if the credential provider is unavailable.
                auth?.signOut()
                user = null
                busy = true
                error = null
                scope.launch {
                    try {
                        credentialManager.clearCredentialState(ClearCredentialStateRequest())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        error = "Signed out. Google account selection could not be reset; please try again when signing in."
                    } finally {
                        busy = false
                    }
                }
            }
        )
        }
    } else {
        LoginScreen(busy = busy, error = error, onSignIn = {
            if (!busy) {
                if (auth == null || clientId.isBlank()) {
                    error = "Google sign-in is not available yet. Please try again once app setup is complete."
                } else {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val option = GetSignInWithGoogleOption.Builder(clientId).build()
                            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                            val credential = credentialManager.getCredential(context, request).credential
                            check(credential is CustomCredential &&
                                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            // Firebase validates the token before granting access to the home screen.
                            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                        } catch (_: GetCredentialCancellationException) {
                            // Closing Google's picker leaves the user on the login page.
                        } catch (_: NoCredentialException) {
                            error = "No Google account is available. Add a Google account to your phone and try again."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            error = "Couldn't sign in with Google. Check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        })
    }
}

@Composable
private fun LoginScreen(busy: Boolean, error: String?, onSignIn: () -> Unit) {
    val pink = Color(0xFFE50973)
    val darkPink = Color(0xFF9E1556)
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(darkPink, pink, Color(0xFFF7B7D2))))
            .safeDrawingPadding().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(54.dp))
        Surface(shape = RoundedCornerShape(26.dp), color = Color.White.copy(alpha = .18f)) {
            Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                Text("T", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("TOPPAY", color = Color.White, fontSize = 14.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
        Text("Your everyday wallet", color = Color.White.copy(alpha = .84f), fontSize = 14.sp)
        Spacer(Modifier.height(44.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            color = Color.White
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 38.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Welcome", color = Color(0xFF302B30), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Sign in or create your TopPay account", color = Color(0xFF777077), fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onSignIn,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = pink, contentColor = Color.White)
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Please wait...")
                    } else {
                        Box(Modifier.size(28.dp).background(Color.White, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                            Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Continue with Google", fontWeight = FontWeight.SemiBold)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                Text(
                    "After Google verification, create or enter your 4-digit wallet PIN.",
                    color = Color(0xFF8A8288), fontSize = 12.sp, lineHeight = 18.sp
                )
                HorizontalDivider(color = Color(0xFFF0E8EC))
                Text("Secure sign-in powered by Firebase", color = Color(0xFF9B9298), fontSize = 11.sp)
            }
        }
    }
}
