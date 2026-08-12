package dev.feature.ads.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.uikit.locale.rememberStrings

/**
 * Eski e'lon (`ad`) ekranining matnlari — bo'lim faqat eski qatorlarni tahrirlash uchun
 * qolgan, lekin u ham to'liq tarjima qilingan.
 */
data class AdsStrings(
    val postAd: String = "Post a listing",
    val whatKind: String = "What would you like to post?",
    val typeJob: String = "Job listing",
    val typeJobSubtitle: String = "Looking for staff or part-time help",
    val typeRental: String = "Rental / Housing",
    val typeRentalSubtitle: String = "Apartment, hostel, flatmate",
    val typeSale: String = "For sale",
    val typeSaleSubtitle: String = "Books, electronics, belongings",
    val typeService: String = "Service / Tutoring",
    val typeServiceSubtitle: String = "Teaching, design, translation",
    val typeOther: String = "Other listing",
    val typeOtherSubtitle: String = "Events, teams, lost & found",
    val addPhoto: String = "Add a photo",
    val fieldTitle: String = "Listing title",
    val fieldTitleHint: String = "SMM manager wanted",
    val fieldCategory: String = "Category",
    val fieldPrice: String = "Price / Salary",
    val fieldPriceHint: String = "3–5M",
    val fieldDescription: String = "Description",
    val fieldDescriptionHint: String = "More details...",
    val submit: String = "Publish listing",
)

private val AdsEn = AdsStrings()

private val AdsRu = AdsStrings(
    postAd = "Подать объявление",
    whatKind = "Какое объявление хотите разместить?",
    typeJob = "Вакансия",
    typeJobSubtitle = "Поиск сотрудника или part-time",
    typeRental = "Аренда / Жильё",
    typeRentalSubtitle = "Квартира, хостел, сосед",
    typeSale = "Продажа",
    typeSaleSubtitle = "Книги, техника, вещи",
    typeService = "Услуга / Репетитор",
    typeServiceSubtitle = "Преподавание, дизайн, перевод",
    typeOther = "Другое объявление",
    typeOtherSubtitle = "Мероприятие, команда, находки",
    addPhoto = "Добавить фото",
    fieldTitle = "Заголовок объявления",
    fieldTitleHint = "Нужен SMM-менеджер",
    fieldCategory = "Категория",
    fieldPrice = "Цена / Зарплата",
    fieldPriceHint = "3–5 млн",
    fieldDescription = "Описание",
    fieldDescriptionHint = "Подробная информация...",
    submit = "Разместить объявление",
)

private val AdsUz = AdsStrings(
    postAd = "Elon berish",
    whatKind = "Qanday e'lon joylamoqchisiz?",
    typeJob = "Ish e'loni",
    typeJobSubtitle = "Xodim yoki part-time izlash",
    typeRental = "Ijara / Turar joy",
    typeRentalSubtitle = "Kvartira, hostel, room-mate",
    typeSale = "Sotuv (bozor)",
    typeSaleSubtitle = "Kitob, texnika, buyum sotish",
    typeService = "Xizmat / Repetitor",
    typeServiceSubtitle = "Dars berish, dizayn, tarjima",
    typeOther = "Boshqa e'lon",
    typeOtherSubtitle = "Tadbir, jamoa, yo'qoldi-topildi",
    addPhoto = "Rasm qo'shish",
    fieldTitle = "E'lon sarlavhasi",
    fieldTitleHint = "SMM menejer kerak",
    fieldCategory = "Kategoriya",
    fieldPrice = "Narx / Maosh",
    fieldPriceHint = "3–5 mln",
    fieldDescription = "Tavsif",
    fieldDescriptionHint = "Batafsil ma'lumot...",
    submit = "E'lonni joylash",
)

@Composable
@ReadOnlyComposable
internal fun adsStrings(): AdsStrings = rememberStrings(AdsEn, AdsRu, AdsUz)
