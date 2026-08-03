package dev.feature.stories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.media.rememberVideoPicker
import dev.core.uikit.media.rememberVideoPreparer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import dev.core.uikit.theme.Sc
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.model.StoryLimits
import org.koin.compose.viewmodel.koinViewModel

/**
 * Avatar diametri, halqa qalinligi va halqa bilan avatar orasidagi oraliq —
 * Telegram/Instagram o'lchamiga yaqin.
 */
private val AVATAR = 62.dp
private val RING = 2.5.dp
private val GAP = 3.dp

/** Katakning to'liq eni — halqa + oraliq ikki tomondan. */
private val CELL = AVATAR + (RING + GAP) * 2

/**
 * Bosh ekrandagi story lentasi (`handoff/07-STORIES.md` §2).
 *
 * Birinchi katak — **o'zimniki**: avatarim ko'rinadi, lavham bo'lsa halqa yonadi va bosish
 * uni **ko'rsatadi**, pastdagi «+» esa doim yangisini qo'shadi. Qolganlari serverdagi
 * **tartibda** chiziladi: avval ko'rilmaganlar, ular ichida yangidan eskiga.
 * ⚠️ Qayta saralanmaydi — server allaqachon saralab beradi.
 *
 * [myName] / [myAvatarUrl] tashqaridan beriladi (bosh ekran ularni allaqachon biladi) —
 * story moduli profil moduliga bog'lanib qolmasin.
 */
@Composable
fun StoriesRow(
    myName: String,
    myAvatarUrl: String?,
    modifier: Modifier = Modifier,
    /**
     * Lavha muallifi ustiga bosildi — uning profili ochilsin.
     *
     * Profil varag'ini **chaqiruvchi** chizadi: undagi «Media / Fayllar / Havolalar»
     * bo'limlari chat modulida yashaydi va story moduli chatga bog'lanolmaydi (chat
     * allaqachon story'ga bog'langan — «Postlar» bo'limi uchun).
     */
    onOpenProfile: ((StudentSummary) -> Unit)? = null,
    vm: StoriesViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()

    var pickerChoice by remember { mutableStateOf(false) }

    val picker = rememberImagePicker { picked ->
        if (picked == null) return@rememberImagePicker
        vm.publish(picked.bytes, picked.fileName, caption = null)
    }
    // Siqish nashrdan KEYIN, katakchadagi halqa ichida ketadi — tanlagandan keyin
    // foydalanuvchi hech narsa kutmaydi.
    val videoPreparer = rememberVideoPreparer()
    val videoPicker = rememberVideoPicker { picked ->
        when {
            // `null` — foydalanuvchi bekor qildi (yoki fayl o'qilmadi). Xabar
            // ko'rsatilmaydi: bekor qilishga javoban chiqadigan oyna bezovta qilardi.
            picked == null -> Unit
            // ⚠️ Chegara YUKLASHDAN OLDIN tekshiriladi: 1 daqiqadan uzun videoni siqib,
            // yuklab bo'lgach serverdan `422` olish — trafik ham, vaqt ham behuda.
            tooLongForStory(picked.durationMs) -> vm.showMessage(STORY_TOO_LONG_MESSAGE)
            else -> vm.publishVideo(picked, videoPreparer, caption = null)
        }
    }

    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "mine") {
            MyStoryCell(
                name = myName,
                avatarUrl = myAvatarUrl,
                publishing = state.publishing,
                progress = state.publishProgress,
                // Halqa lavhalarim soniga bo'linadi — nechta lavham bo'lsa, shuncha bo'lak.
                storyCount = state.mine.size,
                // Lavham bo'lsa — o'zimga ko'rinadi; bo'lmasa darhol qo'shish.
                onClick = {
                    if (state.hasMine) vm.openMine(myName, myAvatarUrl) else pickerChoice = true
                },
                onAdd = { pickerChoice = true },
            )
        }
        items(state.groups, key = { it.author.id }) { group ->
            StoryCell(group = group, onClick = { vm.open(group) })
        }
    }

    // Yuborish natijasi va chegara xabarlari — ilgari ular holatda turardi-yu, ekranda
    // hech qayerda ko'rinmasdi.
    state.message?.let { text ->
        StoryMessageDialog(text = text, onDismiss = vm::messageShown)
    }

    if (pickerChoice) {
        StoryPickerChoice(
            onDismiss = { pickerChoice = false },
            onPhoto = { pickerChoice = false; picker.pick() },
            onVideo = { pickerChoice = false; videoPicker.pick() },
        )
    }

    if (viewer.open) {
        StoryViewerDialog(
            state = viewer,
            mediaHeaders = vm.mediaHeaders(),
            onNext = vm::next,
            onPrevious = vm::previous,
            onClose = vm::close,
            onDelete = vm::delete,
            // O'z lavhamda profil ochilmaydi — u yerda ochadigan «boshqa odam» yo'q.
            onOpenAuthor = { authorId ->
                val author = viewer.group?.author
                if (author != null && !vm.isMine(authorId)) {
                    vm.close()
                    onOpenProfile?.invoke(author)
                }
            },
        )
    }

}

/**
 * Lenta yig'ilganda (bosh ekran topbari siqilganda) uning o'rnini bosadigan **kichik
 * to'plam** — Telegramdagidek bir-birining ustiga chiqqan 3 tagacha avatar.
 *
 * Bosilganda ekran tepasiga qaytaradi ([onClick]), ya'ni to'liq lenta ko'rinadi. Lavha
 * umuman bo'lmasa (o'zimniki ham, boshqalarniki ham) hech nima chizilmaydi.
 */
@Composable
fun StoriesCollapsed(
    myName: String,
    myAvatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Halqa ranglari. Sukut — brend gradienti; gradient topbar ustida oq beriladi, aks
     * holda ko'k halqa ko'k fonda ko'rinmay qoladi.
     */
    ringBrush: Brush = Brush.linearGradient(listOf(Sc.BrandLight, Sc.Brand)),
    vm: StoriesViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // O'zimniki birinchi — to'plamda ham lentadagi tartib saqlanadi.
    val mine = if (state.hasMine) listOf(myName to myAvatarUrl) else emptyList()
    val avatars = (mine + state.groups.map { it.author.displayName to it.author.avatarUrl })
        .take(COLLAPSED_MAX)
    if (avatars.isEmpty()) return

    Row(
        modifier.clip(RoundedCornerShape(percent = 50)).clickable(onClick = onClick),
        // Manfiy oraliq — doiralar bir-birining ustiga chiqadi.
        horizontalArrangement = Arrangement.spacedBy(-COLLAPSED_OVERLAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        avatars.forEach { (name, url) ->
            Box(
                Modifier.size(COLLAPSED_AVATAR + COLLAPSED_RING * 2)
                    .clip(CircleShape)
                    .background(ringBrush),
                contentAlignment = Alignment.Center,
            ) {
                ScAvatar(
                    name = name,
                    size = COLLAPSED_AVATAR,
                    avatarUrl = url,
                    background = Color.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}

/**
 * Rasm yoki video — ikkalasi ham lavha bo'la oladi, lekin tizim tanlagichlari boshqa-boshqa
 * (galereya rasm va video uchun alohida filtr talab qiladi).
 */
@Composable
private fun StoryPickerChoice(onDismiss: () -> Unit, onPhoto: () -> Unit, onVideo: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Sc.Card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScText("Lavha qo'shish", 16f, FontWeight.ExtraBold, Sc.Ink)
            ScText("Rasm", 15f, FontWeight.Bold, Sc.Brand, Modifier.clickable(onClick = onPhoto))
            ScText("Video", 15f, FontWeight.Bold, Sc.Brand, Modifier.clickable(onClick = onVideo))
        }
    }
}

/**
 * «Lavham» katakchasi — Telegramdagi «Hikoyam».
 *
 * Avatarim **doim** ko'rinadi (kamera ikonkasi emas): lavha qo'ygan-qo'ymaganimdan qat'i
 * nazar bu men. Lavham bo'lsa halqa yonadi va bosish uni ko'rsatadi; pastdagi «+» esa
 * har doim yangisini qo'shadi.
 */
@Composable
private fun MyStoryCell(
    name: String,
    avatarUrl: String?,
    publishing: Boolean,
    /** Yuklash foizi; `null` — hali/endi noma'lum (server lavhani yaratmoqda). */
    progress: Float?,
    /** Faol lavhalarim soni — halqa shuncha bo'lakka bo'linadi. */
    storyCount: Int,
    onClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val hasMine = storyCount > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Bosish halqaning O'ZIDA: «+» belgisi ustidan chizilgani uchun o'z bosishini
            // saqlab qoladi (keyin chizilgan element tegishni birinchi oladi).
            Box(Modifier.clip(CircleShape).clickable(enabled = !publishing, onClick = onClick)) {
                // O'z lavhalarim doim "yorqin": ular menga ko'rilgan-ko'rilmagan emas —
                // halqa bu yerda "lavham bor" degan ma'noni bildiradi.
                StoryRing(segments = List(storyCount) { true }) {
                    Box(contentAlignment = Alignment.Center) {
                        ScAvatar(name = name, size = AVATAR, avatarUrl = avatarUrl)
                        // Foiz avatar USTIDA: yuklash davomida ham kimning lavhasi
                        // ketayotgani ko'rinib tursin.
                        if (publishing) {
                            ScUploadRing(progress, size = AVATAR, stroke = 2.5.dp)
                        }
                    }
                }
            }
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(Sc.Brand)
                    .border(2.5.dp, Sc.Bg, CircleShape)
                    .clickable(enabled = !publishing, onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) {
                if (publishing) {
                    Icon(
                        AppIcons.Camera,
                        "Yuklanmoqda",
                        tint = Color.White,
                        modifier = Modifier.size(11.dp),
                    )
                } else {
                    ScText("+", 12f, FontWeight.ExtraBold, Color.White, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        ScText(
            when {
                publishing && progress != null -> "Yuklanmoqda ${scUploadPercent(progress)}"
                // Fayl ketib bo'lgan, server lavhani yaratmoqda — foiz o'rniga holat.
                publishing -> "Tayyorlanmoqda…"
                else -> "Lavham"
            },
            11.5f,
            FontWeight.SemiBold,
            if (hasMine) Sc.Ink else Sc.Muted,
            Modifier.width(CELL),
            maxLines = 1,
        )
    }
}

/**
 * Bitta muallif katakchasi.
 *
 * Halqa **faqat `hasUnseen`** bo'lganda yonadi — bu serverdan keladi, klientda hisoblanmaydi
 * (§2). Ko'rib bo'lingan guruhda halqa xira chiziq bo'lib qoladi.
 */
@Composable
private fun StoryCell(group: StoryGroup, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Har lavha uchun bitta bo'lak: ko'rilmagani yorqin, ko'rilgani xira — Telegram
        // va Instagramdagi kabi, ya'ni "yana nechtasi qolgani" bir qarashda ko'rinadi.
        StoryRing(segments = group.stories.map { !it.seen }) {
            ScAvatar(
                name = group.author.displayName,
                size = AVATAR,
                avatarUrl = group.author.avatarUrl,
            )
        }
        Spacer(Modifier.height(5.dp))
        ScText(
            group.author.displayName,
            11.5f,
            FontWeight.SemiBold,
            if (group.hasUnseen) Sc.Ink else Sc.Muted,
            Modifier.width(CELL).padding(horizontal = 2.dp),
            maxLines = 1,
        )
    }
}

/**
 * Avatar atrofidagi halqa — **lavhalar soniga bo'lingan** (Telegram/Instagramdagidek).
 *
 * [segments] — har lavha uchun bittadan bayroq: `true` — ko'rilmagan (brend gradienti),
 * `false` — ko'rilgan (xira chiziq). Ro'yxat bo'sh bo'lsa (lavha yo'q) butun halqa xira
 * bo'lib chiziladi.
 *
 * Nega bo'laklar `drawBehind` da chiziladi, `background(brush)` bilan emas: to'ldirilgan
 * doira **bitta** shakl, uni bo'lakka ajratib bo'lmaydi. Yoylar esa kerakli sondagi
 * bo'lakni va ular orasidagi bo'shliqni beradi.
 *
 * Halqa bilan avatar orasida oraliq bor ([GAP]) — halqa avatarga «yopishib» qolmaydi.
 */
@Composable
private fun StoryRing(segments: List<Boolean>, content: @Composable () -> Unit) {
    val litBrush = Brush.linearGradient(listOf(Sc.BrandLight, Sc.Brand, Sc.Violet))
    val dimBrush = SolidColor(Sc.Border)
    // Ro'yxat bo'sh — bitta xira halqa (hali lavha yo'q).
    val states = segments.ifEmpty { listOf(false) }

    Box(
        Modifier.size(CELL).drawBehind {
            val stroke = RING.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            if (states.size == 1) {
                drawArc(
                    brush = if (states[0]) litBrush else dimBrush,
                    startAngle = 0f,
                    sweepAngle = FULL_CIRCLE,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke),
                )
                return@drawBehind
            }

            val step = FULL_CIRCLE / states.size
            // Bo'laklar ko'payganda bo'shliq ham kichrayadi — aks holda 10 ta lavhada
            // halqadan ko'ra ko'proq bo'shliq qolardi.
            val gap = if (states.size > MANY_SEGMENTS) SMALL_GAP_DEGREES else GAP_DEGREES
            states.forEachIndexed { index, lit ->
                drawArc(
                    brush = if (lit) litBrush else dimBrush,
                    // Tepadan boshlanadi va soat mili yo'nalishida ketadi.
                    startAngle = TOP_ANGLE + index * step + gap / 2f,
                    sweepAngle = step - gap,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private const val FULL_CIRCLE = 360f

/** Yoy tepadan (soat 12) boshlansin — `0f` o'ng tomon bo'lardi. */
private const val TOP_ANGLE = -90f

/** Bo'laklar orasidagi bo'shliq (gradusda) va uni kichraytirish chegarasi. */
private const val GAP_DEGREES = 9f
private const val SMALL_GAP_DEGREES = 5f
private const val MANY_SEGMENTS = 5

/** Yig'ilgan to'plamdagi doira o'lchamlari va nechtasi ko'rsatilishi. */
private val COLLAPSED_AVATAR = 26.dp
private val COLLAPSED_RING = 1.5.dp
private val COLLAPSED_OVERLAP = 10.dp
private const val COLLAPSED_MAX = 3

/**
 * Tanlangan video lavha uchun juda uzunmi.
 *
 * Davomiylik aniqlanmagan bo'lsa (`null`) to'silmaydi: bu ba'zi kodeklarda bo'ladi va
 * o'shanda chegarani server tekshiradi — foydalanuvchini taxmin bilan to'xtatmaymiz.
 */
private fun tooLongForStory(durationMs: Int?): Boolean =
    durationMs != null && durationMs > StoryLimits.MAX_VIDEO_MS

/** Xabar oynasi — bitta tugmali, chunki bu yerda tanlov yo'q. */
@Composable
private fun StoryMessageDialog(text: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp))
                .background(Sc.Card)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ScText(text, 14.5f, FontWeight.SemiBold, Sc.Ink, maxLines = 4)
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.clip(RoundedCornerShape(14.dp))
                    .background(Sc.Brand.copy(alpha = 0.12f))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 26.dp, vertical = 9.dp),
            ) {
                ScText("Tushunarli", 13.5f, FontWeight.ExtraBold, Sc.Brand, maxLines = 1)
            }
        }
    }
}

/** Chegara **mahsulot** qarori — `CHAT_MEDIA_PARITY_BACKEND.md` §2. */
private const val STORY_TOO_LONG_MESSAGE =
    "Lavha 1 daqiqadan uzun bo'lmasin. Videoni qisqartirib qayta tanlang."
