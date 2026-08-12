package dev.feature.listings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScCircleButton
import dev.core.uikit.components.ScGradientButton
import dev.core.uikit.components.ScIconTile
import dev.core.uikit.components.ScIcons
import dev.core.uikit.components.ScText
import dev.core.uikit.components.scBrandShadow
import dev.core.uikit.components.scCard
import dev.core.uikit.components.scStyle
import dev.core.uikit.components.scTopInset
import dev.core.uikit.theme.Sc
import dev.core.uikit.components.ScShimmerList
import dev.core.uikit.components.ScPullRefresh
import dev.feature.listings.domain.model.Listing
import dev.feature.listings.domain.model.ListingStatus
import dev.feature.listings.domain.model.formatSum
import dev.feature.listings.presentation.components.ListingImage
import dev.feature.listings.presentation.components.StatusPill
import org.koin.compose.viewmodel.koinViewModel

/** Biznes egasining chegirma e'lonlari — status, to'xtatish, tahrirlash, o'chirish. */
@Composable
fun MyListingsScreen(
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    // Biznes shell'ida header "+" yashiriladi — u yerda o'ng-past burchakda aylana FAB bor.
    showHeaderCreate: Boolean = true,
    // Sarlavha (biznes shell'da segment selektor bilan almashtiriladi).
    showHeader: Boolean = true,
    // `true` — faqat chegirma, `false` — faqat oddiy e'lon, `null` — hammasi.
    filterDiscount: Boolean? = null,
    vm: MyListingsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listings = if (filterDiscount == null) {
        state.listings
    } else {
        state.listings.filter { it.isDiscount == filterDiscount }
    }

    Column(Modifier.fillMaxSize().background(Sc.Bg)) {
        if (showHeader) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding).scTopInset(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onBack != null) {
                    ScCircleButton(
                        ScIcons.ChevronLeft, onBack,
                        background = Sc.Card, tint = Sc.Ink, contentDescription = lt("Orqaga"),
                    )
                }
                Column(Modifier.weight(1f)) {
                    // Sarlavha filtrga qarab — talaba shell'ida ("Mening e'lonlarim") chegirma
                    // yo'q, biznesda esa aksincha. [EmptyState] ham AYNAN shu shart bilan.
                    ScText(
                        if (filterDiscount == false) lt("Mening e'lonlarim") else lt("Mening chegirmalarim"),
                        19f, FontWeight.ExtraBold, Sc.Ink, letterSpacing = -0.3f, maxLines = 1,
                    )
                    ScText(Lt.listingsCount(listings.size), 12.5f, FontWeight.Medium, Sc.Muted, maxLines = 1)
                }
                if (showHeaderCreate) {
                    ScCircleButton(
                        ScIcons.Plus, onCreate,
                        background = Sc.Card, tint = Sc.Brand, contentDescription = lt("Qo'shish"),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Tarmoq xatosi keshni almashtirmaydi — ro'yxat joyida qoladi va xabar shu yerda
        // chiqadi. Bo'sh ro'yxatda esa "e'lon yo'q" deb ko'rsatish yolg'on bo'lardi.
        state.message?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Sc.ScreenPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScText(message, 12.5f, FontWeight.Medium, Sc.Muted, Modifier.weight(1f))
                ScText(
                    lt("Qayta urinish"), 12.5f, FontWeight.Bold, Sc.Brand,
                    Modifier.clickable { vm.consumeMessage(); vm.refresh() },
                )
            }
        }

        if (listings.isEmpty() && (state.loading || state.refreshing)) {
            // Ro'yxat kelguncha — kartalarning skeleti.
            ScShimmerList(
                rows = 4,
                modifier = Modifier.padding(horizontal = Sc.ScreenPadding, vertical = 8.dp),
                rowHeight = 58.dp,
            )
        } else if (listings.isEmpty()) {
            EmptyState(onCreate, filterDiscount)
        } else {
            // Tepadan tortish — o'z e'lonlarim serverdan qayta o'qiladi (status boshqa
            // qurilmada yoki moderatsiyada o'zgargan bo'lishi mumkin).
            ScPullRefresh(
                refreshing = state.refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Sc.ScreenPadding, end = Sc.ScreenPadding, top = 8.dp, bottom = 110.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(listings, key = { it.id }) { listing ->
                        MyListingCard(
                            listing = listing,
                            onEdit = { onEdit(listing.id) },
                            onTogglePaused = { vm.togglePaused(listing) },
                            onDelete = { vm.delete(listing) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreate: () -> Unit, discount: Boolean?) {
    // Tab bo'yicha farqli matn — Chegirma yoki E'lon.
    val isDiscount = discount != false
    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Gradient plitka ichida ikonka.
        Box(
            Modifier.size(86.dp)
                .scBrandShadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Sc.tileBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isDiscount) ScIcons.DiscountTag else ScIcons.FileText,
                null,
                tint = Color.White,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        ScText(
            if (isDiscount) lt("Hali chegirma yo'q") else lt("Hali e'lon yo'q"),
            19f, FontWeight.ExtraBold, Sc.Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isDiscount) {
                lt("Birinchi chegirmangizni joylang — talabalar uni Chegirmalar bo'limida ko'radi.")
            } else {
                lt("Birinchi e'loningizni joylang — mahsulot yoki xizmatingizni talabalarga ko'rsating.")
            },
            style = scStyle(13.5f, FontWeight.Medium, Sc.Muted, lineHeight = 20f)
                .copy(textAlign = TextAlign.Center),
        )
        Spacer(Modifier.height(22.dp))
        ScGradientButton(
            if (isDiscount) lt("Chegirma qo'shish") else lt("E'lon qo'shish"),
            onCreate,
        )
    }
}

@Composable
private fun MyListingCard(
    listing: Listing,
    onEdit: () -> Unit,
    onTogglePaused: () -> Unit,
    onDelete: () -> Unit,
) {
    // `accent`/`emoji` endi Listing'ning o'zida — chegirmada biznes turidan, boshqa
    // turlarda ListingKind'dan olinadi, shuning uchun to'rtala tur uchun ham ishlaydi.
    val accent = Color(listing.accent)
    val isDiscount = listing.isDiscount

    Column(Modifier.fillMaxWidth().scCard(radius = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Muqova rasmi (yo'q bo'lsa — tur emoji'si). Chegirmada burchakda badge.
            Box(Modifier.size(72.dp)) {
                Box(
                    Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.07f))),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val cover = listing.images.firstOrNull()
                    if (cover != null) {
                        ListingImage(cover, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)))
                    } else {
                        Text(listing.emoji, style = TextStyle(fontSize = 28.sp))
                    }
                }
                // badge() endi `null` qaytarishi mumkin (chegirmasiz e'lon) — yorliq shunda chiqmaydi.
                val badge = listing.discountDetails?.badge()
                if (badge != null) {
                    Box(
                        Modifier.align(Alignment.TopStart).padding(5.dp)
                            .clip(RoundedCornerShape(8.dp)).background(accent)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        ScText(badge, 10f, FontWeight.ExtraBold, Color.White, maxLines = 1)
                    }
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                ScText(listing.title, 14.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                // Kategoriya chipi.
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    ScText(listing.categoryLabel, 11f, FontWeight.Bold, accent, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ScText("${listing.finalPrice.formatSum()} ${currency()}", 15.5f, FontWeight.ExtraBold, Sc.Ink, maxLines = 1)
                    // Chegirmada eski narx — chiziq bilan; oddiy e'londa yo'q.
                    if (isDiscount && listing.price != listing.finalPrice) {
                        Text(
                            listing.price.formatSum(),
                            style = scStyle(11.5f, FontWeight.Medium, Sc.MutedLight)
                                .copy(textDecoration = TextDecoration.LineThrough),
                            modifier = Modifier.padding(bottom = 1.dp),
                        )
                    }
                }
            }

            StatusPill(listing.status.label, listing.status.scColor())
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp).padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val first = listing.branches.firstOrNull()
            if (first != null) {
                val extra = listing.branches.size - 1
                // Bir nechta filial bo'lsa: "📍 Chilonzor filiali — ... +2 filial"
                ScText(
                    "📍 ${first.display()}" + if (extra > 0) " ${Lt.moreBranches(extra)}" else "",
                    11f, FontWeight.Medium, Sc.MutedLight,
                    Modifier.weight(1f),
                    maxLines = 1,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            CardAction(AppIcons.Pencil, lt("Tahrirlash"), Sc.TintBlue, Sc.Brand, onEdit)
            if (listing.status == ListingStatus.ACTIVE || listing.status == ListingStatus.PAUSED) {
                CardAction(
                    if (listing.status == ListingStatus.ACTIVE) AppIcons.EyeOff else AppIcons.Eye,
                    if (listing.status == ListingStatus.ACTIVE) lt("To'xtatish") else lt("Yoqish"),
                    Sc.Chip, Sc.ChipInk,
                    onTogglePaused,
                )
            }
            CardAction(ScIcons.Close, lt("O'chirish"), Sc.TintPink, Sc.Danger, onDelete)
        }
    }
}

@Composable
private fun CardAction(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    ScIconTile(background, Modifier.clip(RoundedCornerShape(11.dp)).clickable(onClick = onClick), size = 33.dp, radius = 11.dp) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(15.dp))
    }
}

/** Status → dizayn palitrasidagi rang. */
@Composable
private fun ListingStatus.scColor(): Color = when (this) {
    ListingStatus.ACTIVE -> Sc.Success
    ListingStatus.DRAFT -> Sc.MutedLight
    ListingStatus.PENDING_REVIEW, ListingStatus.SCHEDULED -> Sc.Brand
    ListingStatus.REJECTED, ListingStatus.EXPIRED, ListingStatus.SOLD_OUT -> Sc.Danger
    ListingStatus.PAUSED, ListingStatus.ARCHIVED -> Sc.Muted
}
