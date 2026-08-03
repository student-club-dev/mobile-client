package dev.core.uikit.media

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberDeviceGallery(): DeviceGallery {
    val context = LocalContext.current
    val gallery = remember(context) { AndroidDeviceGallery(context) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { gallery.refreshAccess() }
    gallery.launcher = launcher

    // ⚠️ Birinchi o'qish shu yerda: quyidagi kuzatuvchi faqat KEYINGI `ON_RESUME` ni oladi,
    // ya'ni ilova allaqachon old planda bo'lganda (odatiy holat) u hech qachon chaqirilmaydi
    // va ruxsat berilgan bo'lsa ham to'r «ruxsat kerak» deb turaverardi.
    LaunchedEffect(Unit) { gallery.refreshAccess() }

    // Ruxsat ilova sozlamalaridan ham berilishi mumkin (rad etilgandan keyin biz o'sha yerga
    // yuboramiz) — qaytganda holatni qayta o'qiymiz, aks holda to'r bo'sh qolib ketardi.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) gallery.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return gallery
}

/**
 * `MediaStore` ustidagi galereya.
 *
 * Rasm va video **bitta so'rovda** olinadi (`MediaStore.Files` + `MEDIA_TYPE` filtri) —
 * ikkita alohida jadvalni o'qib, keyin sana bo'yicha qo'shib chiqish sahifalashni buzardi:
 * har bir sahifada ikkala manbadan qancha olishni oldindan bilib bo'lmaydi.
 */
private class AndroidDeviceGallery(private val context: Context) : DeviceGallery {

    var launcher: ActivityResultLauncher<Array<String>>? = null

    override var access: GalleryAccess by mutableStateOf(GalleryAccess.UNKNOWN)
        private set

    /** Ruxsat rad etilganini eslab qolamiz: ikkinchi «rad» dan keyin tizim oynasi umuman chiqmaydi. */
    private var asked = false

    fun refreshAccess() {
        access = when {
            context.hasPermission(fullAccessPermissions()) -> GalleryAccess.GRANTED
            asked -> GalleryAccess.DENIED
            else -> GalleryAccess.UNKNOWN
        }
    }

    override fun requestAccess() {
        // Ikkinchi rad etishdan keyin tizim oynasi boshqa chiqmaydi — sozlamalarga yuboramiz,
        // aks holda tugma bosilib, hech nima bo'lmagandek ko'rinardi.
        if (access == GalleryAccess.DENIED) {
            context.openAppSettings()
            return
        }
        asked = true
        launcher?.launch(fullAccessPermissions())
    }

    override suspend fun page(offset: Int, limit: Int): List<GalleryItem> =
        withContext(Dispatchers.IO) {
            if (access == GalleryAccess.DENIED || access == GalleryAccess.UNKNOWN) {
                return@withContext emptyList()
            }
            runCatching { context.contentResolver.queryMedia(offset, limit) }.getOrDefault(emptyList())
        }

    override suspend fun thumbnail(item: GalleryItem, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(item.id)
            runCatching {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
                    // Eski Android: videodan kadr faqat retriever orqali olinadi, rasm esa
                    // oqimdan kichraytirib dekodlanadi.
                    item.isVideo -> context.videoFrame(uri)
                    else -> context.decodeDownsampled(uri, sizePx)
                }
            }.getOrNull()?.asImageBitmap()
        }

    override suspend fun load(items: List<GalleryItem>): PickedMedia = withContext(Dispatchers.IO) {
        val images = mutableListOf<PickedImage>()
        val videos = mutableListOf<PickedVideo>()
        var skipped = 0
        items.forEach { item ->
            val uri = Uri.parse(item.id)
            val picked = if (item.isVideo) {
                context.stagePickedVideo(uri)?.also(videos::add)
            } else {
                runCatching { context.readImage(uri) }.getOrNull()?.also(images::add)
            }
            if (picked == null) skipped += 1
        }
        PickedMedia(images = images, videos = videos, skipped = skipped)
    }
}

// --- Ruxsatlar --------------------------------------------------------------------------

/**
 * To'liq kirish uchun kerak bo'ladigan ruxsatlar.
 *
 * Android 13 dan boshlab media turlari ajratildi (`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`),
 * undan oldin esa bitta umumiy `READ_EXTERNAL_STORAGE` edi.
 */
private fun fullAccessPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun Context.hasPermission(permissions: Array<String>): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

private fun Context.openAppSettings() {
    runCatching {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// --- MediaStore -------------------------------------------------------------------------

/**
 * ⚠️ `DURATION` **faqat Android 10+** da ustun sifatida mavjud; undan eskisida uni
 * proyeksiyaga qo'shish `IllegalArgumentException` bilan butun so'rovni yiqitadi.
 */
private val MEDIA_COLUMNS: Array<String> = buildList {
    add(MediaStore.Files.FileColumns._ID)
    add(MediaStore.Files.FileColumns.MEDIA_TYPE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Files.FileColumns.DURATION)
}.toTypedArray()

private const val ORDER_NEWEST_FIRST = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

/**
 * Rasm va videolar — eng yangisidan boshlab, sahifalab.
 *
 * ⚠️ Sahifalash ikki xil: Android 8+ da `LIMIT`/`OFFSET` **so'rov argumentlari** orqali
 * beriladi, undan eskilarida esa `sortOrder` satrining oxiriga yoziladi. Ikkinchi yo'l
 * yangi Android'da e'tiborsiz qoldiriladi (va butun galereya bir sahifada kelib qolardi).
 */
private fun ContentResolver.queryMedia(offset: Int, limit: Int): List<GalleryItem> {
    val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
    val args = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )

    val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        query(
            collection,
            MEDIA_COLUMNS,
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, ORDER_NEWEST_FIRST)
                putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
            },
            null,
        )
    } else {
        query(collection, MEDIA_COLUMNS, selection, args, "$ORDER_NEWEST_FIRST LIMIT $limit OFFSET $offset")
    } ?: return emptyList()

    return cursor.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeColumn = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val durationColumn = it.getColumnIndex(MediaStore.Files.FileColumns.DURATION)

        buildList {
            while (it.moveToNext()) {
                val isVideo = it.getInt(typeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                add(
                    GalleryItem(
                        id = ContentUris.withAppendedId(collection, it.getLong(idColumn)).toString(),
                        isVideo = isVideo,
                        // `DURATION` provayder bermasligi mumkin — o'shanda katakda vaqt
                        // ko'rsatilmaydi, boshqa hech narsa buzilmaydi.
                        durationMs = if (isVideo && durationColumn >= 0) it.getInt(durationColumn) else 0,
                    ),
                )
            }
        }
    }
}

/**
 * Android 9 va undan eskilari uchun kichik nusxa: `loadThumbnail` faqat 10 (Q) dan bor.
 *
 * To'liq rasmni dekodlamaymiz — avval o'lchamini o'qib, `inSampleSize` bilan kerakli
 * kattalikkacha kichraytiramiz (aks holda 12 MP rasm to'rning har katagida xotirani yerdi).
 */
private fun Context.decodeDownsampled(uri: Uri, sizePx: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (bounds.outWidth / sample > sizePx && bounds.outHeight / sample > sizePx) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

/** Eski Android'da video katagi uchun birinchi kadr. */
private fun Context.videoFrame(uri: Uri): android.graphics.Bitmap? {
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        retriever.setDataSource(this, uri)
        retriever.getFrameAtTime(0)
    } catch (_: RuntimeException) {
        null
    } finally {
        retriever.release()
    }
}
