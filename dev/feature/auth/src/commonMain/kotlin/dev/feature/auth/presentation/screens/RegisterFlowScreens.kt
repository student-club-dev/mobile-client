package dev.feature.auth.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.AuthScreenScaffold
import dev.feature.auth.presentation.components.BackButton
import dev.feature.auth.presentation.components.ErrorText
import dev.feature.auth.presentation.components.FooterLink
import dev.feature.auth.presentation.components.LogoTile
import dev.feature.auth.presentation.components.OutlineButton
import dev.feature.auth.presentation.components.PrimaryButton
import dev.feature.auth.presentation.components.ScreenSubtitle
import dev.feature.auth.presentation.components.ScreenTitle
import dev.feature.auth.presentation.flow.AuthFlowState
import dev.feature.auth.presentation.flow.AuthFlowViewModel
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette

// ===========================================================================
// Ro'yxatdan o'tish — usul tanlash (Telefon / Email)
// ===========================================================================

@Composable
fun RegisterChoiceScreen(
    onBack: () -> Unit,
    onPhone: () -> Unit,
    onEmail: () -> Unit,
    onSignIn: () -> Unit,
    palette: AuthPalette = authPalette,
) {
    AuthScreenScaffold(scroll = false) {
        BackButton(onBack)
        Spacer(Modifier.height(24.dp))
        LogoTile(size = 56, radius = 17, iconSize = 30)
        Spacer(Modifier.height(18.dp))
        ScreenTitle("Hisob yarating")
        Spacer(Modifier.height(6.dp))
        ScreenSubtitle("Ro‘yxatdan o‘tish usulini tanlang.")

        Spacer(Modifier.weight(1f))

        PrimaryButton("Telefon raqami bilan", onPhone, trailingIcon = AuthIcons.Phone)
        Spacer(Modifier.height(12.dp))
        OutlineButton("Email bilan davom etish", onEmail, leadingIcon = AuthIcons.Mail)

        Spacer(Modifier.height(14.dp))
        Text(
            "Telefon orqali SMS kod, email orqali 6 xonali kod yuboriladi.",
            style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint, textAlign = TextAlign.Center, lineHeight = 16.sp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )

        Spacer(Modifier.weight(1f))
        FooterLink("Hisobingiz bormi?", "Kirish", onSignIn)
    }
}

// ===========================================================================
// Email tasdiqlash — 6 xonali kod
// ===========================================================================

@Composable
fun EmailVerifyScreen(
    state: AuthFlowState,
    vm: AuthFlowViewModel,
    onBack: () -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    palette: AuthPalette = authPalette,
) {
    AuthScreenScaffold(scroll = false) {
        BackButton(onBack)
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(60.dp)
                .background(Brush.linearGradient(listOf(palette.primary.copy(alpha = 0.14f), palette.primary.copy(alpha = 0.14f))), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AuthIcons.Mail, null, tint = palette.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        ScreenTitle("Emailingizni tasdiqlang")
        Spacer(Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = palette.ink, fontWeight = FontWeight.Bold)) {
                    append(state.email.ifBlank { "emailingiz" })
                }
                withStyle(SpanStyle(color = palette.inkMuted)) {
                    append(" manziliga yuborilgan 6 xonali kodni kiriting.")
                }
            },
            style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.sp, lineHeight = 19.sp),
        )

        Spacer(Modifier.height(22.dp))
        CodeBoxes(state.emailCode, vm::onEmailCodeChange, palette)

        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(AuthIcons.Clock, null, tint = palette.inkFaint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            if (state.resendSeconds > 0) {
                Text("Qayta yuborish · ", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, color = palette.inkFaint))
                Text(formatTimer(state.resendSeconds), style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
            } else {
                Text(
                    "Kodni qayta yuborish",
                    style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary),
                    modifier = Modifier.clickableNoRipple(onResend),
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton("Tasdiqlash", onVerify, enabled = state.emailCodeValid && !state.isLoading, trailingIcon = AuthIcons.Check)

        state.info?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.successDeep, lineHeight = 17.sp))
        }
        ErrorText(state.error)

        Spacer(Modifier.weight(1f))
        Text("Spam papkasini ham tekshiring.", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.5f.sp, color = palette.inkFaint))
    }
}

private fun formatTimer(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "0$m:${s.toString().padStart(2, '0')}"
}

@Composable
private fun CodeBoxes(value: String, onValueChange: (String) -> Unit, palette: AuthPalette) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { i ->
                    CodeCell(value.getOrNull(i), i == value.length, palette, Modifier.weight(1f))
                }
            }
        },
    )
}

@Composable
private fun CodeCell(ch: Char?, focused: Boolean, palette: AuthPalette, modifier: Modifier) {
    val shape = RoundedCornerShape(13.dp)
    val bg = when {
        ch != null || focused -> palette.fieldBg
        else -> if (palette.dark) Color.White.copy(alpha = 0.04f) else Color(0xFFF4F2FC)
    }
    val border = when {
        focused -> palette.primary
        ch != null -> palette.primary.copy(alpha = 0.20f)
        else -> palette.border
    }
    Box(
        modifier.height(52.dp).clip(shape).background(bg)
            .border(if (focused || ch != null) 1.5.dp else 1.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ch != null -> Text(ch.toString(), style = TextStyle(fontFamily = AuthFontFamily, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink))
            focused -> Box(Modifier.width(2.dp).height(24.dp).background(palette.primary))
        }
    }
}
