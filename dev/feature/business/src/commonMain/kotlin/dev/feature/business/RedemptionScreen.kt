package dev.feature.business

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.designsystem.components.AppFontFamily
import dev.core.designsystem.components.AppIcons
import dev.core.designsystem.components.AppScreenScaffold
import dev.core.designsystem.components.BackButton
import dev.core.designsystem.components.GlassTextField
import dev.core.designsystem.components.PrimaryButton
import dev.core.designsystem.theme.appPalette

/**
 * Biznesmen skaneri — talaba chegirmani ishlatgani (redemption) kodini tasdiqlash. Chiroyli
 * skaner ko'rinishi + qo'lda kod kiritish. Haqiqiy QR skaner/tasdiqlash backend yoqilganda
 * `RedemptionsApi` orqali ulanadi.
 */
@Composable
fun RedemptionScreen(onBack: () -> Unit) {
    val palette = appPalette
    var code by remember { mutableStateOf("") }

    AppScreenScaffold {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 54.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    "Chegirma skaneri",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.ink),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Talaba ko'rsatgan QR yoki kodni skanerlab, chegirma ishlatilganini tasdiqlang.",
                    style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted),
                )
            }

            Spacer(Modifier.height(30.dp))

            // Skaner ko'rinishi (viewfinder) — burchak urg'ulari bilan
            Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(palette.primary.copy(alpha = 0.14f), palette.primary.copy(alpha = 0.05f)),
                            ),
                        )
                        .border(2.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.ScanFace, "Skaner", tint = palette.primary, modifier = Modifier.size(84.dp))
                }
                // 4 burchak urg'usi
                CornerAccent(Alignment.TopStart, palette.primary)
                CornerAccent(Alignment.TopEnd, palette.primary)
                CornerAccent(Alignment.BottomStart, palette.primary)
                CornerAccent(Alignment.BottomEnd, palette.primary)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "QR kodni ramka ichiga joylashtiring",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 12.5f.sp, fontWeight = FontWeight.SemiBold, color = palette.inkMuted, textAlign = TextAlign.Center),
            )

            Spacer(Modifier.height(26.dp))

            // "yoki" ajratgich
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).height(1.dp).background(palette.border))
                Text("yoki kodni kiriting", style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.5f.sp, fontWeight = FontWeight.Bold, color = palette.inkFaint))
                Box(Modifier.weight(1f).height(1.dp).background(palette.border))
            }

            Spacer(Modifier.height(14.dp))
            GlassTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                placeholder = "Masalan: NAVRUZ20",
                leading = AppIcons.Tag,
                height = 50,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Tasdiqlash", { /* backend ulanganda RedemptionsApi */ }, enabled = code.isNotBlank())

            Spacer(Modifier.height(16.dp))
            Text(
                "Backend ulanganda QR skaner va kod tasdiqlash shu yerda ishlaydi.",
                style = TextStyle(fontFamily = AppFontFamily, fontSize = 11.sp, color = palette.inkFaint, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CornerAccent(align: Alignment, color: Color) {
    Box(
        Modifier.align(align).padding(10.dp).size(26.dp)
            .border(3.dp, color, RoundedCornerShape(8.dp)),
    )
}
