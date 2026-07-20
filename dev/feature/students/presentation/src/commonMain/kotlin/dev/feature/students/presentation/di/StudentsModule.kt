package dev.feature.students.presentation.di

import dev.feature.students.data.remote.KtorStudentRemoteDataSource
import dev.feature.students.data.remote.StudentRemoteDataSource
import dev.feature.students.data.repository.StudentRepositoryImpl
import dev.feature.students.domain.repository.StudentRepository
import dev.feature.students.presentation.StudentsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Studentlar feature'ining barcha qatlamlari. [useRemoteApi] — masofaviy sinxronlash bayrog'i. */
fun studentsModule(useRemoteApi: Boolean) = module {
    single<StudentRemoteDataSource> { KtorStudentRemoteDataSource(get()) }
    single<StudentRepository> { StudentRepositoryImpl(get(), get(), get(), useRemoteApi) }
    viewModelOf(::StudentsViewModel)
}
