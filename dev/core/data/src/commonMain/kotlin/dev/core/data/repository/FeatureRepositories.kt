package dev.core.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.core.common.AppDispatchers
import dev.core.data.mapper.joinDb
import dev.core.data.mapper.toBool
import dev.core.data.mapper.toDb
import dev.core.data.mapper.toDomain
import dev.core.database.sql.StudentClubsDatabase
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
import dev.core.domain.repository.AdRepository
import dev.core.domain.repository.ChatRepository
import dev.core.domain.repository.DiscountRepository
import dev.core.domain.repository.JobRepository
import dev.core.domain.repository.StudentRepository
import dev.core.domain.repository.UniversityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// ===========================================================================
// Universitetlar
// ===========================================================================
class UniversityRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : UniversityRepository {
    override fun observeUniversities(): Flow<List<University>> =
        db.universityQueries.selectAll().asFlow().mapToList(dispatchers.io)
            .map { rows -> rows.map { it.toDomain() } }
}

// ===========================================================================
// Chegirmalar
// ===========================================================================
class DiscountRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : DiscountRepository {
    private val q get() = db.discountQueries

    override fun observeCategories(): Flow<List<DiscountCategory>> =
        q.selectCategories().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeOffers(categoryId: String): Flow<List<DiscountOffer>> =
        q.selectOffersByCategory(categoryId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeFeatured(): Flow<List<DiscountOffer>> =
        q.selectFeaturedOffers().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeSaved(): Flow<List<DiscountOffer>> =
        q.selectSavedOffers().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun setSaved(offerId: String, saved: Boolean) = withContext(dispatchers.io) {
        if (saved) q.saveOffer(offerId) else q.unsaveOffer(offerId)
    }
}

// ===========================================================================
// Ishlar
// ===========================================================================
class JobRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : JobRepository {
    private val q get() = db.jobQueries

    override fun observeJobs(): Flow<List<Job>> =
        q.selectAllJobs().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeBookmarked(): Flow<List<Job>> =
        q.selectBookmarked().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeApplications(): Flow<List<JobApplication>> =
        q.selectApplications().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun setBookmarked(jobId: String, bookmarked: Boolean) = withContext(dispatchers.io) {
        q.setBookmark(bookmarked.toDb(), jobId)
    }

    override suspend fun apply(job: Job) = withContext(dispatchers.io) {
        q.upsertApplication(
            id = "app-${job.id}",
            jobId = job.id,
            jobTitle = job.title,
            company = job.company,
            status = dev.core.domain.model.ApplicationStatus.SENT.name,
            appliedAgo = "hozir",
        )
    }
}

// ===========================================================================
// Studentlar
// ===========================================================================
class StudentRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : StudentRepository {
    private val q get() = db.studentQueries

    override fun observeStudents(): Flow<List<Student>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeByUniversity(universityId: String): Flow<List<Student>> =
        q.selectByUniversity(universityId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun setFriendStatus(studentId: String, status: FriendStatus) = withContext(dispatchers.io) {
        q.setFriendStatus(status.name, studentId)
    }
}

// ===========================================================================
// E'lonlar
// ===========================================================================
class AdRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : AdRepository {
    private val q get() = db.adQueries

    override fun observeAds(): Flow<List<Ad>> =
        q.selectAll().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeByOwner(ownerId: String): Flow<List<Ad>> =
        q.selectByOwner(ownerId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun post(ad: Ad) = withContext(dispatchers.io) {
        q.upsert(
            id = ad.id,
            type = ad.type.name,
            title = ad.title,
            category = ad.category,
            price = ad.price,
            description = ad.description,
            images = ad.images.joinDb(),
            ownerId = ad.ownerId,
            createdAgo = ad.createdAgo,
        )
    }

    override suspend fun delete(adId: String) = withContext(dispatchers.io) {
        q.deleteById(adId)
    }
}

// ===========================================================================
// Chat
// ===========================================================================
class ChatRepositoryImpl(
    private val db: StudentClubsDatabase,
    private val dispatchers: AppDispatchers,
) : ChatRepository {
    private val q get() = db.chatQueries

    override fun observeConversations(): Flow<List<Conversation>> =
        q.selectConversations().asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        q.selectMessages(conversationId).asFlow().mapToList(dispatchers.io).map { r -> r.map { it.toDomain() } }

    override suspend fun send(conversationId: String, text: String, time: String, createdAt: Long) =
        withContext(dispatchers.io) {
            q.transaction {
                q.insertMessage(
                    id = "$conversationId-$createdAt",
                    conversationId = conversationId,
                    body = text,
                    outgoing = true.toDb(),
                    time = time,
                    createdAt = createdAt,
                )
                q.touchConversation(text, time, 0L, conversationId)
            }
        }

    override suspend fun markRead(conversationId: String) = withContext(dispatchers.io) {
        q.markRead(conversationId)
    }
}
