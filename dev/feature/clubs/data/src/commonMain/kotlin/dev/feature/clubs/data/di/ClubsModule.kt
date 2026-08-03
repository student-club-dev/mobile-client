package dev.feature.clubs.data.di

import dev.feature.clubs.data.repository.ClubRepositoryImpl
import dev.feature.clubs.domain.repository.ClubRepository
import org.koin.dsl.module

/**
 * Klublar — faqat ma'lumot qatlami.
 *
 * Presentation moduli YO'Q: klub jamoaviy suhbat bo'lgani uchun uning ro'yxati ham,
 * qo'shilish/chiqishi ham "Xabarlar" ekranining "Klublar" papkasida
 * (`ChatScreen` + `ChatViewModel`).
 */
fun clubsModule() = module {
    single<ClubRepository> { ClubRepositoryImpl(get(), get(), get()) }
}
