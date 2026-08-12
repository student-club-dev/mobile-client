package dev.feature.home.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Bosh ekran va yon menyu matnlari. Sukut qiymatlar — inglizcha (ilovaning asosiy tili). */
data class HomeStrings(
    val greeting: String = "Hello 👋",
    val messages: String = "Messages",
    val notifications: String = "Notifications",
    val sideMenu: String = "Side menu",

    // Bo'limlar
    val myUniversityStudents: String = "At my university",
    val myUniversityStudentsSubtitle: String = "Students from your university",
    val allStudents: String = "All students",
    val allStudentsSubtitle: String = "Newest members first",
    val tasksTitle: String = "📚 Coursework help",
    val tasksSubtitle: String = "Essays, problems, handwriting and IT tasks",
    val rentalsTitle: String = "Apartments for rent",
    val rentalsSubtitle: String = "Homes looking for a flatmate",

    // Talaba kartasi
    val message: String = "Message",
    val connect: String = "Connect",
    val requestSent: String = "Sent",

    // Yorliqlar
    val men: String = "Men",
    val women: String = "Women",
    val foodSectionFallback: String = "Food",
    val clothingNamePrefix: String = "Cloth",

    // Yon menyu
    val navSections: String = "Sections",
    val navMyUniversity: String = "My university",
    val navListings: String = "Listings",
    val navOffers: String = "Offers",
    val navRentals: String = "Apartments for rent",
    val navContacts: String = "Contacts",
    val navConnections: String = "Connections",
    val navRequests: String = "Requests",
    val navStudentSearch: String = "Find students",
    val navAccount: String = "Account",
    val navMyListings: String = "My listings",
    val navSettings: String = "Settings",
)

private val HomeEn = HomeStrings()

private val HomeRu = HomeStrings(
    greeting = "Здравствуйте 👋",
    messages = "Сообщения",
    notifications = "Уведомления",
    sideMenu = "Боковое меню",

    myUniversityStudents = "В моём вузе",
    myUniversityStudentsSubtitle = "Студенты вашего университета",
    allStudents = "Все студенты",
    allStudentsSubtitle = "Сначала новые участники",
    tasksTitle = "📚 Помощь с учёбой",
    tasksSubtitle = "Рефераты, задачи, рукописные работы и IT-задания",
    rentalsTitle = "Квартиры в аренду",
    rentalsSubtitle = "Жильё в поиске соседа",

    message = "Написать",
    connect = "Добавить",
    requestSent = "Отправлено",

    men = "Мужчинам",
    women = "Женщинам",
    foodSectionFallback = "Питание",
    clothingNamePrefix = "Kiyim",

    navSections = "Разделы",
    navMyUniversity = "Мой университет",
    navListings = "Объявления",
    navOffers = "Предложения",
    navRentals = "Квартиры в аренду",
    navContacts = "Общение",
    navConnections = "Друзья",
    navRequests = "Заявки",
    navStudentSearch = "Поиск студентов",
    navAccount = "Аккаунт",
    navMyListings = "Мои объявления",
    navSettings = "Настройки",
)

private val HomeUz = HomeStrings(
    greeting = "Assalomu alaykum 👋",
    messages = "Xabarlar",
    notifications = "Bildirishnomalar",
    sideMenu = "Yon menyu",

    myUniversityStudents = "Universitetimda",
    myUniversityStudentsSubtitle = "Bir universitetda o'qiyotgan talabalar",
    allStudents = "Barcha talabalar",
    allStudentsSubtitle = "Yangi qo'shilganlar birinchi",
    tasksTitle = "📚 Fanlardan yordam",
    tasksSubtitle = "Referat, masala, qo'lyozma va IT ishlari",
    rentalsTitle = "Ijara kvartiralar",
    rentalsSubtitle = "Sherik izlayotgan uylar",

    message = "Xabar",
    connect = "Bog'lanish",
    requestSent = "Yuborildi",

    men = "Erkaklar",
    women = "Ayollar",
    foodSectionFallback = "Ovqatlanish",
    clothingNamePrefix = "Kiyim",

    navSections = "Bo'limlar",
    navMyUniversity = "Universitetim",
    navListings = "E'lonlar",
    navOffers = "Takliflar",
    navRentals = "Ijara kvartiralar",
    navContacts = "Aloqa",
    navConnections = "Do'stlar",
    navRequests = "So'rovlar",
    navStudentSearch = "Talaba qidirish",
    navAccount = "Hisob",
    navMyListings = "Mening e'lonlarim",
    navSettings = "Sozlamalar",
)

@Composable
@ReadOnlyComposable
internal fun homeStrings(): HomeStrings = rememberStrings(HomeEn, HomeRu, HomeUz)

/** ViewModel (Compose'dan tashqarida) uchun — bo'lim sarlavhalari shu yerda yig'iladi. */
internal fun homeStringsNow(): HomeStrings = AppLocale.pick(HomeEn, HomeRu, HomeUz)
