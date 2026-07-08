package dev.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.core.database.sql.StudentClubsDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Host (JVM) testlari — SQLDelight sxemasi, migratsiya zanjiri va yangi jadvallar CRUD'ini
 * real SQLite engine'da tekshiradi. SQL mantiqi Android va iOS'da bir xil bo'lgani uchun
 * bu ikkala platforma uchun ham amal qiladi.
 */
class DatabaseSchemaTest {

    private fun freshDriver() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @Test
    fun schemaVersionIsFour() {
        assertEquals(4L, StudentClubsDatabase.Schema.version)
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

        driver.close()
    }

    @Test
    fun migrationFromV1AddsNewTablesAndColumn() {
        val driver = freshDriver()
        // v1 holatini taqlid qilamiz: faqat eski ClubEntity (joined ustunisiz).
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

        // 1.sqm (AppSetting), 2.sqm (Notification), 3.sqm (Club.joined) — hammasi ishga tushadi.
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
