package dev.core.network.media

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Yuklash ketayotganda ilova jarayonini tirik ushlab turadi.
 *
 * Muammo Android'ga xos: fayl yuklash oddiy korutin va u ilova fonga o'tganda tizim uchun
 * "hech narsa" — bir necha daqiqadan keyin jarayon o'ldiriladi va 200 MB lik video yarim
 * yo'lda qoladi. Foydalanuvchi buni faqat chatga qaytganda ko'radi.
 *
 * Yechim — yuklash davomida **old plan xizmati** (`dataSync`): tizim uni o'ldirmaydi va
 * bildirishnomada nima bo'layotgani ko'rinib turadi. Xizmat birinchi yuklash boshlanganda
 * ko'tariladi, oxirgisi tugagach o'chadi — shuning uchun hisoblagich.
 *
 * iOS'da hozircha bo'sh: u yerda fon yuklash `URLSession` ning background konfiguratsiyasini
 * talab qiladi, ya'ni butun boshqa yuklash yo'li — alohida ish.
 */
object UploadKeepAlive {

    private val lock = Mutex()
    private var active = 0

    /**
     * [block] ketayotganda jarayonni tirik ushlaydi.
     *
     * Ichma-ich chaqirilishi mumkin (`chatUploadFile` → `resumableUpload`): hisoblagich
     * faqat noldan birga o'tishda xizmatni ko'taradi.
     */
    suspend fun <T> holding(block: suspend () -> T): T {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }

    private suspend fun enter() = lock.withLock {
        if (active++ == 0) setUploadKeepAlive(true)
    }

    // ⚠️ `NonCancellable`: bekor qilingan korutinda `withLock` navbatda turib istisno
    // beradi va hisoblagich kamaymay qolardi — xizmat esa abadiy osilib turardi.
    private suspend fun exit() = withContext(NonCancellable) {
        lock.withLock {
            if (--active <= 0) {
                active = 0
                setUploadKeepAlive(false)
            }
        }
    }
}

/** Platformaga xos qismi: Android'da old plan xizmati, iOS'da hech narsa. */
internal expect fun setUploadKeepAlive(active: Boolean)
