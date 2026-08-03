// Chat domen qatlami.
// `Connections` ga bog'liqlik ATAYLAB: suhbatdosh — o'sha `StudentSummary`, va bog'lanmagan
// talabalar bilan chat umuman ochilmaydi (403 NOT_CONNECTED).
//
// `Calls` ga bog'liqlik: lentadagi `CALL` pufakchasi qo'ng'iroq domenidagi AYNAN o'sha
// enum'lardan foydalanadi (`CallStatus`, `CallEndReason`, `CallMedia`) — bitta ma'no ikki
// joyda ikki xil enum bilan ifodalansa, mapper ular orasida tarjima qilishga majbur
// bo'lardi va yangi qiymat qo'shilganda faqat bittasi yangilanardi.
plugins { id("sc.kmp-library") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.connections.domain)
    api(projects.dev.feature.calls.domain)
    api(libs.kotlinx.datetime)
} } }
