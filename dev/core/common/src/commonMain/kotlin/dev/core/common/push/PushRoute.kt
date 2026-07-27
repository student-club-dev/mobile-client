package dev.core.common.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push bosilganda ochilishi kerak bo'lgan suhbat — **platforma bilan UI o'rtasidagi ko'prik**.
 *
 * Oqim: bildirishnoma bosildi → platforma qatlami (Android'da `MainActivity`, iOS'da
 * `UNUserNotificationCenter` delegate'i) `data.conversationId` ni [set] qiladi → talaba
 * karkasi (`StudentShell`) uni kuzatib turadi va suhbatni ochib [consume] qiladi.
 *
 * Nega global obyekt: bildirishnoma **ilova ishga tushishidan oldin** bosilishi mumkin, ya'ni
 * hali birorta ekran ham, ViewModel ham mavjud emas. Qiymat shu yerda kutib turadi va UI
 * tayyor bo'lgach o'qiladi.
 */
object PushRoute {

    private val _pendingConversationId = MutableStateFlow<String?>(null)

    /** Kutayotgan suhbat id'si yoki `null`. */
    val pendingConversationId: StateFlow<String?> = _pendingConversationId.asStateFlow()

    /** Platforma qatlami chaqiradi (intent/notification payload'idan). */
    fun set(conversationId: String?) {
        if (!conversationId.isNullOrBlank()) _pendingConversationId.value = conversationId
    }

    /** UI suhbatni ochgach chaqiradi — bir marta ishlashi uchun. */
    fun consume() {
        _pendingConversationId.value = null
    }
}
