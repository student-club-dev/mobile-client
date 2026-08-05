package dev.feature.calls.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.feature.calls.domain.repository.CallController
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Qo'ng'iroq ekranini **butun ilova ustida** ushlab turadi.
 *
 * Ilovaning ildizida (`App()`) bir marta chaqiriladi. Sabab: kiruvchi qo'ng'iroq
 * foydalanuvchi qaysi ekranda turganidan qat'i nazar ko'rinishi kerak, va u
 * navigatsiya grafiga qo'shilsa har ekran o'ziga marshrut qo'shishga majbur bo'lardi.
 *
 * ⚠️ 1-bosqichda VoIP push **yo'q**: qo'ng'iroq faqat ilova ochiq va `/calls` socket'i
 * ulangan bo'lganda keladi (`handoff/09-CALLS-README.md`). Ilova yopiq bo'lsa server
 * hech narsa yubormaydi.
 */
@Composable
fun CallHost() {
    val controller = koinInject<CallController>()

    // Kanal ilova ishga tushganda ochiladi. Kirmagan foydalanuvchida `tokenProvider`
    // `null` qaytaradi va `SocketIoClient` ulanmaydi: u tarmoqqa umuman chiqmasdan,
    // faqat local `TokenStore` ni qisqa oraliqda tekshirib turadi (`NO_SESSION_POLL_MS`),
    // ya'ni kirilgan zahoti kanal o'zi ko'tariladi. Shu sabab bu yerda auth holatini
    // alohida kuzatish shart emas.
    LaunchedEffect(Unit) { controller.start() }

    val session by controller.session.collectAsStateWithLifecycle()
    if (session == null) return

    // Jonli qo'ng'iroq — butun ekran. Kichraytirish (Telegram'dagi "qo'ng'iroqqa qaytish"
    // chizig'i) ataylab yo'q: yarim ishlaydigan kichraytirish qo'ng'iroqni "yo'qotib
    // qo'yish" ga olib keladi va uni faqat ilovani qayta ochib topsa bo'lardi.
    CallScreen(viewModel = koinViewModel<CallViewModel>(), onClose = {})
}
