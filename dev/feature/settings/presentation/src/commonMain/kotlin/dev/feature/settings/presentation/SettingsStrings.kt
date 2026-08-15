package dev.feature.settings.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.uikit.locale.rememberStrings

/**
 * Sozlamalar ekranining matnlari.
 *
 * Sukut qiymatlar — INGLIZCHA. Ya'ni ingliz varianti alohida yozilmaydi (`SettingsEn` —
 * bo'sh konstruktor) va yangi satr qo'shilganda tarjimasi hali yo'q bo'lsa, ekranda
 * ingliz matni ko'rinadi (bo'sh joy emas). Butun loyihada shu qolip ishlatiladi.
 */
data class SettingsStrings(
    val back: String = "Back",
    val title: String = "Settings",
    val sectionAccount: String = "Account",
    val editProfile: String = "Edit profile",
    val sectionAppearance: String = "Appearance",
    val themeSystem: String = "System",
    val themeLight: String = "Light",
    val themeDark: String = "Dark",
    val sectionLanguage: String = "Language",
    val languageHint: String = "The whole app switches instantly — no restart needed.",
    val sectionListings: String = "Listings",
    val region: String = "Region",
    /** Viloyat filtri o'chirilgan holat — ro'yxatdagi qolgan qatorlar bilan bir tilda. */
    val allRegions: String = "All regions",
    val regionHint: String = "Discounts and listings are shown for this region.",
    val selectRegion: String = "Select a region",
    val sectionPrivacy: String = "Privacy",
    val lastSeenHint: String = "Who can see your \"last seen\" and \"online\" status. " +
        "If you pick \"Nobody\", you can still see other people's status.",
    val phoneVisibilityHint: String = "Who can see your phone number. Nobody by default: " +
        "an open number means strangers can call you.",
    val visibilityEveryone: String = "Everyone",
    val visibilityConnections: String = "Connections",
    val visibilityNobody: String = "Nobody",
    val blockedStudents: String = "Blocked students",
    val sectionNotifications: String = "Notifications",
    val pushNotifications: String = "Push notifications",
    val emailNotifications: String = "Email notifications",
    val sectionGeneral: String = "General",
    val about: String = "About the app",
    val version: String = "Version 1.0.0",
    val aboutBody: String = "Student Club — a super-app for students: discounts, jobs, " +
        "listings and messages.\nVersion 1.0.0",
    val logout: String = "Log out",
)

private val SettingsEn = SettingsStrings()

private val SettingsRu = SettingsStrings(
    back = "Назад",
    title = "Настройки",
    sectionAccount = "Аккаунт",
    editProfile = "Редактировать профиль",
    sectionAppearance = "Оформление",
    themeSystem = "Системная",
    themeLight = "Светлая",
    themeDark = "Тёмная",
    sectionLanguage = "Язык",
    languageHint = "Всё приложение переключается сразу — перезапуск не нужен.",
    sectionListings = "Объявления",
    region = "Регион",
    allRegions = "Все регионы",
    regionHint = "Скидки и объявления показываются по этому региону.",
    selectRegion = "Выберите регион",
    sectionPrivacy = "Приватность",
    lastSeenHint = "Кто видит ваш статус «был(а) в сети» и «онлайн». Если выбрать «Никто», " +
        "вы всё равно продолжите видеть статусы других.",
    phoneVisibilityHint = "Кто видит ваш номер телефона. По умолчанию — никто: при открытом " +
        "номере вам могут звонить незнакомые люди.",
    visibilityEveryone = "Все",
    visibilityConnections = "Друзья",
    visibilityNobody = "Никто",
    blockedStudents = "Заблокированные студенты",
    sectionNotifications = "Уведомления",
    pushNotifications = "Push-уведомления",
    emailNotifications = "Email-уведомления",
    sectionGeneral = "Общее",
    about = "О приложении",
    version = "Версия 1.0.0",
    aboutBody = "Student Club — супер-приложение для студентов: скидки, работа, " +
        "объявления и сообщения.\nВерсия 1.0.0",
    logout = "Выйти",
)

private val SettingsUz = SettingsStrings(
    back = "Orqaga",
    title = "Sozlamalar",
    sectionAccount = "Hisob",
    editProfile = "Profilni tahrirlash",
    sectionAppearance = "Ko'rinish",
    themeSystem = "Tizim",
    themeLight = "Yorug'",
    themeDark = "Tungi",
    sectionLanguage = "Til",
    languageHint = "Butun ilova darhol almashadi — qayta ishga tushirish shart emas.",
    sectionListings = "E'lonlar",
    region = "Viloyat",
    allRegions = "Barcha viloyatlar",
    regionHint = "Chegirma va e'lonlar shu viloyat bo'yicha ko'rsatiladi.",
    selectRegion = "Viloyatni tanlang",
    sectionPrivacy = "Maxfiylik",
    lastSeenHint = "\"Oxirgi ko'rilgan\" va \"onlayn\" holatini kim ko'rishi. \"Hech kim\" " +
        "tanlansa, siz ham boshqalarnikini ko'rishda davom etasiz.",
    phoneVisibilityHint = "Telefon raqamingizni kim ko'rishi. Sukut bo'yicha — hech kim: " +
        "raqam ochiq bo'lsa notanish odamlardan qo'ng'iroq kelishi mumkin.",
    visibilityEveryone = "Hamma",
    visibilityConnections = "Do'stlar",
    visibilityNobody = "Hech kim",
    blockedStudents = "Bloklangan talabalar",
    sectionNotifications = "Bildirishnomalar",
    pushNotifications = "Push bildirishnomalar",
    emailNotifications = "Email xabarnomalar",
    sectionGeneral = "Umumiy",
    about = "Ilova haqida",
    version = "Versiya 1.0.0",
    aboutBody = "Student Club — talabalar uchun super-app: chegirmalar, ishlar, " +
        "e'lonlar va xabarlar.\nVersiya 1.0.0",
    logout = "Chiqish",
)

@Composable
@ReadOnlyComposable
internal fun settingsStrings(): SettingsStrings = rememberStrings(SettingsEn, SettingsRu, SettingsUz)
