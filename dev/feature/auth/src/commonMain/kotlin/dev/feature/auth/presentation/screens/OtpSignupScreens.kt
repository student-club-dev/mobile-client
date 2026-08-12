package dev.feature.auth.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.AppScreenScaffold
import dev.core.uikit.components.BackButton
import dev.core.uikit.components.ErrorText
import dev.core.uikit.components.FieldLabel
import dev.core.uikit.components.GlassTextField
import dev.core.uikit.components.HintText
import dev.core.common.format.formatUzPhoneFull
import dev.core.uikit.components.PhonePrefix
import dev.core.uikit.components.PhoneVisualTransformation
import dev.core.uikit.components.PrimaryButton
import dev.core.uikit.components.ScreenSubtitle
import dev.core.uikit.components.ScreenTitle
import dev.feature.auth.presentation.flow.AuthFlowState
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette

// ===========================================================================
// 1g — OTP
// ===========================================================================

/**
 * SMS kod ekrani — ikki oqimda ishlatiladi:
 * - ro'yxatdan keyin **raqamni tasdiqlash** (`otp/verify`) — MAJBURIY: tasdiqlanmaguncha
 *   ilovaga kirilmaydi va profil saqlanmaydi (o'tkazib yuborish yo'q);
 * - parolni tiklashda kodni kiritish (kod yangi parol bilan birga yuboriladi).
 */
@Composable
fun OtpScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    title: String = authStrings().otpTitle,
    confirmLabel: String = authStrings().otpAction,
    palette: AppPalette = appPalette,
) {
    val s = authStrings()
    AppScreenScaffold(
        scroll = true,
        // Tugma scroll maydonidan TASHQARIDA: klaviatura kodni kiritish uchun ochiq
        // turadi va "Tasdiqlash" uning ustida ko'rinib qoladi (ilgari u klaviatura
        // ostida qolib ketardi).
        bottomBar = {
            PrimaryButton(
                confirmLabel,
                onVerify,
                enabled = state.otpValid && !state.isLoading,
                trailingIcon = AppIcons.Check,
            )
            ErrorText(state.error)
        },
    ) {
        BackButton(onBack)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(60.dp)
                .background(Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.14f), palette.primary.copy(alpha = 0.14f))), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.MessageSquare, null, tint = palette.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        ScreenTitle(title)
        Spacer(Modifier.height(6.dp))
        // Raqam jumlada QALIN bo'ladi, lekin uning o'rni tilga qarab o'zgaradi
        // (o'zbekchada boshida, inglizchada oxirida) — shuning uchun tayyor jumlada
        // raqam qidiriladi, jumla qo'lda ikkiga bo'linmaydi.
        val phoneText = formatUzPhoneFull(state.phone.ifEmpty { "901234567" })
        val hint = s.otpHint(phoneText)
        Text(
            buildAnnotatedString {
                val at = hint.indexOf(phoneText)
                val muted = androidx.compose.ui.text.SpanStyle(color = palette.inkMuted)
                val strong = androidx.compose.ui.text.SpanStyle(color = palette.ink, fontWeight = FontWeight.Bold)
                if (at < 0) {
                    withStyle(muted) { append(hint) }
                } else {
                    withStyle(muted) { append(hint.substring(0, at)) }
                    withStyle(strong) { append(phoneText) }
                    withStyle(muted) { append(hint.substring(at + phoneText.length)) }
                }
            },
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, lineHeight = 19.sp),
        )
        Spacer(Modifier.height(22.dp))

        OtpInput(state.otp, vm::onOtpChange, palette)

        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Clock, null, tint = palette.inkFaint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            if (state.resendSeconds > 0) {
                Text(
                    s.resendIn,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint),
                )
                Text(
                    formatTimer(state.resendSeconds),
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
                )
            } else {
                Text(
                    s.resend,
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
                    modifier = Modifier.clickableNoRipple(onResend),
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "0$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun OtpInput(value: String, onValueChange: (String) -> Unit, palette: AppPalette) {
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    val ch = value.getOrNull(i)
                    val focused = i == value.length
                    OtpCell(ch, focused, palette, Modifier.weight(1f))
                }
            }
        },
    )
}

@Composable
private fun OtpCell(ch: Char?, focused: Boolean, palette: AppPalette, modifier: Modifier) {
    val shape = RoundedCornerShape(13.dp)
    val bg = when {
        ch != null -> palette.fieldBg
        focused -> palette.fieldBg
        else -> if (palette.dark) Color.White.copy(alpha = 0.04f) else Color(0xFFF4F2FC)
    }
    val border = when {
        focused -> palette.primary
        ch != null -> palette.primary.copy(alpha = 0.20f)
        else -> palette.border
    }
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused || ch != null) 1.5.dp else 1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ch != null -> Text(ch.toString(), style = TextStyle(fontFamily = AppFontFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
            focused -> Box(Modifier.width(2.dp).height(24.dp).background(palette.primary))
        }
    }
}

// ===========================================================================
// 1h — SIGN UP
// ===========================================================================

@Composable
fun SignUpScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onPickUniversity: () -> Unit,
    onCreate: () -> Unit,
    palette: AppPalette = appPalette,
) {
    val s = authStrings()
    // Qaysi hujjat ochiq — `null` bo'lsa varaq yopiq.
    var openDocument by remember { mutableStateOf<LegalDocument?>(null) }

    AppScreenScaffold(
        scroll = true,
        horizontalPadding = 20,
        topPadding = 54,
        // Rozilik qatori ham, tugma ham mixlangan: forma uzun va klaviatura ochilganda
        // ular ekrandan chiqib ketardi.
        bottomBar = {
            Spacer(Modifier.height(12.dp))
            TermsConsentRow(
                accepted = state.termsAccepted,
                onToggle = vm::toggleTerms,
                onOpenDocument = { openDocument = it },
                palette = palette,
            )
            ErrorText(state.error)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(s.createAccount, onCreate, enabled = state.termsAccepted && !state.isLoading)
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            BackButton(onBack)
            ScreenTitle(s.createAccount, size = 21)
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GlassTextField(state.firstName, vm::onFirstNameChange, s.firstName, Modifier.weight(1f), height = 46)
            GlassTextField(state.lastName, vm::onLastNameChange, s.lastName, Modifier.weight(1f), height = 46)
        }
        Spacer(Modifier.height(9.dp))
        GlassTextField(
            value = state.phone,
            onValueChange = vm::onPhoneChange,
            placeholder = "90 123 45 67",
            leadingContent = { PhonePrefix(palette) },
            height = 46,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneVisualTransformation(),
        )
        Spacer(Modifier.height(9.dp))
        GlassTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            placeholder = "••••••",
            leading = AppIcons.Lock,
            height = 46,
            trailing = {
                Icon(
                    if (state.passwordVisible) AppIcons.EyeOff else AppIcons.Eye,
                    null, tint = palette.inkFaint,
                    modifier = Modifier.size(16.dp).clickableNoRipple { vm.togglePasswordVisible() },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        )
        Spacer(Modifier.height(9.dp))
        UniversitySelectorRow(state.selectedUniversity, onPickUniversity, palette)
        Spacer(Modifier.height(9.dp))

        // Universitet emaili — verified talaba nishonini beradi.
        run {
            val verified = state.universityEmail.endsWith(".uz") && state.universityEmail.contains("@")
            Row(
                Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(13.dp))
                    .background(palette.successBg)
                    .border(1.dp, palette.successDeep.copy(alpha = 0.28f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(AppIcons.ShieldCheck, null, tint = palette.successDeep, modifier = Modifier.size(16.dp))
                Box(Modifier.weight(1f)) {
                    if (state.universityEmail.isEmpty()) {
                        Text(s.emailHint, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint))
                    }
                    BasicTextField(
                        state.universityEmail, vm::onUniversityEmailChange, singleLine = true,
                        textStyle = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.ink),
                        cursorBrush = SolidColor(palette.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (verified) {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(palette.successDeep.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                        Text("TASDIQLANGAN", style = TextStyle(fontFamily = AppFontFamily, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = palette.successDeep))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            HintText(s.universityEmailNote)
        }

        Spacer(Modifier.height(12.dp))
    }

    openDocument?.let { document ->
        LegalDocumentSheet(document = document, onClose = { openDocument = null })
    }
}

/**
 * Rozilik qatori — katakcha + ikkita **bosiladigan** havola.
 *
 * ⚠️ Havolalar `LinkAnnotation` bilan: matn ichida qolgani uchun qator bo'linishi
 * tabiiy ishlaydi va bosish AYNAN so'zning ustida ushlanadi. Qatorning qolgan qismini
 * bosish esa katakchani belgilaydi — ikkalasi bir-biriga xalaqit bermaydi.
 */
@Composable
private fun TermsConsentRow(
    accepted: Boolean,
    onToggle: () -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
    palette: AppPalette,
) {
    val s = authStrings()
    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = palette.primary, fontWeight = FontWeight.Bold),
    )
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(Modifier.clickableNoRipple(onToggle)) { CheckBoxSmall(accepted, palette) }
        Text(
            buildAnnotatedString {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = LegalDocument.TERMS.name,
                        styles = linkStyle,
                        linkInteractionListener = { onOpenDocument(LegalDocument.TERMS) },
                    ),
                ) { append(s.terms) }
                withStyle(SpanStyle(color = palette.label)) { append(s.and) }
                withLink(
                    LinkAnnotation.Clickable(
                        tag = LegalDocument.PRIVACY.name,
                        styles = linkStyle,
                        linkInteractionListener = { onOpenDocument(LegalDocument.PRIVACY) },
                    ),
                ) { append(s.privacy) }
                withStyle(SpanStyle(color = palette.label)) { append(s.agree) }
            },
            style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, lineHeight = 16.sp),
        )
    }
}

// ===========================================================================
// 1i — FORGOT PASSWORD
// ===========================================================================

@Composable
fun ForgotPasswordScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onSend: () -> Unit,
    onBackToLogin: () -> Unit,
    palette: AppPalette = appPalette,
) {
    val s = authStrings()
    AppScreenScaffold(scroll = true) {
        BackButton(onBack)
        Spacer(Modifier.height(40.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp)
                    .background(Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.14f), palette.primary.copy(alpha = 0.14f))), RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(64.dp).background(palette.primaryBrush, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Lock, null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(Modifier.height(22.dp))
            ScreenTitle(s.resetPassword, size = 23)
            Spacer(Modifier.height(8.dp))
            Text(
                s.resetPasswordBody,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        FieldLabel(s.phoneLabel)
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.phone,
            onValueChange = vm::onPhoneChange,
            placeholder = "90 123 45 67",
            leadingContent = { PhonePrefix(palette) },
            focused = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            visualTransformation = PhoneVisualTransformation(),
            textLetterSpacing = 0.5f,
        )

        Spacer(Modifier.height(18.dp))
        PrimaryButton(s.sendCode, onSend, enabled = state.forgotReady && !state.isLoading)

        state.info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.successDeep, lineHeight = 17.sp))
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = Color(0xFFDC2626), lineHeight = 17.sp))
        }

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth().clickableNoRipple(onBackToLogin), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.ArrowLeft, null, tint = palette.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(s.backToSignIn, style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = palette.primary))
        }
    }
}

// ===========================================================================
// 1j — YANGI PAROL (kod tasdiqlangandan keyin)
// ===========================================================================

/**
 * Parolni tiklashning oxirgi qadami — yangi parol shu yerda so'raladi.
 *
 * Raqam va SMS kod allaqachon holatda: [onSave] `password/reset` ga uchalasini birga
 * yuboradi. Kod noto'g'ri bo'lsa xato shu ekranda ko'rinadi va foydalanuvchi orqaga
 * qaytib kodni tuzatishi mumkin.
 */
@Composable
fun NewPasswordScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
    palette: AppPalette = appPalette,
) {
    val s = authStrings()
    AppScreenScaffold(scroll = true) {
        BackButton(onBack)
        Spacer(Modifier.height(30.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(84.dp)
                    .background(palette.primary.copy(alpha = 0.14f), RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(56.dp).background(palette.primaryBrush, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ShieldCheck, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            ScreenTitle(s.newPassword, size = 23)
            Spacer(Modifier.height(8.dp))
            Text(
                s.newPasswordBody,
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 13.sp, color = palette.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 19.sp),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        FieldLabel(s.newPassword)
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.password,
            onValueChange = vm::onPasswordChange,
            placeholder = "••••••••",
            leading = AppIcons.Lock,
            focused = true,
            trailing = {
                Icon(
                    if (state.passwordVisible) AppIcons.EyeOff else AppIcons.Eye,
                    null, tint = palette.inkFaint,
                    modifier = Modifier.size(18.dp).clickableNoRipple { vm.togglePasswordVisible() },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (state.passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
        )

        Spacer(Modifier.height(13.dp))
        FieldLabel(s.repeatPassword)
        Spacer(Modifier.height(7.dp))
        GlassTextField(
            value = state.passwordConfirm,
            onValueChange = vm::onPasswordConfirmChange,
            placeholder = "••••••••",
            leading = AppIcons.Lock,
            trailing = {
                Icon(
                    if (state.passwordVisible) AppIcons.EyeOff else AppIcons.Eye,
                    null, tint = palette.inkFaint,
                    modifier = Modifier.size(18.dp).clickableNoRipple { vm.togglePasswordVisible() },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (state.passwordVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
        )

        // Mos kelmagani darhol ko'rinadi — tugmani bosib ko'rishni kutmasdan.
        if (state.passwordMismatch) ErrorText(s.passwordsDontMatch)

        Spacer(Modifier.height(18.dp))
        PrimaryButton(
            s.savePassword, onSave,
            enabled = state.newPasswordReady && !state.isLoading,
            trailingIcon = AppIcons.Check,
        )

        ErrorText(state.error)

        Spacer(Modifier.height(20.dp))
    }
}

