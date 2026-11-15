package com.aion.chat.compose.ui.call

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aion.chat.compose.data.CameraStreamStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraStreamScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hasCamPermission by remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPermission = granted
    }
    var streaming by remember { mutableStateOf(false) }
    var pushCount by remember { mutableIntStateOf(0) }
    var pushErr by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val pushInterval = 2_000L

    LaunchedEffect(Unit) { if (!hasCamPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    LaunchedEffect(streaming, imageCapture.value) {
        if (!streaming) return@LaunchedEffect
        val ic = imageCapture.value ?: return@LaunchedEffect
        while (streaming) {
            delay(pushInterval)
            runCatching {
                val tmpFile = File(context.cacheDir, "stream_${System.currentTimeMillis()}.jpg")
                ic.takePicture(
                    ImageCapture.OutputFileOptions.Builder(tmpFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            scope.launch(Dispatchers.IO) {
                                val bmp = android.graphics.BitmapFactory.decodeFile(tmpFile.absolutePath)
                                if (bmp != null) {
                                    val ok = CameraStreamStore.pushFrame(bmp)
                                    bmp.recycle()
                                    if (ok) pushCount++ else pushErr = "推送失败"
                                }
                                tmpFile.delete()
                            }
                        }
                        override fun onError(exc: ImageCaptureException) {
                            pushErr = exc.message?.take(60); tmpFile.delete()
                        }
                    }
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { streaming = false; CameraStreamStore.stopStream() }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF232B33)).clickable { onBack() },
                contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                    tint = Color(0xFFD9E2EA), modifier = Modifier.size(20.dp))
            }
            Text("让哥哥看看", fontSize = 16.sp, color = Color(0xFFD9E2EA), fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            Text(if (streaming) "🟢 直播中 · $pushCount 帧" else "⚫ 未开启",
                fontSize = 12.sp, color = if (streaming) Color(0xFF6FBF8F) else Color(0xFF8FA5B5),
                modifier = Modifier.padding(end = 8.dp))
        }
        if (!hasCamPermission) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Text("需要摄像头权限", fontSize = 16.sp, color = Color(0xFFD9E2EA))
                Spacer(Modifier.height(8.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("授权摄像头", color = Color.White) }
                Spacer(Modifier.weight(1f))
            }
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(6.dp).clip(RoundedCornerShape(14.dp))) {
            AndroidView(
                factory = { ctx ->
                    val pv = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener({
                        try {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, capture)
                            imageCapture.value = capture
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "摄像头启动失败：${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    pv
                },
                modifier = Modifier.fillMaxSize()
            )
            if (pushErr != null) {
                Text("⚠ $pushErr", fontSize = 11.sp, color = Color(0xFFD96B6B),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        .background(Color(0xAA111111), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Button(enabled = imageCapture.value != null, onClick = { streaming = !streaming },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (streaming) Color(0xFFD96B6B) else Color(0xFF576B95), contentColor = Color.White),
                modifier = Modifier.weight(1f)) {
                Text(if (streaming) "停止推流" else "开启推流", fontSize = 14.sp)
            }
        }
        Text("Sean 通过 peek_camera 工具看你的画面。关掉推流 = 立刻断开，VPS 缓存清空",
            fontSize = 10.sp, color = Color(0xFF6F7B85),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp))
    }
}
