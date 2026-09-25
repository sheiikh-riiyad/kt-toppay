package com.toppay.org

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private val ScanPink = Color(0xFFE50973)
private val qrOptions = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var permissionRequested by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var photoBusy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    val controller = remember { LifecycleCameraController(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }
    LaunchedEffect(Unit) {
        if (!permissionGranted) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            }
            if (event == Lifecycle.Event.ON_STOP) {
                flash = false
                controller.enableTorch(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            photoBusy = true
            error = null
            scope.launch {
                val scanner = BarcodeScanning.getClient(qrOptions)
                try {
                    val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
                    val value = scanner.process(image).await().firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                    if (value == null) error = "এই ছবিতে QR কোড পাওয়া যায়নি। অন্য ছবি বেছে নিন।"
                    else result = value
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "ছবিটি পড়া যায়নি। আবার চেষ্টা করুন।"
                } finally {
                    scanner.close()
                    photoBusy = false
                }
            }
        }
    }

    // Unbind while showing a result or processing an image, and when leaving this tab.
    val scanning = permissionGranted && result == null && !photoBusy
    DisposableEffect(scanning, lifecycleOwner) {
        var disposed = false
        val scanner = if (scanning) BarcodeScanning.getClient(qrOptions) else null
        if (scanner != null) {
            try {
                controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                controller.setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                        ContextCompat.getMainExecutor(context)) { analysis ->
                        if (!disposed && result == null) {
                            val value = analysis?.getValue(scanner)?.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (value != null) result = value
                        }
                    }
                )
                controller.bindToLifecycle(lifecycleOwner)
                controller.initializationFuture.addListener({
                    if (!disposed) {
                        try {
                            controller.initializationFuture.get()
                            cameraReady = true
                        } catch (_: Exception) {
                            error = "ক্যামেরা চালু করা যায়নি। গ্যালারি থেকে QR ছবি বেছে নিন।"
                        }
                    }
                }, ContextCompat.getMainExecutor(context))
            } catch (_: Exception) {
                error = "ক্যামেরা চালু করা যায়নি। গ্যালারি থেকে QR ছবি বেছে নিন।"
            }
        }
        onDispose {
            disposed = true
            cameraReady = false
            flash = false
            controller.enableTorch(false)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner?.close()
        }
    }

    Column(modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxWidth().background(ScanPink).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹", color = Color.White, fontSize = 32.sp) }
                Text("QR স্ক্যান", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("টপপে", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp))
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("QR কোড স্ক্যান করুন", color = Color(0xFF292929), fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
            Text("কোডটি ক্যামেরার সামনে রাখুন", color = Color(0xFF777777), fontSize = 14.sp)
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF211923)),
                contentAlignment = Alignment.Center
            ) {
                if (scanning) {
                    AndroidView(
                        factory = { PreviewView(it).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            this.controller = controller
                        } }, modifier = Modifier.fillMaxSize()
                    )
                    if (!cameraReady) CircularProgressIndicator(color = Color.White)
                } else if (!permissionGranted) {
                    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("স্ক্যান করতে ক্যামেরার অনুমতি দিন", color = Color.White, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = {
                            permissionRequested = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }, colors = ButtonDefaults.buttonColors(containerColor = ScanPink)) { Text("অনুমতি দিন") }
                    }
                }
                Canvas(Modifier.fillMaxSize().padding(24.dp)) {
                    val length = 30.dp.toPx()
                    val stroke = 4.dp.toPx()
                    listOf(Offset.Zero, Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height)).forEach { corner ->
                        val dx = if (corner.x == 0f) length else -length
                        val dy = if (corner.y == 0f) length else -length
                        drawLine(ScanPink, corner, corner + Offset(dx, 0f), stroke, StrokeCap.Round)
                        drawLine(ScanPink, corner, corner + Offset(0f, dy), stroke, StrokeCap.Round)
                    }
                }
                if (photoBusy) CircularProgressIndicator(color = ScanPink)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (controller.cameraInfo?.hasFlashUnit() == true) {
                            val requested = !flash
                            val future = controller.enableTorch(requested)
                            future.addListener({
                                try { future.get(); flash = requested }
                                catch (_: Exception) { error = "ফ্ল্যাশ চালু করা যায়নি।" }
                            }, ContextCompat.getMainExecutor(context))
                        } else error = "এই ক্যামেরায় ফ্ল্যাশ নেই।"
                    }, enabled = scanning && cameraReady,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) { Text(if (flash) "ফ্ল্যাশ বন্ধ" else "ফ্ল্যাশ চালু", color = ScanPink) }
                OutlinedButton(
                    onClick = { picker.launch("image/*") }, enabled = !photoBusy,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                ) { Text("গ্যালারি", color = ScanPink) }
            }
            if (!permissionGranted && permissionRequested) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("ক্যামেরার অনুমতি: অ্যাপ সেটিংস", color = ScanPink) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center) }
            Surface(color = Color(0xFFFFF0F6), shape = RoundedCornerShape(16.dp)) {
                Text("QR কোড স্ক্যান করে তথ্য দেখুন। কোনো পেমেন্ট স্বয়ংক্রিয়ভাবে করা হবে না।", Modifier.padding(18.dp), color = Color(0xFF91506F), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
    result?.let { value ->
        ModalBottomSheet(onDismissRequest = { result = null }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("QR কোড পাওয়া গেছে", color = ScanPink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("স্ক্যান করা তথ্য", color = Color.Gray, fontSize = 13.sp)
                SelectionContainer { Text(value, color = Color(0xFF333333), fontSize = 15.sp) }
                Text("পেমেন্ট সেবা এখনো চালু হয়নি।", color = Color.Gray, fontSize = 12.sp)
                Button(onClick = { result = null }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ScanPink)) { Text("আবার স্ক্যান করুন") }
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("QR result", value))
                }, modifier = Modifier.fillMaxWidth()) { Text("তথ্য কপি করুন", color = ScanPink) }
            }
        }
    }
}
