package dev.feature.profile.data.mapper

import dev.core.database.sql.ProfileEntity
import dev.core.network.generated.model.CourseYearDto
import dev.core.network.generated.model.GenderDto
import dev.core.network.generated.model.LastSeenVisibilityDto
import dev.core.network.generated.model.PhoneVisibilityDto
import dev.core.network.generated.model.ProfilePhotoDto
import dev.core.network.generated.model.ProfileRoleDto
import dev.core.network.generated.model.UpdateProfileDto
import dev.core.network.generated.model.UserProfileDto
import dev.feature.profile.domain.model.ProfilePhoto
import dev.feature.profile.domain.model.UserProfile

// ---------------------------------------------------------------------------
// Local kesh (SQLDelight)
// ---------------------------------------------------------------------------

fun ProfileEntity.toDomain(): UserProfile = UserProfile(
    firstName = firstName,
    lastName = lastName,
    phoneNumber = phoneNumber,
    role = role,
    universityId = universityId,
    universityEmail = universityEmail,
    regionId = regionId,
    districtId = districtId,
    birthYear = birthYear?.toInt(),
    courseYear = courseYear,
    gender = gender,
    lastSeenVisibility = lastSeenVisibility,
    phoneVisibility = phoneVisibility,
    bio = bio,
    avatarUrl = avatarUrl,
    businessName = businessName,
    businessType = businessType,
    email = email,
)

// ---------------------------------------------------------------------------
// REST — OpenAPI'dan generatsiya qilingan modellar
// ---------------------------------------------------------------------------

fun UserProfileDto.toDomain(): UserProfile = UserProfile(
    firstName = firstName,
    lastName = lastName,
    phoneNumber = phoneNumber,
    role = role?.value,
    universityId = universityId,
    universityEmail = universityEmail,
    regionId = regionId,
    districtId = districtId,
    birthYear = birthYear,
    courseYear = courseYear?.value,
    gender = gender?.value,
    lastSeenVisibility = lastSeenVisibility?.value,
    phoneVisibility = phoneVisibility?.value,
    bio = bio,
    avatarUrl = avatarUrl,
)

fun UserProfile.toUpdateRequest(): UpdateProfileDto = UpdateProfileDto(
    firstName = firstName,
    lastName = lastName,
    phoneNumber = phoneNumber,
    role = role?.toRoleDto(),
    universityId = universityId,
    universityEmail = universityEmail,
    regionId = regionId,
    districtId = districtId,
    birthYear = birthYear,
    courseYear = courseYear?.toCourseYearDto(),
    gender = gender?.toGenderDto(),
    lastSeenVisibility = lastSeenVisibility?.toLastSeenVisibilityDto(),
    phoneVisibility = phoneVisibility?.toPhoneVisibilityDto(),
    // Bo'sh satr — "tozalash" (server shunday tushunadi), `null` esa "tegilmasin".
    bio = bio,
    avatarUrl = avatarUrl,
)

fun ProfilePhotoDto.toDomain(): ProfilePhoto = ProfilePhoto(
    id = id,
    url = url,
    thumbUrl = thumbUrl,
    width = width ?: 0,
    height = height ?: 0,
)

/** Domen `String` -> generatsiya qilingan enum. Noma'lum qiymat bo'lsa `null` (server default'i qoladi). */
private fun String.toRoleDto(): ProfileRoleDto? =
    ProfileRoleDto.entries.firstOrNull { it.value == this }

private fun String.toCourseYearDto(): CourseYearDto? =
    CourseYearDto.entries.firstOrNull { it.value == this }

private fun String.toGenderDto(): GenderDto? =
    GenderDto.entries.firstOrNull { it.value == this }

private fun String.toLastSeenVisibilityDto(): LastSeenVisibilityDto? =
    LastSeenVisibilityDto.entries.firstOrNull { it.value == this }

private fun String.toPhoneVisibilityDto(): PhoneVisibilityDto? =
    PhoneVisibilityDto.entries.firstOrNull { it.value == this }
