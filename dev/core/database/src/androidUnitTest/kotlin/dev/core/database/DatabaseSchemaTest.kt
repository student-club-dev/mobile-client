package dev.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.core.database.sql.StudentClubDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Host (JVM) testlari — SQLDelight sxemasi, migratsiya zanjiri va yangi jadvallar CRUD'ini
 * real SQLite engine'da tekshiradi. SQL mantiqi Android va iOS'da bir xil bo'lgani uchun
 * bu ikkala platforma uchun ham amal qiladi.
 */
class DatabaseSchemaTest {

    private fun freshDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    /**
     * v1 bazasini taqlid qiladi — migratsiya zanjiri tegadigan jadvallar:
     * ClubEntity (3.sqm `joined` qo'shadi), ConversationEntity (4.sqm `archived` qo'shadi),
     * UserEntity (5.sqm profilni ajratib oladi — profil ustunlari hali ichida).
     */
    /**
     * `StudentEntity` **v1 dan beri** mavjud (`Student.sq` birinchi migratsiyadan oldin
     * qo'shilgan), shuning uchun 21.sqm unga ustun QO'SHADI. Migratsiya testlarining
     * boshlang'ich bazasida ham jadval bo'lishi shart, aks holda zanjir uziladi.
     */
    private fun createV1StudentTable(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE StudentEntity (
                id TEXT NOT NULL PRIMARY KEY,
                firstName TEXT NOT NULL,
                lastName TEXT NOT NULL,
                initial TEXT NOT NULL,
                universityId TEXT NOT NULL,
                universityMonogram TEXT NOT NULL,
                course INTEGER NOT NULL,
                faculty TEXT NOT NULL,
                friendStatus TEXT NOT NULL,
                interests TEXT NOT NULL,
                friendsCount INTEGER NOT NULL,
                adsCount INTEGER NOT NULL,
                rating REAL NOT NULL
            )
            """.trimIndent(),
            0,
        )
    }

    private fun createV1Tables(driver: JdbcSqliteDriver) {
        createV1StudentTable(driver)
        driver.execute(
            null,
            """
            CREATE TABLE ClubEntity (
                id INTEGER NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                membersCount INTEGER NOT NULL,
                imageUrl TEXT
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE ConversationEntity (
                id TEXT NOT NULL PRIMARY KEY,
                peerName TEXT NOT NULL,
                peerInitial TEXT NOT NULL,
                type TEXT NOT NULL,
                online INTEGER NOT NULL,
                lastMessage TEXT NOT NULL,
                lastTime TEXT NOT NULL,
                unreadCount INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE UserEntity (
                uid TEXT NOT NULL PRIMARY KEY,
                userId INTEGER NOT NULL,
                fullName TEXT NOT NULL,
                email TEXT NOT NULL,
                role TEXT NOT NULL,
                phoneNumber TEXT,
                photoUrl TEXT,
                firstName TEXT,
                lastName TEXT,
                universityId TEXT,
                universityEmail TEXT,
                birthYear INTEGER,
                courseYear TEXT,
                profileRole TEXT
            )
            """.trimIndent(),
            0,
        )
        createV1DiscountTables(driver)
    }

    /**
     * Chegirma jadvallari base sxemada (ularni yaratadigan migratsiya yo'q — v1'dan bor).
     * 11.sqm ular ustiga ustun qo'shadi, shuning uchun migratsiya testlarida ham mavjud bo'lishi kerak.
     * Eski (v11 gacha) shakli — 11.sqm keyin subcategory/isDiscount/narx ustunlarini qo'shadi.
     */
    private fun createV1DiscountTables(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE DiscountCategoryEntity (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                emoji TEXT NOT NULL,
                offerCount INTEGER NOT NULL,
                accent INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            """
            CREATE TABLE DiscountOfferEntity (
                id TEXT NOT NULL PRIMARY KEY,
                categoryId TEXT NOT NULL,
                merchant TEXT NOT NULL,
                title TEXT NOT NULL,
                discountPercent INTEGER NOT NULL,
                tag TEXT NOT NULL,
                promoCode TEXT,
                location TEXT,
                expiry TEXT,
                emoji TEXT NOT NULL,
                bannerAccent INTEGER NOT NULL,
                featured INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(
            null,
            "CREATE TABLE SavedDiscountEntity (offerId TEXT NOT NULL PRIMARY KEY)",
            0,
        )
    }

    @Test
    fun schemaVersionIsCurrent() {
        // 7.sqm — ListingEntity (chegirma e'lonlari), 8.sqm — ko'p filial (branchesJson),
        // 10.sqm — profil email, 11.sqm — "Siz uchun" e'lonlari (chegirmasiz + sub-kategoriya + narx),
        // 12.sqm — e'lon jinsi (kiyim uchun Erkak/Ayol), 13.sqm — e'lon koordinatasi (xarita),
        // 14.sqm — e'lonning to'rt turi (chegirma/ijara/xizmat/ish): ListingEntity qayta qurildi,
        // turga xos maydonlar detailsJson ga ko'chdi, 15.sqm — sessiya backend tokenlariga
        // ko'chdi: UserEntity.userId olib tashlandi, `uid` = JWT `sub`, 16.sqm — chat backendga
        // ulandi: suhbat ikkinchi tomonning qisqa profilini, xabar esa `seq`/`senderId`/holatini
        // saqlaydi (eski demo jadvallar tashlab yuborildi), 17.sqm — suhbatdoshning
        // "yetkazildi" kursori (`otherDeliveredSeq`) + profil jinsi va "oxirgi ko'rilgan"
        // maxfiyligi (backend 2026-07-28 spec'i), 18.sqm — chatda media: rasm biriktirmasi
        // (`attachmentUrl`/`attachmentThumbUrl`/o'lchamlar) va albom kaliti (`albumId`),
        // 19.sqm — suhbatdosh profili (universitet/jins/kurs) chat ichidagi profil ekrani
        // uchun, 20.sqm — "Siz uchun" e'lonining karta rasmi (`DiscountOfferEntity.imageUrl`),
        // 21.sqm — talaba ro'yxatlaridagi profil rasmi (`StudentEntity.avatarUrl`),
        // 22.sqm — chat media yangi backend kontraktiga ko'chdi: xabar o'chirilishi
        // (`deletedAt`), to'liq biriktirma metama'lumoti (`attachmentId`/`kind`/`status`/
        // `waveform`…), stiker ustunlari va suhbat qatoridagi `lastMessageType`,
        // 23.sqm — bosh ekran bo'limlari katalog guruhiga bog'landi: `DiscountGroupEntity`
        // jadvali + `DiscountCategoryEntity.groupKey` va `DiscountOfferEntity.groupKey`,
        // 24.sqm — boyitilgan profil: `ProfileEntity.bio` va `ProfileEntity.phoneVisibility`
        // (`handoff/08-PROFILE.md` §4–§5), 25.sqm — «faqat menda o'chirish»:
        // `MessageEntity.hiddenAt` (`CHAT_SELECTION_AND_HISTORY_BACKEND.md` §A1),
        // 26.sqm — sitata bilan javob (`MessageEntity.replyTo*`), tarix suv belgisi
        // (`ConversationEntity.clearedBeforeSeq`) va suhbatni ro'yxatdan yashirish
        // (`ConversationEntity.hidden`) — `CHAT_SELECTION_AND_HISTORY_RESPONSE.md` §B, §C.
        assertEquals(27L, StudentClubDatabase.Schema.version)
    }

    @Test
    fun freshSchemaCreatesAllTablesAndCrudWorks() {
        val driver = freshDriver()
        StudentClubDatabase.Schema.create(driver)
        val db = StudentClubDatabase(driver)

        // AppSetting (C5)
        db.appSettingQueries.upsert("theme_mode", "DARK")
        assertEquals("DARK", db.appSettingQueries.selectByKey("theme_mode").executeAsOne())

        // Notification (C1)
        db.notificationQueries.insert("n1", "Sarlavha", "Matn", "JOB", "hozir", 1, 0)
        db.notificationQueries.insert("n2", "Sarlavha 2", "Matn 2", "CHAT", "hozir", 2, 1)
        assertEquals(1L, db.notificationQueries.countUnread().executeAsOne())
        db.notificationQueries.markAllRead()
        assertEquals(0L, db.notificationQueries.countUnread().executeAsOne())

        // Club (C4) — joined ustuni
        db.clubQueries.upsert(1L, "IT Klub", "Tavsif", 10L, null)
        db.clubQueries.setJoined(1L, 1L)
        val club = db.clubQueries.selectAll().executeAsOne()
        assertEquals(1L, club.joined)

        // Profile (feature:profile) — sessiyadan ajratilgan profil keshi
        db.profileQueries.upsert(
            uid = "uid-1",
            firstName = "Quvonchbek",
            lastName = "G'afurov",
            phoneNumber = "+998901234567",
            role = "STUDENT",
            universityId = "tuit",
            universityEmail = null,
            birthYear = 2004L,
            courseYear = "3",
            gender = "MALE",
            lastSeenVisibility = "NOBODY",
            avatarUrl = "https://cdn.studentclub.uz/avatars/uid-1.jpg",
            businessName = null,
            businessType = null,
            email = null,
            bio = "5/5 · Dasturiy injiniring",
            // Sukut `NOBODY` — raqam ko'pchilikda yopiq (`handoff/08-PROFILE.md` §4).
            phoneVisibility = "NOBODY",
        )
        val profile = db.profileQueries.selectCurrent().executeAsOne()
        assertEquals("Quvonchbek", profile.firstName)
        assertEquals("tuit", profile.universityId)
        assertEquals(2004L, profile.birthYear)
        assertEquals("MALE", profile.gender)
        assertEquals("NOBODY", profile.lastSeenVisibility)
        assertEquals("https://cdn.studentclub.uz/avatars/uid-1.jpg", profile.avatarUrl)
        assertEquals("5/5 · Dasturiy injiniring", profile.bio)
        assertEquals("NOBODY", profile.phoneVisibility)

        db.profileQueries.clear()
        assertNull(db.profileQueries.selectCurrent().executeAsOneOrNull())

        driver.close()
    }

    /**
     * Chat keshi (16.sqm) — eng nozik joyi: serverdan kelgan ro'yxat **local** ustunlarni
     * (`archived`, `lastReadSeq`) bosib ketmasligi kerak. Shu sabab `upsert` bitta so'rov
     * emas, `insertConversationIfNew` + `updateConversation` juftligi.
     */
    @Test
    fun chatCacheKeepsLocalColumnsOnRefresh() {
        val driver = freshDriver()
        StudentClubDatabase.Schema.create(driver)
        val db = StudentClubDatabase(driver)
        val q = db.chatQueries

        q.insertConversationIfNew(
            id = "cnv_1", type = "DIRECT", lastMessageAt = 1_000L,
            otherId = "std_ali", otherUsername = "alisher", otherFullName = "Alisher Valiyev",
            otherAvatarUrl = "https://cdn.example.uz/a.jpg", otherOnline = 1L, otherLastSeenAt = null,
            otherUniversityId = "emis-142", otherGender = "MALE", otherCourseYear = "3",
            lastMessageBody = "Salom!", lastMessageSenderId = "std_ali",
            lastMessageType = "TEXT", lastMessageDeleted = 0L,
            unreadCount = 3L, lastReadSeq = 0L, otherReadSeq = 0L, otherDeliveredSeq = 0L,
        )
        // Foydalanuvchi suhbatni arxivlab, 42-xabargacha o'qidi. WS esa suhbatdosh
        // 40-xabargacha o'qiganini aytdi.
        q.setArchived(1L, "cnv_1")
        q.markRead(42L, "cnv_1")
        q.setOtherReadSeq(40L, "cnv_1")
        q.setOtherDeliveredSeq(41L, "cnv_1")

        // Serverdan yangi ro'yxat keldi (u arxivni bilmaydi, kursorlari esa eskirgan
        // bo'lishi mumkin — WS ulardan oldinroq kelgan bo'lardi).
        q.insertConversationIfNew(
            id = "cnv_1", type = "DIRECT", lastMessageAt = 2_000L,
            otherId = "std_ali", otherUsername = "alisher", otherFullName = "Alisher Valiyev",
            otherAvatarUrl = "https://cdn.example.uz/a.jpg", otherOnline = 0L, otherLastSeenAt = 1_500L,
            otherUniversityId = "emis-142", otherGender = "MALE", otherCourseYear = "3",
            lastMessageBody = "Yangi xabar", lastMessageSenderId = "std_ali",
            lastMessageType = "TEXT", lastMessageDeleted = 0L,
            unreadCount = 1L, lastReadSeq = 0L, otherReadSeq = 0L, otherDeliveredSeq = 0L,
        )
        q.updateConversation(
            type = "DIRECT", lastMessageAt = 2_000L,
            otherId = "std_ali", otherUsername = "alisher", otherFullName = "Alisher Valiyev",
            otherAvatarUrl = "https://cdn.example.uz/b.jpg", otherOnline = 0L, otherLastSeenAt = 1_500L,
            otherUniversityId = "emis-142", otherGender = "MALE", otherCourseYear = "3",
            lastMessageBody = "Yangi xabar", lastMessageSenderId = "std_ali",
            lastMessageType = "TEXT", lastMessageDeleted = 0L,
            unreadCount = 1L, lastReadSeq = 0L, otherReadSeq = 10L, otherDeliveredSeq = 10L,
            id = "cnv_1",
        )

        val row = q.selectConversation("cnv_1").executeAsOne()
        assertEquals("Yangi xabar", row.lastMessageBody) // server ma'lumoti yangilandi
        assertEquals(1L, row.unreadCount)
        assertEquals(1L, row.archived) // ...local arxiv bayrog'i saqlandi
        assertEquals(42L, row.lastReadSeq) // ...va kursorlarning hech biri
        assertEquals(40L, row.otherReadSeq) // ...ortga surilmadi
        assertEquals(41L, row.otherDeliveredSeq)
        // Profil maydonlari (19.sqm) — avatar va universitet serverdan yangilandi.
        assertEquals("https://cdn.example.uz/b.jpg", row.otherAvatarUrl)
        assertEquals("emis-142", row.otherUniversityId)
        assertEquals("3", row.otherCourseYear)

        // Arxivlangan suhbat oddiy ro'yxatda ko'rinmaydi.
        assertEquals(0, q.selectConversations().executeAsList().size)
        assertEquals(1, q.selectArchivedConversations().executeAsList().size)

        // Xabarlar: `seq = 0` (yuborilayotgan) ENG OXIRIDA turishi kerak.
        q.message("m2", seq = 42L, sender = "std_ali", body = "Salom!", createdAt = 2_000L)
        q.message("m1", seq = 41L, sender = "std_men", body = "Assalomu alaykum", createdAt = 1_000L)
        q.message(
            "local:x", seq = 0L, sender = "std_men", body = "Ketyapti…", createdAt = 3_000L,
            clientMsgId = "cmid-1", status = "SENDING",
        )
        assertEquals(
            listOf("m1", "m2", "local:x"),
            q.selectMessages("cnv_1").executeAsList().map { it.id },
        )

        // Kursorlar: eng eski (0 dan katta) va eng yangi `seq`.
        assertEquals(41L, q.minSeq("cnv_1").executeAsOne())
        assertEquals(42L, q.maxSeq("cnv_1").executeAsOne())

        // `message:new` o'z xabarimiz bo'lib qaytganda optimistik nusxa `clientMsgId` bo'yicha o'chadi.
        q.deleteSendingByClientMsgId("cnv_1", "cmid-1")
        assertEquals(2, q.selectMessages("cnv_1").executeAsList().size)

        driver.close()
    }

    /**
     * Regressiya: ketma-ket **bir xil matnli** ikkita xabar.
     *
     * Avval moslik tana bo'yicha edi (`deleteSendingByBody`) va bu yerda ikkala qator ham
     * o'chib ketardi — foydalanuvchi aynan shu xatoni xabar qilgan. `clientMsgId` bo'yicha
     * moslik faqat serverdan qaytgan bittasini oladi (`handoff/03-WEBSOCKET.md`).
     */
    @Test
    fun deleteSendingByClientMsgIdRemovesOnlyTheAcknowledgedRow() {
        val driver = freshDriver()
        val db = StudentClubDatabase(driver).also { StudentClubDatabase.Schema.create(driver) }
        val q = db.chatQueries

        q.message(
            "local:a", seq = 0L, sender = "std_men", body = "ha", createdAt = 1_000L,
            clientMsgId = "cmid-a", status = "SENDING",
        )
        q.message(
            "local:b", seq = 0L, sender = "std_men", body = "ha", createdAt = 2_000L,
            clientMsgId = "cmid-b", status = "SENDING",
        )

        q.deleteSendingByClientMsgId("cnv_1", "cmid-a")

        assertNull(q.selectMessageById("local:a").executeAsOneOrNull())
        assertEquals("cmid-b", q.selectMessageById("local:b").executeAsOne().clientMsgId)

        // Ack allaqachon almashtirgan HAQIQIY qator (status SENT) tegilmasin — u ham
        // qayta yuborish uchun o'sha `clientMsgId` ni saqlaydi.
        q.message(
            "msg_real", seq = 42L, sender = "std_men", body = "ha", createdAt = 3_000L,
            clientMsgId = "cmid-b", status = "SENT",
        )
        q.deleteSendingByClientMsgId("cnv_1", "cmid-b")

        assertNull(q.selectMessageById("local:b").executeAsOneOrNull())
        assertEquals(42L, q.selectMessageById("msg_real").executeAsOne().seq)

        driver.close()
    }

    /**
     * Rasm xabari (22.sqm): yuklanayotganda tanasi ham, biriktirmasi ham bo'sh; yuklangach
     * `setMessageAttachment` uni to'ldiradi. `attachmentId` **saqlanishi shart** — biriktirma
     * bir martalik, ya'ni qayta yuborishda fayl qaytadan yuklanmaydi.
     *
     * `clientMsgId` esa o'zgarmaydi — server javobi aynan shu kalit bo'yicha topiladi, ya'ni
     * tanasi bo'sh media xabar ham to'g'ri moslashadi.
     */
    @Test
    fun imageMessageStoresAttachmentAndAlbum() {
        val driver = freshDriver()
        val db = StudentClubDatabase(driver).also { StudentClubDatabase.Schema.create(driver) }
        val q = db.chatQueries

        q.message(
            "local:img", seq = 0L, sender = "std_men", body = "", createdAt = 1_000L,
            clientMsgId = "cmid-img", status = "SENDING", type = "IMAGE", albumId = "alb_1",
        )
        val pending = q.selectMessageById("local:img").executeAsOne()
        assertEquals("IMAGE", pending.type)
        assertEquals("alb_1", pending.albumId)
        assertNull(pending.attachmentUrl)
        assertNull(pending.attachmentId)
        assertEquals(0L, pending.attachmentWidth)

        val url = "/v1/media/med_01H8X/raw"
        q.setMessageAttachment(
            attachmentId = "med_01H8X",
            attachmentUrl = url,
            attachmentThumbUrl = "$url?variant=thumb",
            attachmentWidth = 1920L,
            attachmentHeight = 1080L,
            attachmentKind = "IMAGE",
            attachmentStatus = "READY",
            attachmentMime = "image/webp",
            attachmentSizeBytes = 284_100L,
            attachmentDurationMs = null,
            attachmentWaveform = null,
            attachmentFileName = null,
            attachmentBlurHash = "LKO2?U%2Tw=w",
            attachmentIsAnimated = 0L,
            id = "local:img",
        )

        val uploaded = q.selectMessageById("local:img").executeAsOne()
        // Tana BO'SH qoladi — havola endi tanaga yozilmaydi (eski hiyla olib tashlandi).
        assertEquals("", uploaded.body)
        assertEquals("med_01H8X", uploaded.attachmentId)
        assertEquals(url, uploaded.attachmentUrl)
        assertEquals(1920L, uploaded.attachmentWidth)
        assertEquals("READY", uploaded.attachmentStatus)
        assertEquals("cmid-img", uploaded.clientMsgId)

        // Server o'sha xabarni qaytarganda optimistik qator `clientMsgId` bo'yicha topiladi —
        // tanasi bo'sh bo'lishi moslikka ta'sir qilmaydi.
        q.deleteSendingByClientMsgId("cnv_1", "cmid-img")
        assertNull(q.selectMessageById("local:img").executeAsOneOrNull())

        driver.close()
    }

    /**
     * Soft delete (22.sqm): qator **o'chirilmaydi**, `seq` joyida qoladi va u
     * o'qilmaganlar sanog'idan chiqadi (`handoff/02-API-CHANGES.md` §4b).
     *
     * Qator yo'q qilinsa `seq` da teshik qolardi va `?before=`/`?after=` sahifalash hamda
     * o'qildi arifmetikasi buzilardi — shuning uchun faqat tanasi bo'shatiladi.
     */
    @Test
    fun softDeleteKeepsRowAndDropsUnread() {
        val driver = freshDriver()
        val db = StudentClubDatabase(driver).also { StudentClubDatabase.Schema.create(driver) }
        val q = db.chatQueries

        q.insertConversationIfNew(
            id = "cnv_1", type = "DIRECT", lastMessageAt = 2_000L,
            otherId = "std_ali", otherUsername = "alisher", otherFullName = "Alisher Valiyev",
            otherAvatarUrl = null, otherOnline = 0L, otherLastSeenAt = null,
            otherUniversityId = null, otherGender = null, otherCourseYear = null,
            lastMessageBody = "Salom!", lastMessageSenderId = "std_ali",
            lastMessageType = "TEXT", lastMessageDeleted = 0L,
            unreadCount = 2L, lastReadSeq = 41L, otherReadSeq = 0L, otherDeliveredSeq = 0L,
        )
        q.message("m41", seq = 41L, sender = "std_ali", body = "Salom!", createdAt = 1_000L)
        q.message("m42", seq = 42L, sender = "std_ali", body = "Qalaysan?", createdAt = 2_000L)

        q.setMessageDeleted(9_000L, "m42")
        q.decrementUnreadForDeleted("cnv_1", 42L)
        q.setLastMessageDeleted("cnv_1")

        val row = q.selectMessageById("m42").executeAsOne()
        assertEquals(42L, row.seq) // `seq` joyida — tarixda teshik yo'q
        assertEquals("", row.body) // tana HAQIQATAN bo'shatildi
        assertEquals(9_000L, row.deletedAt)
        // Ikkala xabar ham ro'yxatda qoladi — o'chirilgani tombstone bo'lib.
        assertEquals(listOf("m41", "m42"), q.selectMessages("cnv_1").executeAsList().map { it.id })
        assertEquals(42L, q.maxSeq("cnv_1").executeAsOne())

        val conversation = q.selectConversation("cnv_1").executeAsOne()
        assertEquals(1L, conversation.unreadCount)
        assertEquals(1L, conversation.lastMessageDeleted)
        assertEquals("", conversation.lastMessageBody)

        // Allaqachon o'qilgan xabar (`seq <= lastReadSeq`) sanoqni pasaytirmaydi — aks
        // holda badge manfiyga ketardi.
        q.setMessageDeleted(9_100L, "m41")
        q.decrementUnreadForDeleted("cnv_1", 41L)
        assertEquals(1L, q.selectConversation("cnv_1").executeAsOne().unreadCount)

        driver.close()
    }

    /**
     * «Faqat menda o'chirish» (25.sqm): xabar ro'yxatdan chiqadi, lekin qator, tana va
     * `seq` joyida qoladi — u serverda hamon bor.
     *
     * Eng muhimi oxirgi tekshiruv: tarix serverdan qayta yuklanganda (`upsertMessage`)
     * yashirish **saqlanadi**. `INSERT OR REPLACE` qatorni o'chirib qayta yozadi, ya'ni
     * ustun so'rovda ataylab qayta o'qilmasa har `loadLatest` da xabarlar qaytib chiqardi.
     */
    @Test
    fun hiddenMessagesLeaveHistoryIntact() {
        val driver = freshDriver()
        val db = StudentClubDatabase(driver).also { StudentClubDatabase.Schema.create(driver) }
        val q = db.chatQueries

        q.message("m1", seq = 1L, sender = "std_ali", body = "Salom!", createdAt = 1_000L)
        q.message("m2", seq = 2L, sender = "me", body = "Qalaysan?", createdAt = 2_000L)
        q.message("m3", seq = 3L, sender = "std_ali", body = "Yaxshi", createdAt = 3_000L)

        q.hideMessages(9_000L, listOf("m2", "m3"))

        assertEquals(listOf("m1"), q.selectMessages("cnv_1").executeAsList().map { it.id })
        // Sinxronizatsiya kursori yashirilganlarni HISOBGA OLADI — aks holda `?after=`
        // ularni qayta tortib, tarixda dublikat paydo bo'lardi.
        assertEquals(3L, q.maxSeq("cnv_1").executeAsOne())
        // Qator butun: tana ham, `seq` ham joyida (bu — o'chirish emas, yashirish).
        val hidden = q.selectMessageById("m3").executeAsOne()
        assertEquals("Yaxshi", hidden.body)
        assertNull(hidden.deletedAt)
        // Ro'yxatdagi ko'rinish uchun: ko'rinadigan eng oxirgi xabar.
        assertEquals("m1", q.selectLastVisibleMessage("cnv_1").executeAsOne().id)

        // Server tarixni qayta yubordi — yashirilgani yashiriligicha qolsin.
        q.message("m3", seq = 3L, sender = "std_ali", body = "Yaxshi", createdAt = 3_000L)
        assertEquals(9_000L, q.selectMessageById("m3").executeAsOne().hiddenAt)
        assertEquals(listOf("m1"), q.selectMessages("cnv_1").executeAsList().map { it.id })

        driver.close()
    }

    /** `upsertMessage` ning uzun imzosini testlarda takrorlamaslik uchun. */
    private fun dev.core.database.sql.ChatQueries.message(
        id: String,
        seq: Long,
        sender: String,
        body: String,
        createdAt: Long,
        conversationId: String = "cnv_1",
        type: String = "TEXT",
        clientMsgId: String? = null,
        status: String = "SENT",
        albumId: String? = null,
    ) = upsertMessage(
        id = id,
        conversationId = conversationId,
        senderId = sender,
        seq = seq,
        type = type,
        body = body,
        createdAt = createdAt,
        clientMsgId = clientMsgId,
        status = status,
        deletedAt = null,
        attachmentId = null,
        attachmentUrl = null,
        attachmentThumbUrl = null,
        attachmentWidth = 0L,
        attachmentHeight = 0L,
        attachmentKind = null,
        attachmentStatus = null,
        attachmentMime = null,
        attachmentSizeBytes = null,
        attachmentDurationMs = null,
        attachmentWaveform = null,
        attachmentFileName = null,
        attachmentBlurHash = null,
        attachmentIsAnimated = null,
        stickerId = null,
        stickerEmoji = null,
        stickerUrl = null,
        albumId = albumId,
        // Sitata — bu yordamchi oddiy xabar yozadi; javob xabari alohida sinaladi.
        replyToId = null,
        replyToSeq = null,
        replyToSenderId = null,
        replyToSenderName = null,
        replyToType = null,
        replyToPreview = null,
        replyToQuoteText = null,
        replyToQuoteOffset = null,
        replyToOriginalDeleted = null,
    )

    @Test
    fun migrationFromV1AddsNewTablesAndColumn() {
        val driver = freshDriver()
        createV1Tables(driver)
        // Profili to'ldirilgan mavjud foydalanuvchi — v6 da ProfileEntity'ga ko'chishi kerak.
        driver.execute(
            null,
            """
            INSERT INTO UserEntity(
                uid, userId, fullName, email, role, phoneNumber, photoUrl,
                firstName, lastName, universityId, universityEmail, birthYear, courseYear, profileRole
            ) VALUES (
                'uid-1', 7, 'Eski Foydalanuvchi', 'a@b.uz', 'STUDENT', '+998901234567', NULL,
                'Quvonchbek', 'G''afurov', 'tuit', NULL, 2004, '3', 'STUDENT'
            )
            """.trimIndent(),
            0,
        )

        // 1.sqm (AppSetting), 2.sqm (Notification), 3.sqm (Club.joined),
        // 4.sqm (Chat.archived), 5.sqm (ProfileEntity ajratish), 6.sqm (avatarUrl) — hammasi ishga tushadi.
        StudentClubDatabase.Schema.migrate(driver, 1L, StudentClubDatabase.Schema.version)
        val db = StudentClubDatabase(driver)

        // Migratsiyadan keyin yangi jadvallar mavjud bo'lishi kerak.
        db.appSettingQueries.upsert("k", "v")
        assertEquals("v", db.appSettingQueries.selectByKey("k").executeAsOne())

        db.notificationQueries.insert("n1", "T", "B", "SYSTEM", "hozir", 1, 0)
        assertEquals(1L, db.notificationQueries.count().executeAsOne())

        // Eski Club satri ham joined ustuniga ega bo'lishi (DEFAULT 0) va setJoined ishlashi kerak.
        db.clubQueries.upsert(1L, "IT", "desc", 5L, null)
        db.clubQueries.setJoined(1L, 1L)
        assertEquals(1L, db.clubQueries.selectAll().executeAsOne().joined)

        // v6: profil UserEntity'dan ProfileEntity'ga ko'chgan bo'lishi kerak...
        val profile = db.profileQueries.selectCurrent().executeAsOne()
        assertEquals("uid-1", profile.uid)
        assertEquals("Quvonchbek", profile.firstName)
        assertEquals("tuit", profile.universityId)
        assertEquals("STUDENT", profile.role) // eski `profileRole` ustunidan
        assertEquals(2004L, profile.birthYear)
        assertNull(profile.avatarUrl) // v7 da qo'shilgan ustun — eski yozuvlarda bo'sh

        // ...eski Firebase sessiyasi esa 15.sqm da o'chirilgan: `uid` endi backend JWT'sining
        // `sub` maydoni, eski qiymat yangi API'da yaroqsiz. Foydalanuvchi qaytadan kiradi,
        // profil keshi esa (yuqorida) saqlanib qoladi.
        assertNull(db.userQueries.selectCurrent().executeAsOneOrNull())

        // Yangi shakl: `userId` ustuni yo'q, `uid` — yagona identifikator.
        db.userQueries.upsert("usr_01H8X", "Yangi Talaba", "a@b.uz", "STUDENT", "+998901234567", null)
        val user = db.userQueries.selectCurrent().executeAsOne()
        assertEquals("usr_01H8X", user.uid)
        assertEquals("Yangi Talaba", user.fullName)

        // v8/v9 (7.sqm + 8.sqm): chegirma e'lonlari jadvali migratsiyadan keyin mavjud
        // va ko'p filialli (branchesJson) ustunga ega bo'lishi kerak.
        db.listingQueries.selectAll().executeAsList().also { assertEquals(0, it.size) }

        driver.close()
    }

    /**
     * 23.sqm — bosh ekran bo'limlari katalog guruhiga bog'landi. Keshdagi eski e'lon qatori
     * yo'qolmasligi (`groupKey` bo'sh qolib, keyingi `refresh()` uni to'ldiradi) va guruhlar
     * HAR DOIM server tartibida o'qilishi kerak — bo'limlar ketma-ketligi shundan.
     */
    @Test
    fun migrationV23AddsGroupsAndKeepsCachedOffers() {
        val driver = freshDriver()
        createV1Tables(driver)
        driver.execute(
            null,
            """
            INSERT INTO DiscountOfferEntity(
                id, categoryId, merchant, title, discountPercent, tag, promoCode, location,
                expiry, emoji, bannerAccent, featured
            ) VALUES ('o-1', 'ovqat', 'Evos', 'Lavash', 20, 'STUDENT_ID', NULL, 'Chilonzor', NULL, '🍕', 1, 0)
            """.trimIndent(),
            0,
        )

        StudentClubDatabase.Schema.migrate(driver, 1L, StudentClubDatabase.Schema.version)
        val db = StudentClubDatabase(driver)

        val cached = db.discountQueries.selectOfferById("o-1").executeAsOne()
        assertEquals("ovqat", cached.categoryId)
        assertEquals("", cached.groupKey, "eski kesh qatori guruhsiz qolishi kerak")

        // Tartib ataylab teskari kiritildi — `selectGroups` uni `sortOrder` bo'yicha qaytaradi.
        db.discountQueries.upsertGroup("SHOPPING", "Savdo va xizmat", "🛍", 0xFF06B6D4L, 7L)
        db.discountQueries.upsertGroup("FOOD", "Ovqatlanish", "🍽", 0xFFF97316L, 1L)
        assertEquals(
            listOf("FOOD", "SHOPPING"),
            db.discountQueries.selectGroups().executeAsList().map { it.key },
        )

        driver.close()
    }

    /**
     * 8.sqm — eng nozik migratsiya: v8 dagi yagona manzil ustunlari (lat/lng/address)
     * bitta filialga aylanib `branchesJson` ga ko'chishi kerak. Koordinatasiz e'lon esa
     * filialsiz qoladi (egasi uni xaritadan qayta belgilaydi).
     */
    @Test
    fun migrationV8ConvertsSingleLocationIntoBranch() {
        val driver = freshDriver()

        // v8 dagi ListingEntity (eski shakl — yagona lokatsiya ustunlari bilan).
        driver.execute(
            null,
            """
            CREATE TABLE ListingEntity (
                id TEXT NOT NULL PRIMARY KEY,
                ownerId TEXT NOT NULL,
                businessId TEXT,
                businessType TEXT NOT NULL,
                businessName TEXT NOT NULL,
                categoryKey TEXT NOT NULL,
                customCategoryName TEXT,
                title TEXT NOT NULL,
                description TEXT,
                imagesJson TEXT NOT NULL DEFAULT '[]',
                priceUnit TEXT NOT NULL,
                originalPrice INTEGER NOT NULL,
                currency TEXT NOT NULL DEFAULT 'UZS',
                discountType TEXT NOT NULL,
                discountValue INTEGER NOT NULL,
                finalPrice INTEGER NOT NULL,
                discountConditions TEXT,
                redemptionMethod TEXT NOT NULL,
                promoCode TEXT,
                perUserLimit INTEGER,
                totalLimit INTEGER,
                usedCount INTEGER NOT NULL DEFAULT 0,
                regionId TEXT,
                districtId TEXT,
                address TEXT,
                landmark TEXT,
                lat REAL,
                lng REAL,
                validFrom INTEGER NOT NULL,
                validTo INTEGER NOT NULL,
                attributesJson TEXT NOT NULL DEFAULT '{}',
                optionGroupsJson TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL,
                rejectionReason TEXT,
                viewsCount INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent(),
            0,
        )

        // v8 sxemasidagi boshqa jadvallar — 9/10/11.sqm ular ustiga ustun qo'shadi.
        // ProfileEntity: 5.sqm base + 6.sqm avatarUrl (businessName/businessType/email keyin qo'shiladi).
        driver.execute(
            null,
            """
            CREATE TABLE ProfileEntity (
                uid TEXT NOT NULL PRIMARY KEY,
                firstName TEXT,
                lastName TEXT,
                phoneNumber TEXT,
                role TEXT,
                universityId TEXT,
                universityEmail TEXT,
                birthYear INTEGER,
                courseYear TEXT,
                avatarUrl TEXT
            )
            """.trimIndent(),
            0,
        )
        createV1DiscountTables(driver)
        createV1StudentTable(driver)

        // Koordinatasi bor e'lon — filialga aylanishi kerak.
        driver.execute(
            null,
            """
            INSERT INTO ListingEntity(
                id, ownerId, businessType, businessName, categoryKey, title,
                priceUnit, originalPrice, discountType, discountValue, finalPrice,
                redemptionMethod, regionId, districtId, address, lat, lng,
                validFrom, validTo, status, createdAt, updatedAt
            ) VALUES (
                'l-1', 'u1', 'CAFE_RESTAURANT', 'Navruz', 'PIZZA', 'Pepperoni',
                'PER_ITEM', 55000, 'PERCENT', 20, 44000,
                'QR', 'TOSHKENT_SHAHRI', 'CHILONZOR', 'Chilonzor 9-kvartal, 42-uy', 41.2856, 69.2034,
                0, 9999999999999, 'ACTIVE', 0, 0
            )
            """.trimIndent(),
            0,
        )

        // Koordinatasiz e'lon — filialsiz qolishi kerak.
        driver.execute(
            null,
            """
            INSERT INTO ListingEntity(
                id, ownerId, businessType, businessName, categoryKey, title,
                priceUnit, originalPrice, discountType, discountValue, finalPrice,
                redemptionMethod, address, validFrom, validTo, status, createdAt, updatedAt
            ) VALUES (
                'l-2', 'u1', 'GROCERY', 'Korzinka', 'DAIRY', 'Sut',
                'PER_ITEM', 12000, 'PERCENT', 10, 10800,
                'STUDENT_ID', 'Qayerdadir', 0, 9999999999999, 'DRAFT', 0, 0
            )
            """.trimIndent(),
            0,
        )

        // Nomida qo'shtirnoq bor e'lon — 14.sqm detailsJson ni matn birlashtirish bilan
        // yig'adi, shuning uchun ekranlash buzilmasligini alohida tekshiramiz.
        // Aloqa telefoni ham eski `attributes._phone` dan ustunga ko'chishi kerak.
        driver.execute(
            null,
            """
            INSERT INTO ListingEntity(
                id, ownerId, businessType, businessName, categoryKey, title,
                priceUnit, originalPrice, discountType, discountValue, finalPrice,
                redemptionMethod, attributesJson, validFrom, validTo, status, createdAt, updatedAt
            ) VALUES (
                'l-3', 'u1', 'CAFE_RESTAURANT', 'Kafe "Bahor"', 'PIZZA', 'Lavash',
                'PER_ITEM', 30000, 'PERCENT', 10, 27000,
                'QR', '{"_phone":"+998901234567","_regular":"1"}', 0, 9999999999999, 'ACTIVE', 0, 0
            )
            """.trimIndent(),
            0,
        )

        StudentClubDatabase.Schema.migrate(driver, 8L, StudentClubDatabase.Schema.version)
        val db = StudentClubDatabase(driver)

        // 14.sqm — chegirma ustunlari detailsJson ga ko'chdi, umumiylari ustun bo'lib qoldi.
        val migrated = db.listingQueries.selectById("l-1").executeAsOne()
        assertEquals("DISCOUNT", migrated.kind)
        assertEquals(55_000L, migrated.price)
        assertTrue(
            migrated.detailsJson.contains("\"businessType\":\"CAFE_RESTAURANT\""),
            "biznes turi detailsJson ga ko'chmadi: ${migrated.detailsJson}",
        )
        assertTrue(
            migrated.detailsJson.contains("\"discountValue\":20"),
            "chegirma qiymati ko'chmadi: ${migrated.detailsJson}",
        )
        assertTrue(migrated.detailsJson.contains("\"isDiscounted\":true"))

        val quoted = db.listingQueries.selectById("l-3").executeAsOne()
        assertTrue(
            quoted.detailsJson.contains("""\"Bahor\""""),
            "qo'shtirnoq ekranlanmadi: ${quoted.detailsJson}",
        )
        // `_regular` belgisi bo'lgan qator — oddiy (chegirmasiz) e'lon.
        assertTrue(quoted.detailsJson.contains("\"isDiscounted\":false"))
        assertEquals("+998901234567", quoted.contactPhone)

        val withBranch = db.listingQueries.selectById("l-1").executeAsOne()
        assertTrue(withBranch.branchesJson.contains("41.2856"), "lat ko'chmadi: ${withBranch.branchesJson}")
        assertTrue(withBranch.branchesJson.contains("69.2034"), "lng ko'chmadi: ${withBranch.branchesJson}")
        assertTrue(withBranch.branchesJson.contains("Chilonzor 9-kvartal, 42-uy"), "manzil ko'chmadi")
        assertTrue(withBranch.branchesJson.contains("CHILONZOR"), "tuman ko'chmadi")

        val withoutBranch = db.listingQueries.selectById("l-2").executeAsOne()
        assertEquals("[]", withoutBranch.branchesJson)

        driver.close()
    }

    /** E'lon (feature:listings) — yozish, faol e'lonlarni tanlash, status, o'chirish. */
    @Test
    fun listingCrudAndActiveFilterWork() {
        val driver = freshDriver()
        StudentClubDatabase.Schema.create(driver)
        val db = StudentClubDatabase(driver)
        val q = db.listingQueries

        fun insert(
            id: String,
            status: String,
            validTo: Long,
            kind: String = "DISCOUNT",
            detailsJson: String = """{"kind":"DISCOUNT","businessType":"CAFE_RESTAURANT",""" +
                """"businessName":"Chaykhana Navruz","categoryKey":"PIZZA",""" +
                """"isDiscounted":true,"discountType":"PERCENT","discountValue":20}""",
        ) = q.upsert(
            id = id,
            ownerId = "u1",
            businessId = null,
            kind = kind,
            detailsJson = detailsJson,
            title = "Pepperoni pitsa",
            description = null,
            imagesJson = """["data:image/jpeg;base64,AAA"]""",
            priceUnit = "PER_ITEM",
            price = 55_000,
            priceMax = null,
            currency = "UZS",
            isNegotiable = 0,
            finalPrice = 44_000,
            contactPhone = "+998901234567",
            branchesJson = """[{"id":"br1","lat":41.2856,"lng":69.2034,"address":"Chilonzor 9-kvartal, 42-uy"}]""",
            validFrom = 0,
            validTo = validTo,
            attributesJson = """{"isHalal":"true"}""",
            optionGroupsJson = "[]",
            status = status,
            rejectionReason = null,
            viewsCount = 0,
            createdAt = 0,
            updatedAt = 0,
        )

        insert("l-active", "ACTIVE", validTo = 2_000)
        insert("l-draft", "DRAFT", validTo = 2_000)
        insert("l-expired", "ACTIVE", validTo = 500) // muddati o'tgan

        // Turga qarab filtrlash — Ijara va Ish alohida bo'limlar bo'lgani uchun.
        insert(
            "l-rental",
            "ACTIVE",
            validTo = 2_000,
            kind = "RENTAL",
            detailsJson = """{"kind":"RENTAL","roomCount":3,"currentTenants":2,""" +
                """"neededTenants":2,"gender":"MALE","period":"MONTHLY"}""",
        )

        val rentals = q.selectActiveByKind(kind = "RENTAL", now = 1_000).executeAsList()
        assertEquals(1, rentals.size)
        assertEquals("l-rental", rentals.single().id)
        assertTrue(rentals.single().detailsJson.contains("\"gender\":\"MALE\""))

        assertEquals(1, q.selectActiveByKind(kind = "DISCOUNT", now = 1_000).executeAsList().size)
        q.deleteById("l-rental")

        assertEquals(3, q.selectByOwner("u1").executeAsList().size)
        assertEquals(44_000L, q.selectById("l-active").executeAsOne().finalPrice)

        // Talabaga faqat ACTIVE va muddati o'tmaganlari ko'rinadi.
        val visible = q.selectActive(now = 1_000).executeAsList()
        assertEquals(1, visible.size)
        assertEquals("l-active", visible.single().id)

        q.updateStatus(status = "PAUSED", updatedAt = 5, id = "l-active")
        assertTrue(q.selectActive(now = 1_000).executeAsList().isEmpty())

        q.deleteById("l-draft")
        assertEquals(2, q.selectByOwner("u1").executeAsList().size)

        driver.close()
    }

    @Test
    fun migrationSkipsEmptyProfileRows() {
        val driver = freshDriver()
        createV1Tables(driver)
        // Profili to'ldirilmagan foydalanuvchi — bo'sh ProfileEntity qatori YARATILMASLIGI kerak,
        // aks holda `hasProfile()` noto'g'ri `true` qaytaradi.
        driver.execute(
            null,
            """
            INSERT INTO UserEntity(uid, userId, fullName, email, role, phoneNumber, photoUrl)
            VALUES ('uid-2', 9, 'Profilsiz', 'c@d.uz', 'STUDENT', NULL, NULL)
            """.trimIndent(),
            0,
        )

        StudentClubDatabase.Schema.migrate(driver, 1L, StudentClubDatabase.Schema.version)
        val db = StudentClubDatabase(driver)

        assertNull(db.profileQueries.selectCurrent().executeAsOneOrNull())
        // 15.sqm eski (Firebase) sessiyani o'chiradi — yangi backendda u yaroqsiz.
        assertNull(db.userQueries.selectCurrent().executeAsOneOrNull())

        driver.close()
    }

    @Test
    fun seedInsertIsIdempotentByPrimaryKey() {
        val driver = freshDriver()
        StudentClubDatabase.Schema.create(driver)
        val db = StudentClubDatabase(driver)

        // Bir xil id bilan ikki marta — INSERT OR REPLACE dublikat yaratmasligi kerak.
        db.clubQueries.upsert(1L, "IT", "desc", 5L, null)
        db.clubQueries.upsert(1L, "IT yangilangan", "desc2", 6L, null)
        assertEquals(1, db.clubQueries.selectAll().executeAsList().size)
        assertTrue(db.clubQueries.selectAll().executeAsOne().name == "IT yangilangan")

        driver.close()
    }
}
