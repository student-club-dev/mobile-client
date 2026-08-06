package dev.core.data.seed

import dev.core.common.AppDispatchers
import dev.core.data.mapper.joinDb
import dev.core.data.mapper.toDb
import dev.core.database.sql.StudentClubDatabase
import kotlinx.coroutines.withContext

/**
 * Local bazani namuna ma'lumot bilan to'ldiradi (jadval bo'sh bo'lsagina).
 *
 * ⚠️ Bu yerda **faqat endpoint'i hali yo'q** bo'limlar qoldi. Backendga ulangan hamma narsa
 * (universitetlar, talabalar, "Siz uchun" feed'i, ijara/topshiriq e'lonlari) seed'dan
 * BUTUNLAY olib tashlandi — ular endi serverdan keladi va bo'sh ro'yxat "ma'lumot yo'q"
 * degani, "seed tushmadi" degani emas.
 *
 * Yangi endpoint qo'shilganda mos `seed*` funksiyasi shu yerdan o'chiriladi.
 */
class LocalDataSeeder(
    private val db: StudentClubDatabase,
    private val dispatchers: AppDispatchers,
) {
    suspend fun seedIfEmpty() = withContext(dispatchers.io) {
        seedJobs()
        seedAds()
        seedClubs()
    }

    /** Ishlar — `/v1/jobs` hali yo'q. */
    private fun seedJobs() {
        val q = db.jobQueries
        if (q.countJobs().executeAsOne() > 0) return
        q.transaction {
            q.upsertJob("j-smm", "SMM menejer (part-time)", "Uzum Market", "U", "masofaviy", "IT",
                listOf("IT", "SMM").joinDb(), "3–5 mln so‘m", true.toDb(), true.toDb(), "2 soat oldin", "IT", false.toDb())
            q.upsertJob("j-frontend", "Frontend intern", "PayNet", "P", "Toshkent", "IT",
                listOf("IT", "Vue", "Ofis").joinDb(), "4 mln so‘m", false.toDb(), false.toDb(), "bugun", "IT", false.toDb())
            q.upsertJob("j-ofitsiant", "Ofitsiant (kechqurun)", "Evos", "E", "Chilonzor", "Xizmat",
                listOf("Xizmat", "Smenali").joinDb(), "1.5 mln so‘m", false.toDb(), true.toDb(), "kecha", "Xizmat", false.toDb())
        }
    }

    /**
     * Eski `ad` jadvali — e'lonlar `listing` ga ko'chgan, bu bo'lim faqat eski qatorlarni
     * tahrirlash uchun qoldi va o'z endpoint'i yo'q.
     */
    private fun seedAds() {
        val q = db.adQueries
        if (q.selectAll().executeAsList().isNotEmpty()) return
        q.transaction {
            q.upsert("ad-1", "RENTAL", "Chilonzorda room-mate", "Turar joy", "1.2 mln/oy",
                "2 xonali, metroga yaqin, student uchun qulay.", "", "seed-user", "3 soat oldin")
            q.upsert("ad-2", "SALE", "MacBook Air M1 sotiladi", "Texnika", "9.5 mln",
                "Holati a'lo, 100% batareya sikli past.", "", "seed-user", "kecha")
        }
    }

    // Bildirishnomalar seed'i OLIB TASHLANDI: ro'yxat endi `GET /v1/notifications` dan
    // keladi va `NotificationEntity` — o'sha javobning keshi (`NOTIFICATIONS_BACKEND.md`).
    // Namuna qatorlari kesh bilan aralashib, "Dilnoza Rahimova sizga xabar yozdi" degan
    // soxta yozuvni haqiqiy ro'yxat ustiga olib chiqardi.

    /** Klublar — `/v1/clubs` hali yo'q. */
    private fun seedClubs() {
        val q = db.clubQueries
        if (q.selectAll().executeAsList().isNotEmpty()) return
        q.transaction {
            q.upsert(1, "IT Klub", "Dasturlash, hackathonlar va IT loyihalar jamoasi.", 342, null)
            q.upsert(2, "Debat Klubi", "Mantiqiy fikrlash va notiqlik san'ati.", 128, null)
            q.upsert(3, "Sport Klubi", "Futbol, basketbol va umumjismoniy mashg'ulotlar.", 256, null)
            q.upsert(4, "Volontyorlar", "Ijtimoiy loyihalar va xayriya tadbirlari.", 189, null)
            q.upsert(5, "Dizayn Studiyasi", "UI/UX, grafika va ijodiy ustaxonalar.", 97, null)
            q.upsert(6, "Til Klubi", "Ingliz, koreys va arab tillari amaliyoti.", 214, null)
        }
    }
}
