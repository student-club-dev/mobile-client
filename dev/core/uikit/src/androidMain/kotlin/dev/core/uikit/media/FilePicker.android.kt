package dev.core.uikit.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Har qanday turdagi hujjat — chatga rasm ham, arxiv ham, PDF ham yuborilishi mumkin. */
private val ANY_MIME_TYPE = arrayOf("*/*")

@Composable
actual fun rememberFilePicker(onResult: (PickedFile?) -> Unit): FilePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // `GetContent` emas, `OpenDocument`: u SAF hujjatlarini (Drive, yuklamalar, tashqi
    // xotira) ham beradi va URI ustidan o'qish ruxsati barqaror bo'ladi.
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            onResult(null) // bekor qilindi
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            // ⚠️ Fayl o'nlab megabayt bo'lishi mumkin — asosiy oqimda o'qilsa UI qotadi.
            val picked = withContext(Dispatchers.IO) {
                runCatching { context.readDocument(uri) }.getOrNull()
            }
            onResult(picked)
        }
    }

    return remember(launcher) {
        FilePicker { launcher.launch(ANY_MIME_TYPE) }
    }
}

/**
 * Hujjatni metama'lumoti bilan birga o'qiydi.
 *
 * Nomi va hajmi `ContentResolver` dan olinadi: `content://` URI'da yo'l bo'lmasligi mumkin,
 * shuning uchun oxirgi segmentga tayanib bo'lmaydi.
 */
private fun Context.readDocument(uri: Uri): PickedFile? {
    val meta = queryOpenableMeta(uri)

    // ⚠️ Hajm ma'lum bo'lsa — faylni umuman ochmaymiz: chegaradan kattasini xotiraga
    // yuklash ilovani o'ldiradi.
    if (meta.size != null && meta.size > MAX_FILE_BYTES) return null

    val bytes = contentResolver.openInputStream(uri)?.use { it.readAtMost(MAX_FILE_BYTES) }
        ?: return null
    if (bytes.isEmpty()) return null

    return PickedFile(
        bytes = bytes,
        // Nomsiz provayder ham uchraydi — server uchun hech bo'lmasa biror nom kerak.
        fileName = meta.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file",
        mimeType = contentResolver.getType(uri),
        // Provayder aytgan hajmga emas, haqiqatda o'qilganiga ishonamiz.
        sizeBytes = bytes.size.toLong(),
    )
}

/** Ikkala maydon ham `null` bo'lishi mumkin — provayder ularni berishga majbur emas. */
private class OpenableMeta(val name: String?, val size: Long?)

private fun Context.queryOpenableMeta(uri: Uri): OpenableMeta {
    val cursor = contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    ) ?: return OpenableMeta(name = null, size = null)

    return cursor.use {
        if (!it.moveToFirst()) return@use OpenableMeta(name = null, size = null)

        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val name = if (nameIndex >= 0 && !it.isNull(nameIndex)) {
            it.getString(nameIndex)?.takeIf { value -> value.isNotBlank() }
        } else {
            null
        }

        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
        // Manfiy hajm = "bilmayman" degani, uni chegara tekshiruviga qo'shmaymiz.
        val size = if (sizeIndex >= 0 && !it.isNull(sizeIndex)) {
            it.getLong(sizeIndex).takeIf { value -> value >= 0L }
        } else {
            null
        }

        OpenableMeta(name = name, size = size)
    }
}

/**
 * Oqimni chegaragacha o'qiydi, oshib ketsa `null`.
 *
 * ⚠️ Ba'zi provayderlar `SIZE` ni bermaydi (`null` yoki `-1`). O'sha holatda ham xotira
 * portlamasligi uchun chegaradan **bitta bayt ko'p** o'qishga urinamiz: oshgani shundan
 * bilinadi va butun fayl xotiraga tushmaydi.
 */
private fun InputStream.readAtMost(limit: Int): ByteArray? {
    val sink = ByteArrayOutputStream()
    val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(chunk)
        if (read == -1) break
        total += read
        if (total > limit) return null
        sink.write(chunk, 0, read)
    }
    return sink.toByteArray()
}
