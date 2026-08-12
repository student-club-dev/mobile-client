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

    /**
     * Til almashganda namuna qatorlarni yangi tilda QAYTA yozadi.
     *
     * Faqat o'qish uchun bo'lgan bo'limlar (ishlar, klublar) — ular tahrirlanmaydi, demak
     * ustidan yozish hech narsani yo'qotmaydi. E'lonlar (`ad`) ataylab tegilmaydi:
     * foydalanuvchi ularni tahrirlashi mumkin va til almashishi uning matnini o'chirib
     * yuborishi kerak emas.
     */
    suspend fun resyncLanguage() = withContext(dispatchers.io) {
        writeJobs()
        writeClubs()
    }

    /** Ishlar — `/v1/jobs` hali yo'q. */
    private fun seedJobs() {
        if (db.jobQueries.countJobs().executeAsOne() > 0) return
        writeJobs()
    }

    private fun writeJobs() {
        val q = db.jobQueries
        q.transaction {
            q.upsertJob("j-smm", SeedStrings.jobSmmTitle, "Uzum Market", "U", SeedStrings.jobSmmLocation, SeedStrings.tagIt,
                listOf(SeedStrings.tagIt, SeedStrings.tagSmm).joinDb(), SeedStrings.jobSmmSalary,
                true.toDb(), true.toDb(), SeedStrings.jobSmmPosted, SeedStrings.tagIt, false.toDb())
            q.upsertJob("j-frontend", SeedStrings.jobFrontendTitle, "PayNet", "P", SeedStrings.jobFrontendLocation, SeedStrings.tagIt,
                listOf(SeedStrings.tagIt, "Vue", SeedStrings.tagOffice).joinDb(), SeedStrings.jobFrontendSalary,
                false.toDb(), false.toDb(), SeedStrings.jobFrontendPosted, SeedStrings.tagIt, false.toDb())
            q.upsertJob("j-ofitsiant", SeedStrings.jobWaiterTitle, "Evos", "E", "Chilonzor", SeedStrings.tagService,
                listOf(SeedStrings.tagService, SeedStrings.tagShift).joinDb(), SeedStrings.jobWaiterSalary,
                false.toDb(), true.toDb(), SeedStrings.jobWaiterPosted, SeedStrings.tagService, false.toDb())
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
            q.upsert("ad-1", "RENTAL", SeedStrings.adRoommateTitle, SeedStrings.adRoommateCategory, SeedStrings.adRoommatePrice,
                SeedStrings.adRoommateBody, "", "seed-user", SeedStrings.adRoommatePosted)
            q.upsert("ad-2", "SALE", SeedStrings.adMacbookTitle, SeedStrings.adMacbookCategory, "9.5 mln",
                SeedStrings.adMacbookBody, "", "seed-user", SeedStrings.adMacbookPosted)
        }
    }

    // Bildirishnomalar seed'i OLIB TASHLANDI: ro'yxat endi `GET /v1/notifications` dan
    // keladi va `NotificationEntity` — o'sha javobning keshi (`NOTIFICATIONS_BACKEND.md`).
    // Namuna qatorlari kesh bilan aralashib, "Dilnoza Rahimova sizga xabar yozdi" degan
    // soxta yozuvni haqiqiy ro'yxat ustiga olib chiqardi.

    /** Klublar — `/v1/clubs` hali yo'q. */
    private fun seedClubs() {
        if (db.clubQueries.selectAll().executeAsList().isNotEmpty()) return
        writeClubs()
    }

    private fun writeClubs() {
        val q = db.clubQueries
        q.transaction {
            q.upsert(1, SeedStrings.clubItName, SeedStrings.clubItBody, 342, null)
            q.upsert(2, SeedStrings.clubDebateName, SeedStrings.clubDebateBody, 128, null)
            q.upsert(3, SeedStrings.clubSportName, SeedStrings.clubSportBody, 256, null)
            q.upsert(4, SeedStrings.clubVolunteersName, SeedStrings.clubVolunteersBody, 189, null)
            q.upsert(5, SeedStrings.clubDesignName, SeedStrings.clubDesignBody, 97, null)
            q.upsert(6, SeedStrings.clubLanguageName, SeedStrings.clubLanguageBody, 214, null)
        }
    }
}
