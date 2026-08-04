package dev.core.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.common.format.UZ_PHONE_CODE
import dev.core.common.format.formatAmount
import dev.core.common.format.formatUzPhone
import dev.core.uikit.theme.AppPalette
import dev.core.uikit.theme.appPalette

// Maydonlardagi jonli qoliplar. Qoidalarning o'zi — `dev.core.common.format.Formats`:
// telefon doim "+998 ## ### ## ##", summa doim "90 000".
//
// Holatda (state) esa faqat toza raqamlar saqlanadi — probel, prefiks va ajratgichlar
// hech qachon holatga tushmaydi. Shu sababli har bir maydonning `onValueChange` i
// `toUzPhoneDigits()` / `toAmountDigits()` dan o'tkazadi: ortiqcha belgi yozib bo'lmaydi.

/** Telefon maydonida raqamni "## ### ## ##" bo'yicha ko'rsatib, kursor mosligini saqlaydi. */
class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val out = formatUzPhone(text.text)
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var t = offset
                if (offset > 2) t++
                if (offset > 5) t++
                if (offset > 7) t++
                return t.coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                var o = offset
                if (offset > 2) o--
                if (offset > 6) o--
                if (offset > 9) o--
                return o.coerceIn(0, out.count { it.isDigit() })
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** Telefon maydonining "🇺🇿 +998 |" prefiksi — u maydondan tashqarida, o'zgartirib bo'lmaydi. */
@Composable
fun PhonePrefix(palette: AppPalette = appPalette) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "🇺🇿 $UZ_PHONE_CODE",
            style = TextStyle(
                fontFamily = AppFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
            ),
        )
        Spacer(Modifier.width(9.dp))
        Box(Modifier.width(1.dp).height(22.dp).background(palette.border))
    }
}

/** Summa maydonida raqamni "90 000" qolipida ko'rsatib, kursor mosligini saqlaydi. */
class AmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val out = formatAmount(digits)
        val mapping = object : OffsetMapping {
            // Probellar o'ngdan uchtalab qo'yilgani uchun ularning o'rni raqam uzunligiga
            // bog'liq — shuning uchun siljish qotirilgan qoida bilan emas, sanab topiladi.
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, digits.length)
                var spaces = 0
                for (i in 0 until o) {
                    if (i < digits.length - 1 && (digits.length - 1 - i) % 3 == 0) spaces++
                }
                return (o + spaces).coerceAtMost(out.length)
            }

            override fun transformedToOriginal(offset: Int): Int {
                val t = offset.coerceIn(0, out.length)
                return out.take(t).count { it.isDigit() }
            }
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
