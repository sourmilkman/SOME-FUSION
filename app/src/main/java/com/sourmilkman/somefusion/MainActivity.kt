package com.sourmilkman.somefusion

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import androidx.camera.camera2.interop.Camera2Interop
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
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
    var camera by remember { mutableStateOf<Camera?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var rawEnabled by remember { mutableStateOf(true) }
    var manualOpen by remember { mutableStateOf(true) }
    var latestUri by remember { mutableStateOf<Uri?>(null) }
    var iso by remember { mutableFloatStateOf(selectedCamera?.isoRange?.lower?.toFloat() ?: 100f) }
    var shutterMs by remember { mutableFloatStateOf(8f) }
    var ev by remember { mutableFloatStateOf(0f) }
    var focus by remember { mutableFloatStateOf(0f) }
    var wbMode by remember { mutableStateOf(WbMode.Auto) }
    var zoom by remember { mutableFloatStateOf(1f) }

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

            try {
                provider.unbindAll()
                val capture = captureBuilder.build()
                imageCapture = capture
                camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                applyCameraState(camera, selectedCamera, iso, shutterMs, ev, focus, wbMode, zoom)
                status = if (requestedRaw) "RAW+JPEG ready" else "JPEG ready"
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

    LaunchedEffect(selectedCamera?.id, rawEnabled) {
        iso = selectedCamera?.isoRange?.lower?.toFloat() ?: 100f
        bindCamera()
    }

    LaunchedEffect(camera, iso, shutterMs, ev, focus, wbMode, zoom) {
        applyCameraState(camera, selectedCamera, iso, shutterMs, ev, focus, wbMode, zoom)
    }

    DisposableEffect(Unit) {
        onDispose {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
        }
    }

    Box(Modifier.fillMaxSize().background(ComposeColor.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera) {
                    detectTapGestures { offset ->
                        tapFocus(camera, previewView, offset.x, offset.y)
                        status = "Focus set"
                    }
                }
        )

        TopRail(
            rawEnabled = rawEnabled,
            rawAvailable = selectedCamera?.rawSupported == true,
            status = status,
            onRawToggle = {
                if (selectedCamera?.rawSupported == true) rawEnabled = !rawEnabled
            },
            onManualToggle = { manualOpen = !manualOpen }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = manualOpen) {
                ManualPanel(
                    selected = selectedCamera,
                    iso = iso,
                    shutterMs = shutterMs,
                    ev = ev,
                    focus = focus,
                    wbMode = wbMode,
                    zoom = zoom,
                    onIso = { iso = it },
                    onShutter = { shutterMs = it },
                    onEv = { ev = it },
                    onFocus = { focus = it },
                    onWb = { wbMode = it },
                    onZoom = { zoom = it }
                )
            }
            Spacer(Modifier.height(12.dp))
            LensRail(cameras, selectedCamera) { selectedCamera = it }
            Spacer(Modifier.height(12.dp))
            BottomDeck(
                latestUri = latestUri,
                onCapture = {
                    val capture = imageCapture ?: return@BottomDeck
                    status = "Capturing..."
                    takePhoto(
                        context = context,
                        imageCapture = capture,
                        cameraExecutor = cameraExecutor,
                        rawEnabled = rawEnabled && selectedCamera?.rawSupported == true,
                        onSaved = { uri, message ->
                            latestUri = uri ?: latestUri
                            status = message
                        },
                        onError = { status = it }
                    )
                }
            )
            Text(
                text = "${BuildConfig.BUILD_MODEL} / ${BuildConfig.BUILD_SHA}",
                color = ComposeColor.White.copy(alpha = 0.55f),
                fontSize = 10.sp,
                letterSpacing = 0.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

@Composable
private fun TopRail(
    rawEnabled: Boolean,
    rawAvailable: Boolean,
    status: String,
    onRawToggle: () -> Unit,
    onManualToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .statusBarsPadding()
            .padding(14.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "SOME FUSION",
            color = ComposeColor.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rawEnabled && rawAvailable,
                enabled = rawAvailable,
                onClick = onRawToggle,
                label = { Text(if (rawAvailable) "RAW+JPEG" else "JPEG") },
                colors = chipColors()
            )
            IconButton(onClick = onManualToggle, modifier = Modifier.glassCircle()) {
                Icon(Icons.Filled.Tune, contentDescription = "Manual controls", tint = ComposeColor.White)
            }
            IconButton(onClick = { }, modifier = Modifier.glassCircle()) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = ComposeColor.White.copy(alpha = 0.78f))
            }
        }
    }
    Text(
        text = status,
        color = ComposeColor.White.copy(alpha = 0.74f),
        fontSize = 12.sp,
        letterSpacing = 0.sp,
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 58.dp)
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
    onIso: (Float) -> Unit,
    onShutter: (Float) -> Unit,
    onEv: (Float) -> Unit,
    onFocus: (Float) -> Unit,
    onWb: (WbMode) -> Unit,
    onZoom: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .border(1.dp, ComposeColor.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
    ) {
        ControlSlider(
            label = "ISO",
            value = iso,
            range = selected?.isoRange?.let { it.lower.toFloat()..it.upper.toFloat() } ?: 0f..1f,
            enabled = selected?.isoRange != null,
            display = iso.roundToInt().toString(),
            onValue = onIso
        )
        ControlSlider(
            label = "SHUTTER",
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
            label = "FOCUS",
            value = focus,
            range = 0f..1f,
            enabled = selected?.manualFocus == true,
            display = if (focus <= 0.02f) "AF" else "${(focus * 100).roundToInt()}%",
            onValue = onFocus
        )
        ControlSlider(
            label = "ZOOM",
            value = zoom,
            range = selected?.zoomRange ?: 1f..1f,
            enabled = selected?.zoomRange?.let { it.endInclusive > it.start } == true,
            display = "%.1fx".format(zoom),
            onValue = onZoom
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WbMode.entries.forEach { mode ->
                FilterChip(
                    selected = wbMode == mode,
                    onClick = { onWb(mode) },
                    label = { Text(mode.label) },
                    colors = chipColors(),
                    modifier = Modifier.weight(1f)
                )
            }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = ComposeColor.White.copy(alpha = if (enabled) 0.82f else 0.32f), fontSize = 11.sp, modifier = Modifier.width(72.dp))
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValue,
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        Text(display, color = ComposeColor.White.copy(alpha = if (enabled) 0.82f else 0.32f), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
    }
}

@Composable
private fun LensRail(cameras: List<LensInfo>, selected: LensInfo?, onSelect: (LensInfo) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(ComposeColor.Black.copy(alpha = 0.52f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        cameras.forEach { lens ->
            Text(
                text = lens.label,
                color = if (lens.id == selected?.id) ComposeColor.Black else ComposeColor.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (lens.id == selected?.id) Accent else ComposeColor.Transparent)
                    .clickable { onSelect(lens) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun BottomDeck(latestUri: Uri?, onCapture: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ComposeColor.White.copy(alpha = if (latestUri == null) 0.12f else 0.25f))
                .border(1.dp, ComposeColor.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Grid3x3, contentDescription = "Last photo", tint = ComposeColor.White.copy(alpha = 0.75f), modifier = Modifier.size(20.dp))
        }
        Box(
            Modifier
                .size(86.dp)
                .clip(CircleShape)
                .border(4.dp, ComposeColor.White.copy(alpha = 0.86f), CircleShape)
                .clickable { onCapture() }
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(ComposeColor.White.copy(alpha = 0.92f))
            )
        }
        TextButton(
            onClick = { },
            colors = ButtonDefaults.textButtonColors(contentColor = ComposeColor.White),
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ComposeColor.White.copy(alpha = 0.1f))
        ) {
            Text("NATURAL", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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

private fun Modifier.glassCircle(): Modifier = this
    .size(42.dp)
    .clip(CircleShape)
    .background(ComposeColor.Black.copy(alpha = 0.34f))
    .border(1.dp, ComposeColor.White.copy(alpha = 0.08f), CircleShape)

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = Accent,
    selectedLabelColor = ComposeColor.Black,
    containerColor = ComposeColor.Black.copy(alpha = 0.34f),
    labelColor = ComposeColor.White.copy(alpha = 0.86f),
    disabledContainerColor = ComposeColor.Black.copy(alpha = 0.18f),
    disabledLabelColor = ComposeColor.White.copy(alpha = 0.36f)
)

private fun discoverBackCameras(context: Context): List<LensInfo> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val lenses = manager.cameraIdList.mapNotNull { id ->
        val c = manager.getCameraCharacteristics(id)
        if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
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
            label = labelForFocalLength(focal, id),
            rawSupported = raw,
            manualSensor = manualSensor,
            manualFocus = manualFocus,
            isoRange = iso,
            evRange = evRange,
            zoomRange = 1f..10f
        )
    }
    return lenses.ifEmpty {
        listOf(LensInfo("0", "1x", rawSupported = false, manualSensor = false, manualFocus = false))
    }
}

private fun labelForFocalLength(focalLength: Float?, fallback: String): String {
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
    camera.cameraControl.setZoomRatio(zoom.coerceIn(selected.zoomRange ?: 1f..1f))
    selected.evRange?.let {
        val exposureState = camera.cameraInfo.exposureState
        if (exposureState.isExposureCompensationSupported) {
            val index = (ev / exposureState.exposureCompensationStep.toFloat()).roundToInt()
                .coerceIn(exposureState.exposureCompensationRange.lower, exposureState.exposureCompensationRange.upper)
            camera.cameraControl.setExposureCompensationIndex(index)
        }
    }

    val options = CaptureRequestOptions.Builder()
    if (selected.manualSensor) {
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
        selected.isoRange?.let {
            options.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, iso.roundToInt().coerceIn(it.lower, it.upper))
        }
        options.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, (shutterMs * 1_000_000L).toLong())
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
    val factory: MeteringPointFactory = previewView.meteringPointFactory
    val point = factory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
        .setAutoCancelDuration(3, TimeUnit.SECONDS)
        .build()
    camera?.cameraControl?.startFocusAndMetering(action)
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    cameraExecutor: ExecutorService,
    rawEnabled: Boolean,
    onSaved: (Uri?, String) -> Unit,
    onError: (String) -> Unit
) {
    val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.UK).format(System.currentTimeMillis())
    val jpegOptions = outputOptions(context, "SOMEFUSION_$name.jpg", "image/jpeg", "Pictures/SOME FUSION")

    val callback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            onSaved(outputFileResults.savedUri, if (rawEnabled) "Saved RAW+JPEG" else "Saved JPEG")
        }

        override fun onError(exception: ImageCaptureException) {
            onError("Capture failed: ${exception.message ?: "unknown error"}")
        }
    }

    if (rawEnabled) {
        val rawOptions = outputOptions(context, "SOMEFUSION_$name.dng", "image/x-adobe-dng", "Pictures/SOME FUSION/RAW")
        imageCapture.takePicture(rawOptions, jpegOptions, cameraExecutor, callback)
    } else {
        imageCapture.takePicture(jpegOptions, cameraExecutor, callback)
    }
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
    val isoRange: Range<Int>? = null,
    val evRange: ClosedFloatingPointRange<Float>? = null,
    val zoomRange: ClosedFloatingPointRange<Float>? = null
)

private enum class WbMode(val label: String, val requestValue: Int) {
    Auto("AWB", CaptureRequest.CONTROL_AWB_MODE_AUTO),
    Day("DAY", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    Cloud("CLD", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    Tungsten("TNG", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT)
}

private val Accent = ComposeColor(0xFFD8A84E)
private val Panel = ComposeColor(0xCC0D0F12)
