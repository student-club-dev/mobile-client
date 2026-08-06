package dev.feature.auth.presentation.flow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.error.AppMessageBus
import dev.core.domain.model.User
import dev.core.domain.usecase.CancelRegistrationUseCase
import dev.core.domain.usecase.CompleteRegistrationUseCase
import dev.core.domain.usecase.ForgotPasswordUseCase
import dev.core.domain.usecase.LoginUseCase
import dev.core.domain.usecase.LoginWithGoogleUseCase
import dev.core.domain.usecase.ObserveCurrentUserUseCase
import dev.core.domain.usecase.RegisterUseCase
import dev.core.domain.usecase.RequestRegistrationOtpUseCase
import dev.core.domain.usecase.ResetPasswordUseCase
import dev.feature.profile.domain.model.UserProfile
import dev.feature.profile.domain.usecase.SaveProfileUseCase
import dev.feature.settings.domain.repository.SettingsRepository
import dev.feature.university.domain.model.University
import dev.feature.university.domain.repository.UniversityRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dev.core.common.format.toUzPhoneDigits

/** UI navigatsiyasini boshqaradigan bir martalik hodisalar. */
sealed interface AuthEvent {
    /** Hisob yaratildi va raqamga SMS kod ketdi — tasdiqlash ekraniga. */
    data object OtpSent : AuthEvent

    /** Ro'yxat yakunlandi (raqam tasdiqlandi yoki o'tkazib yuborildi) — muvaffaqiyat ekraniga. */
    data object Registered : AuthEvent

    /** Parolni tiklash kodi yuborildi — kod kiritish ekraniga. */
    data object ResetCodeSent : AuthEvent

    /** Kod to'liq kiritildi — endi yangi parol ekraniga. */
    data object ResetCodeEntered : AuthEvent

    /** Parol yangilandi — kirish ekraniga qaytamiz. */
    data object PasswordReset : AuthEvent

    /** Profil saqlandi — bosh sahifaga o'tish. */
    data object ProfileSaved : AuthEvent

    /** To'liq kirildi (parol yoki Google) — bosh sahifaga o'tish. */
    data class Authenticated(val user: User) : AuthEvent
}

/**
 * Auth oqimining forma holatini va biznes-amallarini boshqaradi.
 *
 * Backend oqimi (`/v1/auth/student/…`):
 * - **kirish** — telefon yoki email + parol, yoki Google (`oauth/google`);
 * - **ro'yxat** — telefon (yoki email) + parol; hisob darhol ochiladi, so'ng SMS kod bilan
 *   raqam tasdiqlanadi (`otp/request` + `otp/verify` — ikkalasi ham **sessiya** talab qiladi,
 *   shuning uchun ular ro'yxatdan KEYIN keladi va o'tkazib yuborilishi mumkin);
 * - **parolni tiklash** — raqamga SMS kod (`password/forgot`) → kodni kiritish → yangi parol
 *   (`password/reset` uchalasini bitta so'rovda oladi).
 *
 * Token yangilash bu yerda emas — u tarmoq qatlamida avtomatik (`createHttpClient`).
 */
class AuthFlowViewModel(
    private val loginUseCase: LoginUseCase,
    private val loginWithGoogleUseCase: LoginWithGoogleUseCase,
    private val registerUseCase: RegisterUseCase,
    private val completeRegistrationUseCase: CompleteRegistrationUseCase,
    private val cancelRegistrationUseCase: CancelRegistrationUseCase,
    private val requestRegistrationOtpUseCase: RequestRegistrationOtpUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
    private val observeCurrentUserUseCase: ObserveCurrentUserUseCase,
    private val saveProfileUseCase: SaveProfileUseCase,
    private val settingsRepository: SettingsRepository,
    private val universityRepository: UniversityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthFlowState())
    val state: StateFlow<AuthFlowState> = _state.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events: Flow<AuthEvent> = _events.receiveAsFlow()

    /**
     * Ilova ochilishida avtomatik kirish holati (local sessiya keshidan):
     * `null` — hali tekshirilmoqda (splash), `true` — kirgan (HOME), `false` — kirmagan.
     */
    val loggedIn: StateFlow<Boolean?> = observeCurrentUserUseCase()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Tanishtiruv ekranlari avval ko'rilganmi: `null` — hali o'qilmoqda, `true` — ko'rilgan.
     * Chiqishdan (logout) keyin bayroq saqlanib qoladi, shuning uchun foydalanuvchi
     * tanishtiruvga qaytmay, to'g'ridan-to'g'ri kirish ekranidan boshlaydi.
     */
    val onboardingSeen: StateFlow<Boolean?> =
        settingsRepository.observeFlag(SettingsRepository.KEY_ONBOARDING_SEEN, default = false)
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Tanishtiruv tugadi (yoki o'tkazib yuborildi) — boshqa ko'rsatilmaydi. */
    fun markOnboardingSeen() {
        viewModelScope.launch {
            settingsRepository.setFlag(SettingsRepository.KEY_ONBOARDING_SEEN, true)
        }
    }

    private var timerJob: Job? = null

    // ------------------------------------------------------------------
    // Forma yangilanishlari
    // ------------------------------------------------------------------

    fun onPhoneChange(v: String) = _state.update {
        it.copy(phone = v.toUzPhoneDigits(), error = null)
    }

    fun onPasswordChange(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onPasswordConfirmChange(v: String) =
        _state.update { it.copy(passwordConfirm = v, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleRememberMe() = _state.update { it.copy(rememberMe = !it.rememberMe) }
    fun onOtpChange(v: String) = _state.update {
        it.copy(otp = v.filter { c -> c.isDigit() }.take(OTP_CODE_LENGTH), error = null)
    }

    fun onFirstNameChange(v: String) = _state.update { it.copy(firstName = v) }
    fun onLastNameChange(v: String) = _state.update { it.copy(lastName = v) }
    fun onUniversityEmailChange(v: String) = _state.update { it.copy(universityEmail = v) }
    fun toggleTerms() = _state.update { it.copy(termsAccepted = !it.termsAccepted, error = null) }

    fun onBirthYearChange(y: Int) = _state.update { it.copy(birthYear = y) }
    fun onCourseYearChange(c: CourseYear) = _state.update { it.copy(courseYear = c) }

    /** Bosilgan tugmani qayta bosish — tanlovni bekor qiladi (maydon ixtiyoriy). */
    fun onGenderChange(g: ProfileGender) =
        _state.update { it.copy(gender = if (it.gender == g) null else g) }

    // ------------------------------------------------------------------
    // Universitet tanlash — ro'yxat prof-emis API'sidan
    // ------------------------------------------------------------------

    /** Yuklab olingan to'liq ro'yxat (qidiruv shu ustida ishlaydi, tarmoqqa qayta bormaydi). */
    private val allUniversities = MutableStateFlow<List<University>>(emptyList())

    private val _universityPicker = MutableStateFlow(UniversityPickerUiState())
    val universityPicker: StateFlow<UniversityPickerUiState> = _universityPicker.asStateFlow()

    /** Tanlash ekrani ochilganda chaqiriladi — ro'yxat allaqachon bo'lsa qayta so'ramaydi. */
    fun loadUniversities() {
        if (allUniversities.value.isNotEmpty() || _universityPicker.value.loading) return
        _universityPicker.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val res = universityRepository.fetchSelectableUniversities()) {
                is Resource.Success -> {
                    allUniversities.value = res.data
                    _universityPicker.update {
                        it.copy(loading = false, results = filterUniversities(res.data, it.query))
                    }
                }
                is Resource.Error ->
                    _universityPicker.update { it.copy(loading = false, error = res.message) }
                Resource.Loading -> Unit
            }
        }
    }

    fun onUniversityQueryChange(v: String) = _universityPicker.update {
        it.copy(query = v, results = filterUniversities(allUniversities.value, v))
    }

    /**
     * Tanlangan OTM local katalogga ham yoziladi. Profilda faqat `universityId` saqlanadi
     * (`emis-245`) va serverda universitetlar katalogi yo'q — nomni Home, Chat, Profil va
     * "Universitetim" ekranlari shu local jadvaldan topadi. Yozilmasa prof-emis ro'yxati
     * tortilmagunicha universitet nomsiz ko'rinardi.
     */
    fun onUniversitySelected(university: University) {
        _state.update { it.copy(universityId = university.id, selectedUniversity = university) }
        viewModelScope.launch { universityRepository.addUniversity(university) }
    }

    /** Ro'yxat uzun (butun O'zbekiston) — ko'rsatishni 200 taga cheklaymiz. */
    private fun filterUniversities(list: List<University>, query: String): List<University> =
        // `matches` — rasmiy nom, qisqa nom, qisqartma va shahar bo'yicha: talaba "TATU"
        // deb yozganda ham topilishi kerak.
        if (query.isBlank()) list.take(UNIVERSITY_RESULT_LIMIT)
        else list.filter { it.matches(query) }.take(UNIVERSITY_RESULT_LIMIT)

    fun clearError() = _state.update { it.copy(error = null, info = null) }

    /** Tashqi (platforma) oqim xatosini ekranga chiqaradi — masalan Google mavjud emas. */
    fun showAuthError(message: String) = _state.update { it.copy(isLoading = false, error = message) }

    // ------------------------------------------------------------------
    // Kirish
    // ------------------------------------------------------------------

    /** Telefon raqami + parol bilan kirish. */
    fun login() {
        val s = _state.value
        if (s.isLoading) return
        val identifier = s.phoneE164
        if (identifier.isBlank()) {
            _state.update { it.copy(error = "To‘liq 9 xonali raqam kiriting.") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loginUseCase(identifier, s.password)) {
                is Resource.Success -> finishAuthenticated(result.data)
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Google ID token bilan kirish — backend tokenni tekshirib sessiya ochadi. */
    fun signInWithGoogle(idToken: String) {
        // Bu yerda "isLoading bo'lsa qaytish" tekshiruvi BO'LMASLIGI kerak: oqim
        // `startExternalAuth()` dan boshlanadi va u allaqachon isLoading = true qilib
        // qo'yadi, ya'ni tekshiruv har safar ishlab, backendga so'rov umuman ketmaydi.
        // Takroriy bosishdan Credential Manager oynasining o'zi himoya qiladi.
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = loginWithGoogleUseCase(idToken)) {
                is Resource.Success -> finishAuthenticated(result.data)
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Google oqimi boshlanishi bilan spinner yoqiladi (tugma bosildi). */
    fun startExternalAuth() = _state.update { it.copy(isLoading = true, error = null) }

    /** Foydalanuvchi tashqi oqimni bekor qildi — xato ko'rsatilmaydi. */
    fun cancelExternalAuth() = _state.update { it.copy(isLoading = false) }

    // ------------------------------------------------------------------
    // Ro'yxatdan o'tish
    // ------------------------------------------------------------------

    /**
     * Ro'yxatdan o'tishning **birinchi** qadami — raqamga SMS kod so'raydi.
     *
     * ⚠️ Hisob bu yerda YARATILMAYDI. Ilgari teskari edi (avval `register`, keyin kod), va
     * aynan shu zaiflik edi: `phoneNumber` bazada `@unique`, ya'ni begona raqam bilan
     * ro'yxatdan o'tgan odam o'sha raqamning haqiqiy egasini butunlay tashqarida
     * qoldirardi. Endi hisob kod tekshirilgandan keyin — [verifyPhone] da — ochiladi.
     */
    fun register() {
        val s = _state.value
        if (s.isLoading) return
        if (!s.termsAccepted) {
            _state.update { it.copy(error = "Shartlarga rozilik bering.") }
            return
        }
        if (!s.phoneValid) {
            _state.update { it.copy(error = "To‘liq 9 xonali raqam kiriting.") }
            return
        }
        // ⚠️ Parolga klient tomonida cheklov QO'YILMAYDI — qoidani server biladi va
        // `422` ni o'z matni bilan qaytaradi (`error.fields.password`). Klientdagi nusxa
        // qoida o'zgarganda jimgina eskirardi va foydalanuvchi serverda haqiqatan qabul
        // qilinadigan parolni kirita olmay qolardi.
        //
        // Parolni TAKRORLASH ham tekshirilmaydi: ro'yxatdan o'tish ekranida ikkinchi
        // maydon YO'Q (`SignUpScreen` da bitta parol maydoni bor), `passwordConfirm` esa
        // faqat parolni tiklash oqimida to'ldiriladi.
        _state.update { it.copy(isLoading = true, error = null, otp = "", otpPurpose = OtpPurpose.VERIFY_PHONE) }
        viewModelScope.launch { requestRegistrationOtp(navigateToOtp = true) }
    }

    /**
     * Ro'yxat uchun SMS kod so'raydi. Kod ketmasa (SMS xizmati javob bermasa) ham
     * foydalanuvchi tasdiqlash ekraniga o'tadi — u yerda xatoni ko'radi va "Kodni qayta
     * yuborish" bilan urinib ko'radi.
     */
    private suspend fun requestRegistrationOtp(navigateToOtp: Boolean) {
        when (val sent = requestRegistrationOtpUseCase(_state.value.phoneE164)) {
            is Resource.Success -> {
                _state.update { it.copy(isLoading = false) }
                startResendTimer(sent.data.resendCooldownSeconds)
            }
            is Resource.Error -> _state.update { it.copy(isLoading = false, error = sent.message) }
            Resource.Loading -> Unit
        }
        if (navigateToOtp) _events.send(AuthEvent.OtpSent)
    }

    /**
     * Tasdiqlash ekrani — **hisob aynan shu yerda yaratiladi**: kod `register` so'roviga
     * qo'shib yuboriladi va uni server tekshiradi.
     *
     * Kod noto'g'ri bo'lsa (`OTP_INVALID`) hech nima yaratilmaydi va foydalanuvchi shu
     * ekranda qoladi — server xatosi matni ko'rsatiladi (`OTP_EXPIRED` — 410,
     * `OTP_TOO_MANY_ATTEMPTS` — 429 va h.k.).
     *
     * Hisob ochilgach: avval profil (ism/universitet), keyin sessiya — shunda sessiya
     * qatoriga to'ldirilgan profil bilan yoziladi.
     */
    fun verifyPhone() {
        val s = _state.value
        if (s.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = registerUseCase(s.phoneE164, s.password, s.otp)) {
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
                is Resource.Success -> {
                    runCatching { saveProfileUseCase(profileFromState(_state.value)) }
                    when (val done = completeRegistrationUseCase()) {
                        is Resource.Error ->
                            _state.update { it.copy(isLoading = false, error = done.message) }
                        else -> finishRegistered()
                    }
                }
            }
        }
    }

    /**
     * Tasdiqlash ekranidan chiqish.
     *
     * Serverda tozalanadigan narsa odatda YO'Q — hisob kod tekshirilgunicha umuman
     * yaratilmaydi. Bu chaqiruv `register` o'tib, lekin profil saqlash yoki sessiya ochish
     * yiqilgan holat uchun: o'shanda qolgan tokenlar bekor qilinadi.
     */
    fun cancelRegistration() {
        viewModelScope.launch { cancelRegistrationUseCase() }
    }

    // ------------------------------------------------------------------
    // Parolni tiklash
    // ------------------------------------------------------------------

    /**
     * 1-qadam — FAQAT raqam kiritiladi va SMS kod so'raladi.
     *
     * Parol bu qadamda so'ralmaydi: foydalanuvchi avval raqamiga egaligini isbotlaydi
     * (kod), yangi parolni esa undan keyingi ekranda kiritadi. Kirish ekranida yozilgan
     * eski parol qolib ketmasligi uchun maydonlar tozalanadi.
     */
    fun requestPasswordReset(navigateToCode: Boolean = true) {
        val s = _state.value
        if (s.isLoading) return
        if (!s.phoneValid) {
            _state.update { it.copy(error = "To‘liq 9 xonali raqam kiriting.") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null, info = null) }
        viewModelScope.launch {
            when (val result = forgotPasswordUseCase(s.phoneE164)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            otp = "",
                            password = "",
                            passwordConfirm = "",
                            otpPurpose = OtpPurpose.RESET_PASSWORD,
                        )
                    }
                    startResendTimer(DEFAULT_RESEND_SECONDS)
                    if (navigateToCode) _events.send(AuthEvent.ResetCodeSent)
                }
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /**
     * 2-qadam — kod kiritildi, yangi parol ekraniga o'tamiz.
     *
     * Backendda kodni ALOHIDA tekshiradigan endpoint yo'q: `password/reset` raqam + kod +
     * yangi parolni bitta so'rovda kutadi. Shuning uchun bu qadam tarmoqqa bormaydi —
     * kod holatda saqlanib turadi va parol bilan birga [resetPassword] da yuboriladi.
     * Kod noto'g'ri bo'lsa xato o'sha yerda chiqadi va foydalanuvchi orqaga qaytib
     * kodni tuzatishi mumkin.
     */
    fun confirmResetCode() {
        val s = _state.value
        if (s.isLoading || !s.otpValid) return
        _state.update { it.copy(error = null) }
        viewModelScope.launch { _events.send(AuthEvent.ResetCodeEntered) }
    }

    /** 3-qadam — yangi parol o'rnatiladi (`password/reset`: raqam + kod + parol). */
    fun resetPassword() {
        val s = _state.value
        if (s.isLoading) return
        if (s.password.length < MIN_PASSWORD_LENGTH) {
            _state.update { it.copy(error = "Parol kamida $MIN_PASSWORD_LENGTH belgidan iborat bo‘lsin.") }
            return
        }
        if (s.password != s.passwordConfirm) {
            _state.update { it.copy(error = "Parollar mos kelmadi.") }
            return
        }
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = resetPasswordUseCase(s.phoneE164, s.otp, s.password)) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(isLoading = false, otp = "", password = "", passwordConfirm = "")
                    }
                    AppMessageBus.success("Parol yangilandi. Endi yangi parol bilan kiring.")
                    _events.send(AuthEvent.PasswordReset)
                }
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                Resource.Loading -> Unit
            }
        }
    }

    // ------------------------------------------------------------------
    // Kodni qayta yuborish
    // ------------------------------------------------------------------

    fun resendCode() {
        val s = _state.value
        if (s.isLoading || s.resendSeconds > 0) return
        when (s.otpPurpose) {
            // Foydalanuvchi allaqachon kod ekranida — qayta navigatsiya qilinmaydi.
            OtpPurpose.RESET_PASSWORD -> requestPasswordReset(navigateToCode = false)
            OtpPurpose.VERIFY_PHONE -> {
                _state.update { it.copy(isLoading = true, error = null) }
                viewModelScope.launch { requestRegistrationOtp(navigateToOtp = false) }
            }
        }
    }

    /** Backend bergan kutish oralig'i bo'yicha taymer. */
    private fun startResendTimer(seconds: Int) {
        timerJob?.cancel()
        _state.update { it.copy(resendSeconds = seconds.coerceAtLeast(0)) }
        timerJob = viewModelScope.launch {
            while (_state.value.resendSeconds > 0) {
                delay(1000)
                _state.update { it.copy(resendSeconds = (it.resendSeconds - 1).coerceAtLeast(0)) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Profil
    // ------------------------------------------------------------------

    /** Profilni to'ldirish ekrani (1n) — universitet/yil/kursni saqlab, bosh sahifaga o'tadi. */
    fun completeProfile() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val saved = saveProfileUseCase(profileFromState(_state.value))) {
                is Resource.Success -> {
                    _state.update { it.copy(isLoading = false) }
                    _events.send(AuthEvent.ProfileSaved)
                }
                is Resource.Error -> _state.update { it.copy(isLoading = false, error = saved.message) }
                Resource.Loading -> Unit
            }
        }
    }

    /** Ilova faqat talabalar uchun — rol doim `STUDENT` (biznes tomoni alohida ElonUz ilovasida). */
    private fun profileFromState(s: AuthFlowState) = UserProfile(
        firstName = s.firstName.ifBlank { null },
        lastName = s.lastName.ifBlank { null },
        phoneNumber = s.phoneE164.ifBlank { null },
        // Raqam **bog'langan** talabalarga ko'rinadi. Server sukuti — `NOBODY`, ya'ni
        // usiz hech kim hech kimning raqamini ko'rmasdi va profildagi qator hech qachon
        // chizilmasdi. `EVERYONE` esa ataylab tanlanmadi: ochiq raqam spam qo'ng'iroqqa
        // olib keladi, bog'lanish esa ikki tomonlama — ya'ni raqamni faqat foydalanuvchi
        // o'zi qabul qilgan odam ko'radi. Sozlamalar → Maxfiylik'dan istalgan vaqt
        // o'zgartiriladi.
        phoneVisibility = PHONE_VISIBILITY_CONNECTIONS,
        role = ROLE_STUDENT,
        universityId = s.universityId,
        universityEmail = s.universityEmail.ifBlank { null },
        birthYear = s.birthYear,
        courseYear = s.courseYear.apiValue,
        gender = s.gender?.apiValue,
    )

    // ------------------------------------------------------------------

    private suspend fun finishRegistered() {
        _state.update { it.copy(isLoading = false) }
        _events.send(AuthEvent.Registered)
    }

    private suspend fun finishAuthenticated(user: User) {
        _state.update { it.copy(isLoading = false, password = "") }
        _events.send(AuthEvent.Authenticated(user))
    }

    override fun onCleared() {
        timerJob?.cancel()
        super.onCleared()
    }

    private companion object {
        /** Backend profilidagi rol qiymati — bu ilovada boshqa rol yo'q. */
        const val ROLE_STUDENT = "STUDENT"

        /** `UserProfile.phoneVisibility` qiymati — qarang `profileFromState`. */
        const val PHONE_VISIBILITY_CONNECTIONS = "CONNECTIONS"

        const val UNIVERSITY_RESULT_LIMIT = 200

        /** Backend qoidasi: parol kamida 8 belgi. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}

/** SMS kodning uzunligi — backend 6 xonali kod yuboradi. */
const val OTP_CODE_LENGTH = 6

/** Backend cheklovi noma'lum bo'lganda ishlatiladigan standart kutish oralig'i. */
private const val DEFAULT_RESEND_SECONDS = 60
