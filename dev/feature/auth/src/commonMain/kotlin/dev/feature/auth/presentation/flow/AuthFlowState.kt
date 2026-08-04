package dev.feature.auth.presentation.flow

import dev.core.common.format.isUzPhoneComplete
import dev.core.common.format.toUzPhoneDigits
import dev.core.common.format.toUzPhoneE164
import dev.feature.university.domain.model.University

/**
 * Kurs bosqichi. [apiValue] — backend kutadigan qiymat (`"1".."4"`, `"MASTER"`); enum
 * nomini yuborib bo'lmaydi, server uni rad etadi va kurs saqlanmay qolardi.
 */
enum class CourseYear(val label: String, val apiValue: String) {
    ONE("1", "1"), TWO("2", "2"), THREE("3", "3"), FOUR("4", "4"), MASTER("Mag", "MASTER")
}

/** Jins — profilda ixtiyoriy, `GET /v1/students?gender=` filtri shunga tayanadi. */
enum class ProfileGender(val label: String, val apiValue: String) {
    MALE("Erkak", "MALE"), FEMALE("Ayol", "FEMALE")
}

/**
 * SMS kod nima uchun so'ralgan — "qayta yuborish" tugmasi qaysi endpointga borishini shu
 * hal qiladi (`otp/request` yoki `password/forgot`).
 */
enum class OtpPurpose { VERIFY_PHONE, RESET_PASSWORD }

/** Butun auth oqimining forma holati. */
data class AuthFlowState(
    // Aloqa
    val phone: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val rememberMe: Boolean = true,
    // SMS kod
    val otp: String = "",
    val otpPurpose: OtpPurpose = OtpPurpose.VERIFY_PHONE,
    val resendSeconds: Int = 0,
    // Ro'yxat
    val firstName: String = "",
    val lastName: String = "",
    val universityEmail: String = "",
    val termsAccepted: Boolean = false,
    // Profil
    val universityId: String? = null,
    /** Tanlangan universitet (prof-emis ro'yxatidan) — nomi/shahri shundan ko'rsatiladi. */
    val selectedUniversity: University? = null,
    val birthYear: Int = 2004,
    val courseYear: CourseYear = CourseYear.TWO,
    /** `null` — ko'rsatilmagan (ixtiyoriy maydon, keyin profilda o'zgartirsa bo'ladi). */
    val gender: ProfileGender? = null,
    // Umumiy
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val phoneDigits: String get() = phone.toUzPhoneDigits()
    val phoneValid: Boolean get() = phone.isUzPhoneComplete()
    val otpValid: Boolean get() = otp.length == 6

    /** Kirish tugmasi faolmi — telefon + parol. */
    val loginReady: Boolean get() = password.isNotBlank() && phoneValid

    /** E.164 formatdagi raqam (`+998901234567`) yoki bo'sh matn. */
    val phoneE164: String get() = phone.toUzPhoneE164().orEmpty()

    /**
     * Parolni tiklashning 1-qadami to'ldirilganmi — raqam va yangi parol.
     * Kod so'rashdan OLDIN tekshiriladi: `password/reset` uchalasini (raqam, kod, parol)
     * bitta so'rovda kutadi, shuning uchun parolni oldindan olib qo'yamiz.
     */
    val resetReady: Boolean get() = phoneValid && password.length >= MIN_PASSWORD_LENGTH
}

/** Backend qoidasi: `RegisterDto.password.minLength = 8`. */
private const val MIN_PASSWORD_LENGTH = 8

/**
 * Universitet tanlash ekranining holati. Ro'yxat **prof-emis API**'sidan keladi
 * (`UniversityRepository.fetchSelectableUniversities`) — statik namunaviy ro'yxat emas.
 */
data class UniversityPickerUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val results: List<University> = emptyList(),
)
