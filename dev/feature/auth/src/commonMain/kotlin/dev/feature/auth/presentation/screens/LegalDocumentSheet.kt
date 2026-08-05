package dev.feature.auth.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc

/** Ro'yxatdan o'tishda roziligi so'raladigan ikki hujjat. */
enum class LegalDocument { TERMS, PRIVACY }

/**
 * Hujjat varag'i — "Foydalanish shartlari" / "Maxfiylik siyosati" bosilganda ochiladi.
 *
 * `skipPartiallyExpanded` — varaq darrov **to'liq** ochiladi: hujjat yarim varaqda
 * bir-ikki qator bo'lib ko'rinsa uni o'qib bo'lmaydi.
 *
 * Matn ataylab qisqa: har bo'lim bitta-ikkita jumla. Uzun yuridik matnni hech kim
 * o'qimaydi, foydalanuvchiga esa aynan nima yig'ilishi va nima qilinishi kerakligi
 * ayon bo'lishi kerak.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegalDocumentSheet(document: LegalDocument, onClose: () -> Unit) {
    val content = when (document) {
        LegalDocument.TERMS -> TermsContent
        LegalDocument.PRIVACY -> PrivacyContent
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Sc.Bg,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(horizontal = Sc.ScreenPadding)
                .padding(bottom = 28.dp)
                // Varaq to'liq ekranli — ichidagi ro'yxat o'zi suriladi.
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ScText(content.title, 20f, FontWeight.ExtraBold, Sc.Ink, maxLines = 2)
            Spacer(Modifier.height(2.dp))
            ScText(content.updated, 11.5f, FontWeight.SemiBold, Sc.Muted, maxLines = 1)
            Spacer(Modifier.height(10.dp))
            content.sections.forEach { section ->
                ScText(section.heading, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                ScText(
                    section.body,
                    13.5f,
                    FontWeight.Medium,
                    Sc.InkSoft,
                    lineHeight = 20f,
                    maxLines = Int.MAX_VALUE,
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Immutable
private data class LegalSection(val heading: String, val body: String)

@Immutable
private data class LegalContent(
    val title: String,
    val updated: String,
    val sections: List<LegalSection>,
)

private val TermsContent = LegalContent(
    title = "Foydalanish shartlari",
    updated = "Oxirgi yangilanish: 2026-yil 5-avgust",
    sections = listOf(
        LegalSection(
            "1. Kim foydalana oladi",
            "StudentClub — talabalar uchun ilova. Hisob ochish uchun haqiqiy ism-familya va " +
                "amaldagi telefon raqami kerak. Bitta odam bitta hisobdan foydalanadi.",
        ),
        LegalSection(
            "2. Sizning hisobingiz",
            "Parolni maxfiy saqlash sizning zimmangizda. Hisobingiz orqali qilingan barcha " +
                "amallar siznikidir. Hisob o'g'irlanganini sezsangiz darhol parolni " +
                "almashtiring va biz bilan bog'laning.",
        ),
        LegalSection(
            "3. E'lonlar va kontent",
            "Qo'ygan e'lonlaringiz, hikoyalaringiz va xabarlaringiz uchun javobgarlik sizda. " +
                "Yolg'on ma'lumot, boshqaning rasmi, haqorat, firibgarlik, qonunga zid tovar " +
                "va xizmatlar taqiqlanadi. Biz bunday kontentni ogohlantirishsiz o'chiramiz.",
        ),
        LegalSection(
            "4. Muomala qoidalari",
            "Boshqa talabalarga hurmat bilan munosabatda bo'ling. Spam yuborish, ruxsatsiz " +
                "reklama tarqatish va ilovani avtomatik vositalar bilan yuklash mumkin emas. " +
                "Qoidalar buzilsa hisob vaqtincha yoki butunlay bloklanadi.",
        ),
        LegalSection(
            "5. Bitimlar",
            "Ilova talabalarni bir-biri bilan bog'laydi, xolos. Ijara, ish va xizmat " +
                "bo'yicha kelishuvlar tomonlar o'rtasida bo'ladi — biz tomon ham, kafil " +
                "ham emasmiz. Pul o'tkazishdan oldin odamni va shartlarni tekshiring.",
        ),
        LegalSection(
            "6. Xizmatdagi o'zgarishlar",
            "Ilova bo'limlari yangilanib, o'zgarib turishi mumkin. Muhim o'zgarishlarni " +
                "ilova ichida oldindan bildiramiz.",
        ),
        LegalSection(
            "7. Hisobni o'chirish",
            "Hisobingizni istagan vaqtda Sozlamalar orqali o'chira olasiz. O'chirilgandan " +
                "keyin profil, e'lonlar va hikoyalar qaytarib bo'lmaydigan tarzda yo'qoladi.",
        ),
    ),
)

private val PrivacyContent = LegalContent(
    title = "Maxfiylik siyosati",
    updated = "Oxirgi yangilanish: 2026-yil 5-avgust",
    sections = listOf(
        LegalSection(
            "1. Qanday ma'lumot yig'amiz",
            "Ism-familya, telefon raqam, universitet va (ixtiyoriy) universitet emaili, " +
                "profil rasmi. Bundan tashqari siz qo'ygan e'lonlar, hikoyalar va " +
                "yozishmalaringiz saqlanadi.",
        ),
        LegalSection(
            "2. Nima uchun kerak",
            "Ma'lumotlar hisobingizni tanish, sizni universitetdoshlaringiz bilan " +
                "bog'lash, e'lonlarni ko'rsatish va xavfsizlikni ta'minlash uchun " +
                "ishlatiladi. Reklama uchun sotilmaydi.",
        ),
        LegalSection(
            "3. Kim ko'radi",
            "Ism, rasm, universitet va e'lonlaringiz boshqa talabalarga ko'rinadi. " +
                "Telefon raqamingiz faqat siz e'londa ko'rsatgan holda ochiladi. " +
                "Yozishmalar faqat suhbatdoshingizga ko'rinadi.",
        ),
        LegalSection(
            "4. Joylashuv va ruxsatlar",
            "Kamera, mikrofon, galereya va joylashuv faqat siz o'sha amalni boshlaganda " +
                "so'raladi (rasm yuborish, ovozli xabar, chegirmagacha masofa). Ruxsat " +
                "bermasangiz ilovaning qolgan qismi ishlashda davom etadi.",
        ),
        LegalSection(
            "5. Qancha saqlanadi",
            "Hikoyalar 24 soatdan keyin lentadan yo'qoladi va arxivingizda qoladi; " +
                "ularning fayllari 365 kundan keyin o'chiriladi. Qolgan ma'lumotlar hisob " +
                "o'chirilgunicha saqlanadi.",
        ),
        LegalSection(
            "6. Xavfsizlik",
            "Ma'lumotlar shifrlangan kanal orqali uzatiladi, parollar esa qaytarib " +
                "bo'lmaydigan ko'rinishda saqlanadi. Shunga qaramay hech bir tizim " +
                "100% xavfsiz emas — kuchli parol tanlang.",
        ),
        LegalSection(
            "7. Sizning huquqingiz",
            "Profil ma'lumotlarini istagan vaqtda tahrirlashingiz, e'lon va hikoyalarni " +
                "o'chirishingiz, hisobni butunlay yopishingiz mumkin. Hisob o'chirilganda " +
                "shaxsiy ma'lumotlar ham o'chiriladi.",
        ),
    ),
)
