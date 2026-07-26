package dev.feature.auth.presentation.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.core.uikit.components.AuthTab
import dev.feature.auth.biometric.BiometricOutcome
import dev.feature.auth.biometric.rememberBiometricAuthenticator
import dev.feature.auth.oauth.GoogleSignInResult
import dev.feature.auth.oauth.rememberGoogleSignIn
import dev.feature.auth.presentation.screens.ForgotPasswordScreen
import dev.feature.auth.presentation.screens.NewPasswordScreen
import dev.feature.auth.presentation.screens.OnboardingScreen
import dev.feature.auth.presentation.screens.OtpScreen
import dev.feature.auth.presentation.screens.ProfileScreen
import dev.feature.auth.presentation.screens.RoleChoiceScreen
import dev.feature.auth.presentation.screens.SignUpScreen
import dev.feature.auth.presentation.screens.SuccessScreen
import dev.feature.auth.presentation.screens.UniversityPickerScreen
import dev.feature.auth.presentation.screens.WelcomeScreen
import dev.feature.business.BusinessProfileScreen
import dev.feature.business.BusinessShell
import dev.feature.settings.presentation.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private object Route {
    const val ONBOARDING = "onboarding"
    const val ROLE = "role"

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

    /** Parolni tiklash: yangi parol (kod allaqachon holatda). */
    const val NEW_PASSWORD = "new_password"

    /** Biznesmen uchun alohida profil ekrani. */
    const val BUSINESS_PROFILE = "business_profile"

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
fun AuthNavHost(
    flow: AuthUserFlow? = null,
    onExit: (() -> Unit)? = null,
    vm: AuthFlowViewModel = koinViewModel(),
) {
    // Rol-scoped oqim (Android'dagi StudentActivity/BusinessActivity) — rolni darrov o'rnatamiz,
    // shunda ro'yxatdan o'tishda to'g'ri rol saqlanadi va rol tanlash ekrani o'tkazib yuboriladi.
    LaunchedEffect(flow) { if (flow != null) vm.onRoleChange(flow.role) }

    // Local keshdagi sessiyani tekshiramiz: kirgan bo'lsa to'g'ridan-to'g'ri HOME.
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    // Splash animatsiyasi va sessiya keshini o'qish PARALLEL ketadi — grafga faqat ikkalasi
    // tayyor bo'lganda o'tiladi.
    var splashShown by rememberSaveable { mutableStateOf(false) }
    if (!splashShown || loggedIn == null) {
        AnimatedSplashScreen(onFinished = { splashShown = true })
        return
    }
    val startDestination = when {
        loggedIn == true -> Route.HOME
        flow != null -> Route.WELCOME
        else -> Route.ONBOARDING
    }

    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    var welcomeTab by remember { mutableStateOf(AuthTab.PHONE) }

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
                    vm.showAuthError("Bu qurilmada Google bilan kirish sozlanmagan.")
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
                // Ro'yxat yakunlandi — biznesmen biznes profiliga, talaba Success/Profile'ga.
                AuthEvent.Registered ->
                    if (vm.state.value.role == Role.BUSINESS) {
                        nav.navigate(Route.BUSINESS_PROFILE) { popUpTo(Route.SIGNUP) }
                    } else {
                        nav.navigate(Route.SUCCESS) { popUpTo(Route.WELCOME) }
                    }
                AuthEvent.ResetCodeSent -> nav.navigate(Route.RESET_CODE) { launchSingleTop = true }
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
            val afterOnboarding = { if (flow == null) nav.navigate(Route.ROLE) else nav.navigate(Route.WELCOME) }
            OnboardingScreen(onNext = afterOnboarding, onSkip = afterOnboarding)
        }

        composable(Route.ROLE) {
            // Login'dan oldin rol tanlash — rol profilga yoziladi va ildiz router shundan o'qiydi.
            RoleChoiceScreen(
                onPick = { role ->
                    vm.onRoleChange(role)
                    nav.navigate(Route.WELCOME)
                },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.WELCOME) {
            val biometric = rememberBiometricAuthenticator()
            val bioScope = rememberCoroutineScope()
            WelcomeScreen(
                state = state,
                vm = vm,
                tab = welcomeTab,
                onTab = {
                    welcomeTab = it
                    vm.onLoginWithEmailChange(it == AuthTab.EMAIL)
                },
                onLogin = vm::login,
                onForgot = { nav.navigate(Route.FORGOT) { launchSingleTop = true } },
                onSignUp = { nav.navigate(Route.SIGNUP) { launchSingleTop = true } },
                onGoogle = onGoogle,
                onBiometric = {
                    if (!biometric.canAuthenticate()) {
                        vm.biometricError("Qurilmada biometrika sozlanmagan (Face ID / barmoq izi).")
                    } else {
                        bioScope.launch {
                            when (biometric.authenticate("Kirish", "Face ID bilan tasdiqlang", "Bekor qilish")) {
                                BiometricOutcome.SUCCESS -> vm.onBiometricAuthenticated()
                                BiometricOutcome.FAILED -> vm.biometricError("Biometrika tanilmadi. Qayta urinib ko'ring.")
                                BiometricOutcome.UNAVAILABLE -> vm.biometricError("Biometrika mavjud emas.")
                                BiometricOutcome.CANCELLED -> Unit
                            }
                        }
                    }
                },
            )
        }

        composable(Route.SIGNUP) {
            SignUpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onCreate = vm::register,
            )
        }

        composable(Route.VERIFY_PHONE) {
            OtpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onVerify = vm::verifyPhone,
                onResend = vm::resendCode,
                onSkip = vm::skipPhoneVerification,
            )
        }

        composable(Route.FORGOT) {
            ForgotPasswordScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onSend = vm::requestPasswordReset,
                onBackToLogin = { nav.popBackStack() },
            )
        }

        // Tiklash ikki qadamga bo'lingan: avval kod, keyin parol — aks holda bitta ekranda
        // 6 raqam va ikki marta parol yozguncha kod eskirib qolardi.
        composable(Route.RESET_CODE) {
            OtpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                title = "Tiklash kodi",
                confirmLabel = "Davom etish",
                onVerify = {
                    // Kod hali serverga yuborilmaydi — u yangi parol bilan birga ketadi
                    // (`password/reset` uchalasini bitta so'rovda kutadi).
                    vm.clearError()
                    nav.navigate(Route.NEW_PASSWORD) { launchSingleTop = true }
                },
                onResend = vm::resendCode,
            )
        }

        composable(Route.NEW_PASSWORD) {
            NewPasswordScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onSubmit = vm::resetPassword,
            )
        }

        composable(Route.BUSINESS_PROFILE) {
            BusinessProfileScreen(
                businessName = state.businessName,
                businessType = state.businessType,
                error = state.error,
                isLoading = state.isLoading,
                onNameChange = vm::onBusinessNameChange,
                onTypeChange = vm::onBusinessTypeChange,
                onBack = { nav.popBackStack() },
                onStart = vm::completeProfile,
            )
        }

        composable(Route.SUCCESS) {
            SuccessScreen(state = state, vm = vm, onContinue = { nav.navigate(Route.PROFILE) })
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
            // Chiqish: Activity oqimida — ildiz router'ga qaytamiz (onExit); aks holda (iOS)
            // kirish ekraniga.
            val loggedOut: () -> Unit = onExit ?: {
                nav.navigate(Route.WELCOME) {
                    popUpTo(Route.HOME) { inclusive = true }
                    launchSingleTop = true
                }
            }
            when (flow) {
                AuthUserFlow.BUSINESS -> BusinessShell(
                    onLoggedOut = loggedOut,
                    settingsContent = { onBack ->
                        SettingsScreen(onBack = onBack, onEditProfile = {}, onLoggedOut = loggedOut)
                    },
                )
                AuthUserFlow.STUDENT -> dev.feature.auth.presentation.main.StudentShell(onLoggedOut = loggedOut)
                // iOS / bo'linmagan rejim — rolga qarab RootShell hal qiladi.
                null -> dev.feature.auth.presentation.main.RootShell(onLoggedOut = loggedOut)
            }
        }
    }
}
