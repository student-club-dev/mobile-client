package dev.feature.auth.presentation.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.core.navigation.PopEnter
import dev.core.navigation.PopExit
import dev.core.navigation.PushEnter
import dev.core.navigation.PushExit
import dev.core.uikit.components.AnimatedSplashScreen
import dev.feature.auth.oauth.GoogleSignInResult
import dev.feature.auth.oauth.rememberGoogleSignIn
import dev.feature.auth.presentation.screens.ForgotPasswordScreen
import dev.feature.auth.presentation.screens.NewPasswordScreen
import dev.feature.auth.presentation.screens.OnboardingScreen
import dev.feature.auth.presentation.screens.OtpScreen
import dev.feature.auth.presentation.screens.ProfileScreen
import dev.feature.auth.presentation.screens.SignUpScreen
import dev.feature.auth.presentation.screens.SuccessScreen
import dev.feature.auth.presentation.screens.UniversityPickerScreen
import dev.feature.auth.presentation.main.StudentShell
import dev.feature.auth.presentation.screens.WelcomeScreen
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import dev.feature.auth.presentation.screens.authStringsNow
import dev.feature.auth.presentation.screens.authStrings

private object Route {
    const val ONBOARDING = "onboarding"

    /** Kirish — telefon/email + parol yoki Google (oqimning asosiy ekrani). */
    const val WELCOME = "welcome"

    /** Ro'yxatdan o'tish — telefon + parol + profil ma'lumotlari. */
    const val SIGNUP = "signup"

    /** SMS kod — ro'yxatdan keyin raqamni tasdiqlash. */
    const val VERIFY_PHONE = "verify_phone"

    /** Parolni tiklash: raqam kiritish. */
    const val FORGOT = "forgot"

    /** Parolni tiklash: SMS kodni kiritish. */
    const val RESET_CODE = "reset_code"

    /** Parolni tiklash: yangi parol (kod kiritilgandan KEYIN). */
    const val NEW_PASSWORD = "new_password"

    const val SUCCESS = "success"
    const val PROFILE = "profile"
    const val UNIVERSITY = "university"
    const val HOME = "home"
}

/**
 * Auth oqimining butun navigatsiya grafi.
 *
 * Backend (`/v1/auth/student/…`) modeliga mos:
 * - **kirish** — telefon yoki email + parol, yoxud Google ID token;
 * - **ro'yxat** — telefon + parol → hisob darhol ochiladi → SMS kod bilan raqam tasdiqlanadi
 *   (o'tkazib yuborish mumkin) → profilni to'ldirish;
 * - **parolni tiklash** — raqam → SMS kod → yangi parol.
 *
 * commonMain'da yashaydi, shu bois Android va iOS'da bir xil ishlaydi.
 */
@Composable
fun AuthNavHost(vm: AuthFlowViewModel = koinViewModel()) {
    // Local keshdagi sessiyani tekshiramiz: kirgan bo'lsa to'g'ridan-to'g'ri HOME.
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    // Tanishtiruv avval ko'rilganmi — chiqishdan keyin unga qaytmaslik uchun.
    val onboardingSeen by vm.onboardingSeen.collectAsStateWithLifecycle()
    // Splash animatsiyasi va local bayroqlarni o'qish PARALLEL ketadi — grafga faqat hammasi
    // tayyor bo'lganda o'tiladi.
    var splashShown by rememberSaveable { mutableStateOf(false) }
    if (!splashShown || loggedIn == null || onboardingSeen == null) {
        AnimatedSplashScreen(onFinished = { splashShown = true })
        return
    }
    // Chiqishdan keyin "0 dan" kirish ekrani: tanishtiruv faqat ilk ochilishda ko'rsatiladi.
    //
    // ⚠️ **Bir marta** hisoblanadi va keyin o'zgarmaydi (`rememberSaveable`). Bu qiymat
    // faqat "qayerdan boshlanamiz" degan savolga javob beradi; keyingi o'tishlarni
    // navigatsiyaning o'zi bajaradi.
    //
    // Nega shunday: `NavHost` grafni `remember(startDestination)` bilan quradi, ya'ni bu
    // qiymat o'zgarsa graf QAYTA quriladi va `navController.graph = …` butun back stack'ni
    // boshidan tiklaydi. Kirish paytida esa qiymat ALBATTA o'zgaradi: `loggedIn` local
    // sessiya bazasidan kelib, `false` dan `true` ga sakraydi. Natijada HOME ikki marta
    // ochilardi — avval `AuthEvent.Authenticated` bilan, so'ng grafning qayta qurilishi
    // bilan. Ikkinchisi `StudentShell` ni noldan quradi (ma'lumot qayta tortiladi, ekran
    // bir lahza bo'shab qotadi).
    val startDestination = rememberSaveable {
        when {
            loggedIn == true -> Route.HOME
            onboardingSeen == true -> Route.WELCOME
            else -> Route.ONBOARDING
        }
    }

    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()

    // Google Sign-In — platformaga xos (Android: Credential Manager). Natija — backend
    // tekshiradigan ID token.
    val googleSignIn = rememberGoogleSignIn()
    val scope = rememberCoroutineScope()
    val onGoogle: () -> Unit = {
        vm.startExternalAuth()
        scope.launch {
            when (val result = googleSignIn.signIn()) {
                is GoogleSignInResult.Success -> vm.signInWithGoogle(result.idToken)
                is GoogleSignInResult.Failed -> vm.showAuthError(result.message)
                GoogleSignInResult.Unavailable ->
                    vm.showAuthError(authStringsNow().googleNotConfigured)
                // Foydalanuvchi o'zi bekor qildi — xato ko'rsatilmaydi.
                GoogleSignInResult.Cancelled -> vm.cancelExternalAuth()
            }
        }
    }

    // Bir martalik hodisalar navigatsiyani boshqaradi (async auth natijalari).
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                // Kod ketdi — tasdiqlash ekraniga.
                AuthEvent.OtpSent -> nav.navigate(Route.VERIFY_PHONE) { launchSingleTop = true }
                // Ro'yxat yakunlandi — tabrik ekrani, so'ng profilni to'ldirish.
                AuthEvent.Registered -> nav.navigate(Route.SUCCESS) { popUpTo(Route.WELCOME) }
                AuthEvent.ResetCodeSent -> nav.navigate(Route.RESET_CODE) { launchSingleTop = true }
                // Kod kiritildi — yangi parol ekraniga (so'rov hali ketmagan).
                AuthEvent.ResetCodeEntered -> nav.navigate(Route.NEW_PASSWORD) { launchSingleTop = true }
                // Parol yangilandi — kirish ekraniga qaytamiz (yangi parol bilan kiradi).
                AuthEvent.PasswordReset -> nav.navigate(Route.WELCOME) {
                    popUpTo(Route.WELCOME) { inclusive = true }
                    launchSingleTop = true
                }
                AuthEvent.ProfileSaved -> nav.navigate(Route.HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
                is AuthEvent.Authenticated -> nav.navigate(Route.HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = PushEnter,
        exitTransition = PushExit,
        popEnterTransition = PopEnter,
        popExitTransition = PopExit,
    ) {
        composable(Route.ONBOARDING) {
            val afterOnboarding = {
                vm.markOnboardingSeen()
                nav.navigate(Route.WELCOME) { popUpTo(Route.ONBOARDING) { inclusive = true } }
            }
            OnboardingScreen(onNext = afterOnboarding, onSkip = afterOnboarding)
        }

        composable(Route.WELCOME) {
            WelcomeScreen(
                state = state,
                vm = vm,
                onLogin = vm::login,
                onForgot = { nav.navigate(Route.FORGOT) { launchSingleTop = true } },
                onSignUp = { nav.navigate(Route.SIGNUP) { launchSingleTop = true } },
                onGoogle = onGoogle,
            )
        }

        composable(Route.SIGNUP) {
            SignUpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onPickUniversity = { nav.navigate(Route.UNIVERSITY) },
                onCreate = vm::register,
            )
        }

        composable(Route.VERIFY_PHONE) {
            OtpScreen(
                state = state, vm = vm,
                // Orqaga qaytish = tasdiqlanmagan ro'yxatni bekor qilish (tokenlar o'chadi).
                onBack = {
                    vm.cancelRegistration()
                    nav.popBackStack()
                },
                onVerify = vm::verifyPhone,
                onResend = vm::resendCode,
            )
        }

        composable(Route.FORGOT) {
            ForgotPasswordScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onSend = { vm.requestPasswordReset() },
                onBackToLogin = { nav.popBackStack() },
            )
        }

        // Kod bu yerda faqat KIRITILADI — backendda uni alohida tekshiradigan endpoint yo'q.
        // Tasdiqlash yangi parol bilan birga (`password/reset`) keyingi ekranda ketadi.
        composable(Route.RESET_CODE) {
            OtpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                title = authStrings().resetCode,
                confirmLabel = authStrings().proceed,
                onVerify = vm::confirmResetCode,
                onResend = vm::resendCode,
            )
        }

        composable(Route.NEW_PASSWORD) {
            NewPasswordScreen(
                state = state, vm = vm,
                // Orqaga — kod ekraniga: kod noto'g'ri bo'lsa uni tuzatib qaytadi.
                onBack = { nav.popBackStack() },
                onSave = vm::resetPassword,
            )
        }

        composable(Route.SUCCESS) {
            SuccessScreen(onContinue = { nav.navigate(Route.PROFILE) })
        }

        composable(Route.PROFILE) {
            ProfileScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onPickUniversity = { nav.navigate(Route.UNIVERSITY) },
                onStart = vm::completeProfile,
            )
        }

        composable(Route.UNIVERSITY) {
            UniversityPickerScreen(
                state = state, vm = vm,
                onClose = { nav.popBackStack() },
                onSelectDone = { nav.popBackStack() },
            )
        }

        composable(Route.HOME) {
            // Chiqish — grafning O'ZIDA kirish ekraniga qaytamiz va butun stack'ni tozalaymiz.
            //
            // MUHIM: bu yerda Activity'ni `recreate()` qilish MUMKIN EMAS. `rememberNavController()`
            // back stack'ni saqlab qo'yadi va recreate'dan keyin uni TIKLAYDI — ustida hamon HOME
            // turadi, ya'ni `startDestination` (WELCOME) e'tiborga olinmaydi va foydalanuvchi
            // chiqqan bo'lsa ham bosh ekranda qolib ketardi.
            val loggedOut: () -> Unit = {
                nav.navigate(Route.WELCOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            StudentShell(onLoggedOut = loggedOut)
        }
    }
}
