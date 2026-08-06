package dev.feature.university.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Barcha misollar — `prof-emis.edu.uz` dagi **haqiqiy** yozuvlar (2026-08 holatiga).
 * Ular tasodifiy tanlanmagan: har biri bitta buzilish turini qamrab oladi.
 */
class UniversityNamingTest {

    // ---------------------------------------------------------------------------------
    // Qisqa nom
    // ---------------------------------------------------------------------------------

    @Test
    fun taxallus_asosiy_qatordan_chiqariladi() {
        val d = UniversityNaming.display("Muhammad al-Xorazmiy nomidagi Toshkent axborot texnologiyalari universiteti")
        assertEquals("Toshkent axborot texnologiyalari universiteti", d.shortName)
        assertEquals("Muhammad al-Xorazmiy", d.eponym)
        assertEquals("TATU", d.abbr)
    }

    @Test
    fun huquqiy_shtamp_va_filial_ajratiladi() {
        val d = UniversityNaming.display(
            "D.I.Mendeleev nomidagi Rossiya kimyo-texnologiya universiteti Federal davlat " +
                "byudjeti oliy taʼlim muassasasining Toshkent filiali",
        )
        assertEquals("Rossiya kimyo-texnologiya universiteti", d.shortName)
        assertEquals("Toshkent filiali", d.branch)
        assertEquals("D.I.Mendeleev", d.eponym)
    }

    /** «universiteti filiali» — filialni kesganda nomning TURI ketib qolmasligi kerak. */
    @Test
    fun filialdan_oldingi_tur_sozi_saqlanadi() {
        val d = UniversityNaming.display(
            "N.I.Pirogov nomidagi Rossiya milliy tadqiqot tibbiyot universitetining Toshkent shahridagi filiali",
        )
        assertEquals("Rossiya milliy tadqiqot tibbiyot universiteti", d.shortName)
        assertEquals("Toshkent filiali", d.branch)
    }

    @Test
    fun qavs_ichidagi_taxallus_tushib_qoladi() {
        val d = UniversityNaming.display("Markaziy Osiyo atrof-muhit va iqlim o‘zgarishini o‘rganish universiteti (Green University)")
        assertEquals("Markaziy Osiyo atrof-muhit va iqlim o'zgarishini o'rganish universiteti", d.shortName)
    }

    @Test
    fun butun_nom_bosh_harflarda_bolsa_oqiladigan_holga_keladi() {
        val d = UniversityNaming.display("AXBOROT TEXNOLOGIYALARI VA MENEJMENT UNIVERSITETI")
        assertEquals("Axborot texnologiyalari va menejment universiteti", d.shortName)
        assertEquals("ATMU", d.abbr)
    }

    /** Qisqa so'z — qisqartma bo'lishi mumkin, kichik harfga o'tkazilmaydi. */
    @Test
    fun qisqartma_sozlar_bosh_harfda_qoladi() {
        assertEquals("TMC Institute", UniversityNaming.display("TMC INSTITUTE").shortName)
        assertEquals("Impuls BSR", UniversityNaming.display("IMPULS BSR").shortName)
    }

    @Test
    fun toza_nom_ozgarmaydi() {
        val d = UniversityNaming.display("Samarqand davlat universiteti")
        assertEquals("Samarqand davlat universiteti", d.shortName)
        assertNull(d.branch)
        assertNull(d.eponym)
    }

    // ---------------------------------------------------------------------------------
    // Qisqartma
    // ---------------------------------------------------------------------------------

    @Test
    fun shahar_prefiksi_ananaviy_shaklda() {
        assertEquals("SamDU", UniversityNaming.display("Samarqand davlat universiteti").abbr)
        assertEquals("BuxDU", UniversityNaming.display("Buxoro davlat universiteti").abbr)
        assertEquals("O'zMU", UniversityNaming.display("Mirzo Ulug'bek nomidagi O‘zbekiston Milliy universiteti").abbr)
        assertEquals("TDIU", UniversityNaming.display("Toshkent davlat iqtisodiyot universiteti").abbr)
    }

    /** Uzun nomda shahar prefiksi sig'maydi — oddiy bosh harflarga qaytiladi. */
    @Test
    fun uzun_nomda_bosh_harflar() {
        val d = UniversityNaming.display(
            "Samarqand davlat veterinariya meditsinasi, chorvachilik va biotexnologiyalar universiteti Nukus filiali",
        )
        assertEquals("SamDVU", d.abbr)
        assertEquals("Nukus filiali", d.branch)
        assertTrue(d.abbr.length <= 6)
    }

    @Test
    fun nom_ichidagi_tayyor_qisqartma_ishlatiladi() {
        assertEquals("MEI", UniversityNaming.display("MEI milliy tadqiqot universiteti Toshkent filiali").abbr)
        assertEquals("MISiS", UniversityNaming.display("Milliy texnologik tadqiqotlar universiteti MISiS").abbr)
        assertEquals("PDP", UniversityNaming.display("PDP University").abbr)
    }

    @Test
    fun brend_nom_ozi_korsatiladi() {
        assertEquals("Turon", UniversityNaming.display("Turon universiteti").abbr)
        assertEquals("Cyber", UniversityNaming.display("Cyber university").abbr)
        assertEquals("EMU", UniversityNaming.display("EMU-UNIVERSITY").abbr)
    }

    @Test
    fun chiziqchali_nomdan_har_bolak_bosh_harfi() {
        assertEquals("AXU", UniversityNaming.display("Al-Xorazmiy universiteti").abbr)
    }

    /** Tur so'zi boshda kelsa ham qisqartma nomdan chiqadi, «Univer» emas. */
    @Test
    fun tur_sozi_boshda_kelgan_nom() {
        assertEquals("EPU", UniversityNaming.display("University of economics and pedagogy").abbr)
    }

    @Test
    fun qisqartma_hech_qachon_bosh_emas() {
        listOf("", "   ", "?!", "Universitet").forEach {
            assertTrue(UniversityNaming.display(it).abbr.isNotBlank(), "bo'sh qisqartma: '$it'")
        }
    }

    // ---------------------------------------------------------------------------------
    // Manzil
    // ---------------------------------------------------------------------------------

    @Test
    fun manzildan_faqat_shahar_qoladi() {
        assertEquals(
            "Toshkent viloyati",
            UniversityNaming.shortCity("Toshkent viloyati, Toshkent tumani ,Kensoy MFY,Kensoy ko'chasi 15 - uy,"),
        )
        assertEquals(
            "Andijon shahri",
            UniversityNaming.shortCity("Q8FQ+7HP, 170119, O’zbekiston Respublikasi, Andijon shahar, Bobur shohko’chasi, 56-uy"),
        )
        assertEquals("Buxoro shahri", UniversityNaming.shortCity("Buxoro sh, Bogoudin"))
    }

    /** Shahar aniqlanmasa bo'sh qaytadi — «Dom» yoki telefon raqami ko'rsatilmaydi. */
    @Test
    fun tanib_bolmaydigan_manzil_bosh_qaytadi() {
        assertEquals("", UniversityNaming.shortCity("998712466348"))
        assertEquals("", UniversityNaming.shortCity(""))
    }

    @Test
    fun ingliz_yozuvidagi_shahar_ham_topiladi() {
        assertEquals("Tashkent", UniversityNaming.shortCity("Azodlik MFY, Fleishmakher street, 2 Angren Street, Tashkent"))
    }

    // ---------------------------------------------------------------------------------
    // Model
    // ---------------------------------------------------------------------------------

    @Test
    fun qidiruv_qisqartma_boyicha_ham_ishlaydi() {
        val uni = University(
            id = "emis-1",
            name = "Muhammad al-Xorazmiy nomidagi Toshkent axborot texnologiyalari universiteti",
            city = "Toshkent shahri, Amir Temur ko'chasi 108",
            accent = 0xFF6C47FF,
        )
        assertTrue(uni.matches("tatu"))
        assertTrue(uni.matches("axborot"))
        assertTrue(uni.matches("xorazmiy"))
        assertTrue(uni.matches("Toshkent"))
        assertTrue(uni.matches(""))
        assertTrue(!uni.matches("qwerty"))
        assertEquals("TATU", uni.monogram)
    }

    // ---------------------------------------------------------------------------------
    // Korpus — manbadagi ENG UZUN 36 ta nom
    // ---------------------------------------------------------------------------------

    /**
     * Bitta-bitta kutilgan natija yozib chiqish o'rniga **xossalar** tekshiriladi: qoida
     * o'zgarganda test har bir nomni emas, faqat haqiqiy buzilishni ushlaydi.
     */
    @Test
    fun eng_uzun_nomlar_qoidalarni_buzmaydi() {
        val forbidden = listOf("muassasasi", "filiali", "nomidagi", "shahridagi", "byudjeti", "budjeti")
        HARDEST.forEach { raw ->
            val d = UniversityNaming.display(raw)
            assertTrue(d.shortName.isNotBlank(), "bo'sh qisqa nom: $raw")
            assertTrue(d.shortName.length <= raw.length, "qisqa nom uzayib ketdi: $raw")
            forbidden.forEach { word ->
                assertTrue(!d.shortName.contains(word, ignoreCase = true), "'$word' qolib ketdi: ${d.shortName}")
            }
            assertTrue(d.abbr.length in 2..6, "qisqartma o'lchami ${d.abbr.length}: '${d.abbr}' ← $raw")
            assertTrue(!d.abbr.contains(' '), "qisqartmada bo'sh joy: '${d.abbr}'")
            // Har bir nomda muassasa turi bor — u qisqa nomda ham qolishi kerak, aks holda
            // «Moskva davlat» kabi tugallanmagan qator chiqadi.
            assertTrue(
                TYPE_TAILS.any { d.shortName.contains(it, ignoreCase = true) },
                "muassasa turi yo'qoldi: ${d.shortName}",
            )
        }
    }

    @Test
    fun ikkilamchi_qatorda_avval_filial_turadi() {
        val uni = University(
            id = "emis-2",
            name = "Samarqand davlat veterinariya meditsinasi universiteti Nukus filiali",
            city = "Qoraqalpog'iston Respublikasi, Nukus shahri, Ch.Abdirov ko'chasi 1 uy",
            accent = 0xFF6C47FF,
        )
        assertEquals("Nukus filiali · Nukus shahri", uni.display.subtitle)
    }
    private companion object {
        val TYPE_TAILS = listOf("universitet", "institut", "akademiya", "maktab", "markaz")

        /** prof-emis ro'yxatidagi eng uzun 36 ta nom (2026-08). */
        val HARDEST = listOf(
            "\"Rossiya Federatsiyasi Tashqi ishlar vazirligining Moskva davlat xalqaro munosabatlar instituti (universiteti)\" Federal davlat avtonom oliy ta’lim muassasasining Toshkent shahridagi filiali",
            "Toshkent shahridagi «S.A. Gerasimov nomidagi Butunrossiya davlat kinematografiya instituti» federal davlat budjeti oliy ta’lim muassasasi filiali",
            "Toshkent shahridagi “A.I.Gersen nomidagi Rossiya Davlat pedagogika universiteti” Federal davlat budjeti oliy ta’lim muassasasi filiali",
            "D.I.Mendeleev nomidagi Rossiya kimyo-texnologiya universiteti Federal davlat byudjeti oliy taʼlim muassasasining Toshkent filiali",
            "Federal davlat avtonom oliy taʼlim muassasasi Milliy texnologik tadqiqotlar universiteti MISiS ning Olmaliq shahridagi filiali",
            "MMFI milliy tadqiqot yadro universiteti federal davlat avtonom oliy taʼlim muassasasining Toshkent shahridagi filiali",
            "\"Toshkent irrigatsiya va qishloq xo‘jaligini mexanizatsiyalash muhandislari instituti\" Milliy tadqiqot universiteti",
            "“Sankt-Peterburg davlat universiteti” Federal davlat budjeti oliy ta’lim muassasasi Toshkent shahridagi filiali",
            "Samarqand davlat veterinariya meditsinasi, chorvachilik va biotexnologiyalar universiteti Toshkent filiali",
            "Samarqand davlat veterinariya meditsinasi, chorvachilik va biotexnologiyalar universiteti Nukus filiali",
            "Toshkent shahridagi Belarus-Oʼzbekiston qoʼshma tarmoqlararo amaliy texnik kvalifikatsiyalar instituti",
            "MEI milliy tadqiqot universiteti Federal davlat byudjeti oliy taʼlim muassasasining Toshkent filiali",
            "N.I.Pirogov nomidagi Rossiya milliy tadqiqot tibbiyot universitetining Toshkent shahridagi filiali",
            "Markaziy Osiyo atrof-muhit va iqlim o‘zgarishini o‘rganish universiteti (Green University)",
            "Toshkent shahridagi Vebster universitetining taʼlim dasturlarini amalga oshirish markazi",
            "Samarqand davlat veterinariya meditsinasi, chorvachilik va biotexnologiyalar universitet",
            "I.M.Gubkin nomidagi Rossiya davlat neft va gaz universiteti Toshkent shahridagi filiali",
            "Latviyaning Аxborot tizimlari menejmenti Oliy maktabining Fargʼona shahridagi filiali",
            "Toshkent shahridagi N.E. Bauman nomidagi Moskva davlat texnika universiteti filiali",
            "M. Аuezov nomidagi Janubiy Qozogʼiston universitetining Chirchiq shahridagi filiali",
            "Toshkent shahridagi Turkiyaning Iqtisodiyot va texnologiyalar universiteti filiali",
            "M.V.Lomonosov nomidagi Moskva davlat universitetining Toshkent shahridagi filiali",
            "Mirzo Ulug'bek nomidagi O‘zbekiston Milliy universitetining Jizzax filiali",
            "O‘zbekiston davlat jismoniy tarbiya va sport universiteti Nukus filiali",
            "Astraxan davlat texnika universitetining Toshkent viloyatidagi filiali",
            "G.V.Plexanov nomli Rossiya iqtisodiyot universiteti Toshkent filiali",
            "Toshkent Xalqaro moliyaviy boshqaruv va texnologiyalar universiteti",
            "“Collegium Humanum” Varshava menejment universiteti Andijon filiali",
            "O‘zbekiston jurnalistika va ommaviy kommunikatsiyalar universiteti",
            "Qoraqalpog'iston qishloq xo'jaligi va agrotexnologiyalar instituti",
            "Toshkent shahridagi Singapur Menejmentni rivojlantirish instituti",
            "O‘zbekiston davlat san’at va madaniyat instituti Nukus filiali",
            "Yunus Rajabiy nomidagi O‘zbek milliy musiqa sanʼati instituti",
            "Termiz davlat muhandislik va agrotexnologiyalar universiteti",
            "Oʼzbekiston Respublikasi Huquqni muhofaza qilish akademiyasi",
            "Oʻzbekiston davlat xoreografiya akademiyasi Urganch filiali",
        )
    }
}
