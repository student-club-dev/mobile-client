package dev.core.data.seed

import dev.core.common.locale.AppLocale

/**
 * Namuna (seed) ma'lumotining matnlari — endpoint'i hali yo'q bo'limlar uchun.
 *
 * Bu qatorlar bazaga YOZILADI, ya'ni til almashganda ular o'z-o'zidan tarjima bo'lmaydi:
 * shuning uchun faqat o'qish uchun bo'lgan bo'limlar (ishlar, klublar) til o'zgarganda
 * qaytadan yoziladi ([LocalDataSeeder.resyncLanguage]). E'lonlar (`ad`) esa tahrirlanadi,
 * shuning uchun bir marta — birinchi seed paytidagi tilda — qoladi.
 */
internal object SeedStrings {
    // --- Ishlar ---
    val jobSmmTitle get() = AppLocale.pick("SMM manager (part-time)", "SMM-менеджер (part-time)", "SMM menejer (part-time)")
    val jobSmmLocation get() = AppLocale.pick("remote", "удалённо", "masofaviy")
    val jobSmmSalary get() = AppLocale.pick("3–5M UZS", "3–5 млн сум", "3–5 mln so‘m")
    val jobSmmPosted get() = AppLocale.pick("2 hours ago", "2 часа назад", "2 soat oldin")

    val jobFrontendTitle get() = AppLocale.pick("Frontend intern", "Frontend-стажёр", "Frontend intern")
    val jobFrontendLocation get() = AppLocale.pick("Tashkent", "Ташкент", "Toshkent")
    val jobFrontendSalary get() = AppLocale.pick("4M UZS", "4 млн сум", "4 mln so‘m")
    val jobFrontendPosted get() = AppLocale.pick("today", "сегодня", "bugun")

    val jobWaiterTitle get() = AppLocale.pick("Waiter (evening shift)", "Официант (вечерняя смена)", "Ofitsiant (kechqurun)")
    val jobWaiterSalary get() = AppLocale.pick("1.5M UZS", "1,5 млн сум", "1.5 mln so‘m")
    val jobWaiterPosted get() = AppLocale.pick("yesterday", "вчера", "kecha")

    val tagIt get() = AppLocale.pick("IT", "IT", "IT")
    val tagSmm get() = AppLocale.pick("SMM", "SMM", "SMM")
    val tagOffice get() = AppLocale.pick("Office", "Офис", "Ofis")
    val tagService get() = AppLocale.pick("Service", "Сервис", "Xizmat")
    val tagShift get() = AppLocale.pick("Shift work", "Посменно", "Smenali")

    // --- E'lonlar (eski `ad` jadvali) ---
    val adRoommateTitle get() = AppLocale.pick("Room-mate in Chilonzor", "Сосед по комнате в Чиланзаре", "Chilonzorda room-mate")
    val adRoommateCategory get() = AppLocale.pick("Housing", "Жильё", "Turar joy")
    val adRoommatePrice get() = AppLocale.pick("1.2M/month", "1,2 млн/мес", "1.2 mln/oy")
    val adRoommateBody get() = AppLocale.pick(
        "Two rooms, close to the metro, comfortable for a student.",
        "Две комнаты, рядом метро, удобно для студента.",
        "2 xonali, metroga yaqin, student uchun qulay.",
    )
    val adRoommatePosted get() = AppLocale.pick("3 hours ago", "3 часа назад", "3 soat oldin")

    val adMacbookTitle get() = AppLocale.pick("MacBook Air M1 for sale", "Продаётся MacBook Air M1", "MacBook Air M1 sotiladi")
    val adMacbookCategory get() = AppLocale.pick("Electronics", "Техника", "Texnika")
    val adMacbookBody get() = AppLocale.pick(
        "Excellent condition, 100% battery health, low cycle count.",
        "Состояние отличное, ёмкость батареи 100%, мало циклов.",
        "Holati a'lo, 100% batareya sikli past.",
    )
    val adMacbookPosted get() = AppLocale.pick("yesterday", "вчера", "kecha")

    // --- Klublar ---
    val clubItName get() = AppLocale.pick("IT Club", "IT-клуб", "IT Klub")
    val clubItBody get() = AppLocale.pick(
        "A community for programming, hackathons and IT projects.",
        "Сообщество по программированию, хакатонам и IT-проектам.",
        "Dasturlash, hackathonlar va IT loyihalar jamoasi.",
    )

    val clubDebateName get() = AppLocale.pick("Debate Club", "Дебат-клуб", "Debat Klubi")
    val clubDebateBody get() = AppLocale.pick(
        "Critical thinking and the art of public speaking.",
        "Логическое мышление и искусство публичных выступлений.",
        "Mantiqiy fikrlash va notiqlik san'ati.",
    )

    val clubSportName get() = AppLocale.pick("Sports Club", "Спортклуб", "Sport Klubi")
    val clubSportBody get() = AppLocale.pick(
        "Football, basketball and general fitness sessions.",
        "Футбол, баскетбол и общефизическая подготовка.",
        "Futbol, basketbol va umumjismoniy mashg'ulotlar.",
    )

    val clubVolunteersName get() = AppLocale.pick("Volunteers", "Волонтёры", "Volontyorlar")
    val clubVolunteersBody get() = AppLocale.pick(
        "Social projects and charity events.",
        "Социальные проекты и благотворительные мероприятия.",
        "Ijtimoiy loyihalar va xayriya tadbirlari.",
    )

    val clubDesignName get() = AppLocale.pick("Design Studio", "Дизайн-студия", "Dizayn Studiyasi")
    val clubDesignBody get() = AppLocale.pick(
        "UI/UX, graphics and creative workshops.",
        "UI/UX, графика и творческие мастерские.",
        "UI/UX, grafika va ijodiy ustaxonalar.",
    )

    val clubLanguageName get() = AppLocale.pick("Language Club", "Языковой клуб", "Til Klubi")
    val clubLanguageBody get() = AppLocale.pick(
        "Practice in English, Korean and Arabic.",
        "Практика английского, корейского и арабского языков.",
        "Ingliz, koreys va arab tillari amaliyoti.",
    )
}
