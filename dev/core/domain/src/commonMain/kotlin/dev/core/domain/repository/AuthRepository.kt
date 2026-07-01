package dev.core.domain.repository

import dev.core.common.Resource
import dev.core.domain.model.ExternalAuthUser
import dev.core.domain.model.User

interface AuthRepository {
    suspend fun login(email: String, password: String): Resource<User>

    /**
     * Firebase (Google/Telefon) orqali autentifikatsiyadan o'tgan foydalanuvchini
     * domen [User] ga aylantiradi va (kelajakda) backend bilan sinxronlaydi.
     */
    suspend fun syncExternalUser(external: ExternalAuthUser): Resource<User>

    suspend fun logout()
    suspend fun currentUser(): User?
}
