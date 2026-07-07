package dev.core.domain.repository

import dev.core.domain.model.Ad
import dev.core.domain.model.Conversation
import dev.core.domain.model.DiscountCategory
import dev.core.domain.model.DiscountOffer
import dev.core.domain.model.FriendStatus
import dev.core.domain.model.Job
import dev.core.domain.model.JobApplication
import dev.core.domain.model.Message
import dev.core.domain.model.Student
import dev.core.domain.model.University
import kotlinx.coroutines.flow.Flow

/** Universitetlar (local DB — profil to'ldirish va studentlar bo'limi uchun). */
interface UniversityRepository {
    fun observeUniversities(): Flow<List<University>>
}

/** Chegirmalar — kategoriyalar, takliflar, saqlangan takliflar. */
interface DiscountRepository {
    fun observeCategories(): Flow<List<DiscountCategory>>
    fun observeOffers(categoryId: String): Flow<List<DiscountOffer>>
    fun observeFeatured(): Flow<List<DiscountOffer>>
    fun observeSaved(): Flow<List<DiscountOffer>>
    suspend fun setSaved(offerId: String, saved: Boolean)
}

/** Ishlar — ro'yxat, saqlangan (bookmark), arizalar. */
interface JobRepository {
    fun observeJobs(): Flow<List<Job>>
    fun observeBookmarked(): Flow<List<Job>>
    fun observeApplications(): Flow<List<JobApplication>>
    suspend fun setBookmarked(jobId: String, bookmarked: Boolean)
    suspend fun apply(job: Job)
}

/** Studentlar — do'st topish (universitet bo'yicha) va do'stlik holati. */
interface StudentRepository {
    fun observeStudents(): Flow<List<Student>>
    fun observeByUniversity(universityId: String): Flow<List<Student>>
    suspend fun setFriendStatus(studentId: String, status: FriendStatus)
}

/** E'lonlar — joylash, o'chirish, foydalanuvchiniki. */
interface AdRepository {
    fun observeAds(): Flow<List<Ad>>
    fun observeByOwner(ownerId: String): Flow<List<Ad>>
    suspend fun post(ad: Ad)
    suspend fun delete(adId: String)
}

/** Chat — suhbatlar va xabarlar (offline, real-time'ga tayyor tuzilma). */
interface ChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    suspend fun send(conversationId: String, text: String, time: String, createdAt: Long)
    suspend fun markRead(conversationId: String)
}
