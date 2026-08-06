package dev.feature.stories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.ScText
import dev.core.uikit.theme.Sc
import dev.feature.stories.domain.model.Story
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profildagi «Postlar» / «Arxivlangan postlar» bo'limi — Telegram maketi.
 *
 * Post — o'sha hikoyaning o'zi (`feature:stories`): 24 soat davomida bog'langanlarga
 * ko'rinadi, keyin **yo'qolmaydi**, faqat egasiga ko'rinadigan arxivga o'tadi. Shuning
 * uchun ikkala ro'yxat ham bitta manbadan keladi va bu yerda faqat qaysi biri
 * chizilishi ([archived]) hal qilinadi.
 *
 * Muallif ismi va avatari tashqaridan uzatiladi: story moduli profil modulini bilmaydi,
 * ko'ruvchining sarlavhasi esa ularsiz bo'sh qolardi.
 */
@Composable
fun MyPostsSection(
    archived: Boolean,
    authorName: String,
    authorAvatarUrl: String?,
    modifier: Modifier = Modifier,
    vm: MyPostsViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    val stories = if (archived) state.archived else state.posts

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Yuklanish yozuvi YO'Q: bo'sh joy jim turadi va to'r ma'lumot kelganda paydo
        // bo'ladi. "Yuklanmoqda…" kartasi qo'yilsa, posti yo'q foydalanuvchida (odatiy
        // hol) u bir soniyadan keyin ko'z oldida yo'qolar va ostidagi hamma narsa
        // yuqoriga sakrardi.
        if (stories.isNotEmpty()) {
            PostGrid(
                stories = stories,
                archived = archived,
                onOpen = { index -> vm.open(stories, index, authorName, authorAvatarUrl) },
            )
        }

        // «Yana» faqat arxivda: faol postlar 10 tadan oshmaydi (`StoryLimits.MAX_ACTIVE`),
        // ya'ni ular doim bitta sahifaga sig'adi.
        if (archived && state.hasMoreArchived) {
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Sc.Card)
                    .clickable(enabled = !state.loadingMore) { vm.loadMoreArchived() }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center,
            ) {
                ScText(
                    if (state.loadingMore) "Yuklanmoqda…" else "Yana",
                    13.5f,
                    FontWeight.Bold,
                    if (state.loadingMore) Sc.Muted else Sc.Brand,
                    maxLines = 1,
                )
            }
        }

        // Saqlash muddati — «arxivim qayoqqa ketdi?» degan savol tug'ilishidan oldin
        // javob bo'lsin (`STORY_ARCHIVE_BACKEND.md` §3: server 365 kun saqlaydi).
        if (archived && stories.isNotEmpty()) {
            ScText(
                "Rasm va videolar bir yil saqlanadi — keyin postning faqat yozuvi qoladi",
                11.5f,
                FontWeight.Medium,
                Sc.MutedLight,
            )
        }

        state.message?.let { ScText(it, 12.5f, FontWeight.SemiBold, Sc.Danger) }
    }

    if (viewer.open) {
        StoryViewerDialog(
            state = viewer,
            mediaHeaders = vm.mediaHeaders(),
            onNext = vm::next,
            onPrevious = vm::previous,
            onClose = vm::close,
            onDelete = vm::delete,
        )
    }
}
