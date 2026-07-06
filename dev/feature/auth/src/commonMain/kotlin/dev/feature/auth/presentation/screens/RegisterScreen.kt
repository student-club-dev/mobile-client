package dev.feature.auth.presentation.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.AuthScreenScaffold
import dev.feature.auth.presentation.components.BackButton
import dev.feature.auth.presentation.components.ErrorText
import dev.feature.auth.presentation.components.FieldLabel
import dev.feature.auth.presentation.components.FooterLink
import dev.feature.auth.presentation.components.GlassTextField
import dev.feature.auth.presentation.components.HintText
import dev.feature.auth.presentation.components.LogoTile
import dev.feature.auth.presentation.components.PrimaryButton
import dev.feature.auth.presentation.components.ScreenSubtitle
import dev.feature.auth.presentation.components.ScreenTitle
import dev.feature.auth.presentation.flow.AuthFlowState
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette

/**
 * Email + parol bilan ro'yxatdan o'tish. Firebase `createUserWithEmailAndPassword`.
 * Muvaffaqiyatdan so'ng → rol tanlash (Success) → profil → home.
 */
@Composable
fun RegisterScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onSignIn: () -> Unit,
    palette: AuthPalette = authPalette,
) {
    val pwVisual = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation()
    AuthScreenScaffold(scroll = true) {
        BackButton(onBack)
        Spacer(Modifier.height(18.dp))
        LogoTile(size = 48, radius = 15, iconSize = 26)
        Spacer(Modifier.height(16.dp))
        ScreenTitle("Hisob yaratish")
        Spacer(Modifier.height(6.dp))
        ScreenSubtitle("Email va parol bilan bir daqiqada ro‘yxatdan o‘ting.")
        Spacer(Modifier.height(18.dp))

        FieldLabel("Email manzil")
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.email,
            onValueChange = vm::onEmailChange,
            placeholder = "aziz.karimov@edu.uz",
            leading = AuthIcons.Mail,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(13.dp))

        FieldLabel("Parol")
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            placeholder = "Kamida 6 belgi",
            leading = AuthIcons.Lock,
            trailing = {
                Icon(
                    if (state.passwordVisible) AuthIcons.EyeOff else AuthIcons.Eye,
                    null, tint = palette.inkFaint,
                    modifier = Modifier.size(18.dp).clip(RoundedCornerShape(6.dp))
                        .clickableNoRipple { vm.togglePasswordVisible() },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = pwVisual,
        )
        Spacer(Modifier.height(13.dp))

        FieldLabel("Parolni tasdiqlang")
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.confirmPassword,
            onValueChange = vm::onConfirmPasswordChange,
            placeholder = "Parolni qayta kiriting",
            leading = AuthIcons.Lock,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = pwVisual,
        )
        Spacer(Modifier.height(8.dp))
        HintText("Ro‘yxatdan o‘tgach rol va profil ma‘lumotlarini to‘ldirasiz.")

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            "Hisob yaratish",
            onCreate,
            enabled = !state.isLoading,
            trailingIcon = AuthIcons.ArrowRight,
        )

        ErrorText(state.error)

        Spacer(Modifier.height(20.dp))
        FooterLink("Hisobingiz bormi?", "Kirish", onSignIn)
    }
}
