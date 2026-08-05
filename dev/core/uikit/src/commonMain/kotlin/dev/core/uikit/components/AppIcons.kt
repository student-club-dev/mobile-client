package dev.core.uikit.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.core.uikit.generated.resources.Res
import dev.core.uikit.generated.resources.ic_apple
import dev.core.uikit.generated.resources.ic_calendar
import dev.core.uikit.generated.resources.ic_camera
import dev.core.uikit.generated.resources.ic_check
import dev.core.uikit.generated.resources.ic_chevron_down
import dev.core.uikit.generated.resources.ic_clock
import dev.core.uikit.generated.resources.ic_eye
import dev.core.uikit.generated.resources.ic_eye_off
import dev.core.uikit.generated.resources.ic_film
import dev.core.uikit.generated.resources.ic_google
import dev.core.uikit.generated.resources.ic_image_icon
import dev.core.uikit.generated.resources.ic_video
import dev.core.uikit.generated.resources.ic_lock
import dev.core.uikit.generated.resources.ic_log_out
import dev.core.uikit.generated.resources.ic_mail
import dev.core.uikit.generated.resources.ic_pencil
import dev.core.uikit.generated.resources.ic_settings
import dev.core.uikit.generated.resources.ic_shield_check
import dev.core.uikit.generated.resources.ic_store
import dev.core.uikit.generated.resources.ic_telegram
import org.jetbrains.compose.resources.vectorResource

/**
 * Ilovaning qo'shimcha ikonalari — auth va forma ekranlari uchun (pochta, qulf, kalendar,
 * ijtimoiy logolar). Dizayner to'plamida bunday ikonalar yo'q.
 *
 * To'plamda ekvivalenti bor nomlar to'g'ridan-to'g'ri [ScIcons] ga yo'naltirilgan, shunda
 * eski ekranlar ham o'sha yagona to'plamdan chizadi.
 *
 * Hammasi — `composeResources/drawable/ic_*.xml`. Kodda birorta ham vektor yo'li
 * saqlanmaydi: ikonani almashtirish = XML ni almashtirish.
 */
object AppIcons {

    // --- Dizayner to'plamidan ([ScIcons] orqali) ---------------------------

    /** `ic_university` */
    val GraduationCap: ImageVector @Composable get() = ScIcons.Cap

    /** `ic_search` */
    val Search: ImageVector @Composable get() = ScIcons.Search

    /** `ic_close` — faqat modal/dialog yopish uchun. */
    val Close: ImageVector @Composable get() = ScIcons.Close

    /** `ic_debate` */
    val MessageSquare: ImageVector @Composable get() = ScIcons.MessageLines

    /** `ic_briefcase` */
    val Briefcase: ImageVector @Composable get() = ScIcons.Briefcase

    /** `ic_home` */
    val Home: ImageVector @Composable get() = ScIcons.Home

    /** `ic_bell` */
    val Bell: ImageVector @Composable get() = ScIcons.Bell

    /** `ic_add` */
    val Plus: ImageVector @Composable get() = ScIcons.Plus

    /** `ic_chevron_right` */
    val ChevronRight: ImageVector @Composable get() = ScIcons.ChevronRight

    /** `ic_chevron_right` — "oldinga" ma'nosida. */
    val ArrowRight: ImageVector @Composable get() = ScIcons.ChevronRight

    /** `ic_back` — orqaga qaytish hamisha `‹`, hech qachon `✕` emas. */
    val ArrowLeft: ImageVector @Composable get() = ScIcons.ChevronLeft

    /** `ic_filter` */
    val Filter: ImageVector @Composable get() = ScIcons.Filter

    /** `ic_ads` */
    val FileText: ImageVector @Composable get() = ScIcons.FileText

    /** To'plamda yo'q — arxiv uchun to'plam uslubida chizilgan. */
    val Bookmark: ImageVector @Composable get() = ScIcons.Archive

    // --- Auth / forma ikonalari -------------------------------------------

    val Mail: ImageVector @Composable get() = vectorResource(Res.drawable.ic_mail)
    val Lock: ImageVector @Composable get() = vectorResource(Res.drawable.ic_lock)
    val Eye: ImageVector @Composable get() = vectorResource(Res.drawable.ic_eye)
    val EyeOff: ImageVector @Composable get() = vectorResource(Res.drawable.ic_eye_off)
    val Clock: ImageVector @Composable get() = vectorResource(Res.drawable.ic_clock)
    val Calendar: ImageVector @Composable get() = vectorResource(Res.drawable.ic_calendar)
    val Check: ImageVector @Composable get() = vectorResource(Res.drawable.ic_check)
    val ChevronDown: ImageVector @Composable get() = vectorResource(Res.drawable.ic_chevron_down)
    val ShieldCheck: ImageVector @Composable get() = vectorResource(Res.drawable.ic_shield_check)
    val Store: ImageVector @Composable get() = vectorResource(Res.drawable.ic_store)
    val Settings: ImageVector @Composable get() = vectorResource(Res.drawable.ic_settings)
    val LogOut: ImageVector @Composable get() = vectorResource(Res.drawable.ic_log_out)
    val Pencil: ImageVector @Composable get() = vectorResource(Res.drawable.ic_pencil)
    val Camera: ImageVector @Composable get() = vectorResource(Res.drawable.ic_camera)

    /** Video kamerasi — «Kamera» ([Camera]) dan farqli: bu galereyadagi videoni bildiradi. */
    val Video: ImageVector @Composable get() = vectorResource(Res.drawable.ic_video)
    val ImageIcon: ImageVector @Composable get() = vectorResource(Res.drawable.ic_image_icon)

    /** Kinolenta — GALEREYADAGI video ([Video] esa kamerani bildiradi). */
    val Film: ImageVector @Composable get() = vectorResource(Res.drawable.ic_film)

    // --- Ijtimoiy logolar (ranglari o'zgarmaydi, `Image` bilan chiziladi) --

    val Apple: ImageVector @Composable get() = vectorResource(Res.drawable.ic_apple)
    val Google: ImageVector @Composable get() = vectorResource(Res.drawable.ic_google)
    val Telegram: ImageVector @Composable get() = vectorResource(Res.drawable.ic_telegram)
}
