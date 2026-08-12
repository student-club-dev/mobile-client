package dev.feature.notifications.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Bildirishnomalar ekrani matnlari. Sukut qiymatlar — inglizcha. */
data class NotificationsStrings(
    val title: String = "Notifications",
    val unreadCount: (Int) -> String = { "$it unread notification" + if (it == 1) "" else "s" },
    val allRead: String = "All caught up",
    val emptySubtitle: String = "Updates will show up here",
    val markAllRead: (Int) -> String = { "Mark all read ($it)" },
    val minutesAgo: (Int) -> String = { "$it min ago" },
    val hoursAgo: (Int) -> String = { "$it h ago" },
    val daysAgo: (Int) -> String = { "$it d ago" },
)

private val NotificationsEn = NotificationsStrings()

private val NotificationsRu = NotificationsStrings(
    title = "Уведомления",
    unreadCount = { "$it непрочитанных уведомлений" },
    allRead = "Всё прочитано",
    emptySubtitle = "Новости появятся здесь",
    markAllRead = { "Прочитать все ($it)" },
    minutesAgo = { "$it мин назад" },
    hoursAgo = { "$it ч назад" },
    daysAgo = { "$it дн назад" },
)

private val NotificationsUz = NotificationsStrings(
    title = "Bildirishnomalar",
    unreadCount = { "$it ta o'qilmagan bildirishnoma" },
    allRead = "Hammasi o'qilgan",
    emptySubtitle = "Yangiliklar shu yerda to'planadi",
    markAllRead = { "Hammasini o'qildi ($it)" },
    minutesAgo = { "$it daqiqa oldin" },
    hoursAgo = { "$it soat oldin" },
    daysAgo = { "$it kun oldin" },
)

@Composable
@ReadOnlyComposable
internal fun notificationsStrings(): NotificationsStrings =
    rememberStrings(NotificationsEn, NotificationsRu, NotificationsUz)

/** Vaqt yorlig'i sof funksiyada hisoblanadi — Compose'dan tashqarida. */
internal fun notificationsStringsNow(): NotificationsStrings =
    AppLocale.pick(NotificationsEn, NotificationsRu, NotificationsUz)
