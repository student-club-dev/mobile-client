package dev.core.uikit.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import dev.core.uikit.generated.resources.Res
import dev.core.uikit.generated.resources.ic_add
import dev.core.uikit.generated.resources.ic_ads
import dev.core.uikit.generated.resources.ic_archive
import dev.core.uikit.generated.resources.ic_attach
import dev.core.uikit.generated.resources.ic_back
import dev.core.uikit.generated.resources.ic_ball
import dev.core.uikit.generated.resources.ic_bell
import dev.core.uikit.generated.resources.ic_book
import dev.core.uikit.generated.resources.ic_briefcase
import dev.core.uikit.generated.resources.ic_cafe
import dev.core.uikit.generated.resources.ic_call
import dev.core.uikit.generated.resources.ic_cart
import dev.core.uikit.generated.resources.ic_chat
import dev.core.uikit.generated.resources.ic_check_double
import dev.core.uikit.generated.resources.ic_chevron_right
import dev.core.uikit.generated.resources.ic_chevron_updown
import dev.core.uikit.generated.resources.ic_close
import dev.core.uikit.generated.resources.ic_debate
import dev.core.uikit.generated.resources.ic_emoji
import dev.core.uikit.generated.resources.ic_filter
import dev.core.uikit.generated.resources.ic_gamepad
import dev.core.uikit.generated.resources.ic_home
import dev.core.uikit.generated.resources.ic_home_filled
import dev.core.uikit.generated.resources.ic_home_filled_green
import dev.core.uikit.generated.resources.ic_laptop
import dev.core.uikit.generated.resources.ic_map
import dev.core.uikit.generated.resources.ic_messages
import dev.core.uikit.generated.resources.ic_mic
import dev.core.uikit.generated.resources.ic_more
import dev.core.uikit.generated.resources.ic_pin
import dev.core.uikit.generated.resources.ic_pizza
import dev.core.uikit.generated.resources.ic_search
import dev.core.uikit.generated.resources.ic_send_enter
import dev.core.uikit.generated.resources.ic_tag
import dev.core.uikit.generated.resources.ic_tools
import dev.core.uikit.generated.resources.ic_university
import dev.core.uikit.generated.resources.ic_university_badge
import dev.core.uikit.generated.resources.ic_users
import org.jetbrains.compose.resources.vectorResource

/**
 * Dizayn ikonalari.
 *
 * Manba — dizayner bergan SVG'lar (`design/icons`), ular vektor-drawable XML ga
 * o'girilib `composeResources/drawable` ga qo'yilgan va shu yerdan o'qiladi.
 * Ikonani yangilash uchun faqat XML ni almashtirish kifoya — Kotlin kodiga tegilmaydi.
 *
 * Nega XML, qo'lda yozilgan `ImageVector` emas: `stroke-linecap` / `stroke-linejoin`
 * kabi atributlar fayldan keladi. Qo'lda ko'chirilganda ular kod default'iga tushib
 * qolib, ba'zi ikonalar dizayndagidan farq qilgan edi.
 *
 * Rang XML da qora qilib "pishirilgan" — `Icon(tint = ...)` uni chaqiruv joyida
 * bo'yaydi, shuning uchun bitta ikona turli ekranlarda turli rangda ishlaydi.
 *
 * Barcha a'zolar `@Composable` getter: chaqiruv joyi o'zgarmaydi
 * (`Icon(ScIcons.Search, ...)` avvalgidek yoziladi), lekin ular faqat kompozitsiya
 * ichida o'qiladi — `enum` yoki top-level `val` da saqlab bo'lmaydi.
 */
object ScIcons {

    // --- Pastki panel ------------------------------------------------------

    val Home: ImageVector @Composable get() = vectorResource(Res.drawable.ic_home)
    val Cap: ImageVector @Composable get() = vectorResource(Res.drawable.ic_university)

    /** Maketdagi variant — topbar chipi va universitet selektori (o'ng tayoqchali). */
    val CapBadge: ImageVector @Composable get() = vectorResource(Res.drawable.ic_university_badge)

    val FileText: ImageVector @Composable get() = vectorResource(Res.drawable.ic_ads)
    val Message: ImageVector @Composable get() = vectorResource(Res.drawable.ic_messages)
    val MessageLines: ImageVector @Composable get() = vectorResource(Res.drawable.ic_debate)
    val Plus: ImageVector @Composable get() = vectorResource(Res.drawable.ic_add)

    // --- Topbar tugmalari --------------------------------------------------

    val ChatRound: ImageVector @Composable get() = vectorResource(Res.drawable.ic_chat)
    val Bell: ImageVector @Composable get() = vectorResource(Res.drawable.ic_bell)
    val Search: ImageVector @Composable get() = vectorResource(Res.drawable.ic_search)
    val Filter: ImageVector @Composable get() = vectorResource(Res.drawable.ic_filter)

    /** Orqaga qaytish hamisha shu `‹`, X emas. */
    val ChevronLeft: ImageVector @Composable get() = vectorResource(Res.drawable.ic_back)

    val ChevronRight: ImageVector @Composable get() = vectorResource(Res.drawable.ic_chevron_right)

    /** Universitet selektoridagi tepa-past chevron. */
    val ChevronUpDown: ImageVector @Composable get() = vectorResource(Res.drawable.ic_chevron_updown)

    val Close: ImageVector @Composable get() = vectorResource(Res.drawable.ic_close)

    // --- Kontent -----------------------------------------------------------

    val Map: ImageVector @Composable get() = vectorResource(Res.drawable.ic_map)
    val Briefcase: ImageVector @Composable get() = vectorResource(Res.drawable.ic_briefcase)
    val Users: ImageVector @Composable get() = vectorResource(Res.drawable.ic_users)
    val MapPin: ImageVector @Composable get() = vectorResource(Res.drawable.ic_pin)
    val Laptop: ImageVector @Composable get() = vectorResource(Res.drawable.ic_laptop)
    val Gamepad: ImageVector @Composable get() = vectorResource(Res.drawable.ic_gamepad)
    val Coffee: ImageVector @Composable get() = vectorResource(Res.drawable.ic_cafe)
    val Cart: ImageVector @Composable get() = vectorResource(Res.drawable.ic_cart)
    val Medal: ImageVector @Composable get() = vectorResource(Res.drawable.ic_ball)
    val Wrench: ImageVector @Composable get() = vectorResource(Res.drawable.ic_tools)
    val PhoneCall: ImageVector @Composable get() = vectorResource(Res.drawable.ic_call)
    val DotsVertical: ImageVector @Composable get() = vectorResource(Res.drawable.ic_more)
    val Paperclip: ImageVector @Composable get() = vectorResource(Res.drawable.ic_attach)
    val Smile: ImageVector @Composable get() = vectorResource(Res.drawable.ic_emoji)
    val Mic: ImageVector @Composable get() = vectorResource(Res.drawable.ic_mic)

    /** Chiquvchi xabar tasdig'i (✓✓). */
    val DoubleCheck: ImageVector @Composable get() = vectorResource(Res.drawable.ic_check_double)

    /** Chegirma bildirishnomasi. */
    val DiscountTag: ImageVector @Composable get() = vectorResource(Res.drawable.ic_tag)

    /**
     * Xabar jo'natish (`ic_send_enter.svg`).
     *
     * To'plamda soxta iOS klaviaturasi uchun `ic_shift` / `ic_backspace` ham bor, lekin
     * qidiruvda tizim klaviaturasi ishlatiladi — ular hech qayerda chizilmaydi, shuning
     * uchun resurs sifatida yuklanmaydi. Manba SVG'lari `design/icons` da turibdi.
     */
    val Return: ImageVector @Composable get() = vectorResource(Res.drawable.ic_send_enter)

    // --- Dizayn to'plamida yo'q -------------------------------------------

    /** Arxiv — dizaynda yo'q, ilovaning o'z funksiyasi; to'plam uslubida chizilgan. */
    val Archive: ImageVector @Composable get() = vectorResource(Res.drawable.ic_archive)

    /** Pitsa bo'lagi ("Ovqatlar") — ko'p rangli, [ScGlyph] bilan chiziladi. */
    val Pizza: ImageVector @Composable get() = vectorResource(Res.drawable.ic_pizza)

    // --- Ko'p rangli (ranglari XML da, [ScGlyph] bilan chiziladi) ----------

    /** `ic_book` — "Yordam e'lonlari" (pushti kitob). */
    val Book: ImageVector @Composable get() = vectorResource(Res.drawable.ic_book)

    /** `ic_home_filled` — ijara kvartira kartochkasi (to'q sariq uy). */
    val HouseFilled: ImageVector @Composable get() = vectorResource(Res.drawable.ic_home_filled)

    /** `ic_home_filled` ning yashil varianti — "Ijara" bo'limi va segment. */
    val HouseFilledGreen: ImageVector @Composable get() = vectorResource(Res.drawable.ic_home_filled_green)
}
