package dev.feature.auth.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Kirish/ro'yxatdan o'tish ekranlarining matnlari. Sukut qiymatlar — inglizcha. */
data class AuthScreenStrings(
    // Tanishtiruv
    val skip: String = "Skip",
    val onboardingTitle: String = "Every service in one app",
    val onboardingBody: String = "One account instead of 7–10 apps. From meals to classes, from jobs to housing.",
    val next: String = "Next",
    val order: String = "Order",
    val aiTutor: String = "AI Tutor",

    // Kirish
    val welcome: String = "Welcome 👋",
    val welcomeSubtitle: String = "Sign in to your account or register in under a minute.",
    val phoneLabel: String = "Your phone number",
    val passwordLabel: String = "Password",
    val forgotPassword: String = "Forgot your password?",
    val signIn: String = "Sign in",
    val noAccount: String = "Don't have an account?",
    val signUp: String = "Sign up",

    // OTP
    val otpTitle: String = "Verification code",
    val otpAction: String = "Verify",
    val otpHint: (String) -> String = { "Enter the 6-digit code sent to $it." },
    val resendIn: String = "Resend code · ",
    val resend: String = "Resend code",
    val resetCode: String = "Reset code",
    val proceed: String = "Continue",

    // Ro'yxatdan o'tish
    val createAccount: String = "Create account",
    val firstName: String = "First name",
    val lastName: String = "Last name",
    /** Namuna ekanligi ko'rinib tursin — aks holda maydon to'ldirilgandek o'qiladi. */
    val emailHint: String = "e.g. aziz@tuit.uz",
    val continueWithGoogle: String = "Continue with Google",
    val universityEmailNote: String = "University email (optional) — gives you a verified student badge.",
    val terms: String = "Terms of Use",
    val and: String = " and ",
    val privacy: String = "Privacy Policy",
    val agree: String = ".",

    // Parolni tiklash
    val resetPassword: String = "Reset password",
    val resetPasswordBody: String = "Enter your phone number — we'll send a 6-digit code by SMS. " +
        "You'll set the new password after the code is verified.",
    val sendCode: String = "Send code",
    val backToSignIn: String = "Back to sign in",
    val newPassword: String = "New password",
    val newPasswordBody: String = "Your number is verified. Now think of a new password — at least 8 characters.",
    val repeatPassword: String = "Repeat password",
    val passwordsDontMatch: String = "The passwords don't match.",
    val savePassword: String = "Save password",

    // Muvaffaqiyat va profil
    val congrats: String = "Congratulations! 🎉",
    val accountReady: String = "Your account is ready. Now let's fill in your profile.",
    val proceedButton: String = "Continue",
    val aboutYourProfile: String = "About your profile",
    val almostDone: String = "Almost done — step 2 of 2",
    val nameAndSurname: String = "First and last name",
    val firstNameHint: String = "Aziz",
    val lastNameHint: String = "Karimov",
    val university: String = "University",
    val birthYear: String = "Year of birth",
    val courseYear: String = "Which year",
    val gender: String = "Gender (optional)",
    val start: String = "Get started",
    val selectUniversity: String = "Select a university",
    val pickFromList: String = "Pick from the list",
    val cityHint: String = "Tashkent",
    val loadingUpper: String = "LOADING…",
    val loadFailedUpper: String = "COULDN'T LOAD THE LIST",
    val resultsUpper: (Int) -> String = { "$it RESULTS" },
    val select: String = "Select",

    // Xatolar
    val phoneIncomplete: String = "Enter the full 9-digit number.",
    val acceptTerms: String = "Please accept the terms.",
    val passwordTooShort: (Int) -> String = { "The password must be at least $it characters." },
    val passwordUpdated: String = "Password updated.",
    val googleNotConfigured: String = "Signing in with Google isn't set up on this device.",

    // Jins
    val genderMale: String = "Male",
    val genderFemale: String = "Female",
    val master: String = "Mas",
)

private val AuthEn = AuthScreenStrings()

private val AuthRu = AuthScreenStrings(
    skip = "Пропустить",
    onboardingTitle = "Все сервисы в одном приложении",
    onboardingBody = "Один аккаунт вместо 7–10 приложений. От еды до учёбы, от работы до жилья.",
    next = "Далее",
    order = "Заказ",
    aiTutor = "AI-репетитор",

    welcome = "Добро пожаловать 👋",
    welcomeSubtitle = "Войдите в аккаунт или зарегистрируйтесь за минуту.",
    phoneLabel = "Ваш номер телефона",
    passwordLabel = "Пароль",
    forgotPassword = "Забыли пароль?",
    signIn = "Войти",
    noAccount = "Нет аккаунта?",
    signUp = "Зарегистрироваться",

    otpTitle = "Код подтверждения",
    otpAction = "Подтвердить",
    otpHint = { "Введите 6-значный код, отправленный на $it." },
    resendIn = "Отправить код заново · ",
    resend = "Отправить код заново",
    resetCode = "Код восстановления",
    proceed = "Продолжить",

    createAccount = "Создать аккаунт",
    firstName = "Имя",
    lastName = "Фамилия",
    emailHint = "напр. aziz@tuit.uz",
    continueWithGoogle = "Войти через Google",
    universityEmailNote = "Университетская почта (необязательно) — даёт значок подтверждённого студента.",
    terms = "Условиями использования",
    and = " и ",
    privacy = "Политикой конфиденциальности",
    agree = ".",

    resetPassword = "Восстановление пароля",
    resetPasswordBody = "Введите номер телефона — мы отправим 6-значный код по SMS. " +
        "Новый пароль зададите после подтверждения кода.",
    sendCode = "Отправить код",
    backToSignIn = "Вернуться ко входу",
    newPassword = "Новый пароль",
    newPasswordBody = "Номер подтверждён. Теперь придумайте новый пароль — минимум 8 символов.",
    repeatPassword = "Повторите пароль",
    passwordsDontMatch = "Пароли не совпадают.",
    savePassword = "Сохранить пароль",

    congrats = "Поздравляем! 🎉",
    accountReady = "Аккаунт готов. Теперь заполним профиль.",
    proceedButton = "Продолжить",
    aboutYourProfile = "О вашем профиле",
    almostDone = "Почти готово — шаг 2 из 2",
    nameAndSurname = "Имя и фамилия",
    firstNameHint = "Азиз",
    lastNameHint = "Каримов",
    university = "Университет",
    birthYear = "Год рождения",
    courseYear = "Какой курс",
    gender = "Пол (необязательно)",
    start = "Начать",
    selectUniversity = "Выберите университет",
    pickFromList = "Выберите из списка",
    cityHint = "Ташкент",
    loadingUpper = "ЗАГРУЗКА…",
    loadFailedUpper = "НЕ УДАЛОСЬ ЗАГРУЗИТЬ СПИСОК",
    resultsUpper = { "$it РЕЗУЛЬТАТОВ" },
    select = "Выбрать",

    phoneIncomplete = "Введите полный 9-значный номер.",
    acceptTerms = "Примите условия.",
    passwordTooShort = { "Пароль должен содержать не менее $it символов." },
    passwordUpdated = "Пароль обновлён.",
    googleNotConfigured = "Вход через Google не настроен на этом устройстве.",

    genderMale = "Мужской",
    genderFemale = "Женский",
    master = "Маг",
)

private val AuthUz = AuthScreenStrings(
    skip = "O‘tkazib yuborish",
    onboardingTitle = "Hamma xizmat bitta ilovada",
    onboardingBody = "7-10 ta ilova o‘rniga bitta hisob. Ovqatdan darsgacha, ishdan turar joygacha.",
    next = "Keyingi",
    order = "Buyurtma",
    aiTutor = "AI Muallim",

    welcome = "Xush kelibsiz 👋",
    welcomeSubtitle = "Hisobingizga kiring yoki bir daqiqada ro‘yxatdan o‘ting.",
    phoneLabel = "Telefon raqamingiz",
    passwordLabel = "Parol",
    forgotPassword = "Parolni unutdingizmi?",
    signIn = "Kirish",
    noAccount = "Hisobingiz yo‘qmi?",
    signUp = "Ro‘yxatdan o‘tish",

    otpTitle = "Tasdiqlash kodi",
    otpAction = "Tasdiqlash",
    otpHint = { "$it raqamiga yuborilgan 6 xonali kodni kiriting." },
    resendIn = "Kodni qayta yuborish · ",
    resend = "Kodni qayta yuborish",
    resetCode = "Tiklash kodi",
    proceed = "Davom etish",

    createAccount = "Hisob yaratish",
    firstName = "Ism",
    lastName = "Familya",
    emailHint = "masalan, aziz@tuit.uz",
    continueWithGoogle = "Google bilan kirish",
    universityEmailNote = "Universitet emaili (ixtiyoriy) — verified talaba nishoni beradi.",
    terms = "Foydalanish shartlari",
    and = " va ",
    privacy = "Maxfiylik siyosati",
    agree = "ga roziman.",

    resetPassword = "Parolni tiklash",
    resetPasswordBody = "Telefon raqamingizni kiriting — SMS orqali 6 xonali kod yuboramiz. " +
        "Yangi parolni kod tasdiqlangandan keyin belgilaysiz.",
    sendCode = "Kod yuborish",
    backToSignIn = "Kirishga qaytish",
    newPassword = "Yangi parol",
    newPasswordBody = "Raqamingiz tasdiqlandi. Endi yangi parolni o‘ylab toping — kamida 8 belgi.",
    repeatPassword = "Parolni takrorlang",
    passwordsDontMatch = "Parollar mos kelmadi.",
    savePassword = "Parolni saqlash",

    congrats = "Tabriklaymiz! 🎉",
    accountReady = "Hisobingiz tayyor. Endi profilingizni to'ldiramiz.",
    proceedButton = "Davom etish",
    aboutYourProfile = "Profilingiz haqida",
    almostDone = "Deyarli tayyor — 2-qadam / 2",
    nameAndSurname = "Ism va familya",
    firstNameHint = "Aziz",
    lastNameHint = "Karimov",
    university = "Universitet",
    birthYear = "Tug‘ilgan yil",
    courseYear = "Nechanchi kurs",
    gender = "Jins (ixtiyoriy)",
    start = "Boshlash",
    selectUniversity = "Universitetni tanlang",
    pickFromList = "Ro‘yxatdan tanlang",
    cityHint = "Toshkent",
    loadingUpper = "YUKLANMOQDA…",
    loadFailedUpper = "RO‘YXATNI YUKLAB BO‘LMADI",
    resultsUpper = { "$it TA NATIJA" },
    select = "Tanlash",

    phoneIncomplete = "To‘liq 9 xonali raqam kiriting.",
    acceptTerms = "Shartlarga rozilik bering.",
    passwordTooShort = { "Parol kamida $it belgidan iborat bo‘lsin." },
    passwordUpdated = "Parol yangilandi.",
    googleNotConfigured = "Bu qurilmada Google bilan kirish sozlanmagan.",

    genderMale = "Erkak",
    genderFemale = "Ayol",
    master = "Mag",
)

@Composable
@ReadOnlyComposable
internal fun authStrings(): AuthScreenStrings = rememberStrings(AuthEn, AuthRu, AuthUz)

/** ViewModel uchun (Compose'dan tashqarida). */
internal fun authStringsNow(): AuthScreenStrings = AppLocale.pick(AuthEn, AuthRu, AuthUz)
