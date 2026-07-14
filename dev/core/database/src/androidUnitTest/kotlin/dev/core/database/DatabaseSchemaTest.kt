package dev.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.core.database.sql.StudentClubsDatabase
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
    private fun createV1Tables(driver: JdbcSqliteDriver) {
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
    }

    @Test
    fun schemaVersionIsSeven() {
        assertEquals(7L, StudentClubsDatabase.Schema.version)
    }

    @Test
    fun freshSchemaCreatesAllTablesAndCrudWorks() {
        val driver = freshDriver()
        StudentClubsDatabase.Schema.create(driver)
        val db = StudentClubsDatabase(driver)

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
            avatarUrl = "https://cdn.studentclubs.dev/avatars/uid-1.jpg",
        )
        val profile = db.profileQueries.selectCurrent().executeAsOne()
        assertEquals("Quvonchbek", profile.firstName)
        assertEquals("tuit", profile.universityId)
        assertEquals(2004L, profile.birthYear)
        assertEquals("https://cdn.studentclubs.dev/avatars/uid-1.jpg", profile.avatarUrl)

        db.profileQueries.clear()
        assertNull(db.profileQueries.selectCurrent().executeAsOneOrNull())

        driver.close()
    }

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
        StudentClubsDatabase.Schema.migrate(driver, 1L, StudentClubsDatabase.Schema.version)
        val db = StudentClubsDatabase(driver)

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

        // ...sessiya esa UserEntity'da saqlanib qolgan (profil ustunlarisiz).
        val user = db.userQueries.selectCurrent().executeAsOne()
        assertEquals("uid-1", user.uid)
        assertEquals("Eski Foydalanuvchi", user.fullName)
        assertEquals(7L, user.userId)

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

        StudentClubsDatabase.Schema.migrate(driver, 1L, StudentClubsDatabase.Schema.version)
        val db = StudentClubsDatabase(driver)

        assertNull(db.profileQueries.selectCurrent().executeAsOneOrNull())
        assertEquals("Profilsiz", db.userQueries.selectCurrent().executeAsOne().fullName)

        driver.close()
    }

    @Test
    fun seedInsertIsIdempotentByPrimaryKey() {
        val driver = freshDriver()
        StudentClubsDatabase.Schema.create(driver)
        val db = StudentClubsDatabase(driver)

        // Bir xil id bilan ikki marta — INSERT OR REPLACE dublikat yaratmasligi kerak.
        db.clubQueries.upsert(1L, "IT", "desc", 5L, null)
        db.clubQueries.upsert(1L, "IT yangilangan", "desc2", 6L, null)
        assertEquals(1, db.clubQueries.selectAll().executeAsList().size)
        assertTrue(db.clubQueries.selectAll().executeAsOne().name == "IT yangilangan")

        driver.close()
    }
}
