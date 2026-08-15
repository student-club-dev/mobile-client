package dev.feature.university.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** "Universitetim" ekrani matnlari. Sukut qiymatlar — inglizcha. */
data class UniversityStrings(
    val title: String = "My university",
    val universities: String = "Universities",
    val matesSectionTitle: String = "Students to connect with",
    /**
     * Talabalar soni.
     *
     * ⚠️ Bittasi uchun BIRLIK shakl: "1 students" grammatik xato edi (bug hisoboti #34).
     * Ingliz va rus tillarida shakl sonning o'ziga bog'liq, o'zbekchada esa ot soni bilan
     * ko'plikda kelmaydi — shuning uchun har til o'z qoidasini o'zi yozadi.
     */
    val studentsCount: (Int) -> String = { if (it == 1) "1 student" else "$it students" },
    val notSelectedTitle: String = "No university selected",
    val notSelectedBody: String = "Pick your university from the \"Universities\" button above.",

    // Bog'lanish tugmasi
    val connect: String = "+ Connect",
    val requestSent: String = "Sent",
    val requestIncoming: String = "Wants to connect",
    val connected: String = "Connected",

    // Bo'limlar
    val tasksTitle: String = "📚 Coursework help",
    val tasksSubtitle: String = "Task listings from my university",
    val food: String = "Food",
    val printShops: String = "Print shops",
    val foodOnMap: String = "Food on the map",
    val printShopsOnMap: String = "Print shops on the map",
    val map: String = "Map",
    val promoCode: (String) -> String = { "Promo code: $it" },
    val defaultLocation: String = "Yunusobod",
    val defaultLocationFull: String = "Yunusobod, Tashkent",

    // Universitet tanlash varag'i
    val pickTitle: String = "Select a university",
    val pickListFailed: String = "Couldn't load the list.\nCheck your connection.",
    val pickNotFound: String = "No such university in the list. Try spelling the name differently.",
    val searchUniversity: String = "Search universities",
    val searchStudents: String = "Search students",

    // Talabalar ro'yxati
    val studentsTitle: String = "Students",
    val courseAll: String = "All",
    val noStudentsMatch: String = "No students match these filters. Change the search or filters.",
    val online: String = "🟢 Online",

    // Kurs yorliqlari
    val year1: String = "Year 1",
    val year2: String = "Year 2",
    val year3: String = "Year 3",
    val year4: String = "Year 4",
    val master: String = "Master's",
)

private val UniversityEn = UniversityStrings()

private val UniversityRu = UniversityStrings(
    title = "Мой университет",
    universities = "Университеты",
    matesSectionTitle = "Студенты для знакомства",
    studentsCount = {
        val mod10 = it % 10
        val mod100 = it % 100
        when {
            mod10 == 1 && mod100 != 11 -> "$it студент"
            mod10 in 2..4 && mod100 !in 12..14 -> "$it студента"
            else -> "$it студентов"
        }
    },
    notSelectedTitle = "Университет не выбран",
    notSelectedBody = "Выберите свой университет через кнопку «Университеты» выше.",

    connect = "+ Добавить",
    requestSent = "Отправлено",
    requestIncoming = "Есть заявка",
    connected = "В друзьях",

    tasksTitle = "📚 Помощь с учёбой",
    tasksSubtitle = "Задания из моего университета",
    food = "Еда",
    printShops = "Копицентры",
    foodOnMap = "Еда на карте",
    printShopsOnMap = "Копицентры на карте",
    map = "Карта",
    promoCode = { "Промокод: $it" },
    defaultLocation = "Юнусабад",
    defaultLocationFull = "Юнусабад, Ташкент",

    pickTitle = "Выберите университет",
    pickListFailed = "Не удалось загрузить список.\nПроверьте интернет.",
    pickNotFound = "Такого университета нет в списке. Попробуйте написать название иначе.",
    searchUniversity = "Поиск университета",
    searchStudents = "Поиск студентов",

    studentsTitle = "Студенты",
    courseAll = "Все",
    noStudentsMatch = "Нет студентов по этим условиям. Измените поиск или фильтр.",
    online = "🟢 В сети",

    year1 = "1 курс",
    year2 = "2 курс",
    year3 = "3 курс",
    year4 = "4 курс",
    master = "Магистр",
)

private val UniversityUz = UniversityStrings(
    title = "Mening universitetim",
    universities = "Universitetlar",
    matesSectionTitle = "Do'stlashish uchun talabalar",
    studentsCount = { "$it talaba" },
    notSelectedTitle = "Universitet tanlanmagan",
    notSelectedBody = "Yuqoridagi \"Universitetlar\" tugmasidan universitetingizni tanlang.",

    connect = "+ Bog'lanish",
    requestSent = "Yuborildi",
    requestIncoming = "So'rov bor",
    connected = "Bog'langan",

    tasksTitle = "📚 Fanlardan yordam",
    tasksSubtitle = "Universitetimdagi topshiriq e'lonlari",
    food = "Ovqatlar",
    printShops = "Printerxonalar",
    foodOnMap = "Ovqatlar xaritada",
    printShopsOnMap = "Printerxonalar xaritada",
    map = "Xarita",
    promoCode = { "Promokod: $it" },
    defaultLocation = "Yunusobod",
    defaultLocationFull = "Yunusobod, Toshkent",

    pickTitle = "Universitetni tanlang",
    pickListFailed = "Ro'yxatni yuklab bo'lmadi.\nInternetni tekshiring.",
    pickNotFound = "Bunday universitet ro'yxatda yo'q. Nomini boshqacha yozib ko'ring.",
    searchUniversity = "Universitet qidiring",
    searchStudents = "Talaba qidiring",

    studentsTitle = "Talabalar",
    courseAll = "Hammasi",
    noStudentsMatch = "Bu shartlarga mos talaba yo'q. Qidiruvni yoki filtrni o'zgartiring.",
    online = "🟢 Onlayn",

    year1 = "1-kurs",
    year2 = "2-kurs",
    year3 = "3-kurs",
    year4 = "4-kurs",
    master = "Magistr",
)

@Composable
@ReadOnlyComposable
internal fun universityStrings(): UniversityStrings =
    rememberStrings(UniversityEn, UniversityRu, UniversityUz)

/** `LazyListScope` ichida va sof funksiyalarda — Compose'dan tashqarida. */
internal fun universityStringsNow(): UniversityStrings =
    AppLocale.pick(UniversityEn, UniversityRu, UniversityUz)
