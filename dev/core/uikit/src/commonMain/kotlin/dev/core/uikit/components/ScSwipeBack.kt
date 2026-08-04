package dev.core.uikit.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gorizontal harakat vertikaldan shuncha marta ustun bo'lsagina "orqaga" deb qaraladi.
 *
 * Imo-ishora ekranning ISTALGAN joyidan boshlanadi, shuning uchun oddiy `dx > dy` yetarli
 * emas: ro'yxatni tez surganda barmoq deyarli har doim biroz yon tomonga ham ketadi va
 * qiya harakatlar suhbatni yopib yuborardi.
 */
private const val HorizontalBias = 1.6f

/** Shundan tez surilgan bo'lsa masofaga qaralmaydi — qisqa, lekin keskin harakat ham yetadi. */
private const val FlingVelocity = 450f

/** Barmoq sekin uzilganda: ekranning shuncha qismi bosib o'tilgan bo'lsa — orqaga. */
private const val CommitFraction = 0.32f

/** Barmoq uzilgandan keyingi tekislanish — ochilish/yopilish egrisi bilan bir xil. */
private const val SettleDuration = 220
private val SwipeEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * Tagdagi ekran ustki ekrandan shuncha sekinroq siljiydi (iOS'dagi kabi).
 *
 * Ikkalasi baravar yursa ular bitta varaqdek ko'rinadi va "tagida turgan ekran" degan
 * tuyg'u yo'qoladi — surish shunchaki chapga siljish bo'lib qolardi.
 */
private const val UnderParallax = 0.25f

/** Tagdagi ekran to'liq berkilgan paytdagi qorayishi — ochilgan sari tarqaydi. */
private const val UnderDim = 0.16f

/**
 * "Orqaga surish" holati: `0` — ustki ekran joyida, kenglik — u butunlay o'ngga chiqib
 * ketgan.
 *
 * Holat ekran darajasida ko'tarilishi mumkin ([rememberScSwipeBackState]): tagdagi ekran
 * ham shu qiymatga qarab chiziladi ([Modifier.scSwipeBackUnder]).
 *
 * Qiymat `Animatable` da: bitta qiymat ham barmoqni (surilayotganda), ham tekislanish
 * animatsiyasini boshqaradi — ekran barmoqdan "uzilib" qolmaydi.
 */
@Stable
class ScSwipeBackState internal constructor(private val scope: CoroutineScope) {
    internal val offset = Animatable(0f)

    /** Ekran kengligi — o'lchangandan keyin to'ldiriladi (chegara va ulush shundan). */
    internal var widthPx by mutableStateOf(0f)

    /**
     * Ekran surilyaptimi — ya'ni **tagidagi ekranni chizish kerakmi**.
     *
     * `derivedStateOf` orqali: to'g'ridan-to'g'ri `offset.value` o'qilsa, surishning har
     * kadrida butun ekran qayta kompozitsiya qilinardi. Bu yerda esa qiymat faqat
     * `false ⇄ true` almashganda o'zgaradi.
     */
    private val revealingState = derivedStateOf { offset.value > 0f }
    val revealing: Boolean get() = revealingState.value

    /** `0` — ustki ekran joyida, `1` — butunlay surib chiqarilgan. */
    internal val progress: Float get() = if (widthPx <= 0f) 0f else offset.value / widthPx

    /**
     * Surish paytidagi maqsad qiymati. `offset.value` dan o'qib bo'lmaydi: har bir `snapTo`
     * alohida korutinada bajariladi va ular bir-birini bekor qiladi — oraliq qiymatlar
     * yo'qolib, ekran barmoqdan orqada qolardi.
     */
    private var pending = 0f

    internal fun startDrag() {
        pending = offset.value
    }

    internal fun drag(deltaPx: Float) {
        pending = (pending + deltaPx).coerceIn(0f, widthPx)
        val target = pending
        scope.launch { offset.snapTo(target) }
    }

    /** Barmoq uzildi: tezlikka, u past bo'lsa — bosib o'tilgan yo'lga qarab hal qilinadi. */
    internal fun settle(velocityPx: Float, onBack: () -> Unit) {
        val commit = when {
            velocityPx > FlingVelocity -> true
            velocityPx < -FlingVelocity -> false
            else -> offset.value > widthPx * CommitFraction
        }
        pending = 0f
        scope.launch {
            if (commit) {
                // Avval ekran oxirigacha chiqib ketadi, keyin orqaga qaytiladi: teskarisida
                // ekran barmoq uzilgan joyda muzlab, keyin g'oyib bo'lardi.
                offset.animateTo(widthPx, tween(SettleDuration, easing = SwipeEasing))
                onBack()
                // Chaqiruvchi ekranni yopgani uchun bu qiymat ko'rinmaydi. Baribir
                // qaytariladi: agar ekran biror sababga ko'ra joyida qolsa, u ekrandan
                // tashqarida osilib qolmasin.
                offset.snapTo(0f)
            } else {
                offset.animateTo(0f, tween(SettleDuration, easing = SwipeEasing))
            }
        }
    }
}

@Composable
fun rememberScSwipeBackState(): ScSwipeBackState {
    val scope = rememberCoroutineScope()
    return remember(scope) { ScSwipeBackState(scope) }
}

/**
 * **O'ngga surish — orqaga qaytish** (iOS/Telegram uslubi), ekranning istalgan joyidan.
 *
 * Ekran barmoq ortidan siljiydi va uzilganda hal qilinadi: yetarli surilgan (yoki keskin
 * itarilgan) bo'lsa [onBack] chaqiriladi, aks holda joyiga qaytadi. Ya'ni harakat bekor
 * qilinadigan — tasodifan tegib ketilsa xabar yozayotgan ekrandan chiqib ketilmaydi.
 *
 * ⚠️ Hodisalar odatdagi ([PointerEventPass.Main]) bosqichida, ya'ni **bolalardan KEYIN**
 * kuzatiladi va allaqachon egallangan harakatga tegilmaydi. Ekran ichida gorizontal
 * surishning o'z egalari bor — stiker/emoji lentasi, video pufagidagi vaqt chizig'i, matn
 * maydonidagi kursor — va ular ustidan surish o'sha yerda ishlashi kerak.
 *
 * Tagidagi ekran ko'rinishi uchun holat ko'tariladi: [state] ni [rememberScSwipeBackState]
 * bilan yaratib, pastdagi ekranga [Modifier.scSwipeBackUnder] qo'yiladi.
 *
 * @param enabled `false` bo'lsa imo-ishora o'chadi (ekran o'zi ushlab turgan holat bor —
 *   masalan tanlash rejimi — bo'lsa foydali).
 */
@Composable
fun Modifier.scSwipeBack(
    state: ScSwipeBackState,
    enabled: Boolean = true,
    onBack: () -> Unit,
): Modifier {
    // Har surishda oxirgi chaqiruv ishlatiladi — `pointerInput` qayta ishga tushmasin.
    val back by rememberUpdatedState(onBack)

    return this
        .onSizeChanged { state.widthPx = it.width.toFloat() }
        .pointerInput(state, enabled) {
            if (!enabled) return@pointerInput
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                // Bosishning O'ZI yutilgan bo'lishi normal (masalan `clickable` uni oladi) —
                // shuning uchun `requireUnconsumed = false`. Muhimi keyingi siljishlar.
                val down = awaitFirstDown(requireUnconsumed = false)
                if (state.widthPx <= 0f) return@awaitEachGesture

                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var dx = 0f
                var dy = 0f
                var captured = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (!change.pressed) {
                        if (captured) state.settle(tracker.calculateVelocity().x, back)
                        return@awaitEachGesture
                    }
                    val move = change.positionChange()
                    if (captured) {
                        change.consume()
                        state.drag(move.x)
                        continue
                    }
                    // Harakatni kimdir egalladi — ro'yxat scroll'i, gorizontal lenta, uzoq
                    // bosib belgilash. Bu bizniki emas va boshqa bu imo-ishorada
                    // qatnashmaymiz.
                    if (change.isConsumed) return@awaitEachGesture
                    dx += move.x
                    dy += move.y
                    // Vertikal (yoki qiya) harakat — ro'yxat surilmoqda, aralashmaymiz.
                    if (abs(dy) > slop && abs(dx) < abs(dy) * HorizontalBias) return@awaitEachGesture
                    // Chapga surish ham bizniki emas (o'ngdan chapga hech narsa ochilmaydi).
                    if (dx < -slop) return@awaitEachGesture
                    if (dx > slop && dx > abs(dy) * HorizontalBias) {
                        captured = true
                        state.startDrag()
                        change.consume()
                        state.drag(dx)
                    }
                }
            }
        }
        // Imo-ishora qatlamdan TASHQARIDA turishi shart: aks holda barmoq holati ekran
        // bilan birga siljib, surish o'z-o'zini "quvlab" ketardi.
        .graphicsLayer { translationX = state.offset.value }
}

/**
 * **Tagda turgan ekran** — [scSwipeBack] bilan surilayotgan ekranning ostidan chiqadi.
 *
 * Sekinroq siljiydi ([UnderParallax]) va berkilgan sari qorayadi ([UnderDim]): shundagina
 * u "pastda kutib turgan" ekranga o'xshaydi. Ikkalasi baravar yurganda esa bitta uzun
 * lentaning ikki bo'lagidek ko'rinardi.
 *
 * Qiymatlar `graphicsLayer`/`drawWithContent` ichida o'qiladi — ya'ni surish davomida
 * faqat chizish yangilanadi, kompozitsiya emas.
 */
fun Modifier.scSwipeBackUnder(state: ScSwipeBackState): Modifier = this
    .graphicsLayer {
        // Ustki ekran joyida turganda tagdagisi ham joyida: bu holatda u ko'rinmaydi
        // (yoki umuman yolg'iz qoladi) va uni siljitib qo'yish xato bo'lardi.
        translationX = if (state.offset.value <= 0f) 0f else -(1f - state.progress) * state.widthPx * UnderParallax
    }
    .drawWithContent {
        drawContent()
        val dim = (1f - state.progress) * UnderDim
        if (state.offset.value > 0f && dim > 0f) drawRect(Color.Black, alpha = dim)
    }
