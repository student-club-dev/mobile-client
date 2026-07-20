package dev.feature.students.data.mapper

import dev.core.database.sql.StudentEntity
import dev.feature.students.domain.model.FriendStatus
import dev.feature.students.domain.model.Student

internal fun StudentEntity.toDomain(): Student = Student(
    id = id,
    firstName = firstName,
    lastName = lastName,
    initial = initial,
    universityId = universityId,
    universityMonogram = universityMonogram,
    course = course.toInt(),
    faculty = faculty,
    friendStatus = parseEnum(friendStatus, FriendStatus.NONE),
    interests = interests.splitDb(),
    friendsCount = friendsCount.toInt(),
    adsCount = adsCount.toInt(),
    rating = rating,
)

internal fun List<String>.joinDb(): String = joinToString("|")
internal fun String.splitDb(): List<String> = if (isBlank()) emptyList() else split("|").filter { it.isNotBlank() }
internal inline fun <reified T : Enum<T>> parseEnum(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
