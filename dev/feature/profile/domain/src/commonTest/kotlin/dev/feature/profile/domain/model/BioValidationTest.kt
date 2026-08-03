package dev.feature.profile.domain.model

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tarjimayi hol qoidalari (`handoff/08-PROFILE.md` §5).
 *
 * Nega klientda tekshiriladi: server baribir `422 BIO_NOT_ALLOWED` beradi, lekin foydalanuvchi
 * buni faqat «Saqlash» bosgandan keyin ko'rardi. Bu testlar qoidalarni serverdagi bilan bir
 * xil ushlab turadi — biri o'zgarsa ikkinchisi ham o'zgarishi kerak.
 */
class BioValidationTest {

    @Test
    fun `oddiy matn o'tadi`() {
        assertNull(bioRejectionReason("5/5 · Dasturiy injiniring"))
        assertNull(bioRejectionReason("Kitob o'qishni yoqtiraman"))
        // Bo'sh satr — "tozalash", xato emas.
        assertNull(bioRejectionReason(""))
        assertNull(bioRejectionReason("   "))
    }

    @Test
    fun `havolalar rad etiladi`() {
        assertNotNull(bioRejectionReason("https://studentclub.uz"))
        assertNotNull(bioRejectionReason("http://example.com"))
        assertNotNull(bioRejectionReason("t.me/kanalim"))
        assertNotNull(bioRejectionReason("@kanalim"))
        // Yalang'och domen — protokolsiz ham.
        assertNotNull(bioRejectionReason("arzonkiyim.uz da chegirmalar"))
    }

    @Test
    fun `telefon raqami rad etiladi — ajratgichlar hisobga olinmaydi`() {
        assertNotNull(bioRejectionReason("+998 90 123 45 67"))
        assertNotNull(bioRejectionReason("(90) 123-45-67"))
        assertNotNull(bioRejectionReason("901234567"))
    }

    @Test
    fun `qisqa raqamlar o'tadi`() {
        // 7 tadan kam raqam telefon deb hisoblanmaydi — kurs/yil kabi qiymatlar yozilsin.
        assertNull(bioRejectionReason("2026-yil bitiruvchisi"))
        assertNull(bioRejectionReason("3-kurs, 15-guruh"))
    }

    @Test
    fun `uzunlik chegarasi`() {
        assertNull(bioRejectionReason("a".repeat(UserProfile.MAX_BIO)))
        assertNotNull(bioRejectionReason("a".repeat(UserProfile.MAX_BIO + 1)))
    }
}
