package dev.feature.stories.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.core.common.locale.AppLocale
import dev.core.uikit.locale.rememberStrings

/** Hikoya (post) ekranlarining matnlari. Sukut qiymatlar — inglizcha. */
data class StoriesStrings(
    // Lenta
    val loading: String = "Loading",
    val loadingEllipsis: String = "Loading…",
    val addStory: String = "Add story",
    val uploading: (String) -> String = { "Uploading $it" },
    val preparing: String = "Preparing…",
    val myStory: String = "My story",
    val gotIt: String = "Got it",
    val videoTooLong: String = "A story can't be longer than 1 minute. Trim the video and pick it again.",
    val videoTooLarge: String = "Couldn't send the video — it's too large.",
    val storyPosted: String = "Story posted",
    val storyCreateFailed: String = "Couldn't create the story",

    // Ko'ruvchi
    val mediaMissing: String = "This story's file wasn't kept — only the caption remains in the archive.",
    val noViewsYet: String = "No one has seen it yet",
    val viewCount: (Int) -> String = { "Seen $it times" },
    val delete: String = "Delete",
    val close: String = "Close",
    val deleteTitle: String = "Delete this story?",
    val deleteBody: String = "The story disappears right away. It can't be edited — you'd have to delete and post again.",
    val cancel: String = "Cancel",

    // Ko'rganlar varag'i
    val viewers: String = "Viewers",
    val viewersCount: (Int) -> String = { "$it views" },
    val noViewersYet: String = "No one has seen it yet. The post is visible to your connections.",

    // Postlar to'ri
    val more: String = "More",
    val mediaRetention: String = "Photos and videos are kept for a year — after that only the caption remains",
    val videoPost: String = "Video post",
    val post: String = "Post",
    val mediaNotStored: String = "Media not stored",
    val views: String = "Views",
)

private val StoriesEn = StoriesStrings()

private val StoriesRu = StoriesStrings(
    loading = "Загрузка",
    loadingEllipsis = "Загрузка…",
    addStory = "Добавить историю",
    uploading = { "Загрузка $it" },
    preparing = "Подготовка…",
    myStory = "Моя история",
    gotIt = "Понятно",
    videoTooLong = "История не должна быть длиннее 1 минуты. Обрежьте видео и выберите заново.",
    videoTooLarge = "Не удалось отправить видео — оно слишком большое.",
    storyPosted = "История опубликована",
    storyCreateFailed = "Не удалось создать историю",

    mediaMissing = "Файл этой истории не сохранён — в архиве осталась только подпись.",
    noViewsYet = "Пока никто не посмотрел",
    viewCount = { "Просмотров: $it" },
    delete = "Удалить",
    close = "Закрыть",
    deleteTitle = "Удалить историю?",
    deleteBody = "История исчезнет сразу. Изменить нельзя — придётся удалить и опубликовать заново.",
    cancel = "Отмена",

    viewers = "Просмотры",
    viewersCount = { "$it просмотров" },
    noViewersYet = "Пока никто не посмотрел. Пост виден вашим друзьям.",

    more = "Ещё",
    mediaRetention = "Фото и видео хранятся год — после этого от поста остаётся только подпись",
    videoPost = "Видеопост",
    post = "Пост",
    mediaNotStored = "Медиа не сохранено",
    views = "Просмотры",
)

private val StoriesUz = StoriesStrings(
    loading = "Yuklanmoqda",
    loadingEllipsis = "Yuklanmoqda…",
    addStory = "Hikoya qo'shish",
    uploading = { "Yuklanmoqda $it" },
    preparing = "Tayyorlanmoqda…",
    myStory = "Hikoyam",
    gotIt = "Tushunarli",
    videoTooLong = "Hikoya 1 daqiqadan uzun bo'lmasin. Videoni qisqartirib qayta tanlang.",
    videoTooLarge = "Videoni yuborib bo'lmadi — u juda katta.",
    storyPosted = "Hikoya joylandi",
    storyCreateFailed = "Story yaratilmadi",

    mediaMissing = "Bu hikoyaning fayli saqlanmagan — arxivda faqat yozuvi qoldi.",
    noViewsYet = "Hali hech kim ko'rmagan",
    viewCount = { "$it marta ko'rilgan" },
    delete = "O'chirish",
    close = "Yopish",
    deleteTitle = "Hikoyani o'chirasizmi?",
    deleteBody = "Hikoya darhol yo'qoladi. Tahrirlash imkoni yo'q — o'chirib, qaytadan qo'yish kerak.",
    cancel = "Bekor qilish",

    viewers = "Ko'rganlar",
    viewersCount = { "$it ta ko'rish" },
    noViewersYet = "Hozircha hech kim ko'rmagan. Post bog'langanlaringizga ko'rinadi.",

    more = "Yana",
    mediaRetention = "Rasm va videolar bir yil saqlanadi — keyin postning faqat yozuvi qoladi",
    videoPost = "Video post",
    post = "Post",
    mediaNotStored = "Media saqlanmagan",
    views = "Ko'rishlar",
)

@Composable
@ReadOnlyComposable
internal fun storiesStrings(): StoriesStrings = rememberStrings(StoriesEn, StoriesRu, StoriesUz)

/** ViewModel/repository uchun — Compose'dan tashqarida. */
internal fun storiesStringsNow(): StoriesStrings = AppLocale.pick(StoriesEn, StoriesRu, StoriesUz)
