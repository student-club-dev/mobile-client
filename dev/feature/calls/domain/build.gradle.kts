// Qo'ng'iroq domeni — 1:1 audio/video (`handoff/09-CALLS-PROTOCOL.md`).
//
// `chat:domain` shu modulga bog'lanadi: chat lentasidagi `CALL` pufakchasi ham aynan shu
// enum'lardan (`CallStatus`, `CallEndReason`, `CallMedia`) foydalanadi — bitta ma'no ikki
// joyda ikki xil enum bilan ifodalanmasin. Shuning uchun bog'liqlik yo'nalishi
// chat → calls, teskarisi emas.
//
// Chaqiruvchining qisqa profili (`StudentSummary`) `connections` domenidan keladi.
plugins { id("sc.kmp-library") }
kotlin { sourceSets { commonMain.dependencies {
    api(projects.dev.feature.connections.domain)
} } }
