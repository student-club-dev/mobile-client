package dev.feature.listings.presentation.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.core.uikit.components.AppFontFamily
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.OutlineButton
import dev.core.uikit.components.PrimaryButton
import dev.core.uikit.components.scTopInset
import dev.core.uikit.theme.AppPalette
import dev.feature.listings.domain.model.ListingKind
import dev.feature.listings.presentation.MessageBar
import dev.feature.listings.presentation.PostListingUiState
import dev.feature.listings.presentation.PostListingViewModel
import dev.feature.listings.presentation.components.ErrorColor
import dev.feature.listings.presentation.components.IconSquareButton

/**
 * Tanlangan e'lon turining formasini ochadi.
 *
 * Har bir tur o'z faylida ([DiscountForm], [RentalForm], [ServiceForm], [JobForm]) —
 * ularning maydonlari bir-biriga umuman o'xshamaydi va bitta "universal" formaga
 * sig'dirishga urinish har bir maydon atrofida shartlar to'plamini keltirib chiqaradi.
 * Umumiy qismlar esa takrorlanmaydi: karkas shu yerda, bloklar [CommonSections] da.
 */
@Composable
fun KindListingForm(
    kind: ListingKind,
    state: PostListingUiState,
    palette: AppPalette,
    vm: PostListingViewModel,
) {
    when (kind) {
        ListingKind.DISCOUNT -> DiscountForm(state, palette, vm)
        ListingKind.RENTAL -> RentalForm(state, palette, vm)
        ListingKind.SERVICE -> ServiceForm(state, palette, vm)
        ListingKind.JOB -> JobForm(state, palette, vm)
        ListingKind.TASK -> TaskForm(state, palette, vm)
    }
}

/**
 * Forma ekranining karkasi: sarlavha → bloklar → tugmalar.
 *
 * Bloklar ketma-ketligi har turda boshqacha (ijarada avval uy tafsiloti, ishda avval
 * ish sharti), shuning uchun ularni karkas emas, turning o'z formasi joylashtiradi.
 */
@Composable
fun ListingFormShell(
    title: String,
    subtitle: String,
    state: PostListingUiState,
    palette: AppPalette,
    vm: PostListingViewModel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).scTopInset(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                IconSquareButton(vm::back, AppIcons.ArrowLeft, palette)
                Column {
                    Text(
                        if (state.editing) "E'lonni tahrirlash" else title,
                        style = TextStyle(
                            fontFamily = AppFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = palette.ink
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = TextStyle(
                            fontFamily = AppFontFamily,
                            fontSize = 11.5f.sp,
                            color = palette.inkFaint
                        ),
                    )
                }
            }

            content()

            val message = state.message
            if (message != null) {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    MessageBar(message, palette, onDismiss = vm::consumeMessage)
                }
            }

            Spacer(Modifier.height(4.dp))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.errors.isNotEmpty()) {
                Text(
                    "To'ldirilmagan ${state.errors.size} ta joy bor — yuqorida qizil bilan belgilandi.",
                    style = TextStyle(
                        fontFamily = AppFontFamily,
                        fontSize = 11.5f.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorColor
                    ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlineButton("Qoralama", vm::saveDraft)
                }
                Box(Modifier.weight(1.4f)) {
                    PrimaryButton(
                        if (state.submitting) "Yuborilmoqda..." else "E'lonni joylash",
                        vm::publish,
                        enabled = !state.submitting,
                    )
                }
            }
        }
    }
}
