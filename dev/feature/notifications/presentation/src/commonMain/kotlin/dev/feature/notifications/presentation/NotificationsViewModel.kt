package dev.feature.notifications.presentation

import dev.core.common.event.RealtimeSignals
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.model.NotificationType
import dev.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ro'yxatdagi bitta qator — bitta bildirishnoma yoki BIR ODAMDAN kelgan bir nechtasi.
 *
 * Chat bildirishnomalari guruhlanadi: bitta suhbatdosh ketma-ket besh marta yozsa, ro'yxat
 * ham beshta bir xil qatorga to'lib ketardi va boshqa hamma narsani pastga surib yuborardi
 * (skrinshotda "Oybek Gafurov" ikki marta). Endi ular bitta qator: eng oxirgi xabar matni
 * va yonida nechtaligi.
 *
 * [ids] — guruhdagi HAMMA bildirishnoma id'si: qator bosilganda barchasi o'qilgan bo'ladi,
 * aks holda ro'yxat yopilgach yashiringan qatorlar o'qilmagan bo'lib qolaverardi.
 */
data class NotificationRow(
    val notification: AppNotification,
    val ids: List<String>,
) {
    /** Guruhdagi bildirishnomalar soni — 1 bo'lsa nishon chizilmaydi. */
    val count: Int get() = ids.size

    /** Guruhda kamida bittasi o'qilmagan bo'lsa qator o'qilmagan hisoblanadi. */
    val id: String get() = notification.id
}

data class NotificationsUiState(
    val items: List<NotificationRow> = emptyList(),
    val unreadCount: Int = 0,
    /**
     * Serverdan o'qilyapti. Skelet FAQAT ro'yxat bo'sh bo'lganda ko'rsatiladi — keshdagi
     * bildirishnomalar bo'lsa ekran darrov to'ladi va yangilanish sezilmaydi.
     */
    val loading: Boolean = false,
    /**
     * Inline xato — faqat ko'rsatadigan HECH NARSA bo'lmaganda chiziladi. Kesh bo'lsa xato
     * yashiriladi: eski ro'yxat ustiga qizil qalqon chiqarish foydalanuvchiga hech narsa
     * bermaydi, u baribir ro'yxatni o'qiy oladi.
     */
    val error: String? = null,
    /**
     * Serverdan o'qilyapti va ro'yxat allaqachon to'la — "tepadan tortish" indikatori
     * shu bayroq bilan aylanadi ([loading] esa skelet uchun, bo'sh ro'yxatda).
     */
    val refreshing: Boolean = false,
)

class NotificationsViewModel(
    private val repository: NotificationRepository,
) : ViewModel() {

    private val remote = MutableStateFlow(RemoteState(loading = true))

    val state: StateFlow<NotificationsUiState> =
        combine(
            repository.observeAll(),
            repository.observeUnreadCount(),
            remote,
        ) { items, unread, remoteState ->
            NotificationsUiState(
                items = groupChatNotifications(items),
                unreadCount = unread,
                loading = remoteState.loading && items.isEmpty(),
                error = remoteState.error?.takeIf { items.isEmpty() },
                refreshing = remoteState.loading && items.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationsUiState(loading = true))

    init {
        refresh()
        // Yangi xabar kelganda ro'yxat O'ZI yangilanadi. Ilgari server bildirishnomani
        // qo'shar, lekin ekran buni bilmasdi: foydalanuvchi xabarni chatda ko'rgan bo'lsa
        // ham bildirishnomalar ro'yxatida u faqat qo'lda tortib yangilagandan keyin
        // paydo bo'lardi.
        viewModelScope.launch {
            RealtimeSignals.incomingMessages.collect { refresh() }
        }
    }

    fun refresh() {
        remote.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = repository.refresh()
            remote.value = RemoteState(
                loading = false,
                error = (result as? Resource.Error)?.message,
            )
        }
    }

    /**
     * Bildirishnoma bosildi — o'qilgan deb belgilaydi.
     *
     * Ekran almashishi bu yerda EMAS: `target` → route xaritasi navigatsiya grafiga tegishli
     * va u `StudentShell` da yig'iladi (qarang [dev.feature.notifications.domain.model.NotificationTarget]).
     */
    fun onOpened(row: NotificationRow) {
        // Allaqachon o'qilgan bo'lsa tegilmaydi — aks holda ro'yxatni har aylantirganda
        // keraksiz `POST /v1/notifications/read` ketardi. Guruhdagi HAMMA qator
        // belgilanadi: foydalanuvchi ular o'rniga bitta qatorni ko'rgan.
        viewModelScope.launch {
            row.ids.forEach { id -> repository.markRead(id) }
        }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    private data class RemoteState(val loading: Boolean = false, val error: String? = null)
}

/**
 * `CHAT` turidagi KETMA-KET bildirishnomalarni bitta odam bo'yicha birlashtiradi.
 *
 * Faqat ketma-ketlari: oradan boshqa bildirishnoma o'tgan bo'lsa vaqt tartibi buzilmasin
 * (aks holda ikki kun oldingi xabar bugungi qatorga qo'shilib ketardi). Boshqa turlar
 * (so'rov, e'lon, tizim) hech qachon birlashtirilmaydi — ularning har biri alohida hodisa.
 */
internal fun groupChatNotifications(items: List<AppNotification>): List<NotificationRow> {
    val rows = mutableListOf<NotificationRow>()
    items.forEach { item ->
        val previous = rows.lastOrNull()
        val mergeable = previous != null &&
            item.type == NotificationType.CHAT &&
            previous.notification.type == NotificationType.CHAT &&
            previous.notification.title == item.title
        if (mergeable) {
            // Ro'yxat yangidan eskiga: guruhning KO'RINADIGAN qatori birinchisi, ya'ni
            // eng so'nggi xabar — u o'zgarmaydi, faqat id'lar to'planadi.
            rows[rows.lastIndex] = previous.copy(ids = previous.ids + item.id)
        } else {
            rows += NotificationRow(item, listOf(item.id))
        }
    }
    return rows
}
