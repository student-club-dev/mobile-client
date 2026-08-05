package dev.feature.university.domain.model

/**
 * Universitet — profil to'ldirish va student bo'limlari uchun.
 * Backend kelganda `id` server ID'siga mos keladi.
 *
 * [name] va [city] — manbadagi (`prof-emis.edu.uz`) **xom** qiymatlar: rasmiy nom to'liq
 * huquqiy shtampi bilan, manzil esa ko'chasi va uy raqamigacha. UI ularni to'g'ridan-to'g'ri
 * ko'rsatmaydi — buning uchun [display] bor (qarang [UniversityNaming]).
 */
data class University(
    val id: String,
    val name: String,
    val city: String,
    val faculty: String? = null,
    val accent: Long, // ARGB, masalan 0xFF6C47FF
    /**
     * Ko'rsatish uchun tayyorlangan ko'rinish. Konstruktorda **bir marta** hisoblanadi:
     * ro'yxat chizilganda har kadrda qayta ishlamasin.
     */
    val display: UniversityDisplay = UniversityNaming.display(name, city),
) {
    /** Tile/chip qisqartmasi — `TATU`, `SamDU`. Bazadagi `monogram` ustuni shundan to'ladi. */
    val monogram: String get() = display.abbr

    /** Ro'yxat va tanlov qatorlarining asosiy matni. */
    val shortName: String get() = display.shortName

    /** Manzildan ajratilgan shahar/viloyat — bo'sh bo'lishi mumkin. */
    val shortCity: String get() = display.city

    /** `Nukus filiali` yoki `null`. */
    val branch: String? get() = display.branch

    /**
     * Qidiruv uchun yagona matn: to'liq nom ham, qisqa nom ham, qisqartma ham mos kelsin —
     * foydalanuvchi `TATU` deb yozganda ham, `axborot` deb yozganda ham topilsin.
     */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        return name.contains(q, ignoreCase = true) ||
            display.shortName.contains(q, ignoreCase = true) ||
            display.abbr.contains(q, ignoreCase = true) ||
            display.city.contains(q, ignoreCase = true) ||
            display.branch?.contains(q, ignoreCase = true) == true ||
            display.eponym?.contains(q, ignoreCase = true) == true
    }
}
