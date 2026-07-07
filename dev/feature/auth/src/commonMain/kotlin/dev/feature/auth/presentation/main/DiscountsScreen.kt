package dev.feature.auth.presentation.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.DiscountTag
import dev.feature.auth.presentation.components.AuthFontFamily
import dev.feature.auth.presentation.components.AuthIcons
import dev.feature.auth.presentation.components.GlassTextField
import dev.feature.auth.presentation.theme.AuthPalette
import dev.feature.auth.presentation.theme.authPalette
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DiscountsScreen(vm: DiscountsViewModel = koinViewModel()) {
    val palette = authPalette
    val state by vm.state.collectAsStateWithLifecycle()

    if (state.selected == null) {
        DiscountsGrid(state.categories, palette, onOpen = vm::open)
    } else {
        CategoryOffers(
            category = state.selected!!,
            offers = state.offers,
            query = state.query,
            savedIds = state.savedIds,
            palette = palette,
            onBack = vm::close,
            onQuery = vm::onQuery,
            onToggleSaved = { offer, saved -> vm.toggleSaved(offer, saved) },
        )
    }
}

// ---------------------------------------------------------------------------
// 1q — kategoriyalar grid
// ---------------------------------------------------------------------------
@Composable
private fun DiscountsGrid(categories: List<DiscountCategory>, palette: AuthPalette, onOpen: (DiscountCategory) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 54.dp)) {
        Text("Chegirmalar", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 24.sp, fontWeight = FontWeight.Black, color = palette.ink))
        Spacer(Modifier.height(4.dp))
        Text("Bo'limni tanlang — ichida takliflar ochiladi.", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 12.5f.sp, color = palette.inkMuted))
        Spacer(Modifier.height(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            categories.chunked(2).forEach { rowItems ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    rowItems.forEach { CategoryCard(it, palette, Modifier.weight(1f), onOpen) }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun CategoryCard(category: DiscountCategory, palette: AuthPalette, modifier: Modifier, onOpen: (DiscountCategory) -> Unit) {
    val accent = Color(category.accent)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .clickable { onOpen(category) }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) { Text(category.emoji, style = TextStyle(fontSize = 22.sp)) }
        Text(category.name, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${category.offerCount} taklif", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.sp, color = palette.inkFaint))
    }
}

// ---------------------------------------------------------------------------
// 1w — kategoriya ichidagi takliflar
// ---------------------------------------------------------------------------
@Composable
private fun CategoryOffers(
    category: DiscountCategory,
    offers: List<DiscountOffer>,
    query: String,
    savedIds: Set<String>,
    palette: AuthPalette,
    onBack: () -> Unit,
    onQuery: (String) -> Unit,
    onToggleSaved: (DiscountOffer, Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 54.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(12.dp))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(AuthIcons.ArrowLeft, "Orqaga", tint = palette.ink, modifier = Modifier.size(18.dp)) }
                Text("${category.emoji}  ${category.name} chegirmalari", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Black, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(14.dp))
            GlassTextField(query, onQuery, "Restoran yoki taom qidiring", leading = AuthIcons.Search, height = 46)
        }

        Spacer(Modifier.height(14.dp))

        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            items(offers, key = { it.id }) { offer ->
                OfferCard(offer, saved = savedIds.contains(offer.id), palette = palette, onToggleSaved = onToggleSaved)
            }
        }
    }
}

@Composable
private fun OfferCard(offer: DiscountOffer, saved: Boolean, palette: AuthPalette, onToggleSaved: (DiscountOffer, Boolean) -> Unit) {
    val accent = Color(offer.bannerAccent)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.glass).border(1.dp, palette.border, RoundedCornerShape(18.dp)),
    ) {
        // Rangli banner
        Box(Modifier.fillMaxWidth().height(64.dp).background(accent.copy(alpha = 0.16f)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
            Text(offer.emoji, style = TextStyle(fontSize = 30.sp))
            Box(
                Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(10.dp)).background(accent).padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("−${offer.discountPercent}%", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White))
            }
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${offer.merchant} — ${offer.title}", style = TextStyle(fontFamily = AuthFontFamily, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = palette.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val tagText = if (offer.tag == DiscountTag.STUDENT_ID) "Talaba ID" else "Promokod"
                Box(Modifier.clip(RoundedCornerShape(8.dp)).background(palette.primary.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                    Text(tagText, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.Bold, color = palette.primary))
                }
                val promo = offer.promoCode
                if (promo != null) {
                    Spacer(Modifier.size(6.dp))
                    Text(promo, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 10.5f.sp, fontWeight = FontWeight.ExtraBold, color = palette.inkMuted))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    AuthIcons.Bookmark, "Saqlash",
                    tint = if (saved) palette.primary else palette.inkFaint,
                    modifier = Modifier.size(20.dp).clickable { onToggleSaved(offer, saved) },
                )
            }
            val meta = listOfNotNull(offer.location?.let { "📍 $it" }, offer.expiry).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = TextStyle(fontFamily = AuthFontFamily, fontSize = 11.sp, color = palette.inkFaint))
            }
        }
    }
}
