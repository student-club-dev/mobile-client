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
import dev.core.uikit.locale.rememberStrings
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
        LegalDocument.TERMS -> rememberStrings(TermsEn, TermsRu, TermsUz)
        LegalDocument.PRIVACY -> rememberStrings(PrivacyEn, PrivacyRu, PrivacyUz)
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

private val TermsEn = LegalContent(
    title = "Terms of Use",
    updated = "Last updated: 5 August 2026",
    sections = listOf(
        LegalSection(
            "1. Who can use it",
            "StudentClub is an app for students. To open an account you need your real name " +
                "and a working phone number. One person uses one account.",
        ),
        LegalSection(
            "2. Your account",
            "Keeping your password private is up to you. Everything done through your account " +
                "is yours. If you notice your account has been stolen, change the password " +
                "immediately and get in touch with us.",
        ),
        LegalSection(
            "3. Listings and content",
            "You are responsible for the listings, stories and messages you post. False " +
                "information, other people's photos, abuse, fraud, and goods or services that " +
                "break the law are forbidden. We remove such content without warning.",
        ),
        LegalSection(
            "4. How to behave",
            "Treat other students with respect. Sending spam, spreading unsolicited ads and " +
                "scraping the app with automated tools are not allowed. Breaking the rules " +
                "gets an account suspended or blocked for good.",
        ),
        LegalSection(
            "5. Deals",
            "The app only connects students with each other. Agreements about rentals, jobs " +
                "and services are between the two sides — we are neither a party nor a " +
                "guarantor. Check the person and the terms before you send money.",
        ),
        LegalSection(
            "6. Changes to the service",
            "Sections of the app may be updated or changed. We announce important changes " +
                "inside the app in advance.",
        ),
        LegalSection(
            "7. Deleting your account",
            "You can delete your account at any time from Settings. After deletion your " +
                "profile, listings and stories are gone for good.",
        ),
    ),
)

private val TermsRu = LegalContent(
    title = "Условия использования",
    updated = "Последнее обновление: 5 августа 2026 года",
    sections = listOf(
        LegalSection(
            "1. Кто может пользоваться",
            "StudentClub — приложение для студентов. Для регистрации нужны настоящие имя и " +
                "фамилия и действующий номер телефона. Один человек — один аккаунт.",
        ),
        LegalSection(
            "2. Ваш аккаунт",
            "Хранить пароль в тайне — ваша обязанность. Все действия, совершённые через ваш " +
                "аккаунт, считаются вашими. Если заметили кражу аккаунта, сразу смените пароль " +
                "и свяжитесь с нами.",
        ),
        LegalSection(
            "3. Объявления и контент",
            "За размещённые объявления, истории и сообщения отвечаете вы. Ложная информация, " +
                "чужие фотографии, оскорбления, мошенничество, запрещённые законом товары и " +
                "услуги не допускаются. Такой контент мы удаляем без предупреждения.",
        ),
        LegalSection(
            "4. Правила общения",
            "Относитесь к другим студентам с уважением. Спам, несогласованная реклама и " +
                "автоматический сбор данных из приложения запрещены. За нарушение правил " +
                "аккаунт блокируется временно или навсегда.",
        ),
        LegalSection(
            "5. Сделки",
            "Приложение лишь связывает студентов друг с другом. Договорённости об аренде, " +
                "работе и услугах заключаются между сторонами — мы не являемся ни стороной, " +
                "ни поручителем. Проверяйте человека и условия до перевода денег.",
        ),
        LegalSection(
            "6. Изменения в сервисе",
            "Разделы приложения могут обновляться и меняться. О важных изменениях мы " +
                "сообщаем заранее внутри приложения.",
        ),
        LegalSection(
            "7. Удаление аккаунта",
            "Вы можете удалить аккаунт в любой момент через Настройки. После удаления профиль, " +
                "объявления и истории пропадают безвозвратно.",
        ),
    ),
)

private val TermsUz = LegalContent(
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

private val PrivacyEn = LegalContent(
    title = "Privacy Policy",
    updated = "Last updated: 5 August 2026",
    sections = listOf(
        LegalSection(
            "1. What we collect",
            "Your name, phone number, university and (optionally) university email, and your " +
                "profile photo. We also store the listings, stories and messages you create.",
        ),
        LegalSection(
            "2. Why we need it",
            "The data is used to identify your account, connect you with people from your " +
                "university, show listings and keep the app safe. It is never sold for advertising.",
        ),
        LegalSection(
            "3. Who sees it",
            "Your name, photo, university and listings are visible to other students. Your " +
                "phone number is shown only when you put it in a listing yourself. Messages " +
                "are visible only to the person you're talking to.",
        ),
        LegalSection(
            "4. Location and permissions",
            "Camera, microphone, gallery and location are requested only when you start that " +
                "action (sending a photo, a voice message, distance to a discount). If you " +
                "decline, the rest of the app keeps working.",
        ),
        LegalSection(
            "5. How long we keep it",
            "Stories disappear from the feed after 24 hours and stay in your archive; their " +
                "files are deleted after 365 days. The rest of your data is kept until you " +
                "delete your account.",
        ),
        LegalSection(
            "6. Security",
            "Data travels over an encrypted channel and passwords are stored in a form that " +
                "can't be reversed. Even so, no system is 100% secure — pick a strong password.",
        ),
        LegalSection(
            "7. Your rights",
            "You can edit your profile at any time, delete listings and stories, or close your " +
                "account entirely. Deleting the account also deletes your personal data.",
        ),
    ),
)

private val PrivacyRu = LegalContent(
    title = "Политика конфиденциальности",
    updated = "Последнее обновление: 5 августа 2026 года",
    sections = listOf(
        LegalSection(
            "1. Какие данные мы собираем",
            "Имя и фамилия, номер телефона, университет и (по желанию) университетская почта, " +
                "фото профиля. Также сохраняются размещённые вами объявления, истории и переписка.",
        ),
        LegalSection(
            "2. Зачем это нужно",
            "Данные используются, чтобы опознать ваш аккаунт, связать вас с одногруппниками, " +
                "показывать объявления и обеспечивать безопасность. Для рекламы они не продаются.",
        ),
        LegalSection(
            "3. Кто это видит",
            "Имя, фото, университет и ваши объявления видны другим студентам. Номер телефона " +
                "открывается только если вы сами указали его в объявлении. Переписка видна " +
                "только вашему собеседнику.",
        ),
        LegalSection(
            "4. Геолокация и разрешения",
            "Камера, микрофон, галерея и геолокация запрашиваются только когда вы сами " +
                "начинаете это действие (отправка фото, голосовое сообщение, расстояние до " +
                "скидки). Если отказать, остальная часть приложения продолжит работать.",
        ),
        LegalSection(
            "5. Сколько хранится",
            "Истории исчезают из ленты через 24 часа и остаются в вашем архиве; их файлы " +
                "удаляются через 365 дней. Остальные данные хранятся до удаления аккаунта.",
        ),
        LegalSection(
            "6. Безопасность",
            "Данные передаются по зашифрованному каналу, а пароли хранятся в необратимом виде. " +
                "И всё же ни одна система не защищена на 100% — выбирайте надёжный пароль.",
        ),
        LegalSection(
            "7. Ваши права",
            "Вы можете в любой момент изменить данные профиля, удалить объявления и истории, " +
                "полностью закрыть аккаунт. При удалении аккаунта личные данные тоже удаляются.",
        ),
    ),
)

private val PrivacyUz = LegalContent(
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
