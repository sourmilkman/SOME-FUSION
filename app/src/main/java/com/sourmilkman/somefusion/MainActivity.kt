package com.sourmilkman.somefusion

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Range
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        setContent {
            SomeFusionTheme {
                SomeFusionApp(cameraExecutor)
            }
        }
    }

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        window.decorView.setBackgroundColor(Color.BLACK)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}

@Composable
private fun SomeFusionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Accent,
            background = ComposeColor.Black,
            surface = Panel,
            onSurface = ComposeColor.White
        ),
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
private fun SomeFusionApp(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        CameraScreen(cameraExecutor)
    } else {
        PermissionScreen { permissionLauncher.launch(Manifest.permission.CAMERA) }
    }
}

@Composable
private fun CameraScreen(cameraExecutor: ExecutorService) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameras = remember { discoverBackCameras(context) }
    var selectedCamera by remember { mutableStateOf(cameras.firstOrNull()) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var spyVideoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var pendingSpyRecording by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var rawEnabled by remember { mutableStateOf(false) }
    var focusZoomEnabled by remember { mutableStateOf(false) }
    var manualOpen by remember { mutableStateOf(true) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureFlash by remember { mutableStateOf(false) }
    var focusReticle by remember { mutableStateOf<Offset?>(null) }
    var focusLocked by remember { mutableStateOf(false) }
    var latestUri by remember { mutableStateOf<Uri?>(null) }
    var iso by remember { mutableFloatStateOf(selectedCamera?.isoRange?.lower?.toFloat() ?: 100f) }
    var shutterMs by remember { mutableFloatStateOf(8f) }
    var ev by remember { mutableFloatStateOf(0f) }
    var focus by remember { mutableFloatStateOf(0f) }
    var wbMode by remember { mutableStateOf(WbMode.Auto) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var liveZoomRange by remember { mutableStateOf(1f..1f) }
    var spyMode by remember { mutableStateOf(false) }

    fun stopSpyRecording(message: String = "Spy recording saved") {
        pendingSpyRecording = false
        activeRecording?.stop()
        activeRecording = null
        status = message
    }

    fun startSpyRecording(capture: VideoCapture<Recorder>) {
        if (activeRecording != null) return
        pendingSpyRecording = false
        val outputOptions = videoOutputOptions(context)
        activeRecording = capture.output
            .prepareRecording(context, outputOptions)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> status = "Spy recording"
                    is VideoRecordEvent.Finalize -> {
                        val savedUri = event.outputResults.outputUri
                        if (savedUri != Uri.EMPTY) latestUri = savedUri
                        activeRecording = null
                        pendingSpyRecording = false
                        if (event.hasError()) {
                            status = "Spy failed: ${event.error}"
                        } else {
                            status = "LIVE MODE"
                        }
                    }
                }
            }
    }

    fun bindCamera() {
        val cameraInfo = selectedCamera ?: return
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val selector = CameraSelector.Builder()
                .addCameraFilter { cameraInfos ->
                    cameraInfos.filter { Camera2CameraInfo.from(it).cameraId == cameraInfo.id }
                }
                .build()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val requestedRaw = rawEnabled && cameraInfo.rawSupported
            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(100)
            if (requestedRaw) {
                captureBuilder.setOutputFormat(ImageCapture.OUTPUT_FORMAT_RAW_JPEG)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            val video = VideoCapture.withOutput(recorder)

            try {
                provider.unbindAll()
                if (spyMode) {
                    imageCapture = null
                    spyVideoCapture = video
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, video)
                    liveZoomRange = currentZoomRange(camera)
                    zoom = 1f.coerceIn(liveZoomRange.start, liveZoomRange.endInclusive)
                    applyCameraState(camera, selectedCamera, iso, shutterMs, ev, focus, wbMode, zoom)
                    if (activeRecording == null && !pendingSpyRecording) status = "LIVE MODE"
                    if (pendingSpyRecording) startSpyRecording(video)
                } else {
                    spyVideoCapture = null
                    val capture = captureBuilder.build()
                    imageCapture = capture
                    camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    liveZoomRange = currentZoomRange(camera)
                    zoom = zoom.coerceIn(liveZoomRange.start, liveZoomRange.endInclusive)
                    applyCameraState(camera, selectedCamera, iso, shutterMs, ev, focus, wbMode, effectiveZoom(zoom, focusZoomEnabled, liveZoomRange))
                    status = if (requestedRaw) "RAW+JPEG ready" else "JPEG ready"
                }
            } catch (error: Exception) {
                if (requestedRaw) {
                    rawEnabled = false
                    status = "RAW unavailable on this lens - using JPEG"
                    bindCamera()
                } else {
                    status = "Camera failed: ${error.message ?: "unknown error"}"
                }
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(selectedCamera?.id, rawEnabled, spyMode) {
        iso = selectedCamera?.isoRange?.lower?.toFloat() ?: 100f
        bindCamera()
    }

    LaunchedEffect(camera, iso, shutterMs, ev, focus, wbMode, zoom, focusZoomEnabled, liveZoomRange) {
        applyCameraState(camera, selectedCamera, iso, shutterMs, ev, focus, wbMode, effectiveZoom(zoom, focusZoomEnabled, liveZoomRange))
    }

    DisposableEffect(camera) {
        val zoomState = camera?.cameraInfo?.zoomState
        val observer = Observer<androidx.camera.core.ZoomState> {
            val updated = it.minZoomRatio..it.maxZoomRatio
            liveZoomRange = updated
            zoom = zoom.coerceIn(updated.start, updated.endInclusive)
        }
        zoomState?.observe(lifecycleOwner, observer)
        onDispose {
            zoomState?.removeObserver(observer)
        }
    }

    LaunchedEffect(captureFlash) {
        if (captureFlash) {
            delay(95)
            captureFlash = false
        }
    }

    LaunchedEffect(focusReticle, focusLocked) {
        if (focusReticle != null && !focusLocked) {
            delay(850)
            focusReticle = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            spyVideoCapture = null
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        if (!spyMode) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (spyMode) {
            Box(Modifier.fillMaxSize().background(ComposeColor.Black))
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to ComposeColor.Black.copy(alpha = 0.34f),
                        0.24f to ComposeColor.Transparent,
                        0.62f to ComposeColor.Transparent,
                        1f to ComposeColor.Black.copy(alpha = 0.78f)
                    )
                )
        )

        if (!spyMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures(
                        onTap = { offset ->
                            focusLocked = false
                            focus = 0f
                            tapFocus(camera, previewView, offset.x, offset.y)
                            focusReticle = offset
                            status = "Tap focus"
                        },
                        onLongPress = { offset ->
                            focus = 0f
                            lockFocus(camera, previewView, offset.x, offset.y)
                            focusLocked = true
                            focusReticle = offset
                            status = "Focus locked"
                        }
                        )
                    }
            )
        }

        focusReticle?.let {
            FocusReticle(it, focusLocked)
        }

        if (captureFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor.White.copy(alpha = 0.58f))
            )
        }

        TopRail(
            rawEnabled = rawEnabled,
            rawAvailable = selectedCamera?.rawSupported == true,
            focusZoomEnabled = focusZoomEnabled,
            status = status,
            onRawToggle = {
                if (selectedCamera?.rawSupported == true) rawEnabled = !rawEnabled
            },
            onFocusZoomToggle = {
                val next = !focusZoomEnabled
                focusZoomEnabled = next
                val focusZoom = effectiveZoom(zoom, next, liveZoomRange)
                status = if (next) "Focus zoom ${"%.1f".format(focusZoom)}x" else "Focus zoom off"
            },
            onManualToggle = { manualOpen = !manualOpen }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = manualOpen && !spyMode) {
                ManualPanel(
                    selected = selectedCamera,
                    iso = iso,
                    shutterMs = shutterMs,
                    ev = ev,
                    focus = focus,
                    wbMode = wbMode,
                    zoom = zoom,
                    zoomRange = liveZoomRange,
                    onIso = { iso = it },
                    onShutter = { shutterMs = it },
                    onEv = { ev = it },
                    onFocus = { focus = it },
                    onWb = { wbMode = it },
                    onZoom = { zoom = it },
                    onClose = { manualOpen = false }
                )
            }
            Spacer(Modifier.height(8.dp))
            if (!spyMode) {
                LensRail(cameras, selectedCamera) { selectedCamera = it }
                Spacer(Modifier.height(8.dp))
            }
            BottomDeck(
                latestUri = latestUri,
                isCapturing = isCapturing,
                spyMode = spyMode,
                isRecording = activeRecording != null,
                onGallery = { openGallery(context, latestUri) },
                onSpy = {
                    if (activeRecording != null) {
                        stopSpyRecording()
                    } else if (spyMode) {
                        val capture = spyVideoCapture
                        if (capture != null) {
                            startSpyRecording(capture)
                        } else {
                            pendingSpyRecording = true
                            bindCamera()
                        }
                    } else {
                        selectedCamera = widestRearCamera(cameras) ?: selectedCamera
                        zoom = 1f
                        spyMode = true
                        pendingSpyRecording = false
                        manualOpen = false
                        focusZoomEnabled = false
                        focusReticle = null
                        focusLocked = false
                        status = "LIVE MODE"
                    }
                },
                onCapture = {
                    if (isCapturing) return@BottomDeck
                    val capture = imageCapture ?: return@BottomDeck
                    isCapturing = true
                    status = "Capturing..."
                    takePhoto(
                        context = context,
                        imageCapture = capture,
                        rawEnabled = rawEnabled && selectedCamera?.rawSupported == true,
                        onSaved = { uri, message ->
                            isCapturing = false
                            captureFlash = true
                            latestUri = uri ?: latestUri
                            status = message
                        },
                        onError = {
                            isCapturing = false
                            status = it
                        }
                    )
                }
            )
            Text(
                text = "${BuildConfig.BUILD_MODEL} / ${BuildConfig.BUILD_SHA}",
                color = ComposeColor.White.copy(alpha = 0.55f),
                fontSize = 8.sp,
                letterSpacing = 0.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun TopRail(
    rawEnabled: Boolean,
    rawAvailable: Boolean,
    focusZoomEnabled: Boolean,
    status: String,
    onRawToggle: () -> Unit,
    onFocusZoomToggle: () -> Unit,
    onManualToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "SOME FUSION",
            color = ComposeColor.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompactChip(
                selected = rawEnabled && rawAvailable,
                enabled = rawAvailable,
                onClick = onRawToggle,
                label = if (rawAvailable) "RAW" else "JPG",
                minWidth = 48.dp
            )
            CompactChip(
                selected = focusZoomEnabled,
                onClick = onFocusZoomToggle,
                label = "FZ",
                minWidth = 44.dp
            )
            IconButton(onClick = onManualToggle, modifier = Modifier.glassCircle(32.dp)) {
                Icon(Icons.Filled.Tune, contentDescription = "Manual controls", tint = ComposeColor.White, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { }, modifier = Modifier.glassCircle(32.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = ComposeColor.White.copy(alpha = 0.78f), modifier = Modifier.size(16.dp))
            }
        }
    }
    Text(
        text = status,
        color = ComposeColor.White.copy(alpha = 0.74f),
        fontSize = 10.sp,
        letterSpacing = 0.sp,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 44.dp)
            .fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ManualPanel(
    selected: LensInfo?,
    iso: Float,
    shutterMs: Float,
    ev: Float,
    focus: Float,
    wbMode: WbMode,
    zoom: Float,
    zoomRange: ClosedFloatingPointRange<Float>,
    onIso: (Float) -> Unit,
    onShutter: (Float) -> Unit,
    onEv: (Float) -> Unit,
    onFocus: (Float) -> Unit,
    onWb: (WbMode) -> Unit,
    onZoom: (Float) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(1.dp, ComposeColor.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("MANUAL", color = ComposeColor.White.copy(alpha = 0.54f), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            CompactChip(selected = false, onClick = onClose, label = "HIDE", minWidth = 56.dp)
        }
        ControlSlider(
            label = "ISO",
            value = iso,
            range = selected?.isoRange?.let { it.lower.toFloat()..it.upper.toFloat() } ?: 0f..1f,
            enabled = selected?.isoRange != null,
            display = iso.roundToInt().toString(),
            onValue = onIso
        )
        ControlSlider(
            label = "S",
            value = shutterMs,
            range = 1f..125f,
            enabled = selected?.manualSensor == true,
            display = "${shutterMs.roundToInt()} ms",
            onValue = onShutter
        )
        ControlSlider(
            label = "EV",
            value = ev,
            range = selected?.evRange ?: -2f..2f,
            enabled = selected?.evRange != null,
            display = "%+.1f".format(ev),
            onValue = onEv
        )
        ControlSlider(
            label = "FOC",
            value = focus,
            range = 0f..1f,
            enabled = selected?.manualFocus == true,
            display = if (focus <= 0.02f) "AF" else "${(focus * 100).roundToInt()}%",
            onValue = onFocus
        )
        ControlSlider(
            label = "Z",
            value = zoom,
            range = zoomRange,
            enabled = zoomRange.endInclusive > zoomRange.start,
            display = "%.1fx".format(zoom),
            onValue = onZoom
        )
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            WbMode.entries.forEach { mode ->
                CompactChip(
                    selected = wbMode == mode,
                    onClick = { onWb(mode) },
                    label = mode.label,
                    modifier = Modifier.weight(1f),
                    minWidth = 0.dp
                )
            }
        }
    }
}

@Composable
private fun FocusReticle(center: Offset, locked: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val radius = 34.dp.toPx()
        val tick = 10.dp.toPx()
        val stroke = if (locked) 3.dp.toPx() else 2.dp.toPx()
        drawCircle(color = Accent, radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(Accent, Offset(center.x - radius - tick, center.y), Offset(center.x - radius + tick, center.y), stroke)
        drawLine(Accent, Offset(center.x + radius - tick, center.y), Offset(center.x + radius + tick, center.y), stroke)
        drawLine(Accent, Offset(center.x, center.y - radius - tick), Offset(center.x, center.y - radius + tick), stroke)
        drawLine(Accent, Offset(center.x, center.y + radius - tick), Offset(center.x, center.y + radius + tick), stroke)
        if (locked) {
            drawCircle(color = Accent, radius = 5.dp.toPx(), center = center)
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    display: String,
    onValue: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(25.dp)) {
        Text(label, color = ComposeColor.White.copy(alpha = if (enabled) 0.82f else 0.32f), fontSize = 9.sp, modifier = Modifier.width(34.dp), maxLines = 1)
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Text(display, color = ComposeColor.White.copy(alpha = if (enabled) 0.82f else 0.32f), fontSize = 9.sp, textAlign = TextAlign.End, modifier = Modifier.width(42.dp), maxLines = 1)
    }
}

@Composable
private fun LensRail(cameras: List<LensInfo>, selected: LensInfo?, onSelect: (LensInfo) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ComposeColor.Black.copy(alpha = 0.52f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        cameras.forEach { lens ->
            Text(
                text = lens.label,
                color = if (lens.id == selected?.id) ComposeColor.Black else ComposeColor.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (lens.id == selected?.id) Accent else ComposeColor.Transparent)
                    .clickable { onSelect(lens) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun BottomDeck(
    latestUri: Uri?,
    isCapturing: Boolean,
    spyMode: Boolean,
    isRecording: Boolean,
    onGallery: () -> Unit,
    onSpy: () -> Unit,
    onCapture: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ComposeColor.White.copy(alpha = if (latestUri == null) 0.12f else 0.25f))
                .border(1.dp, ComposeColor.White.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .clickable(enabled = !spyMode) { onGallery() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Grid3x3, contentDescription = "Last photo", tint = ComposeColor.White.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
        }
        if (!spyMode) {
            Box(
                Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .border(3.dp, ComposeColor.White.copy(alpha = if (isCapturing) 0.38f else 0.86f), CircleShape)
                    .clickable { onCapture() }
                    .padding(7.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(if (isCapturing) Accent.copy(alpha = 0.74f) else ComposeColor.White.copy(alpha = 0.92f))
                )
            }
        } else {
            Spacer(Modifier.width(70.dp))
        }
        TextButton(
            onClick = onSpy,
            colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White),
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ComposeColor.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Filled.VisibilityOff, contentDescription = "Spy mode", tint = if (spyMode) Accent else ComposeColor.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                when {
                    isRecording -> "STOP"
                    spyMode -> "REC"
                    else -> "SPY"
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit) {
    Surface(color = ComposeColor.Black, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SOME FUSION", color = ComposeColor.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Camera permission is required to use the viewfinder.", color = ComposeColor.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Text(
                "Enable camera",
                color = ComposeColor.Black,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Accent)
                    .clickable { onRequest() }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            )
        }
    }
}

private fun Modifier.glassCircle(size: androidx.compose.ui.unit.Dp): Modifier = this
    .size(size)
    .clip(CircleShape)
    .background(ComposeColor.Black.copy(alpha = 0.34f))
    .border(1.dp, ComposeColor.White.copy(alpha = 0.08f), CircleShape)

@Composable
private fun CompactChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minWidth: androidx.compose.ui.unit.Dp = 42.dp
) {
    val bg = when {
        !enabled -> ComposeColor.Black.copy(alpha = 0.2f)
        selected -> Accent
        else -> ComposeColor.Black.copy(alpha = 0.42f)
    }
    val fg = when {
        !enabled -> ComposeColor.White.copy(alpha = 0.34f)
        selected -> ComposeColor.Black
        else -> ComposeColor.White.copy(alpha = 0.88f)
    }
    Text(
        text = label,
        color = fg,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = modifier
            .height(32.dp)
            .widthIn(min = minWidth)
            .clip(RoundedCornerShape(7.dp))
            .background(bg)
            .border(1.dp, ComposeColor.White.copy(alpha = if (selected) 0f else 0.14f), RoundedCornerShape(7.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

private fun discoverBackCameras(context: Context): List<LensInfo> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val lenses = manager.cameraIdList.mapNotNull { id ->
        val c = manager.getCameraCharacteristics(id)
        val facing = c.get(CameraCharacteristics.LENS_FACING)
        if (facing != CameraCharacteristics.LENS_FACING_BACK && facing != CameraCharacteristics.LENS_FACING_FRONT) return@mapNotNull null
        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
        val raw = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        val manualSensor = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR)
        val manualFocus = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)?.let { it > 0f } == true
        val iso = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        val evSteps = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val evStep = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
        val evRange = if (evSteps != null && evStep != null) {
            (evSteps.lower * evStep.toFloat())..(evSteps.upper * evStep.toFloat())
        } else {
            null
        }
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
        LensInfo(
            id = id,
            label = labelForFocalLength(focal, id, facing),
            rawSupported = raw,
            manualSensor = manualSensor,
            manualFocus = manualFocus,
            focalLength = focal,
            isoRange = iso,
            evRange = evRange,
            zoomRange = 1f..10f,
            facing = facing
        )
    }
    return lenses.ifEmpty {
        listOf(LensInfo("0", "1x", rawSupported = false, manualSensor = false, manualFocus = false))
    }
}

private fun labelForFocalLength(focalLength: Float?, fallback: String, facing: Int?): String {
    if (facing == CameraCharacteristics.LENS_FACING_FRONT) return "SELF"
    return when {
        focalLength == null -> fallback
        focalLength < 2.2f -> "0.6x"
        focalLength < 4.0f -> "1x"
        focalLength < 8.0f -> "3x"
        else -> "10x"
    }
}

private fun applyCameraState(
    camera: Camera?,
    selected: LensInfo?,
    iso: Float,
    shutterMs: Float,
    ev: Float,
    focus: Float,
    wbMode: WbMode,
    zoom: Float
) {
    if (camera == null || selected == null) return
    val zoomRange = currentZoomRange(camera)
    camera.cameraControl.setZoomRatio(zoom.coerceIn(zoomRange.start, zoomRange.endInclusive))
    selected.evRange?.let {
        val exposureState = camera.cameraInfo.exposureState
        if (exposureState.isExposureCompensationSupported) {
            val index = (ev / exposureState.exposureCompensationStep.toFloat()).roundToInt()
                .coerceIn(exposureState.exposureCompensationRange.lower, exposureState.exposureCompensationRange.upper)
            camera.cameraControl.setExposureCompensationIndex(index)
        }
    }

    val options = CaptureRequestOptions.Builder()
    if (selected.manualSensor && (iso > (selected.isoRange?.lower ?: 100) + 2 || shutterMs > 12f)) {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        selected.isoRange?.let {
            options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso.roundToInt().coerceIn(it.lower, it.upper))
        }
        options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, (shutterMs * 1_000_000L).toLong())
    } else {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
    }
    if (focus > 0.02f && selected.manualFocus) {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        options.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, focus * 10f)
    } else {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }
    options.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, wbMode.requestValue)
    Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(options.build())
}

private fun tapFocus(camera: Camera?, previewView: PreviewView, x: Float, y: Float) {
    camera ?: return
    val options = CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        .build()
    Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(options)
    val factory: MeteringPointFactory = previewView.meteringPointFactory
    val point = factory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .setAutoCancelDuration(3, TimeUnit.SECONDS)
        .build()
    camera.cameraControl.startFocusAndMetering(action)
}

private fun lockFocus(camera: Camera?, previewView: PreviewView, x: Float, y: Float) {
    camera ?: return
    val options = CaptureRequestOptions.Builder()
        .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        .build()
    Camera2CameraControl.from(camera.cameraControl).setCaptureRequestOptions(options)
    val point = previewView.meteringPointFactory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .disableAutoCancel()
        .build()
    camera.cameraControl.startFocusAndMetering(action)
}

private fun currentZoomRange(camera: Camera?): ClosedFloatingPointRange<Float> {
    val zoomState = camera?.cameraInfo?.zoomState?.value ?: return 1f..1f
    return zoomState.minZoomRatio..zoomState.maxZoomRatio
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    rawEnabled: Boolean,
    onSaved: (Uri?, String) -> Unit,
    onError: (String) -> Unit
) {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(System.currentTimeMillis())
    val jpegOptions = outputOptions(context, "SOMEFUSION_$name.jpg", "image/jpeg", "Pictures/SOME FUSION")
    val callbackExecutor = ContextCompat.getMainExecutor(context)

    val callback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onSaved(outputFileResults.savedUri, if (rawEnabled) "Saved RAW+JPEG" else "Saved JPEG")
        }

        override fun onError(exception: ImageCaptureException) {
            if (rawEnabled) {
                imageCapture.takePicture(jpegOptions, callbackExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onSaved(outputFileResults.savedUri, "RAW failed - saved JPEG")
                    }

                    override fun onError(jpegException: ImageCaptureException) {
                        onError("Capture failed: ${jpegException.message ?: exception.message ?: "unknown error"}")
                    }
                })
            } else {
                onError("Capture failed: ${exception.message ?: "unknown error"}")
            }
        }
    }

    if (rawEnabled) {
        val rawOptions = outputOptions(context, "SOMEFUSION_$name.dng", "image/x-adobe-dng", "Pictures/SOME FUSION/RAW")
        imageCapture.takePicture(rawOptions, jpegOptions, callbackExecutor, callback)
    } else {
        imageCapture.takePicture(jpegOptions, callbackExecutor, callback)
    }
}

private fun openGallery(context: Context, latestUri: Uri?) {
    val intent = if (latestUri != null) {
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(latestUri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    } else {
        Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    }
    runCatching { context.startActivity(intent) }
        .recover {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
            })
        }
}

private fun videoOutputOptions(context: Context): MediaStoreOutputOptions {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(System.currentTimeMillis())
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "SOMEFUSION_SPY_$name.mp4")
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/SOME FUSION")
    }
    return MediaStoreOutputOptions.Builder(
        context.contentResolver,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    )
        .setContentValues(values)
        .build()
}

private fun outputOptions(
    context: Context,
    displayName: String,
    mimeType: String,
    relativePath: String
): ImageCapture.OutputFileOptions {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
    }
    return ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    ).build()
}

private data class LensInfo(
    val id: String,
    val label: String,
    val rawSupported: Boolean,
    val manualSensor: Boolean,
    val manualFocus: Boolean,
    val focalLength: Float? = null,
    val isoRange: Range<Int>? = null,
    val evRange: ClosedFloatingPointRange<Float>? = null,
    val zoomRange: ClosedFloatingPointRange<Float>? = null,
    val facing: Int? = null
)

private fun widestRearCamera(cameras: List<LensInfo>): LensInfo? {
    return cameras
        .filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
        .minByOrNull { it.focalLength ?: Float.MAX_VALUE }
        ?: cameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }
}

private fun effectiveZoom(baseZoom: Float, focusZoomEnabled: Boolean, range: ClosedFloatingPointRange<Float>): Float {
    return if (focusZoomEnabled) {
        10f.coerceIn(range.start, range.endInclusive)
    } else {
        baseZoom.coerceIn(range.start, range.endInclusive)
    }
}

private enum class WbMode(val label: String, val requestValue: Int) {
    Auto("AWB", CaptureRequest.CONTROL_AWB_MODE_AUTO),
    Day("DAY", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    Cloud("CLD", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    Tungsten("TNG", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
}

private val Accent = ComposeColor(0xFFD8A84E)
private val Panel = ComposeColor(0xCC0D0F12)
