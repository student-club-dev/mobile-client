package dev.core.uikit.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import dev.core.uikit.locale.uiStrings

/**
 * Android: CameraX (`LifecycleCameraController`).
 *
 * `LifecycleCameraController` ataylab: u `Preview`, `ImageCapture` va `VideoCapture`
 * use-case'larini o'zi yig'adi va lifecycle'ga bog'laydi. Qo'lda `ProcessCameraProvider`
 * bilan qilinganda o'sha ish uch barobar kod bo'lardi va aylantirish/nisbat mantiqi
 * qaytadan yozilishi kerak edi.
 */
@Composable
actual fun ScCameraScreen(
    onPhoto: (PickedImage) -> Unit,
    onVideo: (PickedVideo) -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
    galleryThumbnail: ImageBitmap?,
    allowVideo: Boolean,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    // Ruxsat ekran ochilishi bilan so'raladi: kamera ekraniga kirishning o'zi
    // "kamerani ochmoqchiman" degani, ya'ni qo'shimcha tugma ortiqcha bo'lardi.
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraSurface(
                context = context,
                allowVideo = allowVideo,
                onPhoto = onPhoto,
                onVideo = onVideo,
                onOpenGallery = onOpenGallery,
                onClose = onClose,
                galleryThumbnail = galleryThumbnail,
                lifecycleOwner = lifecycleOwner,
            )
        } else {
            PermissionPrompt(
                onRequest = { permission.launch(Manifest.permission.CAMERA) },
                onOpenGallery = onOpenGallery,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun CameraSurface(
    context: Context,
    allowVideo: Boolean,
    onPhoto: (PickedImage) -> Unit,
    onVideo: (PickedVideo) -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
    galleryThumbnail: ImageBitmap?,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
) {
    var videoMode by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }
    /** Yozish ketmoqda — tugma qizil kvadratga aylanadi. */
    var recording by remember { mutableStateOf<Recording?>(null) }
    /** Surat/video tayyorlanmoqda — takroriy bosishlar to'siladi. */
    var busy by remember { mutableStateOf(false) }

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                if (allowVideo) {
                    CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE
                } else {
                    CameraController.IMAGE_CAPTURE
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            // Ekran yopilganda yozish ketayotgan bo'lsa uni to'xtatamiz — aks holda
            // kamera va mikrofon band qolib ketardi.
            recording?.stop()
            controller.unbind()
        }
    }

    LaunchedEffect(frontCamera) {
        controller.cameraSelector = if (frontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                this.controller = controller
                // Kadr butun ekranni to'ldiradi (Telegram/Instagramdagi kabi) — «fit»
                // bo'lsa yon tomonlarda qora chiziqlar qolardi.
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

    // --- Tepadagi qator: yopish -------------------------------------------------------
    Box(Modifier.fillMaxSize()) {
        Icon(
            ScIcons.ChevronLeft,
            uiStrings().close,
            tint = Color.White,
            modifier = Modifier.align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onClose)
                .padding(9.dp),
        )

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Chap-past — galereya rasmchasi. Bosilganda galereyadan tanlash boshlanadi.
                GalleryThumb(galleryThumbnail, onOpenGallery)

                ShutterButton(
                    recording = recording != null,
                    enabled = !busy,
                    onClick = {
                        when {
                            recording != null -> {
                                recording?.stop()
                                recording = null
                            }
                            // `startRecording` darrov qaytadi va yozish boshlanadi;
                            // natija esa `stop()` dan keyin keladi.
                            videoMode -> {
                                recording = context.startRecording(controller) { picked ->
                                    recording = null
                                    if (picked != null) onVideo(picked)
                                }
                            }
                            else -> {
                                busy = true
                                context.takePhoto(controller) { picked ->
                                    busy = false
                                    if (picked != null) onPhoto(picked)
                                }
                            }
                        }
                    },
                )

                // O'ng — old/orqa kamera. Yozish paytida almashtirish CameraX'da
                // yozuvni uzib yuboradi, shuning uchun o'sha paytda o'chiriladi.
                Icon(
                    ScIcons.CameraSwitch,
                    "Kamerani almashtirish",
                    tint = if (recording == null) Color.White else Color.White.copy(alpha = 0.35f),
                    modifier = Modifier.size(46.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(enabled = recording == null) { frontCamera = !frontCamera }
                        .padding(11.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            if (allowVideo) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeChip("Rasm", selected = !videoMode, enabled = recording == null) {
                        videoMode = false
                    }
                    ModeChip("Video", selected = videoMode, enabled = recording == null) {
                        videoMode = true
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** Katta yumaloq tugma — yozish ketayotganda ichida qizil kvadrat. */
@Composable
private fun ShutterButton(recording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(74.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.4f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE23B3B)))
        } else {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        ScText(
            label,
            13.5f,
            if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

/** Chap-pastdagi galereya rasmchasi — Telegramdagi kabi kichik kvadrat. */
@Composable
private fun GalleryThumb(thumbnail: ImageBitmap?, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            androidx.compose.foundation.Image(
                bitmap = thumbnail,
                contentDescription = uiStrings().chooseFromGallery,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                AppIcons.ImageIcon,
                uiStrings().chooseFromGallery,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun PermissionPrompt(
    onRequest: () -> Unit,
    onOpenGallery: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScText(uiStrings().cameraPermissionTitle, 17f, FontWeight.ExtraBold, Color.White, maxLines = 2)
        Spacer(Modifier.height(8.dp))
        ScText(
            uiStrings().cameraPermissionBody,
            13.5f,
            FontWeight.Medium,
            Color.White.copy(alpha = 0.75f),
            lineHeight = 19f,
            maxLines = 4,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptButton(uiStrings().grantPermission, onRequest)
            PromptButton("Galereya", onOpenGallery)
            PromptButton(uiStrings().close, onClose)
        }
    }
}

@Composable
private fun PromptButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        ScText(label, 13f, FontWeight.Bold, Color.White, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// CameraX chaqiruvlari
// ---------------------------------------------------------------------------

/**
 * Surat oladi va uni [PickedImage] ga aylantiradi.
 *
 * Fayl ilovaning **keshiga** yoziladi va baytlari o'qilgach o'chiriladi: galereyaga
 * tushmasligi kerak (`newCaptureFile` izohiga q.).
 */
private fun Context.takePhoto(
    controller: LifecycleCameraController,
    onResult: (PickedImage?) -> Unit,
) {
    val file = newCaptureFile("jpg")
    val options = ImageCapture.OutputFileOptions.Builder(file).build()
    controller.takePicture(
        options,
        ContextCompat.getMainExecutor(this),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val bytes = runCatching { file.readBytes() }.getOrNull()
                file.delete()
                onResult(bytes?.takeIf { it.isNotEmpty() }?.let { PickedImage(it, file.name) })
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                onResult(null)
            }
        },
    )
}

/**
 * Video yozishni boshlaydi. Qaytgan [Recording] ni `stop()` qilish — yozishni tugatadi va
 * shundan keyingina [onResult] chaqiriladi.
 *
 * Ovoz faqat `RECORD_AUDIO` ruxsati bor bo'lsa yoziladi: ruxsatsiz `withAudioEnabled`
 * `SecurityException` bilan yiqiladi va butun yozuv yo'qolardi.
 */
private fun Context.startRecording(
    controller: LifecycleCameraController,
    onResult: (PickedVideo?) -> Unit,
): Recording? {
    val file = newCaptureFile("mp4")
    val audio = if (hasAudioPermission()) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED
    return runCatching {
        controller.startRecording(
            FileOutputOptions.Builder(file).build(),
            audio,
            ContextCompat.getMainExecutor(this),
        ) { event ->
            if (event !is VideoRecordEvent.Finalize) return@startRecording
            if (event.hasError()) {
                file.delete()
                onResult(null)
                return@startRecording
            }
            // Tavsiflash (davomiylik, poster, kodek) fon oqimida — u `MediaMetadataRetriever`
            // bilan ishlaydi va asosiy oqimda ekranni qotirardi.
            stageRecordedVideo(file, onResult)
        }
    }.getOrElse {
        file.delete()
        onResult(null)
        null
    }
}

/**
 * Yozib olingan faylni [PickedVideo] ga aylantiradi.
 *
 * `stagePickedVideo` — galereyadan tanlangan video bilan bir xil yo'l: davomiylik,
 * o'lcham, kodek va birinchi kadr o'qiladi. `ownedFile` bilan chaqiriladi, ya'ni fayl
 * nusxalanmaydi (u allaqachon bizning keshimizda).
 */
private fun Context.stageRecordedVideo(file: File, onResult: (PickedVideo?) -> Unit) {
    val uri = captureUriOrNull(file)
    if (uri == null) {
        file.delete()
        onResult(null)
        return
    }
    cameraScope.launchStaging(this, uri, ownedFile = file) { picked ->
        if (picked == null) file.delete()
        onResult(picked)
    }
}

/**
 * Tavsiflash uchun korutina doirasi.
 *
 * Kompozitsiyaniki EMAS: kamera ekrani natija kelishi bilan yopiladi va uning doirasi
 * bekor bo'lardi — video esa tavsiflanmay qolardi.
 */
private val cameraScope = kotlinx.coroutines.CoroutineScope(
    kotlinx.coroutines.SupervisorJob() + Dispatchers.Main,
)

private fun Context.hasAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

// ---------------------------------------------------------------------------
// Galereyadagi oxirgi element
// ---------------------------------------------------------------------------

@Composable
actual fun rememberLatestGalleryThumbnail(sizePx: Int): ImageBitmap? {
    val gallery = rememberDeviceGallery()
    val access = gallery.access
    return produceState<ImageBitmap?>(initialValue = null, access, sizePx) {
        if (access != GalleryAccess.GRANTED && access != GalleryAccess.LIMITED) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                gallery.page(offset = 0, limit = 1).firstOrNull()
                    ?.let { gallery.thumbnail(it, sizePx) }
            }.getOrNull()
        }
    }.value
}
