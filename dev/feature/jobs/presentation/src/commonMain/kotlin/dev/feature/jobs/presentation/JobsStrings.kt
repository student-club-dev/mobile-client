package dev.feature.jobs.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.uikit.locale.rememberStrings

/** Ishlar ekrani matnlari. Sukut qiymatlar — inglizcha. */
data class JobsStrings(
    val title: String = "Jobs",
    val filter: String = "Filter",
    val filterAll: String = "All",
    val filterRemote: String = "Remote",
    val filterPartTime: String = "Part-time",
    val bookmark: String = "Save",
    val salary: String = "Salary",
    val fieldNote: (String) -> String = { "This job matches your field ($it). The full description comes from the poster." },
    val apply: String = "Apply",
    val applied: String = "Application sent ✓",
)

private val JobsEn = JobsStrings()

private val JobsRu = JobsStrings(
    title = "Работа",
    filter = "Фильтр",
    filterAll = "Все",
    filterRemote = "Удалённо",
    filterPartTime = "Part-time",
    bookmark = "Сохранить",
    salary = "Зарплата",
    fieldNote = { "Эта вакансия соответствует вашему направлению ($it). Полное описание — от автора объявления." },
    apply = "Откликнуться",
    applied = "Отклик отправлен ✓",
)

private val JobsUz = JobsStrings(
    title = "Ishlar",
    filter = "Filtr",
    filterAll = "Barchasi",
    filterRemote = "Masofaviy",
    filterPartTime = "Part-time",
    bookmark = "Saqlash",
    salary = "Maosh",
    fieldNote = { "Bu ish sizning bo'limingizga ($it) mos keladi. To'liq tavsif e'lon egasidan." },
    apply = "Ariza berish",
    applied = "Ariza yuborildi ✓",
)

@Composable
@ReadOnlyComposable
internal fun jobsStrings(): JobsStrings = rememberStrings(JobsEn, JobsRu, JobsUz)
