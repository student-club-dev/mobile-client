package dev.core.network.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Media havolasini tuzatish — rasm KO'RINMASLIGINING eng keng tarqalgan sababi shu yerda
 * yopiladi (`localhost`, `http://`, nisbiy yo'l). Xato bo'lsa ilovada bitta ham avatar
 * chizilmaydi, shuning uchun har bir holat alohida tekshiriladi.
 */
class MediaUrlTest {

    private val origin = "https://api.studentclub.uz"

    @Test
    fun loopbackHostIsReplacedWithApiOrigin() {
        // Backend o'zining ishga tushirilgan manzilini qaytaradi — telefonda `localhost`
        // telefonning O'ZI bo'lgani uchun rasm hech qachon yuklanmasdi.
        listOf(
            "http://localhost:3000/uploads/LOGO/a.jpg",
            "http://127.0.0.1:3000/uploads/LOGO/a.jpg",
            "http://0.0.0.0:3000/uploads/LOGO/a.jpg",
            "http://10.0.2.2:3000/uploads/LOGO/a.jpg",
        ).forEach { url ->
            assertEquals(
                "https://api.studentclub.uz/uploads/LOGO/a.jpg",
                MediaUrl.normalize(url, origin),
                url,
            )
        }
    }

    @Test
    fun ownHostIsUpgradedToHttps() {
        // Android 9+ cleartext `http` ni bloklaydi — havola `https` ga ko'tarilmasa
        // rasm jimgina yuklanmay qoladi.
        assertEquals(
            "https://api.studentclub.uz/uploads/LOGO/a.jpg",
            MediaUrl.normalize("http://api.studentclub.uz/uploads/LOGO/a.jpg", origin),
        )
    }

    @Test
    fun relativePathGetsOrigin() {
        assertEquals(
            "https://api.studentclub.uz/uploads/LOGO/a.jpg",
            MediaUrl.normalize("/uploads/LOGO/a.jpg", origin),
        )
        assertEquals(
            "https://api.studentclub.uz/uploads/LOGO/a.jpg",
            MediaUrl.normalize("uploads/LOGO/a.jpg", origin),
        )
    }

    @Test
    fun protocolRelativeGetsHttps() {
        assertEquals(
            "https://cdn.example.uz/a.jpg",
            MediaUrl.normalize("//cdn.example.uz/a.jpg", origin),
        )
    }

    @Test
    fun foreignHttpsUrlIsKept() {
        // Boshqa (masalan Google avatar) xosti — tegilmaydi.
        val google = "https://lh3.googleusercontent.com/a/abc123"
        assertEquals(google, MediaUrl.normalize(google, origin))
    }

    @Test
    fun foreignHttpUrlIsKept() {
        // Begona xostni `https` ga majburlab ko'tarish mumkin emas — u yerda sertifikat
        // bo'lmasligi mumkin. Faqat O'Z serverimiz ko'tariladi.
        val foreign = "http://cdn.example.uz/a.jpg"
        assertEquals(foreign, MediaUrl.normalize(foreign, origin))
    }

    @Test
    fun queryAndPortArePreserved() {
        assertEquals(
            "https://api.studentclub.uz/uploads/a.jpg?v=2",
            MediaUrl.normalize("http://localhost:3000/uploads/a.jpg?v=2", origin),
        )
    }

    @Test
    fun blankIsNull() {
        assertNull(MediaUrl.normalize(null, origin))
        assertNull(MediaUrl.normalize("", origin))
        assertNull(MediaUrl.normalize("   ", origin))
    }
}
