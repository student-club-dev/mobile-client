package dev.feature.listings.presentation.form

import dev.feature.listings.domain.model.BusinessType
import dev.feature.listings.presentation.lt

/**
 * Bitta biznes turining ekrandagi **matnlari**.
 *
 * Nega alohida: kafe "taom", game club "sessiya", o'quv markaz "kurs" sotadi — bir xil
 * "Sarlavha / Narx / Tavsif" yozuvlari ularning hech biriga to'g'ri kelmaydi. Har turning
 * o'z ekrani bor ([TypeForms]), ekranlar esa umumiy bloklardan yig'iladi va shu yerdagi
 * matnlarni oladi. Shunday qilib takrorlanish ham yo'q, yozuvlar ham har xil.
 */
data class ListingFormCopy(
    val screenTitle: String,
    val screenSubtitle: String,

    val businessSection: String,
    val businessHint: String,
    val categoryHint: String,

    val imagesSection: String,
    val imagesHint: String,

    val aboutSection: String,
    val titleHint: String,
    val descriptionHint: String,

    val priceSection: String,
    val priceHint: String,

    val conditionsHint: String,

    val detailsSection: String,
    val detailsHint: String,

    /** Qo'shimchalar bo'limi ko'rsatiladimi (kafeda "Hajm", kiyimda "O'lcham"...). */
    val hasOptions: Boolean,
    val optionsSection: String,
    val optionsHint: String,
    val optionGroupHint: String,
    val optionItemHint: String,
) {
    companion object {
        fun of(type: BusinessType): ListingFormCopy = when (type) {

            BusinessType.CAFE_RESTAURANT -> ListingFormCopy(
                screenTitle = lt("Kafe chegirmasi"),
                screenSubtitle = lt("Taomga talaba chegirmasi"),
                businessSection = lt("Kafe va menyu bo'limi"),
                businessHint = lt("Kafe nomi: Chaykhana Navruz"),
                categoryHint = lt("Menyudagi bo'limni tanlang"),
                imagesSection = lt("Taom rasmlari"),
                imagesHint = lt("Birinchisi muqova bo'ladi"),
                aboutSection = lt("Taom haqida"),
                titleHint = lt("Taom nomi: Pepperoni pitsa"),
                descriptionHint = lt("Tarkibi, tayyorlash usuli..."),
                priceSection = lt("Menyudagi narx"),
                priceHint = "55000",
                conditionsHint = lt("Shart: talaba ID bilan, 12:00–17:00"),
                detailsSection = lt("Taom tafsilotlari"),
                detailsHint = lt("Talaba nima olishini bilishi uchun"),
                hasOptions = true,
                optionsSection = lt("Hajm va qo'shimchalar"),
                optionsHint = lt("Masalan: 30/35 sm, qo'shimcha pishloq"),
                optionGroupHint = lt("Guruh nomi: Hajmni tanlang"),
                optionItemHint = lt("Variant: 35 sm"),
            )

            BusinessType.GAME_CLUB -> ListingFormCopy(
                screenTitle = lt("Game Club chegirmasi"),
                screenSubtitle = lt("O'yin sessiyasiga talaba chegirmasi"),
                businessSection = lt("Klub va qurilma"),
                businessHint = lt("Klub nomi: CyberZone"),
                categoryHint = lt("Qaysi qurilma yoki o'yin turi"),
                imagesSection = lt("Zal rasmlari"),
                imagesHint = lt("Zal, qurilmalar, o'yin joyi"),
                aboutSection = lt("Sessiya haqida"),
                titleHint = lt("Masalan: PS5 — 1 soat o'yin"),
                descriptionHint = lt("Zal sharoiti, qulayliklar..."),
                priceSection = lt("Bir soatlik narx"),
                priceHint = "25000",
                conditionsHint = lt("Shart: talaba ID bilan, dush–juma 10:00–17:00"),
                detailsSection = lt("Zal va qurilma ma'lumotlari"),
                detailsHint = lt("Talaba nimaga o'tirishini bilsin"),
                hasOptions = true,
                optionsSection = lt("Zal turi va qo'shimchalar"),
                optionsHint = lt("Masalan: Standart / VIP, qo'shimcha joystik"),
                optionGroupHint = lt("Guruh nomi: Zal turini tanlang"),
                optionItemHint = lt("Variant: VIP zal"),
            )

            BusinessType.GROCERY -> ListingFormCopy(
                screenTitle = lt("Oziq-ovqat chegirmasi"),
                screenSubtitle = lt("Mahsulotga talaba chegirmasi"),
                businessSection = lt("Do'kon va bo'lim"),
                businessHint = lt("Do'kon nomi: Korzinka"),
                categoryHint = lt("Mahsulot bo'limini tanlang"),
                imagesSection = lt("Mahsulot rasmlari"),
                imagesHint = lt("Qadoq va mahsulotning o'zi"),
                aboutSection = lt("Mahsulot haqida"),
                titleHint = lt("Mahsulot nomi: Sut 2.5%, 1 l"),
                descriptionHint = lt("Tarkibi, saqlash sharti..."),
                priceSection = lt("Do'kondagi narx"),
                priceHint = "12000",
                conditionsHint = lt("Shart: talaba ID bilan, kuniga 2 tagacha"),
                detailsSection = lt("Mahsulot tafsilotlari"),
                detailsHint = lt("Og'irlik, muddat, brend"),
                hasOptions = false,
                optionsSection = "",
                optionsHint = "",
                optionGroupHint = "",
                optionItemHint = "",
            )

            BusinessType.CLOTHING -> ListingFormCopy(
                screenTitle = lt("Kiyim chegirmasi"),
                screenSubtitle = lt("Kiyimga talaba chegirmasi"),
                businessSection = lt("Do'kon va bo'lim"),
                businessHint = lt("Do'kon nomi: Zara"),
                categoryHint = lt("Kiyim bo'limini tanlang"),
                imagesSection = lt("Kiyim rasmlari"),
                imagesHint = lt("Old va orqa ko'rinishi"),
                aboutSection = lt("Kiyim haqida"),
                titleHint = lt("Mahsulot nomi: Oversize futbolka"),
                descriptionHint = lt("Material, o'lcham jadvali..."),
                priceSection = lt("Do'kondagi narx"),
                priceHint = "199000",
                conditionsHint = lt("Shart: talaba ID bilan, chegirma boshqa aksiyalar bilan qo'shilmaydi"),
                detailsSection = lt("Kiyim tafsilotlari"),
                detailsHint = lt("Brend, material, mavsum"),
                hasOptions = true,
                optionsSection = lt("O'lcham va rang"),
                optionsHint = lt("Talaba tanlaydigan variantlar"),
                optionGroupHint = lt("Guruh nomi: O'lchamni tanlang"),
                optionItemHint = lt("Variant: M"),
            )

            BusinessType.EDUCATION_CENTER -> ListingFormCopy(
                screenTitle = lt("Kurs chegirmasi"),
                screenSubtitle = lt("O'quv kursiga talaba chegirmasi"),
                businessSection = lt("Markaz va yo'nalish"),
                businessHint = lt("Markaz nomi: PDP Academy"),
                categoryHint = lt("Kurs yo'nalishini tanlang"),
                imagesSection = lt("Markaz rasmlari"),
                imagesHint = lt("Sinf xonasi, o'quvchilar"),
                aboutSection = lt("Kurs haqida"),
                titleHint = lt("Kurs nomi: IELTS 6.5+ intensiv"),
                descriptionHint = lt("Dastur, natija, o'qituvchi haqida..."),
                priceSection = lt("Oylik to'lov"),
                priceHint = "500000",
                conditionsHint = lt("Shart: faqat yangi o'quvchilar uchun"),
                detailsSection = lt("Kurs tafsilotlari"),
                detailsHint = lt("Davomiylik, format, daraja"),
                hasOptions = false,
                optionsSection = "",
                optionsHint = "",
                optionGroupHint = "",
                optionItemHint = "",
            )

            BusinessType.ENTERTAINMENT -> ListingFormCopy(
                screenTitle = lt("Ko'ngilochar chegirma"),
                screenSubtitle = lt("Chiptaga talaba chegirmasi"),
                businessSection = lt("Muassasa va turi"),
                businessHint = lt("Nomi: Cinema Park"),
                categoryHint = lt("Tadbir turini tanlang"),
                imagesSection = lt("Tadbir rasmlari"),
                imagesHint = lt("Afisha yoki zal rasmi"),
                aboutSection = lt("Tadbir haqida"),
                titleHint = lt("Nomi: Dune — IMAX seansi"),
                descriptionHint = lt("Seans, zal, qo'shimcha ma'lumot..."),
                priceSection = lt("Chipta narxi"),
                priceHint = "60000",
                conditionsHint = lt("Shart: dush–payshanba seanslarida"),
                detailsSection = lt("Seans tafsilotlari"),
                detailsHint = lt("Format, til, yosh chegarasi"),
                hasOptions = false,
                optionsSection = "",
                optionsHint = "",
                optionGroupHint = "",
                optionItemHint = "",
            )

            BusinessType.ELECTRONICS -> ListingFormCopy(
                screenTitle = lt("Texnika chegirmasi"),
                screenSubtitle = lt("Qurilmaga talaba chegirmasi"),
                businessSection = lt("Do'kon va bo'lim"),
                businessHint = lt("Do'kon nomi: Texnomart"),
                categoryHint = lt("Qurilma bo'limini tanlang"),
                imagesSection = lt("Qurilma rasmlari"),
                imagesHint = lt("Qurilma va qutisi"),
                aboutSection = lt("Qurilma haqida"),
                titleHint = "Model: MacBook Air M3 13\"",
                descriptionHint = lt("Xotira, protsessor, komplekt..."),
                priceSection = lt("Do'kondagi narx"),
                priceHint = "14500000",
                conditionsHint = lt("Shart: talaba ID bilan, kafolat saqlanadi"),
                detailsSection = lt("Qurilma tafsilotlari"),
                detailsHint = lt("Brend, holati, kafolat"),
                hasOptions = true,
                optionsSection = lt("Xotira va rang"),
                optionsHint = lt("Talaba tanlaydigan variantlar"),
                optionGroupHint = lt("Guruh nomi: Xotira hajmi"),
                optionItemHint = lt("Variant: 512 GB"),
            )
        }
    }
}
