package dev.feature.students.presentation.di

import dev.feature.students.data.remote.KtorStudentRemoteDataSource
import dev.feature.students.data.remote.StudentRemoteDataSource
import dev.feature.students.data.repository.StudentRepositoryImpl
import dev.feature.students.domain.repository.StudentRepository
import org.koin.dsl.module

/**
 * Studentlar feature'i — **local ro'yxatlar** (Home va "Mening universitetim" ekranlaridagi
 * universitet/kurs/fakultet bo'yicha kartochkalar). [useRemoteApi] — masofaviy sinxronlash
 * bayrog'i.
 *
 * ⚠️ Bu bo'lim **bog'lanish (connect) tizimi EMAS**. Haqiqiy do'stlik oqimi — talaba
 * qidirish, so'rov yuborish, blok va shikoyat — `:dev:feature:connections` da, backendning
 * `Connections` bo'limiga ulangan. Bu yerdagi `friendStatus` faqat local seed ma'lumot,
 * chunki backendda "barcha talabalar ro'yxati" endpointi yo'q (faqat `?q=` bo'yicha qidiruv).
 */
fun studentsModule(useRemoteApi: Boolean) = module {
    single<StudentRemoteDataSource> { KtorStudentRemoteDataSource(get()) }
    single<StudentRepository> { StudentRepositoryImpl(get(), get(), get(), useRemoteApi) }
}
