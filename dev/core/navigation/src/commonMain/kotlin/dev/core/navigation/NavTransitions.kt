package dev.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry

/**
 * Ekrandan ekranga o'tish animatsiyalari.
 *
 * Nega animatsiya kerak: animatsiyasiz almashishda yangi ekran faqat to'liq kompozitsiya va
 * o'lchov tugagach paydo bo'ladi — foydalanuvchi shu paytgacha eski ekranni ko'rib turadi va
 * bu **qotib qolgandek** seziladi. Siljish esa o'sha kechikishni yashiradi: harakat darhol
 * boshlanadi, mazmun esa yo'l-yo'lakay chiziladi. Shuning uchun animatsiyali o'tish
 * animatsiyasizdan **tezroq** tuyuladi.
 *
 * Ikki xil naqsh ishlatiladi:
 * - **Push/pop** (tafsilot ekranlari) — gorizontal siljish: yangi ekran o'ngdan kiradi,
 *   orqaga qaytishda teskarisi. Bu ierarxiyani ko'rsatadi.
 * - **Tab** (pastki panel) — faqat fade: tablar bir darajada, ular orasida "chuqurlik" yo'q,
 *   shuning uchun siljish noto'g'ri ma'no berardi.
 */

/** O'tish davomiyligi — 280 ms silliq, lekin sekin tuyulmaydi. */
private const val PUSH_MS = 280
private const val FADE_MS = 180

private val pushEasing = FastOutSlowInEasing

// --- Push/pop: tafsilot ekranlari (NavHost sukut qiymati sifatida beriladi) ---

/** Yangi ekran o'ngdan kirib keladi. */
val PushEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(SlideDirection.Left, tween(PUSH_MS, easing = pushEasing)) +
        fadeIn(tween(PUSH_MS, easing = pushEasing))
}

/** Eski ekran chapga siljib chiqadi. */
val PushExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(SlideDirection.Left, tween(PUSH_MS, easing = pushEasing)) +
        fadeOut(tween(PUSH_MS, easing = pushEasing))
}

/** Orqaga qaytish — oldingi ekran chapdan qaytib kiradi. */
val PopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(SlideDirection.Right, tween(PUSH_MS, easing = pushEasing)) +
        fadeIn(tween(PUSH_MS, easing = pushEasing))
}

/** Orqaga qaytish — joriy ekran o'ngga siljib chiqadi. */
val PopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(SlideDirection.Right, tween(PUSH_MS, easing = pushEasing)) +
        fadeOut(tween(PUSH_MS, easing = pushEasing))
}

// --- Tab: pastki paneldagi bo'limlar ---

/** Tab almashish — faqat fade (tablar bir darajada, siljish ierarxiya ma'nosini berardi). */
val TabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(FADE_MS))
}

val TabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(FADE_MS))
}

// --- Animatsiyasiz (kerak bo'lganda) ---

val NoEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { EnterTransition.None }

val NoExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { ExitTransition.None }
