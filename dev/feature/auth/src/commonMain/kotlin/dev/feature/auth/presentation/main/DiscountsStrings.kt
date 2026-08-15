package dev.feature.auth.presentation.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** "Takliflar" (chegirmalar) ekrani va e'lon varag'ining matnlari. Sukut — inglizcha. */
data class DiscountsStrings(
    val searchHint: String = "Search shops or listings",
    val title: String = "Offers",
    val pickDirection: String = "Pick a direction",
    val search: String = "Search",
    /** ⚠️ Bittasi uchun birlik shakl: "1 listings" grammatik xato edi (#39). */
    val offersCount: (Int) -> String = { if (it == 1) "1 listing" else "$it listings" },
    /** Bo'limda e'lon yo'q — katakda qizil rangda chiziladi. */
    val noListings: String = "No listings",
    val allOffers: String = "All offers",
    val allDirectionsCount: (Int) -> String = { "$it listings — all directions" },
    val allDirections: String = "By all directions",
    val offersWithDiscounts: (Int) -> String = { "$it listings — discounts and offers" },
    val noOffersForFilter: String = "No listings match this filter. Loosen the conditions.",
    val all: String = "All",
    val filter: String = "Filter",
    val clear: String = "Clear",
    val discountState: String = "Discount state",
    val discounted: String = "Discounted",
    val notDiscounted: String = "No discount",
    val catalogSection: String = "Catalog section",
    val businessType: String = "Business type",
    val gender: String = "Gender",
    val male: String = "Male",
    val female: String = "Female",
    /**
     * ⚠️ Hozircha ISHLATILMAYDI — «Bo'lim» filtri ekrandan olib tashlangan (bug hisoboti
     * #31). Matn qoldirilgan: filtr qaytarilsa tarjimalar bilan birga tayyor turadi.
     */
    val section: String = "Section",
    val sort: String = "Sort",
    val sortRelevant: String = "Best match",
    val sortDiscount: String = "Discount %",
    val sortCheap: String = "Cheapest",
    val sortExpensive: String = "Priciest",
    val serverCount: (Int) -> String = { "$it listings on the server" },
    val apply: (Int) -> String = { "Apply · $it listings" },
    val location: String = "Location",
    /**
     * Viloyat filtri o'chirilgan holat.
     *
     * "All of Uzbekistan" EMAS: ro'yxatdagi qolgan qatorlar viloyatlar, ya'ni bu qator ham
     * ular bilan bir qatorda o'qilishi kerak — "barcha viloyatlar". Eski matn esa
     * mamlakatni viloyat bilan bir ro'yxatga qo'yib, tanlovni chalkashtirardi (#26).
     */
    val allRegions: String = "All regions",
    val save: String = "Save",
    val shopsCount: (Int) -> String = { "$it shops" },
    val studentId: String = "Student ID",
    val promoCode: String = "Promo code",
    val copied: String = "Copied ✓",

    // E'lon varag'i
    val listing: String = "Listing",
    val loadFailed: String = "Couldn't load the listing.",
    val offlineCache: String = "No connection — showing cached data.",
    val youSave: (String) -> String = { "You save $it" },
    val howToGet: String = "How to get it",
    val withPromoCode: String = "With a promo code",
    val withStudentId: String = "With a Student ID",
    val copy: String = "Copy",
    val link: (String) -> String = { "Link: $it" },
    val perStudent: (String) -> String = { "$it times per student" },
    val youHaveLeft: (String) -> String = { "$it left for you" },
    val description: String = "Description",
    val attributes: String = "Attributes",
    val branches: (Int) -> String = { "Branches ($it)" },
    /**
     * Filiallar sarlavhasi — bitta filial uchun BIRLIK shakl.
     *
     * "Branches (1)" mantiqsiz o'qilardi (bug hisoboti #35): bitta narsa ko'plikda
     * atalgan va yonida yana uning soni turgan.
     */
    val branchesLabel: (Int) -> String = { if (it == 1) "Branch" else "Branches · $it" },
    /** Qolgan filiallarni ochish. */
    val showMore: (Int) -> String = { "Show $it more" },
    val onMap: String = "On the map",
    val validUntil: String = "Valid until",
    val notSpecified: String = "Not specified",
    val telegram: (String) -> String = { "Telegram: $it" },
    val instagram: (String) -> String = { "Instagram: $it" },
    val viewsCount: (Int) -> String = { "Viewed $it times" },
    val postListing: String = "Post a listing",
)

private val DiscountsEn = DiscountsStrings()

private val DiscountsRu = DiscountsStrings(
    searchHint = "Поиск магазина или объявления",
    title = "Предложения",
    pickDirection = "Выберите направление",
    search = "Поиск",
    offersCount = {
        val mod10 = it % 10
        val mod100 = it % 100
        when {
            mod10 == 1 && mod100 != 11 -> "$it объявление"
            mod10 in 2..4 && mod100 !in 12..14 -> "$it объявления"
            else -> "$it объявлений"
        }
    },
    noListings = "Нет объявлений",
    allOffers = "Все предложения",
    allDirectionsCount = { "$it объявлений — все направления" },
    allDirections = "По всем направлениям",
    offersWithDiscounts = { "$it объявлений — скидки и предложения" },
    noOffersForFilter = "По этому фильтру ничего не найдено. Смягчите условия.",
    all = "Все",
    filter = "Фильтр",
    clear = "Сбросить",
    discountState = "Скидка",
    discounted = "Со скидкой",
    notDiscounted = "Без скидки",
    catalogSection = "Раздел каталога",
    businessType = "Тип бизнеса",
    gender = "Пол",
    male = "Мужской",
    female = "Женский",
    section = "Раздел",
    sort = "Сортировка",
    sortRelevant = "По релевантности",
    sortDiscount = "Скидка %",
    sortCheap = "Дешевле",
    sortExpensive = "Дороже",
    serverCount = { "$it объявлений на сервере" },
    apply = { "Применить · $it объявл." },
    location = "Расположение",
    allRegions = "Все регионы",
    save = "Сохранить",
    shopsCount = { "$it магазинов" },
    studentId = "Student ID",
    promoCode = "Промокод",
    copied = "Скопировано ✓",

    listing = "Объявление",
    loadFailed = "Не удалось загрузить объявление.",
    offlineCache = "Нет сети — показаны данные из кэша.",
    youSave = { "Вы экономите $it" },
    howToGet = "Как получить",
    withPromoCode = "По промокоду",
    withStudentId = "По Student ID",
    copy = "Копировать",
    link = { "Ссылка: $it" },
    perStudent = { "$it раз на студента" },
    youHaveLeft = { "У вас осталось $it" },
    description = "Описание",
    attributes = "Характеристики",
    branches = { "Филиалы ($it)" },
    branchesLabel = { if (it == 1) "Филиал" else "Филиалы · $it" },
    showMore = { "Показать ещё $it" },
    onMap = "На карте",
    validUntil = "Срок действия",
    notSpecified = "Не указан",
    telegram = { "Telegram: $it" },
    instagram = { "Instagram: $it" },
    viewsCount = { "Просмотров: $it" },
    postListing = "Подать объявление",
)

private val DiscountsUz = DiscountsStrings(
    searchHint = "Do'kon yoki e'lon qidiring",
    title = "Takliflar",
    pickDirection = "Yo'nalishni tanlang",
    search = "Qidiruv",
    offersCount = { "$it ta e'lon" },
    noListings = "E'lon yo'q",
    allOffers = "Barcha takliflar",
    allDirectionsCount = { "$it ta e'lon — barcha yo'nalishlar" },
    allDirections = "Barcha yo'nalishlar bo'yicha",
    offersWithDiscounts = { "$it ta e'lon — chegirma va takliflar" },
    noOffersForFilter = "Bu filtr bo'yicha e'lon topilmadi. Shartlarni yumshating.",
    all = "Hammasi",
    filter = "Filter",
    clear = "Tozalash",
    discountState = "Chegirma holati",
    discounted = "Chegirmali",
    notDiscounted = "Chegirmasiz",
    catalogSection = "Katalog bo'limi",
    businessType = "Biznes turi",
    gender = "Jins",
    male = "Erkak",
    female = "Ayol",
    section = "Bo'lim",
    sort = "Saralash",
    sortRelevant = "Mos",
    sortDiscount = "Chegirma %",
    sortCheap = "Arzon",
    sortExpensive = "Qimmat",
    serverCount = { "Serverda $it ta e'lon" },
    apply = { "Qo'llash · $it ta e'lon" },
    location = "Joylashuv",
    allRegions = "Barcha viloyatlar",
    save = "Saqlash",
    shopsCount = { "$it ta do'kon" },
    studentId = "Talaba ID",
    promoCode = "Promokod",
    copied = "Nusxalandi ✓",

    listing = "E'lon",
    loadFailed = "E'lonni yuklab bo'lmadi.",
    offlineCache = "Tarmoq yo'q — keshdagi ma'lumot ko'rsatilmoqda.",
    youSave = { "$it tejaysiz" },
    howToGet = "Qanday olinadi",
    withPromoCode = "Promokod bilan",
    withStudentId = "Talaba ID bilan",
    copy = "Nusxalash",
    link = { "Havola: $it" },
    perStudent = { "Har talabaga $it marta" },
    youHaveLeft = { "Sizda $it marta qoldi" },
    description = "Tavsif",
    attributes = "Xususiyatlar",
    branches = { "Filiallar ($it)" },
    branchesLabel = { if (it == 1) "Filial" else "Filiallar · $it" },
    showMore = { "Yana $it ta ko'rsatish" },
    onMap = "Xaritada",
    validUntil = "Amal qilish muddati",
    notSpecified = "Ko'rsatilmagan",
    telegram = { "Telegram: $it" },
    instagram = { "Instagram: $it" },
    viewsCount = { "$it marta ko'rilgan" },
    postListing = "Elon berish",
)

@Composable
@ReadOnlyComposable
internal fun discountsStrings(): DiscountsStrings =
    rememberStrings(DiscountsEn, DiscountsRu, DiscountsUz)

/** ViewModel/sof funksiyalar uchun. */
internal fun discountsStringsNow(): DiscountsStrings =
    AppLocale.pick(DiscountsEn, DiscountsRu, DiscountsUz)
