package dev.feature.stories.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.core.uikit.components.AppIcons
import dev.core.uikit.components.ScAvatar
import dev.core.uikit.components.ScText
import dev.core.uikit.components.ScUploadRing
import dev.core.uikit.components.scUploadPercent
import dev.core.uikit.media.PickedImage
import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.rememberImageCapture
import dev.core.uikit.media.rememberImagePicker
import dev.core.uikit.media.rememberVideoCapture
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
import kotlin.math.roundToInt

/**
 * Avatar diametri, halqa qalinligi va halqa bilan avatar orasidagi oraliq —
 * Telegram/Instagram o'lchamiga yaqin.
 */
private val AVATAR = 62.dp
private val RING = 2.5.dp
private val GAP = 3.dp

/** Katakning to'liq eni — halqa + oraliq ikki tomondan. */
private val CELL = AVATAR + (RING + GAP) * 2

/** Kataklar orasidagi oraliq. */
private val CELL_GAP = 12.dp

/**
 * Katakning to'liq o'lchami — lentani ushlab turgan panel avatar MARKAZI qayerda
 * turishini shundan hisoblaydi ([StoriesRow] ning yig'ilishi, `MessagesHeader`).
 */
val StoriesCell: Dp get() = CELL

/** Yig'ilgan to'plamdagi bitta doira (halqasi bilan) diametri. */
val StoriesCollapsedCell: Dp get() = COLLAPSED_AVATAR + COLLAPSED_RING * 2

/** Yig'ilgan to'plamdagi qo'shni doiralar markazlari orasidagi qadam. */
val StoriesCollapsedStep: Dp get() = StoriesCollapsedCell - COLLAPSED_OVERLAP

/**
 * Yig'ilgan to'plam qancha joy egallaydi — sarlavha shuncha o'ngga suriladi.
 *
 * Kataklar soni O'ZGARGANDAgina qayta hisoblanadi (hikoya qo'shilganda/tugaganda), ya'ni
 * yig'ilish animatsiyasi davomida bu qiymat qimirlamaydi.
 */
@Composable
fun storiesCollapsedWidth(vm: StoriesViewModel = koinViewModel()): Dp {
    val state by vm.state.collectAsStateWithLifecycle()
    // Birinchi katak — doim o'zimniki ("Hikoyam"), shuning uchun kamida bittasi bor.
    val visible = (1 + state.groups.size).coerceAtMost(COLLAPSED_MAX)
    return StoriesCollapsedCell + StoriesCollapsedStep * (visible - 1)
}

/**
 * Bosh ekrandagi story lentasi (`handoff/07-STORIES.md` §2).
 *
 * Birinchi katak — **o'zimniki**: avatarim ko'rinadi, hikoyam bo'lsa halqa yonadi va bosish
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
     * Lenta chetidagi bo'shliq. Xabarlar ekranida lenta topbar ICHIDA turadi va u yerda
     * chekka topbarniki bilan bir xil bo'lishi kerak ([Sc.HeaderPadding]).
     */
    contentPadding: PaddingValues = PaddingValues(horizontal = Sc.ScreenPadding, vertical = 4.dp),
    /**
     * `true` — lenta KO'K gradient topbar ustida (Xabarlar ekrani): yozuvlar oq, halqalar
     * ham oq bo'ladi. Sukut — odatiy fon ustida (bosh ekran), brend gradienti bilan.
     *
     * Rang tokenlari bilan hal qilib bo'lmaydi: `Sc.Ink` va brend ko'ki ikkalasi ham
     * ko'k gradient ustida ko'rinmay qoladi, ya'ni bu fon emas — KONTEKST farqi.
     */
    onHeader: Boolean = false,
    /**
     * Hikoya muallifi ustiga bosildi — uning profili ochilsin.
     *
     * Profil varag'ini **chaqiruvchi** chizadi: undagi «Media / Fayllar / Havolalar»
     * bo'limlari chat modulida yashaydi va story moduli chatga bog'lanolmaydi (chat
     * allaqachon story'ga bog'langan — «Postlar» bo'limi uchun).
     */
    onOpenProfile: ((StudentSummary) -> Unit)? = null,
    /**
     * Yig'ilish darajasi: `0f` — to'liq lenta, `1f` — sarlavha yonidagi kichik to'plam.
     * Kataklar shu oraliqda UZLUKSIZ kichrayib, bir-birining ustiga suriladi.
     *
     * ⚠️ Bu lambda ataylab `Float` emas: qiymat faqat o'lchash va chizish bosqichida
     * o'qiladi, ya'ni surish davomida lenta qayta KOMPOZITSIYA qilinmaydi.
     */
    collapse: () -> Float = { 0f },
    /** Yig'ilgan to'plamning chap cheti — lentaning o'z chap chetidan hisoblab. */
    collapsedStart: Dp = 0.dp,
    /** Yig'ilgan to'plam bosildi (lenta qayta ochilishi kerak). */
    onCollapsedClick: (() -> Unit)? = null,
    vm: StoriesViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()

    var pickerChoice by remember { mutableStateOf(false) }

    // Kameradan kelgan surat galereyadan tanlanganidan farq qilmaydi ([PickedImage]) —
    // shuning uchun ikkala tanlagich ham bitta ishlovchiga tushadi.
    val onImagePicked: (PickedImage?) -> Unit = { picked ->
        if (picked != null) vm.publish(picked.bytes, picked.fileName, caption = null)
    }
    val picker = rememberImagePicker(onImagePicked)
    val camera = rememberImageCapture(onImagePicked)

    // Siqish nashrdan KEYIN, katakchadagi halqa ichida ketadi — tanlagandan keyin
    // foydalanuvchi hech narsa kutmaydi.
    val videoPreparer = rememberVideoPreparer()
    // Kamerada yozilgan video ham xuddi shu yo'ldan o'tadi — davomiylik chegarasi
    // ikkalasiga ham baravar tegishli.
    val onVideoPicked: (PickedVideo?) -> Unit = { picked ->
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
    val videoPicker = rememberVideoPicker(onVideoPicked)
    val videoCamera = rememberVideoCapture(onVideoPicked)

    StoryStrip(
        collapse = collapse,
        contentPadding = contentPadding,
        collapsedStart = collapsedStart,
        onCollapsedClick = onCollapsedClick,
        modifier = modifier,
    ) {
        MyStoryCell(
            name = myName,
            avatarUrl = myAvatarUrl,
            publishing = state.publishing,
            progress = state.publishProgress,
            // Halqa hikoyalarim soniga bo'linadi — nechta hikoyam bo'lsa, shuncha bo'lak.
            storyCount = state.mine.size,
            onHeader = onHeader,
            collapse = collapse,
            // Hikoyam bo'lsa — o'zimga ko'rinadi; bo'lmasa darhol qo'shish.
            onClick = {
                if (state.hasMine) vm.openMine(myName, myAvatarUrl) else pickerChoice = true
            },
            onAdd = { pickerChoice = true },
        )
        state.groups.forEach { group ->
            StoryCell(
                group = group,
                onHeader = onHeader,
                collapse = collapse,
                onClick = { vm.open(group) },
            )
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
            onCapturePhoto = { pickerChoice = false; camera.pick() },
            onCaptureVideo = { pickerChoice = false; videoCamera.pick() },
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
            // O'z hikoyamda profil ochilmaydi — u yerda ochadigan «boshqa odam» yo'q.
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
 * Kataklarni ushlab turgan gorizontal tasma — va ularni sarlavha yonidagi to'plamga
 * **uzluksiz** aylantiruvchi joy.
 *
 * Nega `LazyRow` emas: lazy ro'yxat o'z chegarasidan tashqarini QIRQADI, morfing esa
 * kataklarni tasmadan tashqariga — sarlavha qatoriga olib chiqadi. Bu yerda esa qirqish
 * faqat GORIZONTAL (surilgan kataklar chetdan chiqib ketmasin), vertikal bo'yicha esa
 * ochiq: panel tasmani butunligicha yuqoriga surganda kataklar sarlavha ustida chiziladi.
 * Lentadagi kataklar soni kichik (server faqat FAOL hikoyalarni qaytaradi), shuning uchun
 * hammasini birdan kompozitsiya qilish qimmat emas.
 *
 * Har bir katakning o'rni, o'lchami va shaffofligi FAQAT joylashtirish lambda'sida
 * hisoblanadi (`placeWithLayer`) — ya'ni surish davomida qayta kompozitsiya bo'lmaydi.
 */
@Composable
private fun StoryStrip(
    collapse: () -> Float,
    contentPadding: PaddingValues,
    collapsedStart: Dp,
    onCollapsedClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    // Surish chegarasi O'LCHASHda ma'lum bo'ladi. Snapshot holati EMAS: uni faqat imo-ishora
    // ishlovchisi o'qiydi, ya'ni o'zgarishi hech nimani qayta chizishi shart emas.
    val maxOffset = remember { floatArrayOf(0f) }
    // Yig'ilgan to'plam qaysi to'rtburchakda turadi (chap, tepa, o'ng, past). Tegish
    // FAQAT shu yerda ushlanadi: tasma butun kenglikni egallaydi va yig'ilganda
    // sarlavhaning ustiga chiqadi — cheklovsiz u orqaga va qidiruv tugmalarini
    // to'sib qo'yardi.
    val collapsedBounds = remember { floatArrayOf(0f, 0f, 0f, 0f) }
    val scroll = rememberScrollableState { delta ->
        val old = offset
        offset = (old - delta).coerceIn(0f, maxOffset[0])
        old - offset
    }

    Layout(
        content = content,
        modifier = modifier
            // Yig'ilgan to'plam bitta butun tugma bo'lib qoladi: alohida kataklarning
            // bosilishi ham, tasmaning surilishi ham o'sha paytda ma'nosiz.
            .pointerInput(onCollapsedClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (collapse() < CollapsedTapThreshold) return@awaitEachGesture
                    val (left, top, right, bottom) = collapsedBounds
                    val point = down.position
                    if (point.x < left || point.x > right || point.y < top || point.y > bottom) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                    onCollapsedClick?.invoke()
                }
            }
            .scrollable(scroll, Orientation.Horizontal)
            .clipHorizontally(),
    ) { measurables, constraints ->
        val loose = Constraints(maxWidth = Constraints.Infinity, maxHeight = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val padStart = contentPadding.calculateLeftPadding(layoutDirection).roundToPx()
        val padEnd = contentPadding.calculateRightPadding(layoutDirection).roundToPx()
        val padTop = contentPadding.calculateTopPadding().roundToPx()
        val padBottom = contentPadding.calculateBottomPadding().roundToPx()
        val gap = CELL_GAP.roundToPx()

        val contentWidth = placeables.sumOf { it.width } +
            gap * (placeables.size - 1).coerceAtLeast(0) + padStart + padEnd
        val width = constraints.maxWidth
        val height = padTop + padBottom + (placeables.maxOfOrNull { it.height } ?: 0)
        maxOffset[0] = (contentWidth - width).coerceAtLeast(0).toFloat()

        val ringCenter = (CELL / 2).toPx()
        val collapsedCell = StoriesCollapsedCell.toPx()
        val collapsedStartPx = collapsedStart.toPx()
        val collapsedStep = StoriesCollapsedStep.toPx()
        val collapsedScale = collapsedCell / CELL.toPx()

        val stacked = placeables.size.coerceAtMost(COLLAPSED_MAX)
        val stackWidth = collapsedCell + collapsedStep * (stacked - 1).coerceAtLeast(0)
        collapsedBounds[0] = collapsedStartPx
        collapsedBounds[1] = padTop + ringCenter - collapsedCell
        collapsedBounds[2] = collapsedStartPx + stackWidth
        collapsedBounds[3] = padTop + ringCenter + collapsedCell

        layout(width, height) {
            val f = collapse().coerceIn(0f, 1f)
            var x = padStart - offset
            placeables.forEachIndexed { index, placeable ->
                // Kataklar ustma-ust tushganda ham halqa markazi qimirlamasin: shkala
                // va siljish shu nuqtaga nisbatan hisoblanadi.
                val restCenter = x + placeable.width / 2f
                val targetCenter = collapsedStartPx + collapsedCell / 2f + index * collapsedStep
                val center = lerp(restCenter, targetCenter, f)
                // To'plamda faqat dastlabki bir nechtasi qoladi — qolganlari yo'qoladi
                // (aks holda sarlavha yonida o'nlab doira yig'ilib qolardi).
                val fade = if (index < COLLAPSED_MAX) 1f else (1f - f).coerceIn(0f, 1f)
                val left = x
                placeable.placeWithLayer(left.roundToInt(), padTop) {
                    transformOrigin = TransformOrigin(
                        pivotFractionX = 0.5f,
                        pivotFractionY = ringCenter / placeable.height,
                    )
                    val scale = lerp(1f, collapsedScale, f)
                    scaleX = scale
                    scaleY = scale
                    translationX = center - restCenter
                    alpha = fade
                }
                x += placeable.width + gap
            }
        }
    }
}

/**
 * Faqat GORIZONTAL qirqish.
 *
 * Odatiy `clipToBounds()` ikkala o'q bo'yicha qirqadi va yig'ilayotgan kataklarning
 * sarlavha qatoriga chiqishini to'sib qo'yardi. Vertikal chegara ataylab juda keng
 * olingan — amalda "yo'q" degani.
 */
private fun Modifier.clipHorizontally() = drawWithContent {
    val scope = this
    clipRect(
        left = 0f,
        top = -size.height * VerticalClipSlack,
        right = size.width,
        bottom = size.height * VerticalClipSlack,
    ) {
        scope.drawContent()
    }
}

/** Shundan ortiq yig'ilganda tasma bitta tugma bo'lib qoladi. */
private const val CollapsedTapThreshold = 0.6f

/** Vertikal qirqishning "yo'qligi" — tasma balandligining shuncha barobari. */
private const val VerticalClipSlack = 6f

/**
 * Manba tanlash — **kamera** ham, galereya ham.
 *
 * To'rt band, chunki tizimda to'rtta alohida yo'l bor: kamera surat va video uchun boshqa-boshqa
 * chaqiruv talab qiladi (`TakePicture` / `CaptureVideo`), galereya esa rasm va video uchun
 * alohida filtr. Kamera bandlari **birinchi**: hikoya ko'pincha "hozir, shu yerda" olinadi.
 *
 * Sarlavha yo'q: oyna «+» bosilgandan keyin chiqadi, ya'ni foydalanuvchi nima qilayotganini
 * allaqachon biladi va «Hikoya qo'shish» yozuvi faqat joy egallardi.
 */
@Composable
private fun StoryPickerChoice(
    onDismiss: () -> Unit,
    onCapturePhoto: () -> Unit,
    onCaptureVideo: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Sc.Card).padding(vertical = 8.dp),
        ) {
            StoryPickerAction(AppIcons.Camera, "Suratga olish", onCapturePhoto)
            StoryPickerAction(AppIcons.Video, "Video yozish", onCaptureVideo)
            StoryPickerAction(AppIcons.ImageIcon, "Galereyadan rasm", onPhoto)
            StoryPickerAction(AppIcons.Film, "Galereyadan video", onVideo)
        }
    }
}

/** Manba tanlash oynasidagi bitta band — ikonka va yozuv bitta bosiladigan qatorda. */
@Composable
private fun StoryPickerAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Sc.Brand, modifier = Modifier.size(21.dp))
        ScText(label, 15f, FontWeight.Bold, Sc.Ink, maxLines = 1)
    }
}

/**
 * Katak ostidagi yozuv va «+» belgisi yig'ilish boshlanishi bilan TEZ yo'qoladi: 26dp'lik
 * doirachalar yonida ular o'qilmaydigan chizgiga aylanardi.
 */
private fun labelAlpha(collapse: Float): Float = (1f - collapse * LabelFadeSpeed).coerceIn(0f, 1f)

private const val LabelFadeSpeed = 2.5f

/**
 * «Hikoyam» katakchasi — Telegram/Instagramdagi «Mening hikoyam» bilan bir xil.
 *
 * Avatarim **doim** ko'rinadi (kamera ikonkasi emas): hikoya qo'ygan-qo'ymaganimdan qat'i
 * nazar bu men. Hikoyam bo'lsa halqa yonadi va bosish uni ko'rsatadi; pastdagi «+» esa
 * har doim yangisini qo'shadi.
 */
@Composable
private fun MyStoryCell(
    name: String,
    avatarUrl: String?,
    publishing: Boolean,
    /** Yuklash foizi; `null` — hali/endi noma'lum (server hikoyani yaratmoqda). */
    progress: Float?,
    /** Faol hikoyalarim soni — halqa shuncha bo'lakka bo'linadi. */
    storyCount: Int,
    /** Ko'k topbar ustidami — qarang [StoriesRow]. */
    onHeader: Boolean,
    /** Yig'ilish darajasi — yozuv va «+» belgisining shaffofligi uchun. */
    collapse: () -> Float,
    onClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val hasMine = storyCount > 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            // Bosish halqaning O'ZIDA: «+» belgisi ustidan chizilgani uchun o'z bosishini
            // saqlab qoladi (keyin chizilgan element tegishni birinchi oladi).
            Box(Modifier.clip(CircleShape).clickable(enabled = !publishing, onClick = onClick)) {
                // O'z hikoyalarim doim "yorqin": ular menga ko'rilgan-ko'rilmagan emas —
                // halqa bu yerda "hikoyam bor" degan ma'noni bildiradi.
                StoryRing(segments = List(storyCount) { true }, onHeader = onHeader) {
                    Box(contentAlignment = Alignment.Center) {
                        ScAvatar(name = name, size = AVATAR, avatarUrl = avatarUrl)
                        // Foiz avatar USTIDA: yuklash davomida ham kimning hikoyasi
                        // ketayotgani ko'rinib tursin.
                        if (publishing) {
                            ScUploadRing(progress, size = AVATAR, stroke = 2.5.dp)
                        }
                    }
                }
            }
            Box(
                Modifier.graphicsLayer { alpha = labelAlpha(collapse()) }
                    .size(22.dp).clip(CircleShape).background(Sc.Brand)
                    // Halqa fon rangida — topbar ustida esa oq, aks holda kulrang doira
                    // ko'k gradientda "kir dog'" bo'lib ko'rinardi.
                    .border(2.5.dp, if (onHeader) Color.White else Sc.Bg, CircleShape)
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
                    // ⚠️ Matn EMAS, ikonka. «+» harf sifatida shrift qatoriga (baseline,
                    // ascender/descender) tayanadi va doira ichida ko'zga tashlanadigan
                    // darajada pastga surilib qolardi. Vektor esa o'z ramkasining aynan
                    // markazida chizilgan.
                    Icon(
                        AppIcons.Plus,
                        "Hikoya qo'shish",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        ScText(
            when {
                publishing && progress != null -> "Yuklanmoqda ${scUploadPercent(progress)}"
                // Fayl ketib bo'lgan, server hikoyani yaratmoqda — foiz o'rniga holat.
                publishing -> "Tayyorlanmoqda…"
                else -> "Hikoyam"
            },
            11.5f,
            FontWeight.SemiBold,
            storyLabelColor(active = hasMine, onHeader = onHeader),
            Modifier.width(CELL).graphicsLayer { alpha = labelAlpha(collapse()) },
            maxLines = 1,
        )
    }
}

/**
 * Katak ostidagi yozuv rangi: faol (hikoyasi bor / ko'rilmagan) — to'q, aks holda xira.
 * Ko'k topbar ustida ikkalasi ham oq, farqi shaffoflikda.
 */
@Composable
private fun storyLabelColor(active: Boolean, onHeader: Boolean): Color = when {
    onHeader -> if (active) Color.White else Color.White.copy(alpha = 0.7f)
    active -> Sc.Ink
    else -> Sc.Muted
}

/**
 * Bitta muallif katakchasi.
 *
 * Halqa **faqat `hasUnseen`** bo'lganda yonadi — bu serverdan keladi, klientda hisoblanmaydi
 * (§2). Ko'rib bo'lingan guruhda halqa xira chiziq bo'lib qoladi.
 */
@Composable
private fun StoryCell(
    group: StoryGroup,
    onHeader: Boolean,
    collapse: () -> Float,
    onClick: () -> Unit,
) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Har hikoya uchun bitta bo'lak: ko'rilmagani yorqin, ko'rilgani xira — Telegram
        // va Instagramdagi kabi, ya'ni "yana nechtasi qolgani" bir qarashda ko'rinadi.
        StoryRing(segments = group.stories.map { !it.seen }, onHeader = onHeader) {
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
            storyLabelColor(active = group.hasUnseen, onHeader = onHeader),
            Modifier.width(CELL)
                .padding(horizontal = 2.dp)
                .graphicsLayer { alpha = labelAlpha(collapse()) },
            maxLines = 1,
        )
    }
}

/**
 * Avatar atrofidagi halqa — **hikoyalar soniga bo'lingan** (Telegram/Instagramdagidek).
 *
 * [segments] — har hikoya uchun bittadan bayroq: `true` — ko'rilmagan (brend gradienti),
 * `false` — ko'rilgan (xira chiziq). Ro'yxat bo'sh bo'lsa (hikoya yo'q) butun halqa xira
 * bo'lib chiziladi.
 *
 * Nega bo'laklar `drawBehind` da chiziladi, `background(brush)` bilan emas: to'ldirilgan
 * doira **bitta** shakl, uni bo'lakka ajratib bo'lmaydi. Yoylar esa kerakli sondagi
 * bo'lakni va ular orasidagi bo'shliqni beradi.
 *
 * Halqa bilan avatar orasida oraliq bor ([GAP]) — halqa avatarga «yopishib» qolmaydi.
 */
@Composable
private fun StoryRing(segments: List<Boolean>, onHeader: Boolean, content: @Composable () -> Unit) {
    // Ko'k gradient ustida brend gradienti yo'qoladi — u yerda halqa oq bo'ladi,
    // ko'rilgani esa shaffof oq (fon ustidagi xira kulrang chiziqning o'rnida).
    val litBrush =
        if (onHeader) SolidColor(Color.White)
        else Brush.linearGradient(listOf(Sc.BrandLight, Sc.Brand, Sc.Violet))
    val dimBrush = SolidColor(if (onHeader) Color.White.copy(alpha = 0.38f) else Sc.Border)
    // Ro'yxat bo'sh — bitta xira halqa (hali hikoya yo'q).
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
            // Bo'laklar ko'payganda bo'shliq ham kichrayadi — aks holda 10 ta hikoyada
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
 * Lenta yig'ilganda (bosh ekran topbari siqilganda) uning o'rnini bosadigan **kichik
 * to'plam** — Telegramdagidek bir-birining ustiga chiqqan 3 tagacha avatar.
 *
 * Bosilganda ekran tepasiga qaytaradi ([onClick]), ya'ni to'liq lenta ko'rinadi. Hikoya
 * umuman bo'lmasa (o'zimniki ham, boshqalarniki ham) hech nima chizilmaydi.
 *
 * ⚠️ Xabarlar ekranida bu ishlatilmaydi: u yerda lentaning O'ZI shu to'plamga aylanadi
 * ([StoriesRow] `collapse`), ya'ni ikkita alohida ko'rinish o'rniga bitta uzluksiz harakat.
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
 * Tanlangan video hikoya uchun juda uzunmi.
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
    "Hikoya 1 daqiqadan uzun bo'lmasin. Videoni qisqartirib qayta tanlang."
