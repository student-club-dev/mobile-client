package dev.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.feature.notifications.domain.model.AppNotification
import dev.feature.notifications.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val items: List<AppNotification> = emptyList(),
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
                items = items,
                unreadCount = unread,
                loading = remoteState.loading && items.isEmpty(),
                error = remoteState.error?.takeIf { items.isEmpty() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationsUiState(loading = true))

    init {
        refresh()
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
    fun onOpened(notification: AppNotification) {
        // Allaqachon o'qilgan bo'lsa tegilmaydi — aks holda ro'yxatni har aylantirganda
        // keraksiz `POST /v1/notifications/read` ketardi.
        if (notification.read) return
        viewModelScope.launch { repository.markRead(notification.id) }
    }

    fun markAllRead() {
        viewModelScope.launch { repository.markAllRead() }
    }

    private data class RemoteState(val loading: Boolean = false, val error: String? = null)
}
