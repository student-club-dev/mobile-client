package dev.core.common.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Telefon va summa qoliplari — butun ilova shu qoidalarga tayangani uchun
 * ular test bilan qotirilgan.
 */
class FormatsTest {

    // --- Telefon -----------------------------------------------------------

    @Test
    fun `har xil yozilgan raqamdan 9 xona ajratiladi`() {
        assertEquals("901234567", "+998 90 123 45 67".toUzPhoneDigits())
        assertEquals("901234567", "998901234567".toUzPhoneDigits())
        assertEquals("901234567", "901234567".toUzPhoneDigits())
        assertEquals("901234567", "(90) 123-45-67".toUzPhoneDigits())
    }

    @Test
    fun `9 xonadan ortiq raqam yozib bolmaydi`() {
        assertEquals("901234567", "9012345671234".toUzPhoneDigits())
        assertEquals(UZ_PHONE_DIGITS, "9012345671234".toUzPhoneDigits().length)
    }

    @Test
    fun `harflar va boshqa belgilar tashlanadi`() {
        assertEquals("901234567", "90abc123!45#67".toUzPhoneDigits())
    }

    @Test
    fun `qolip 90 123 45 67 korinishida`() {
        assertEquals("90 123 45 67", formatUzPhone("901234567"))
        assertEquals("90", formatUzPhone("90"))
        assertEquals("90 1", formatUzPhone("901"))
        assertEquals("90 123 45", formatUzPhone("9012345"))
        assertEquals("", formatUzPhone(""))
    }

    @Test
    fun `korsatish uchun toliq qolip`() {
        assertEquals("+998 90 123 45 67", formatUzPhoneFull("+998901234567"))
        assertEquals("+998 90 123 45 67", formatUzPhoneFull("901234567"))
        assertEquals("", formatUzPhoneFull(null))
    }

    @Test
    fun `chala raqam ozgarishsiz qaytadi`() {
        assertEquals("9012", formatUzPhoneFull("9012"))
    }

    @Test
    fun `saqlashda doim +998 prefiksi`() {
        assertEquals("+998901234567", "90 123 45 67".toUzPhoneE164())
        assertEquals("+998901234567", "+998 90 123 45 67".toUzPhoneE164())
    }

    @Test
    fun `chala raqam saqlanmaydi`() {
        assertNull("9012345".toUzPhoneE164())
        assertNull("".toUzPhoneE164())
        assertFalse("9012345".isUzPhoneComplete())
        assertTrue("+998901234567".isUzPhoneComplete())
    }

    // --- Summa -------------------------------------------------------------

    @Test
    fun `summa uch xonali guruhlarga bolinadi`() {
        assertEquals("90 000", formatAmount("90000"))
        assertEquals("900", formatAmount("900"))
        assertEquals("1 500 000", formatAmount("1500000"))
        assertEquals("", formatAmount(""))
    }

    @Test
    fun `Long summasi ham xuddi shunday`() {
        assertEquals("55 000", 55_000L.formatAmount())
        assertEquals("0", 0L.formatAmount())
    }

    @Test
    fun `summadan raqam bolmagan belgilar tashlanadi`() {
        assertEquals("90000", "90 000 so'm".toAmountDigits())
        assertEquals("90000", "90.000".toAmountDigits())
    }

    @Test
    fun `boshidagi nollar olib tashlanadi`() {
        assertEquals("90", "090".toAmountDigits())
        assertEquals("", "000".toAmountDigits())
    }

    @Test
    fun `summa uzunligi chegaralangan`() {
        assertEquals(MAX_AMOUNT_DIGITS, "1234567890123456".toAmountDigits().length)
    }

    @Test
    fun `birligi bilan korsatish`() {
        // Sukut birlik endi TILGA bog'langan (ilova ingliz tilida ochiladi), shuning uchun
        // test uni aniq uzatadi — aks holda tekshiruv joriy tilga qarab o'zgarardi.
        assertEquals("90 000 so'm", formatAmountWithUnit("90000", unit = "so'm"))
        assertEquals("90 000 UZS", formatAmountWithUnit("90000"))
        assertEquals("", formatAmountWithUnit(""))
    }

    @Test
    fun `tor joy uchun qisqartma`() {
        assertEquals("21k", 21_000L.formatAmountShort())
        assertEquals("890k", 890_000L.formatAmountShort())
        assertEquals("6.5M", 6_500_000L.formatAmountShort())
        assertEquals("2M", 2_000_000L.formatAmountShort())
        assertEquals("500", 500L.formatAmountShort())
    }
}
