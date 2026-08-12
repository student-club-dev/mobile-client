package dev.feature.listings.domain.model

import dev.core.common.locale.AppLocale

/**
 * E'lon formasining validatsiya matnlari.
 *
 * Validatsiya domen qatlamida — Compose'dan tashqarida — bajariladi, shuning uchun til
 * [AppLocale] global holatidan olinadi. Matnlar to'g'ridan-to'g'ri maydon ostida
 * ko'rsatiladi, ya'ni buyruq ohangida va qisqa bo'lishi kerak.
 */
internal object ValidationStrings {
    val titleRequired get() = AppLocale.pick("Enter a title", "Введите заголовок", "Sarlavhani kiriting")
    val titleTooShort get() = AppLocale.pick("The title is too short", "Заголовок слишком короткий", "Sarlavha juda qisqa")
    val titleTooLong get() = AppLocale.pick("The title must be 120 characters or fewer", "Заголовок не длиннее 120 символов", "Sarlavha 120 belgidan oshmasin")
    val imageRequired get() = AppLocale.pick("Add at least 1 photo", "Добавьте хотя бы 1 фото", "Kamida 1 ta rasm qo'shing")
    fun tooManyImages(max: Int) = AppLocale.pick("At most $max photos", "Максимум $max фото", "Maksimal $max ta rasm")
    val priceRequired get() = AppLocale.pick(
        "Enter a price or tick \"negotiable\"",
        "Укажите цену или отметьте «договорная»",
        "Narxni kiriting yoki \"kelishilgan\" ni belgilang",
    )
    val priceRangeInvalid get() = AppLocale.pick(
        "The upper bound must be greater than the lower one",
        "Верхняя граница должна быть больше нижней",
        "Yuqori chegara quyi chegaradan katta bo'lsin",
    )
    val phoneRequired get() = AppLocale.pick("Enter a phone number", "Введите номер телефона", "Telefon raqamini kiriting")
    val phoneIncomplete get() = AppLocale.pick(
        "Enter the full number: +998 90 123 45 67",
        "Введите номер полностью: +998 90 123 45 67",
        "Raqamni to'liq kiriting: +998 90 123 45 67",
    )
    val endBeforeStart get() = AppLocale.pick(
        "The end date must come after the start",
        "Дата окончания должна быть позже начала",
        "Tugash sanasi boshlanishdan keyin bo'lsin",
    )
    fun tooManyBranches(max: Int) = AppLocale.pick("At most $max addresses", "Максимум $max адресов", "Maksimal $max ta manzil")
    val pointOutsideCountry get() = AppLocale.pick(
        "The point is outside Uzbekistan",
        "Точка за пределами Узбекистана",
        "Nuqta O'zbekiston hududidan tashqarida",
    )
    val addressEmpty get() = AppLocale.pick("The address is empty", "Адрес пуст", "Manzil bo'sh")
    val duplicateAddress get() = AppLocale.pick(
        "Two addresses point to the same place",
        "Два адреса указывают на одно место",
        "Ikkita manzil bir joyda belgilangan",
    )

    val markBranchOnMap get() = AppLocale.pick(
        "Mark at least 1 branch on the map",
        "Отметьте хотя бы 1 филиал на карте",
        "Kamida 1 ta filialni xaritadan belgilang",
    )
    val markHomeOnMap get() = AppLocale.pick("Mark the home on the map", "Отметьте жильё на карте", "Uy joyini xaritadan belgilang")
    val markServiceOnMap get() = AppLocale.pick(
        "Mark where the service is provided on the map",
        "Отметьте на карте, где оказывается услуга",
        "Xizmat ko'rsatiladigan joyni xaritadan belgilang",
    )
    val markJobOnMap get() = AppLocale.pick("Mark the workplace on the map", "Отметьте место работы на карте", "Ish joyini xaritadan belgilang")
    val markTaskOnMap get() = AppLocale.pick(
        "Mark on the map where the work is handed over",
        "Отметьте на карте, где сдаётся работа",
        "Ish topshiriladigan joyni xaritadan belgilang",
    )

    fun tooManyOptionGroups(max: Int) = AppLocale.pick(
        "At most $max option groups", "Максимум $max групп дополнений", "Maksimal $max ta qo'shimcha guruhi",
    )
    val optionGroupNameRequired get() = AppLocale.pick(
        "Enter a name for the option group", "Введите название группы дополнений", "Qo'shimcha guruhining nomini kiriting",
    )
    fun optionGroupEmpty(name: String) = AppLocale.pick(
        "\"$name\" needs at least 1 option",
        "В группе «$name» нужен хотя бы 1 вариант",
        "\"$name\" guruhida kamida 1 ta variant bo'lsin",
    )
    fun optionGroupTooBig(name: String, max: Int) = AppLocale.pick(
        "\"$name\" has more than $max options",
        "В «$name» больше $max вариантов",
        "\"$name\" da $max tadan ko'p variant",
    )
    val otherCategoryNeedsName get() = AppLocale.pick(
        "\"Other\" was picked — write the section name",
        "Выбрано «Другое» — впишите название раздела",
        "\"Boshqa\" tanlandi — bo'lim nomini yozing",
    )

    val discountPercentRequired get() = AppLocale.pick("Enter the discount percentage", "Введите процент скидки", "Chegirma foizini kiriting")
    fun discountPercentTooBig(max: Long) = AppLocale.pick(
        "The discount must not exceed $max%", "Скидка не должна превышать $max%", "Chegirma $max% dan oshmasin",
    )
    val discountAmountRequired get() = AppLocale.pick("Enter the discount amount", "Введите сумму скидки", "Chegirma summasini kiriting")
    val discountBelowPrice get() = AppLocale.pick(
        "The discount must be less than the price", "Скидка должна быть меньше цены", "Chegirma narxdan kam bo'lsin",
    )
    val studentPriceRequired get() = AppLocale.pick("Enter the student price", "Введите студенческую цену", "Talaba narxini kiriting")
    val studentPriceBelowOriginal get() = AppLocale.pick(
        "The student price must be lower than the original",
        "Студенческая цена должна быть ниже исходной",
        "Talaba narxi asl narxdan past bo'lsin",
    )
    val giftConditionRequired get() = AppLocale.pick(
        "Describe the offer (e.g. the second coffee is free)",
        "Опишите условие акции (например: второй кофе бесплатно)",
        "Aksiya shartini yozing (masalan: ikkinchi kofe bepul)",
    )
    val promoCodeRequired get() = AppLocale.pick("Enter the promo code", "Введите промокод", "Promokodni kiriting")

    val propertyTypeRequired get() = AppLocale.pick("Pick the housing type", "Выберите тип жилья", "Turarjoy turini tanlang")
    val roomCountRequired get() = AppLocale.pick("Enter the number of rooms", "Введите количество комнат", "Nechi xonaligini kiriting")
    fun roomCountRange(max: Int) = AppLocale.pick(
        "The number of rooms must be 1 to $max", "Количество комнат от 1 до $max", "Xonalar soni 1 dan $max gacha bo'lsin",
    )
    val currentTenantsRequired get() = AppLocale.pick(
        "Enter how many people live there now", "Укажите, сколько человек живёт сейчас", "Hozir nechi kishi yashashini kiriting",
    )
    val currentTenantsInvalid get() = AppLocale.pick(
        "The current number of people is invalid", "Неверное количество жильцов", "Hozirgi kishilar soni noto'g'ri",
    )
    val neededTenantsRequired get() = AppLocale.pick(
        "Enter how many people are needed", "Укажите, сколько человек нужно", "Nechi kishi kerakligini kiriting",
    )
    val neededTenantsMin get() = AppLocale.pick(
        "At least 1 person must be needed", "Нужен хотя бы 1 человек", "Kamida 1 kishi kerak bo'lsin",
    )
    fun tooManyTenants(rooms: Int, total: Int) = AppLocale.pick(
        "$total people is a lot for $rooms rooms — check the numbers",
        "$total человек для $rooms комнат — многовато, проверьте числа",
        "$rooms xonaga $total kishi ko'p — sonlarni tekshiring",
    )
    val tenantGenderRequired get() = AppLocale.pick(
        "Choose who it's for — girls or guys", "Выберите, для кого — девушки или парни", "Kim uchun ekanini tanlang — qiz yoki o'g'il",
    )
    val floorAboveTotal get() = AppLocale.pick(
        "The floor is higher than the number of floors in the building",
        "Этаж больше количества этажей в доме",
        "Qavat binoning qavatlar sonidan katta",
    )

    val serviceTypeRequired get() = AppLocale.pick("Pick the service area", "Выберите сферу услуги", "Xizmat sohasini tanlang")
    fun subjectRequired(subjectLabel: String) = AppLocale.pick(
        "Pick a $subjectLabel", "Выберите: $subjectLabel", "${subjectLabel}ni tanlang",
    )
    val otherSubjectNeedsName get() = AppLocale.pick(
        "\"Other\" was picked — write its name", "Выбрано «Другое» — впишите название", "\"Boshqa\" tanlandi — nomini yozing",
    )
    fun attributeEmpty(label: String) = AppLocale.pick(
        "\"$label\" is not filled in", "Поле «$label» не заполнено", "\"$label\" to'ldirilmagan",
    )
    val experienceInvalid get() = AppLocale.pick(
        "The years of experience are invalid", "Неверное количество лет опыта", "Tajriba yillari noto'g'ri",
    )

    val taskSubjectRequired get() = AppLocale.pick("Pick the work area", "Выберите направление работы", "Ish yo'nalishini tanlang")
    val taskTypeRequired get() = AppLocale.pick("Pick the work type", "Выберите тип работы", "Ish turini tanlang")
    val taskTypeNameRequired get() = AppLocale.pick("Write the work type", "Впишите тип работы", "Ish turini yozing")
    val taskBriefRequired get() = AppLocale.pick("Describe the task", "Опишите задание", "Topshiriq shartini yozing")
    val taskDeadlineRequired get() = AppLocale.pick("Set the due date", "Укажите срок сдачи", "Topshirish muddatini belgilang")
    val taskDeadlineInFuture get() = AppLocale.pick(
        "The deadline must be in the future", "Срок должен быть позже текущего времени", "Muddat hozirgi vaqtdan keyin bo'lsin",
    )

    val businessNameRequired get() = AppLocale.pick(
        "Enter the organisation or employer name",
        "Введите название организации или работодателя",
        "Tashkilot yoki ish beruvchi nomini kiriting",
    )
    val shiftRequired get() = AppLocale.pick("Pick the work shift", "Выберите смену", "Ish smenasini tanlang")
    val scheduleRequired get() = AppLocale.pick("Enter the working hours", "Укажите интервал рабочего времени", "Ish vaqti oralig'ini kiriting")
    val workDateRequired get() = AppLocale.pick("Mark which day the work is", "Укажите, в какой день работа", "Ish qaysi kuni ekanini belgilang")
    val workDaysRequired get() = AppLocale.pick("Pick the working days", "Выберите рабочие дни", "Ish kunlarini tanlang")
    val hoursPerDayRange get() = AppLocale.pick(
        "Hours per day must be 1 to 24", "Часов в день — от 1 до 24", "Kunlik soat 1 dan 24 gacha bo'lsin",
    )
    val vacanciesRequired get() = AppLocale.pick("Enter how many people are needed", "Укажите, сколько людей нужно", "Nechta odam kerakligini kiriting")
    fun vacanciesRange(max: Int) = AppLocale.pick(
        "The number of people must be 1 to $max", "Количество людей от 1 до $max", "Kerakli odamlar soni 1 dan $max gacha",
    )
    val ageRangeInvalid get() = AppLocale.pick("The age range is invalid", "Неверный возрастной диапазон", "Yosh oralig'i noto'g'ri")
}
