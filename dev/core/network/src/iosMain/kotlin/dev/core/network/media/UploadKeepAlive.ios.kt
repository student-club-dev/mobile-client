package dev.core.network.media

/**
 * iOS'da hech narsa qilinmaydi.
 *
 * Fonda yuklashni davom ettirish `URLSession` ning **background** konfiguratsiyasini
 * talab qiladi: so'rovni tizim demoni bajaradi, ilova esa uyg'otiladi. Bu Ktor'ning
 * Darwin dvigateli bilan emas, alohida yuklash yo'li bilan qilinadi — ya'ni bu yerdagi
 * bo'sh amal "unutilgan" emas, ataylab.
 */
internal actual fun setUploadKeepAlive(active: Boolean) = Unit
