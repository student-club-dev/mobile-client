package dev.core.database

import dev.core.database.sql.StudentClubDatabase

object DatabaseFactory {
    fun create(driverFactory: DriverFactory): StudentClubDatabase =
        StudentClubDatabase(driverFactory.createDriver())
}
