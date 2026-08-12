package dev.core.domain.model

import dev.core.common.locale.AppLanguage
import dev.core.common.locale.AppLocale

/**
 * Server katalogi nomlarining tarjimasi — "Takliflar" bo'limidagi guruh, biznes turi va
 * bo'lim chiplari uchun.
 *
 * Nega kerak: bu nomlar ilovada yozilmagan, ular `POST /v1/catalog/groups`,
 * `/catalog/types` va `/catalog/filter-schema` dan keladi. Kontraktda esa faqat `nameUz`
 * bor (`CatalogGroupDto` da qo'shimcha `nameRu`, `CatalogTypeDto` da u ham yo'q),
 * `nameEn` umuman yo'q — shuning uchun ilovaning qolgan qismi tarjima qilinganda bu
 * chiplar o'zbekcha qolib ketardi.
 *
 * Tarjima **kalit bo'yicha** qidiriladi: kalit (`FOOD`, `BARBERSHOP`) barqaror, nom esa
 * serverda tahrirlanishi mumkin. Kalit topilmasa nom bo'yicha urinib ko'riladi
 * (bo'limlar/sub-kategoriyalar kalitsiz keladi), u ham bo'lmasa serverdagi nom
 * o'zgarishsiz ko'rsatiladi — yangi kategoriya qo'shilsa ekran bo'sh qolmaydi.
 *
 * ⚠️ Bu **vaqtinchalik yechim**: har yangi kategoriya uchun shu yerga qator qo'shish
 * kerak. Uzoq muddatli to'g'ri yo'l — katalogga `nameEn` (va turlarga `nameRu`) qo'shish;
 * o'shanda tarjima serverdan keladi va ilovani yangilash shart bo'lmaydi.
 */
object CatalogNames {

    /**
     * Serverdagi nomni joriy tilga o'giradi.
     *
     * @param key katalog kaliti (`DiscountGroup.key` / `DiscountCategory.id`); kalitsiz
     *   ma'lumot uchun bo'sh satr uzatilsa ham bo'ladi — o'shanda faqat nom qaraladi.
     * @param uzName serverdan kelgan `nameUz`
     */
    fun tr(key: String, uzName: String): String {
        if (AppLocale.current == AppLanguage.UZ) return uzName
        val pair = BY_KEY[key.uppercase()] ?: BY_NAME[uzName] ?: return uzName
        return if (AppLocale.current == AppLanguage.EN) pair.first else pair.second
    }

    /**
     * Kalit → (inglizcha, ruscha).
     *
     * Ro'yxat backenddagi katalogdan olingan: 7 ta guruh va 26 ta biznes turi.
     */
    private val BY_KEY: Map<String, Pair<String, String>> = mapOf(
        // --- Guruhlar ---
        "FOOD" to ("Food" to "Питание"),
        "SPORT" to ("Sport" to "Спорт"),
        "GAMES" to ("Games & leisure" to "Игры и досуг"),
        "ENTERTAINMENT" to ("Entertainment" to "Развлечения"),
        "EDUCATION" to ("Education" to "Образование"),
        "BEAUTY" to ("Beauty" to "Красота"),
        "SHOPPING" to ("Shopping & services" to "Покупки и услуги"),

        // --- Biznes turlari: BEAUTY ---
        "BARBERSHOP" to ("Barbershop" to "Барбершоп"),
        "BEAUTY_SALON" to ("Beauty salon" to "Салон красоты"),

        // --- EDUCATION ---
        "EDUCATION_CENTER" to ("Learning centre" to "Учебный центр"),
        "LIBRARY" to ("Library / Co-working" to "Библиотека / Коворкинг"),
        "TUTOR" to ("Tutor" to "Репетитор"),

        // --- ENTERTAINMENT ---
        "CINEMA" to ("Cinema" to "Кинотеатр"),
        "KARAOKE" to ("Karaoke" to "Караоке"),

        // --- FOOD ---
        "FAST_FOOD" to ("Fast food" to "Фастфуд"),
        "NATIONAL_FOOD" to ("National dishes" to "Национальная кухня"),
        "SOMSA" to ("Somsa / Bakery" to "Самса / Пекарня"),

        // --- GAMES ---
        "BILLIARDS" to ("Billiards" to "Бильярд"),
        "BOWLING" to ("Bowling" to "Боулинг"),
        "CYBER_CLUB" to ("Cyber club" to "Компьютерный клуб"),
        "PLAYSTATION" to ("PlayStation" to "PlayStation"),

        // --- SHOPPING ---
        "CLOTHING" to ("Clothing" to "Одежда"),
        "PRINTING" to ("Print shop" to "Типография"),

        // --- SPORT ---
        "BASKETBALL" to ("Basketball court" to "Баскетбольная площадка"),
        "BOXING" to ("Boxing / Martial arts" to "Бокс / Единоборства"),
        "FITNESS" to ("Fitness / Gym" to "Фитнес / Тренажёрный зал"),
        "FOOTBALL_FIELD" to ("Football pitch" to "Футбольное поле"),
        "FOOTBALL_TRAINING" to ("Football school" to "Футбольная школа"),
        "SWIMMING_POOL" to ("Swimming pool" to "Бассейн"),
        "TABLE_TENNIS" to ("Table tennis" to "Настольный теннис"),
        "TENNIS" to ("Tennis" to "Большой теннис"),
        "VOLLEYBALL" to ("Volleyball court" to "Волейбольная площадка"),
        "WRESTLING_MMA" to ("Wrestling / MMA" to "Борьба / MMA"),
    )

    /**
     * Nom bo'yicha zaxira — bo'limlar (`subcategory`) va filtr sxemasidagi yorliqlar
     * kalitsiz keladi, shuning uchun ular faqat shu jadvaldan topiladi.
     */
    private val BY_NAME: Map<String, Pair<String, String>> = mapOf(
        // Guruh nomlari (kalit o'zgarib ketsa ham ishlashi uchun)
        "Ovqatlanish" to ("Food" to "Питание"),
        "Sport" to ("Sport" to "Спорт"),
        "O'yin va bo'sh vaqt" to ("Games & leisure" to "Игры и досуг"),
        "Ko'ngilochar" to ("Entertainment" to "Развлечения"),
        "Ta'lim" to ("Education" to "Образование"),
        "Go'zallik" to ("Beauty" to "Красота"),
        "Savdo va xizmat" to ("Shopping & services" to "Покупки и услуги"),
        // `CatalogRules.goodsName` "Savdo va xizmat" dan xizmatni ajratganda qoladigan nom
        "Savdo" to ("Shopping" to "Покупки"),
        "Xizmatlar" to ("Services" to "Услуги"),

        // Biznes turlari — nom bo'yicha ham
        "Sartaroshxona" to ("Barbershop" to "Барбершоп"),
        "Go'zallik saloni" to ("Beauty salon" to "Салон красоты"),
        "O'quv markaz" to ("Learning centre" to "Учебный центр"),
        "Kutubxona / Co-working" to ("Library / Co-working" to "Библиотека / Коворкинг"),
        "Repetitor" to ("Tutor" to "Репетитор"),
        "Kinoteatr" to ("Cinema" to "Кинотеатр"),
        "Karaoke" to ("Karaoke" to "Караоке"),
        "Fast food" to ("Fast food" to "Фастфуд"),
        "Milliy taomlar" to ("National dishes" to "Национальная кухня"),
        "Somsa / Nonvoyxona" to ("Somsa / Bakery" to "Самса / Пекарня"),
        "Billiard" to ("Billiards" to "Бильярд"),
        "Bouling" to ("Bowling" to "Боулинг"),
        "Kompyuter klubi" to ("Cyber club" to "Компьютерный клуб"),
        "Kiyim-kechak" to ("Clothing" to "Одежда"),
        "Bosmaxona / Tipografiya" to ("Print shop" to "Типография"),
        "Basketbol maydoni" to ("Basketball court" to "Баскетбольная площадка"),
        "Boks / Yakkakurash zali" to ("Boxing / Martial arts" to "Бокс / Единоборства"),
        "Fitnes / Trenajyor zali" to ("Fitness / Gym" to "Фитнес / Тренажёрный зал"),
        "Futbol maydoni" to ("Football pitch" to "Футбольное поле"),
        "Futbol maktabi" to ("Football school" to "Футбольная школа"),
        "Suzish havzasi" to ("Swimming pool" to "Бассейн"),
        "Stol tennis" to ("Table tennis" to "Настольный теннис"),
        "Katta tennis" to ("Tennis" to "Большой теннис"),
        "Voleybol maydoni" to ("Volleyball court" to "Волейбольная площадка"),
        "Kurash / MMA" to ("Wrestling / MMA" to "Борьба / MMA"),

        // Bo'limlar (e'lon kartochkasidagi `subcategory`)
        "Butun menyu" to ("Whole menu" to "Всё меню"),
        "Burger" to ("Burgers" to "Бургеры"),
        "Osh / Palov" to ("Osh / Plov" to "Ош / Плов"),
        "Chet tillari" to ("Foreign languages" to "Иностранные языки"),
        "Erkaklar soch olish" to ("Men's haircut" to "Мужская стрижка"),
        "Ustki kiyim" to ("Outerwear" to "Верхняя одежда"),
        "Poyabzal" to ("Footwear" to "Обувь"),
        "Erkaklar" to ("Men" to "Мужское"),
        "Ayollar" to ("Women" to "Женское"),
    )
}
