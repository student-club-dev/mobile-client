package dev.core.uikit.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import dev.core.uikit.theme.LocalScColors
import dev.core.uikit.theme.angledGradient
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** Splash ko'rinib turadigan vaqt (spetsifikatsiya: kamida 2.5-3.0s). */
val SplashAnimationDuration: Duration = 3_000.milliseconds

/** Popuk tebranishining yarim davri (to'liq davr — 2.2s). */
private const val TasselHalfPeriodMillis = 1_100

/** Spetsifikatsiyadagi asosiy tezlanish egri chizig'i. */
private val SplashEasing = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)

/** Kirish uchun "otilib chiqish" (overshoot) egri chizig'i. */
private val EntranceEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)


/** Uchqunlar: ekran bo'yicha nisbiy joylashuv va sikl davri (ms). */
private val Sparkles = listOf(
    Triple(0.22f, 0.30f, 2_600),
    Triple(0.80f, 0.42f, 3_100),
    Triple(0.30f, 0.72f, 2_850),
)

/**
 * Ilova ochilishidagi splash — brend ko'k gradient ustida shishasimon nishon
 * ([SplashEmblem]) va uning ostida brend yozuvi.
 *
 * Ekranda birorta ham rasm asseti yo'q: nishon Canvas bilan chiziladi, fon effektlari
 * (yumshoq blob'lar, uchqunlar) va butun harakat esa shu yerda.
 *
 * Ekran holatni ushlab turmaydi: tugash payti [onFinished] orqali yuqoriga uzatiladi,
 * navigatsiya qarori chaqiruvchida (`AuthNavHost`) qabul qilinadi. Shu bois bu yerda
 * alohida ViewModel yo'q — saqlanadigan holat ham, I/O ham yo'q, faqat taymer.
 *
 * @param onFinished vaqt tugaganda yoki foydalanuvchi bosib o'tkazib yuborganda chaqiriladi.
 *   Bir marta chaqirilishi kafolatlanadi.
 */
@Composable
fun AnimatedSplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    duration: Duration = SplashAnimationDuration,
) {
    // Fon to'q ko'k — tizim panellarining belgilari oq bo'lishi kerak.
    StatusBarAppearance(darkIcons = false)
    NavigationBarAppearance(darkIcons = false)

    var finished by rememberSaveable { mutableStateOf(false) }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(duration) {
        delay(duration)
        finished = true
    }
    // Skip ham, taymer ham shu yagona yo'ldan o'tadi — onFinished ikki marta chaqirilmaydi.
    LaunchedEffect(finished) { if (finished) currentOnFinished() }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // t=0.0s — nishon: 90%->100%, +10px->0, shaffoflik 0->1 (overshoot bilan).
    val enter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = EntranceEasing),
        label = "splashEnter",
    )
    // Fon tizim splash'idagi tekis rangdan gradientga o'tadi — chegara sezilmasin.
    val backgroundBlend by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = SplashEasing),
        label = "splashBackground",
    )
    // t=1.4s — wordmark; t=1.5s — chiziqcha; t=1.6s — subtitle.
    val titleEnter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 1_400, easing = SplashEasing),
        label = "splashTitleEnter",
    )
    val underlineEnter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 1_500, easing = SplashEasing),
        label = "splashUnderlineEnter",
    )
    val subtitleEnter by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 1_600, easing = SplashEasing),
        label = "splashSubtitleEnter",
    )

    val cycle = rememberInfiniteTransition(label = "splashCycle")
    // Nishon: yumshoq suzish, halo pulsatsiyasi va shisha bo'ylab sirpanuvchi yaltirash.
    val emblemFloat by cycle.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(4_000, easing = SplashEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashEmblemFloat",
    )
    // Halqalar tinimsiz tashqariga tarqaladi — shuning uchun chiziqli va Reverse'siz
    // (Reverse bo'lsa halqalar orqaga, nishonga qarab qaytardi).
    val emblemHalo by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(2_600, easing = LinearEasing)),
        label = "splashEmblemHalo",
    )
    val emblemSweep by cycle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(3_400, easing = LinearEasing)),
        label = "splashEmblemSweep",
    )
    // Popuk — uzluksiz, mustaqil chayqaladi.
    val tassel by cycle.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(TasselHalfPeriodMillis, easing = SplashEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashTassel",
    )

    // Fon: tizim splash'idagi TEKIS rangdan boshlanib, 150 gradusli brend gradientiga
    // o'tadi — ikkalasi orasida chok sezilmasin. Ranglar palitradan, ya'ni dark rejimda
    // avtomatik to'qroq variantga almashadi.
    val colors = LocalScColors.current
    val background = angledGradient(
        150f,
        0f to lerp(colors.splashSolid, colors.splashTop, backgroundBlend),
        0.46f to lerp(colors.splashSolid, colors.splashMid, backgroundBlend),
        1f to lerp(colors.splashSolid, colors.splashBottom, backgroundBlend),
    )

    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .bleedIntoNavigationBar()
            .fillMaxSize()
            // Spetsifikatsiyadagi 44dp burchak faqat "karta" ko'rinishi uchun edi
            // ("if used as a card, not full-screen") — bu ekran to'liq ekranli,
            // shuning uchun burchak yumaloqlanmaydi (aks holda chetlarida ilova foni ko'rinadi).
            .background(background)
            // Bosib o'tkazib yuborish — ripple'siz, splash'da vizual shovqin bo'lmasin.
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { finished = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        SplashAmbience(cycle)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        ) {
            SplashEmblem(
                sweep = emblemSweep,
                halo = emblemHalo,
                tasselDegrees = tassel,
                modifier = Modifier
                    .width(168.dp)
                    .aspectRatio(1f)
                    .graphicsLayer {
                        alpha = min(enter, 1f)
                        val scale = 0.9f + 0.1f * enter
                        scaleX = scale
                        scaleY = scale
                        translationY = emblemFloat.dp.toPx() + 10.dp.toPx() * (1f - enter)
                    },
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = buildAnnotatedString {
                    append("Student")
                    withStyle(SpanStyle(color = colors.splashWordmarkAccent)) { append("Club") }
                },
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = AppFontFamily,
                textAlign = TextAlign.Center,
                // Harflar orasi +4px dan -0.6px gacha "joyiga o'tiradi".
                letterSpacing = (4f - 4.6f * titleEnter).sp,
                modifier = Modifier.graphicsLayer {
                    alpha = min(titleEnter, 1f)
                    translationY = 14.dp.toPx() * (1f - titleEnter)
                },
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .width(66.dp)
                    .height(3.dp)
                    .graphicsLayer { alpha = min(underlineEnter, 1f) }
                    .background(Color.White, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "TALABALAR HAMJAMIYATI",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFontFamily,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = min(subtitleEnter, 1f) },
            )
        }
    }
}

/**
 * Ekranni pastdagi navigatsiya paneli ostiga ham cho'zadi.
 *
 * Ilova ildizi (`App.kt`) butun kontentga navigatsiya paneli uchun padding qo'yadi —
 * bu oddiy ekranlar uchun to'g'ri, lekin splash butun displeyni egallashi kerak,
 * aks holda pastda ilovaning och fonidan tasma qolib ketadi. Shu modifikator o'sha
 * paddingni qaytarib beradi.
 *
 * `WindowInsets.navigationBars` xom qiymatni beradi: `windowInsetsPadding` insets'ni
 * faqat `windowInsetsPadding` zanjiri uchun "iste'mol qiladi", to'g'ridan-to'g'ri
 * o'qishga ta'sir qilmaydi.
 */
@Composable
private fun Modifier.bleedIntoNavigationBar(): Modifier {
    val bottomInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    if (bottomInset == 0) return this
    return this.layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(
                maxHeight = constraints.maxHeight + bottomInset,
                minHeight = constraints.minHeight + bottomInset,
            ),
        )
        // Diqqat: o'z o'lchamimiz sifatida ESKI balandlikni qaytaramiz. Agar kattasini
        // qaytarsak, ota-layout ("fillMaxSize" va Box) o'lchamdan oshgan bolani markazga
        // tekislaydi va ortiqcha balandlik yuqori/pastga TENG bo'linadi — pastda aynan
        // yarmi yetmay qolardi. Bola esa y=0 dan chizilib, pastdagi panel ostiga chiqadi.
        layout(placeable.width, constraints.maxHeight) { placeable.place(0, 0) }
    }
}

/**
 * Fon muhiti — ikkita juda yumshoq oq "blob" va uchta uchqun.
 *
 * Blob'lar `Modifier.blur` bilan emas, shaffoflikka o'tuvchi radial gradient bilan
 * chizilgan: natija bir xil, lekin `blur` Android'da faqat API 31+ da ishlaydi
 * (loyihaning minSdk'si 24) va iOS'da qimmatroq tushadi.
 */
@Composable
private fun SplashAmbience(cycle: InfiniteTransition) {
    val driftA by cycle.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(7_000, easing = SplashEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBlobA",
    )
    val driftB by cycle.animateFloat(
        initialValue = 7f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(9_000, easing = SplashEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashBlobB",
    )
    val twinkles = Sparkles.map { (_, _, period) ->
        cycle.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(period, easing = LinearEasing)),
            label = "splashSparkle$period",
        )
    }

    Canvas(Modifier.fillMaxSize()) {
        fun blob(cx: Float, cy: Float, radius: Float, drift: Float, alpha: Float) {
            val center = Offset(size.width * cx, size.height * cy + drift)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        blob(0.86f, 0.14f, size.width * 0.52f, driftA, 0.10f)
        blob(0.12f, 0.84f, size.width * 0.58f, driftB, 0.08f)

        twinkles.forEachIndexed { index, twinkle ->
            val (x, y, _) = Sparkles[index]
            val t = twinkle.value
            // 0 -> 1 -> 0.4 masshtab, 0 -> 1 -> 0 shaffoflik: qisqa "yonib o'chish".
            val alpha = if (t < 0.5f) t * 2f else (1f - t) * 2f
            val scale = if (t < 0.5f) t * 2f else 1f - 0.6f * ((t - 0.5f) * 2f)
            drawCircle(
                color = Color.White.copy(alpha = 0.7f * alpha),
                radius = 5f * scale * (size.width / 400f),
                center = Offset(size.width * x, size.height * y),
            )
        }
    }
}
