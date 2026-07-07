package dev.feature.auth.presentation.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.AuthScreenScaffold
import dev.feature.auth.presentation.components.AuthTab
import dev.feature.auth.presentation.components.LogoTile
import dev.feature.auth.presentation.screens.EmailLoginScreen
import dev.feature.auth.presentation.screens.ForgotPasswordScreen
import dev.feature.auth.presentation.screens.OnboardingScreen
import dev.feature.auth.presentation.screens.OtpScreen
import dev.feature.auth.presentation.screens.PhoneScreen
import dev.feature.auth.presentation.screens.EmailVerifyScreen
import dev.feature.auth.presentation.screens.ProfileScreen
import dev.feature.auth.presentation.screens.RegisterChoiceScreen
import dev.feature.auth.presentation.screens.RegisterScreen
import dev.feature.auth.presentation.screens.SignUpScreen
import dev.feature.auth.presentation.screens.SuccessScreen
import dev.feature.auth.presentation.screens.UniversityPickerScreen
import dev.feature.auth.presentation.screens.WelcomeScreen
import dev.feature.auth.presentation.theme.authPalette
import dev.feature.auth.social.rememberSocialAuthController
import org.koin.compose.viewmodel.koinViewModel

private object Route {
    const val ONBOARDING = "onboarding"
    const val WELCOME = "welcome"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val OTP = "otp"
    const val SIGNUP = "signup"
    const val REGISTER_CHOICE = "register_choice"
    const val REGISTER = "register"
    const val VERIFY_EMAIL = "verify_email"
    const val FORGOT = "forgot"
    const val SUCCESS = "success"
    const val PROFILE = "profile"
    const val UNIVERSITY = "university"
    const val HOME = "home"
}

/**
 * Auth oqimining butun navigatsiya grafi — barcha dizayn ekranlari.
 * commonMain'da yashaydi, shu bois Android va iOS'da bir xil ishlaydi.
 */
@Composable
fun AuthNavHost(vm: AuthFlowViewModel = koinViewModel()) {
    // Local keshdagi sessiyani tekshiramiz: kirgan bo'lsa to'g'ridan-to'g'ri HOME.
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    if (loggedIn == null) {
        BootSplash() // kesh o'qilmagунча qisqa splash
        return
    }
    val startDestination = if (loggedIn == true) Route.HOME else Route.ONBOARDING

    val nav = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()
    val socialAuth = rememberSocialAuthController()
    var welcomeTab by remember { mutableStateOf(AuthTab.PHONE) }

    // Bir martalik hodisalar navigatsiyani boshqaradi (async auth natijalari).
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                AuthEvent.OtpSent -> nav.navigate(Route.OTP)
                // Kod ishlatildi — orqaga qaytib bekor bo'lgan OTP ekraniga tushmasin.
                AuthEvent.OtpVerified -> nav.navigate(Route.SIGNUP) {
                    popUpTo(Route.OTP) { inclusive = true }
                }
                AuthEvent.EmailVerificationSent -> nav.navigate(Route.VERIFY_EMAIL)
                // Hisob yaratildi — orqaga qaytib ro'yxat formasini qayta yubormasin.
                AuthEvent.Registered -> nav.navigate(Route.SUCCESS) {
                    popUpTo(Route.WELCOME)
                }
                AuthEvent.ProfileSaved,
                is AuthEvent.Authenticated -> nav.navigate(Route.HOME) {
                    popUpTo(Route.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Route.ONBOARDING) {
            OnboardingScreen(
                onNext = { nav.navigate(Route.WELCOME) },
                onSkip = { nav.navigate(Route.WELCOME) },
            )
        }
        composable(Route.WELCOME) {
            WelcomeScreen(
                state = state, vm = vm, tab = welcomeTab, onTab = { welcomeTab = it },
                onContinue = {
                    if (welcomeTab == AuthTab.PHONE) vm.sendOtp(socialAuth)
                    else nav.navigate(Route.EMAIL)
                },
                onSignUp = { nav.navigate(Route.REGISTER_CHOICE) },
                onGoogle = { vm.signInWithGoogle(socialAuth) },
                onApple = { vm.signInWithApple(socialAuth) },
                onTelegram = { vm.signInWithTelegram(socialAuth) },
            )
        }
        composable(Route.PHONE) {
            PhoneScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onSwitchEmail = { nav.navigate(Route.EMAIL) },
                onGetCode = { vm.sendOtp(socialAuth) },
                onSignIn = { nav.navigate(Route.EMAIL) },
                onGoogle = { vm.signInWithGoogle(socialAuth) },
                onApple = { vm.signInWithApple(socialAuth) },
                onTelegram = { vm.signInWithTelegram(socialAuth) },
            )
        }
        composable(Route.EMAIL) {
            EmailLoginScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onSwitchPhone = { nav.navigate(Route.PHONE) },
                onLogin = { vm.login() },
                onForgot = { nav.navigate(Route.FORGOT) },
                onBiometric = { vm.notSupported("Face ID") },
                onSignUp = { nav.navigate(Route.REGISTER_CHOICE) },
            )
        }
        composable(Route.OTP) {
            OtpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onVerify = { vm.confirmOtp(socialAuth) },
                onResend = { vm.resend(socialAuth) },
                onTelegram = { vm.signInWithTelegram(socialAuth) },
            )
        }
        composable(Route.SIGNUP) {
            SignUpScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onCreate = { vm.register() },
            )
        }
        composable(Route.REGISTER_CHOICE) {
            RegisterChoiceScreen(
                onBack = { nav.popBackStack() },
                onPhone = { nav.navigate(Route.PHONE) },
                onEmail = { nav.navigate(Route.REGISTER) },
                onSignIn = { nav.navigate(Route.EMAIL) },
            )
        }
        composable(Route.REGISTER) {
            RegisterScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onCreate = { vm.registerWithEmail() },
                onSignIn = { nav.navigate(Route.EMAIL) },
            )
        }
        composable(Route.VERIFY_EMAIL) {
            EmailVerifyScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onVerify = { vm.verifyEmailCode() },
                onResend = { vm.resendEmailCode() },
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
        composable(Route.SUCCESS) {
            SuccessScreen(
                state = state, vm = vm,
                onContinue = { nav.navigate(Route.PROFILE) },
            )
        }
        composable(Route.PROFILE) {
            ProfileScreen(
                state = state, vm = vm,
                onBack = { nav.popBackStack() },
                onPickUniversity = { nav.navigate(Route.UNIVERSITY) },
                onStart = { vm.completeProfile() },
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
            dev.feature.auth.presentation.main.MainShell(
                onLoggedOut = {
                    nav.navigate(Route.WELCOME) {
                        popUpTo(Route.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/** Kesh o'qilguncha ko'rsatiladigan qisqa boshlang'ich ekran (session restore). */
@Composable
private fun BootSplash() {
    AuthScreenScaffold {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LogoTile(size = 72, radius = 22, iconSize = 38)
        }
    }
}

