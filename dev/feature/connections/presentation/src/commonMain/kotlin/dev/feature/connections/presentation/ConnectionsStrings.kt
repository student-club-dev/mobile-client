package dev.feature.connections.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Bog'lanishlar (do'stlar), talaba profili va bloklanganlar ekranlarining matnlari. */
data class ConnectionsStrings(
    // Bo'limlar
    val title: String = "Connections",
    val tabConnections: String = "Connections",
    val tabRequests: String = "Requests",
    val tabSearch: String = "Search",

    // Amallar
    val writeMessage: String = "Send a message",
    val disconnect: String = "Remove connection",
    val block: String = "Block",
    val report: String = "Report",
    val cancel: String = "Cancel",
    val message: String = "Message",
    val connect: String = "Connect",
    val requestSent: String = "Sent",
    val respond: String = "Respond",
    val accept: String = "Accept",
    val decline: String = "Decline",
    val pending: String = "Pending",
    val incoming: String = "Incoming",
    val outgoing: String = "Outgoing",
    val menu: String = "Menu",
    val requestReceived: String = "Wants to connect",

    // Tasdiqlash oynalari
    val disconnectBody: (String) -> String = { "Your connection with $it will be removed. You can send a new request later." },
    val disconnectConfirm: String = "Remove",
    val blockBody: (String) -> String = {
        "$it will be blocked: the connection is removed and neither of you can write to the other. " +
            "Unblocking does not restore the connection."
    },

    // Qidiruv
    val searchHint: String = "Name or username…",
    val searchOneWord: String = "Type a single word — searching by full name doesn't work.",
    val noStudentsForFilters: String = "No students match these filters.",
    val clear: String = "Clear",
    val filterMyUniversity: String = "My university",
    val filterNewPeople: String = "New people",
    val filterMale: String = "Male",
    val filterFemale: String = "Female",
    val sortByName: String = "By name",

    // Shikoyat
    val reportTitle: (String) -> String = { "Report: $it" },
    val reportNote: String = "Comment (optional)",
    val reportSend: String = "Send",
    val reasonSpam: String = "Spam",
    val reasonFraud: String = "Fraud",
    val reasonHarassment: String = "Harassment",
    val reasonInappropriate: String = "Inappropriate",
    val reasonOther: String = "Other",

    // Toast xabarlari
    val connectedWith: (String) -> String = { "You're now connected with $it" },
    val requestWasSent: String = "Request sent",
    val requestDeclined: String = "Request declined",
    val connectionRemoved: String = "Connection removed",
    val userBlocked: (String) -> String = { "$it has been blocked" },
    val reportAccepted: String = "Your report has been received",
    val userUnblocked: (String) -> String = { "$it has been unblocked" },

    // Bloklanganlar ekrani
    val blockedTitle: String = "Blocked",
    val blockedSubtitle: String = "Only students you blocked are listed here. Who blocked you is never shown.",
    val blockedCount: (Int) -> String = { "$it students" },
    val unblock: String = "Unblock",
    val unblockBody: (String) -> String = {
        "$it will be unblocked and will see you in search again. The previous connection is not restored — " +
            "you'd need to send a new request."
    },
    val unblockConfirm: String = "Unblock",
    val blockedSince: (String) -> String = { "Blocked since $it" },

    // Talaba profili varag'i
    val loadingProfile: String = "loading…",
    val requestedYou: String = "Wants to connect",
    val mute: String = "Mute",
    val muteSoon: String = "Muting is coming soon",
    val call: String = "Call",
    val callSoon: String = "Calls are coming soon",
    val video: String = "Video",
    val videoSoon: String = "Video calls are coming soon",
    val usernameLabel: String = "Username",
    val phoneLabel: String = "Mobile number",
    val bioLabel: String = "Bio",
    val universityLabel: String = "University",
    val courseLabel: String = "Year",
    val genderLabel: String = "Gender",
    val genderMale: String = "Male",
    val genderFemale: String = "Female",
    val lastSeenOn: (String) -> String = { "last seen $it" },
    val masterDegree: String = "Master's",
    val courseYear: (String) -> String = { "Year $it" },
)

private val ConnectionsEn = ConnectionsStrings()

private val ConnectionsRu = ConnectionsStrings(
    title = "Друзья",
    tabConnections = "Друзья",
    tabRequests = "Заявки",
    tabSearch = "Поиск",

    writeMessage = "Написать сообщение",
    disconnect = "Удалить из друзей",
    block = "Заблокировать",
    report = "Пожаловаться",
    cancel = "Отмена",
    message = "Написать",
    connect = "Добавить",
    requestSent = "Отправлено",
    respond = "Ответить",
    accept = "Принять",
    decline = "Отклонить",
    pending = "Ожидает",
    incoming = "Входящие",
    outgoing = "Исходящие",
    menu = "Меню",
    requestReceived = "Хочет добавить",

    disconnectBody = { "Связь с $it будет удалена. Позже вы сможете отправить заявку снова." },
    disconnectConfirm = "Удалить",
    blockBody = {
        "$it будет заблокирован: связь удалится, и вы не сможете писать друг другу. " +
            "После разблокировки связь не восстановится."
    },

    searchHint = "Имя или username…",
    searchOneWord = "Введите одно слово — поиск по полному имени не работает.",
    noStudentsForFilters = "Нет студентов по этим фильтрам.",
    clear = "Сбросить",
    filterMyUniversity = "Мой вуз",
    filterNewPeople = "Новые люди",
    filterMale = "Мужской",
    filterFemale = "Женский",
    sortByName = "По имени",

    reportTitle = { "Жалоба: $it" },
    reportNote = "Комментарий (необязательно)",
    reportSend = "Отправить",
    reasonSpam = "Спам",
    reasonFraud = "Мошенничество",
    reasonHarassment = "Оскорбления",
    reasonInappropriate = "Неприемлемое",
    reasonOther = "Другое",

    connectedWith = { "Вы теперь друзья с $it" },
    requestWasSent = "Заявка отправлена",
    requestDeclined = "Заявка отклонена",
    connectionRemoved = "Связь удалена",
    userBlocked = { "$it заблокирован" },
    reportAccepted = "Ваша жалоба принята",
    userUnblocked = { "$it разблокирован" },

    blockedTitle = "Заблокированные",
    blockedSubtitle = "Здесь только те, кого заблокировали вы. Кто заблокировал вас — не показывается.",
    blockedCount = { "$it студентов" },
    unblock = "Разблокировать",
    unblockBody = {
        "$it будет разблокирован и снова увидит вас в поиске. Прежняя связь не восстановится — " +
            "при необходимости отправьте заявку заново."
    },
    unblockConfirm = "Разблокировать",
    blockedSince = { "Заблокирован с $it" },

    loadingProfile = "загрузка…",
    requestedYou = "Отправил заявку",
    mute = "Без звука",
    muteSoon = "Отключение звука скоро появится",
    call = "Звонок",
    callSoon = "Звонки скоро появятся",
    video = "Видео",
    videoSoon = "Видеозвонки скоро появятся",
    usernameLabel = "Имя пользователя",
    phoneLabel = "Мобильный номер",
    bioLabel = "О себе",
    universityLabel = "Университет",
    courseLabel = "Курс",
    genderLabel = "Пол",
    genderMale = "Мужской",
    genderFemale = "Женский",
    lastSeenOn = { "был(а) в сети $it" },
    masterDegree = "Магистратура",
    courseYear = { "$it курс" },
)

private val ConnectionsUz = ConnectionsStrings(
    title = "Do'stlar",
    tabConnections = "Do'stlar",
    tabRequests = "So'rovlar",
    tabSearch = "Qidiruv",

    writeMessage = "Xabar yozish",
    disconnect = "Bog'lanishni uzish",
    block = "Bloklash",
    report = "Shikoyat qilish",
    cancel = "Bekor",
    message = "Xabar",
    connect = "Bog'lanish",
    requestSent = "Yuborildi",
    respond = "Javob berish",
    accept = "Qabul",
    decline = "Rad",
    pending = "Kutilmoqda",
    incoming = "Kiruvchi",
    outgoing = "Chiquvchi",
    menu = "Menyu",
    requestReceived = "So'rov bor",

    disconnectBody = { "$it bilan bog'lanish uziladi. Keyinroq qayta so'rov yuborishingiz mumkin." },
    disconnectConfirm = "Uzish",
    blockBody = {
        "$it bloklanadi: bog'lanish o'chadi, ikkalangiz bir-biringizga yozolmaysiz. " +
            "Blokni yechganda bog'lanish tiklanmaydi."
    },

    searchHint = "Ism yoki username…",
    searchOneWord = "Bitta so'z yozing — to'liq ism bo'yicha qidiruv ishlamaydi.",
    noStudentsForFilters = "Bu filtrlarga mos talaba yo'q.",
    clear = "Tozalash",
    filterMyUniversity = "Universitetim",
    filterNewPeople = "Yangi odamlar",
    filterMale = "Erkak",
    filterFemale = "Ayol",
    sortByName = "Ism bo'yicha",

    reportTitle = { "Shikoyat: $it" },
    reportNote = "Izoh (ixtiyoriy)",
    reportSend = "Yuborish",
    reasonSpam = "Spam",
    reasonFraud = "Firibgarlik",
    reasonHarassment = "Haqorat",
    reasonInappropriate = "Nomaqbul",
    reasonOther = "Boshqa",

    connectedWith = { "$it bilan bog'landingiz" },
    requestWasSent = "So'rov yuborildi",
    requestDeclined = "So'rov rad etildi",
    connectionRemoved = "Bog'lanish uzildi",
    userBlocked = { "$it bloklandi" },
    reportAccepted = "Shikoyatingiz qabul qilindi",
    userUnblocked = { "$it blokdan chiqarildi" },

    blockedTitle = "Bloklanganlar",
    blockedSubtitle = "Bu yerda faqat siz bloklagan talabalar. Sizni kim bloklagani ko'rsatilmaydi.",
    blockedCount = { "$it ta talaba" },
    unblock = "Blokdan chiqarish",
    unblockBody = {
        "$it blokdan chiqariladi va sizni qidiruvda yana ko'radi. Avvalgi bog'lanish " +
            "tiklanmaydi — kerak bo'lsa qaytadan so'rov yuborasiz."
    },
    unblockConfirm = "Chiqarish",
    blockedSince = { "$it dan beri bloklangan" },

    loadingProfile = "yuklanmoqda…",
    requestedYou = "So'rov yuborgan",
    mute = "Sukut qilish",
    muteSoon = "Sukut qilish tez orada",
    call = "Chaqiruv",
    callSoon = "Qo'ng'iroq tez orada",
    video = "Video",
    videoSoon = "Video qo'ng'iroq tez orada",
    usernameLabel = "Foydalanuvchi nomi",
    phoneLabel = "Mobil raqam",
    bioLabel = "Tarjimayi hol",
    universityLabel = "Universitet",
    courseLabel = "Kurs",
    genderLabel = "Jinsi",
    genderMale = "Erkak",
    genderFemale = "Ayol",
    lastSeenOn = { "oxirgi faollik $it" },
    masterDegree = "Magistratura",
    courseYear = { "$it-kurs" },
)

@Composable
@ReadOnlyComposable
internal fun connectionsStrings(): ConnectionsStrings =
    rememberStrings(ConnectionsEn, ConnectionsRu, ConnectionsUz)

/** ViewModel uchun (Compose'dan tashqarida). */
internal fun connectionsStringsNow(): ConnectionsStrings =
    AppLocale.pick(ConnectionsEn, ConnectionsRu, ConnectionsUz)
