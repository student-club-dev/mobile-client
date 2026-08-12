package dev.core.uikit.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale

/**
 * Umumiy UI komponentlarining matnlari ("Yopish", "Bo'sh", "Bekor qilish"...) — ular
 * hamma ekranda takrorlanadi, shuning uchun bitta joyda turadi.
 *
 * Sukut qiymatlar inglizcha: tarjimasi yozilmagan satr ham hech bo'lmasa ingliz tilida
 * ko'rinadi (default til ham shu).
 */
data class UiKitStrings(
    val close: String = "Close",
    val cancel: String = "Cancel",
    val back: String = "Back",
    val retry: String = "Try again",
    val or: String = "or",
    val emptyTitle: String = "Nothing here yet",
    val notFoundTitle: String = "Nothing found",
    val cancelUpload: String = "Cancel upload",
    val viewOnMap: String = "View on map",
    val noListingsForFilters: String = "No listings match these filters",
    val splashTagline: String = "STUDENT COMMUNITY",
    val chooseFromGallery: String = "Choose from gallery",
    val cameraPermissionTitle: String = "Camera access needed",
    val cameraPermissionBody: String = "Turn on the camera to capture a story. " +
        "Even without permission you can still pick a photo or video from the gallery.",
    val grantPermission: String = "Allow",
    val cannotOpenSource: (String) -> String = { "Could not open the source: $it" },

    // --- Butun ilovada takrorlanadigan so'zlar ---
    /** Pul birligi qo'shimchasi — summadan KEYIN qo'yiladi ("150 000 UZS"). */
    val currency: String = "UZS",
    val negotiable: String = "Negotiable",
    val all: String = "All",
    val more: String = "More",
    val save: String = "Save",
    val delete: String = "Delete",
    val edit: String = "Edit",
    val send: String = "Send",
    val search: String = "Search",
    val loadFailed: String = "Couldn't load the list",
    val today: String = "Today",
    val tomorrow: String = "Tomorrow",
    val yesterday: String = "Yesterday",
    val inDays: (Int) -> String = { "in $it days" },
    val hoursAgo: (Int) -> String = { "$it h ago" },
    val daysAgo: (Int) -> String = { "$it d ago" },
    val justNow: String = "just now",
    val student: String = "Student",
    /** Oy nomlari — sanani "12 July" ko'rinishida yozish uchun (indeks 0 = yanvar). */
    val months: List<String> = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
)

private val UiKitEn = UiKitStrings()

private val UiKitRu = UiKitStrings(
    close = "Закрыть",
    cancel = "Отмена",
    back = "Назад",
    retry = "Повторить",
    or = "или",
    emptyTitle = "Пока пусто",
    notFoundTitle = "Ничего не найдено",
    cancelUpload = "Отменить отправку",
    viewOnMap = "Показать на карте",
    noListingsForFilters = "Нет объявлений по этим условиям",
    splashTagline = "СТУДЕНЧЕСКОЕ СООБЩЕСТВО",
    chooseFromGallery = "Выбрать из галереи",
    cameraPermissionTitle = "Нужен доступ к камере",
    cameraPermissionBody = "Включите камеру, чтобы снять историю. Даже без разрешения " +
        "можно выбрать фото или видео из галереи.",
    grantPermission = "Разрешить",
    cannotOpenSource = { "Не удалось открыть источник: $it" },

    currency = "сум",
    negotiable = "Договорная",
    all = "Все",
    more = "Ещё",
    save = "Сохранить",
    delete = "Удалить",
    edit = "Изменить",
    send = "Отправить",
    search = "Поиск",
    loadFailed = "Не удалось загрузить список",
    today = "Сегодня",
    tomorrow = "Завтра",
    yesterday = "Вчера",
    inDays = { "через $it дн." },
    hoursAgo = { "$it ч назад" },
    daysAgo = { "$it дн назад" },
    justNow = "только что",
    student = "Студент",
    months = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    ),
)

private val UiKitUz = UiKitStrings(
    close = "Yopish",
    cancel = "Bekor qilish",
    back = "Orqaga",
    retry = "Qayta urinish",
    or = "yoki",
    emptyTitle = "Hozircha bo'sh",
    notFoundTitle = "Hech narsa topilmadi",
    cancelUpload = "Yuborishni bekor qilish",
    viewOnMap = "Xaritada ko'rish",
    noListingsForFilters = "Bu shartlarga mos e'lon topilmadi",
    splashTagline = "TALABALAR HAMJAMIYATI",
    chooseFromGallery = "Galereyadan tanlash",
    cameraPermissionTitle = "Kameraga ruxsat kerak",
    cameraPermissionBody = "Hikoya olish uchun kamerani yoqing. Ruxsat bermasangiz ham " +
        "galereyadan rasm yoki video tanlashingiz mumkin.",
    grantPermission = "Ruxsat berish",
    cannotOpenSource = { "Manbani ochib bo'lmadi: $it" },

    currency = "so'm",
    negotiable = "Kelishilgan",
    all = "Barchasi",
    more = "Ko'proq",
    save = "Saqlash",
    delete = "O'chirish",
    edit = "Tahrirlash",
    send = "Yuborish",
    search = "Qidirish",
    loadFailed = "Ro'yxat yuklanmadi",
    today = "Bugun",
    tomorrow = "Ertaga",
    yesterday = "Kecha",
    inDays = { "$it kundan keyin" },
    hoursAgo = { "$it soat oldin" },
    daysAgo = { "$it kun oldin" },
    justNow = "hozirgina",
    student = "Talaba",
    months = listOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun",
        "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr",
    ),
)

/** Compose ichida — joriy tilning umumiy UI matnlari. */
@Composable
@ReadOnlyComposable
fun uiStrings(): UiKitStrings = rememberStrings(UiKitEn, UiKitRu, UiKitUz)

/** Compose'dan tashqarida (platforma kodi, callback) — o'sha to'plam. */
fun uiStringsNow(): UiKitStrings = AppLocale.pick(UiKitEn, UiKitRu, UiKitUz)
