package dev.feature.stories.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.core.common.Resource
import dev.core.common.auth.TokenStore
import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.VideoPreparer
import dev.core.uikit.media.deleteMediaFile
import dev.core.uikit.media.saveBytesToStudentClubFolder
import dev.core.uikit.media.saveToStudentClubFolder
import dev.core.uikit.media.storyMediaFileName
import dev.core.uikit.media.videoNeedsPreparing
import dev.feature.connections.domain.model.StudentSummary
import dev.feature.stories.domain.model.Story
import dev.feature.stories.domain.model.StoryGroup
import dev.feature.stories.domain.repository.StoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lenta holati (`handoff/07-STORIES.md` §2). */
@Immutable
data class StoriesState(
    /** **Server tartibida**: avval ko'rilmaganlar, ular ichida yangidan eskiga. */
    val groups: List<StoryGroup> = emptyList(),
    /** O'z faol lavhalarim — «Sizning lavhangiz» katakchasi uchun. */
    val mine: List<Story> = emptyList(),
    val loading: Boolean = false,
    /** Yangi lavha yuklanyapti — katakchada indikator. */
    val publishing: Boolean = false,
    /**
     * Yuklash foizi (`0f..1f`). Fayl ketib bo'lgach `null` bo'ladi va katakcha
     * "tayyorlanmoqda" holatiga o'tadi: server lavhani yaratishi (video bo'lsa —
     * transkod qilishi) qancha davom etishini oldindan bilib bo'lmaydi.
     */
    val publishProgress: Float? = null,
    val message: String? = null,
) {
    val hasMine: Boolean get() = mine.isNotEmpty()
}

/** Ochilgan viewer holati — qaysi guruh, qaysi lavha. */
@Immutable
data class StoryViewerState(
    val group: StoryGroup? = null,
    val index: Int = 0,
) {
    val story: Story? get() = group?.stories?.getOrNull(index)
    val open: Boolean get() = group != null
}

/**
 * Story lentasi va ko'ruvchisi.
 *
 * Kesh yo'q ([StoryRepository] izohi): lavha 24 soat yashaydi va istalgan payt muddati
 * o'tishi mumkin, shu jumladan foydalanuvchi uni **ochib turganda**. Shuning uchun `404`
 * xato oynasi bilan emas, **lentani yangilash** bilan qarshi olinadi (§10).
 */
class StoriesViewModel(
    private val repository: StoryRepository,
    private val tokenStore: TokenStore,
) : ViewModel() {

    /**
     * Story medialari uchun sarlavhalar.
     *
     * ⚠️ `PROFILE_PHOTO` dan farqli, story fayllarini faqat muallif va unga **bog'langan**
     * odam o'qiy oladi (`handoff/07-STORIES.md` §11.2) — tokensiz so'rov `404` oladi.
     */
    fun mediaHeaders(): Map<String, String> =
        tokenStore.tokens()?.accessToken?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty()

    private val _state = MutableStateFlow(StoriesState())
    val state: StateFlow<StoriesState> = _state.asStateFlow()

    private val _viewer = MutableStateFlow(StoryViewerState())
    val viewer: StateFlow<StoryViewerState> = _viewer.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val feed = repository.feed()
            val mine = repository.mine()
            // Faqat O'ZIMNIKIGA local nusxa qidiriladi — boshqa odamning lavhasi telefonda
            // yo'q.
            val local = localMediaUrls()
            _state.update { current ->
                current.copy(
                    groups = (feed as? Resource.Success)?.data ?: current.groups,
                    mine = (mine as? Resource.Success)?.data?.map { it.withLocalMedia(local) }
                        ?: current.mine,
                    loading = false,
                    // Lenta yuklanmasa ham ekran ishlayveradi — story qo'shimcha qatlam,
                    // shuning uchun xato faqat foydalanuvchi o'zi amal qilganda ko'rsatiladi.
                    message = current.message,
                )
            }
        }
    }

    /** Guruhni ochadi — **birinchi ko'rilmagan** lavhadan (`StoryGroup.startIndex`). */
    fun open(group: StoryGroup) {
        _viewer.value = StoryViewerState(group = group, index = group.startIndex)
        markViewed()
    }

    /**
     * **O'z lavhalarimni** ochadi — Instagram/Telegramdagidek: lenta ularni ham ko'rsatadi.
     *
     * `GET /v1/stories/feed` faqat **boshqalarnikini** qaytaradi (o'zingiz o'zingizga
     * bog'langan emassiz), shuning uchun guruh shu yerda `GET /v1/stories/me`
     * ([StoriesState.mine]) dan yig'iladi. Muallif — o'zim: ism va avatar tashqaridan
     * (bosh ekran profilidan) keladi, chunki story moduli profil modulini bilmaydi.
     *
     * Lavhalar **eskidan yangiga** tartiblanadi — guruh ichidagi ko'rish tartibi shunday
     * (`handoff/07-STORIES.md` §2), `mine` esa yangidan eskiga keladi.
     */
    fun openMine(name: String, avatarUrl: String?) {
        val stories = _state.value.mine.sortedBy { it.createdAt }
        if (stories.isEmpty()) return
        open(
            StoryGroup(
                author = StudentSummary(
                    id = stories.first().authorId,
                    fullName = name,
                    avatarUrl = avatarUrl,
                ),
                stories = stories,
                // O'z lavham doim "ko'rilgan" — halqa boshqacha sababga ko'ra yonadi.
                hasUnseen = false,
                lastCreatedAt = stories.last().createdAt,
            ),
        )
    }

    fun close() {
        _viewer.value = StoryViewerState()
        // Ko'rilgan lavhalar halqasi o'chishi kerak — lentani yangilaymiz.
        refresh()
    }

    /**
     * Keyingi lavha; guruhning oxirida — **keyingi guruh** (Telegram/Instagram xulqi).
     * Oxirgi guruh tugasa viewer yopiladi.
     */
    fun next() {
        val current = _viewer.value
        val group = current.group ?: return
        if (current.index + 1 < group.stories.size) {
            _viewer.value = current.copy(index = current.index + 1)
            markViewed()
            return
        }
        val groups = _state.value.groups
        // ⚠️ Guruh lentada BO'LMASLIGI mumkin — o'z lavhalarim ([openMine]) feed'dan
        // kelmaydi. U holda oxirida boshqa odamning lavhasiga sakrab o'tmaslik kerak.
        val index = groups.indexOfFirst { it.author.id == group.author.id }
        val nextGroup = if (index < 0) null else groups.getOrNull(index + 1)
        if (nextGroup == null) close() else open(nextGroup)
    }

    /** Oldingi lavha; guruh boshida bo'lsa hech narsa qilinmaydi (ekran yopilmaydi). */
    fun previous() {
        val current = _viewer.value
        if (current.index <= 0) return
        _viewer.value = current.copy(index = current.index - 1)
    }

    /**
     * Lavha yaratadi. Fayl turi nomidan aniqlanadi; rasm uchun 12 MB, video uchun 48 MB
     * chegara serverda ham bor (§1).
     */
    fun publish(bytes: ByteArray, fileName: String, caption: String?) =
        publish { onProgress ->
            repository.create(bytes, fileName, caption, onProgress).alsoKeepOnDevice { story ->
                saveBytesToStudentClubFolder(
                    bytes = bytes,
                    fileName = storyMediaFileName(story.id, isVideo = false),
                    isVideo = false,
                )
            }
        }

    /**
     * Video lavha — media **diskdagi fayldan** yuklanadi.
     *
     * Rasmdan farqli o'laroq baytlar xotiraga o'qilmaydi: 48 MB lik `ByteArray` va uning
     * multipart nusxasi birga arzon telefonni xotiradan qoqib tashlardi.
     *
     * ⚠️ Fayl yuborilgandan keyin **o'chiriladi** — u ilova keshida turadi va uni boshqa
     * hech kim tozalamaydi.
     */
    fun publishVideo(video: PickedVideo, preparer: VideoPreparer, caption: String?) =
        publish { onProgress ->
            // Siqishga ajratilgan ulush: siqilmaydigan videoda `0f`, ya'ni halqa darrov
            // yuklashdan boshlanadi va yarmidan sakrab ketmaydi.
            val prepareShare = if (videoNeedsPreparing(video.sizeBytes)) PREPARE_SHARE else 0f
            val ready = preparer.prepare(video) { fraction -> onProgress(fraction * prepareShare) }
                ?: return@publish Resource.Error("Videoni yuborib bo'lmadi — u juda katta.")

            repository.createFromFile(
                path = ready.path,
                sizeBytes = ready.sizeBytes,
                fileName = ready.fileName,
                caption = caption,
                onProgress = { fraction -> onProgress(prepareShare + fraction * (1f - prepareShare)) },
            )
                // ⚠️ Nusxa keshdagi fayl O'CHIRILISHIDAN oldin olinadi.
                .alsoKeepOnDevice { story ->
                    saveToStudentClubFolder(
                        sourcePath = ready.path,
                        fileName = storyMediaFileName(story.id, isVideo = true),
                        isVideo = true,
                    )
                }
                .also { deleteMediaFile(ready.path) }
        }

    /**
     * Yuborilgan media telefonda **qoladi** — «StudentClub» papkasida.
     *
     * Nega: o'z lavhangizni ko'rish uchun uni serverdan qayta yuklab olish bekorga
     * sarflangan trafik. Saqlash yiqilsa hech narsa bo'lmaydi — o'shanda media eskicha,
     * tarmoqdan o'qiladi.
     */
    private suspend fun Resource<Story>.alsoKeepOnDevice(
        save: suspend (Story) -> String?,
    ): Resource<Story> {
        if (this is Resource.Success) {
            val savedUri = runCatching { save(data) }.getOrNull()
            if (savedUri != null) return Resource.Success(data.copy(localUri = savedUri))
        }
        return this
    }

    /** Ikkala yo'lning umumiy qismi — holat, foiz va yakuniy xabar. */
    private fun publish(create: suspend ((Float) -> Unit) -> Resource<Story>) {
        if (_state.value.publishing) return
        _state.update { it.copy(publishing = true, publishProgress = 0f, message = null) }
        viewModelScope.launch {
            val result = create { fraction ->
                _state.update { current ->
                    when {
                        // Fayl to'liq ketdi. Endi server lavhani yaratmoqda (video bo'lsa —
                        // transkod, `MEDIA_NOT_READY` bilan qayta urinishlar) va bu qancha
                        // davom etishi noma'lum — foiz o'rniga aylanma halqa.
                        fraction >= UPLOAD_DONE -> current.copy(publishProgress = null)
                        // Ktor har bufer bo'shaganda xabar beradi, 48 MB video esa minglab
                        // hodisa — 1% dan kichik o'zgarishda qayta chizmaymiz.
                        fraction - (current.publishProgress ?: 0f) < PROGRESS_STEP -> current
                        else -> current.copy(publishProgress = fraction)
                    }
                }
            }
            when (result) {
                is Resource.Success -> {
                    _state.update {
                        it.copy(publishing = false, publishProgress = null, message = "Lavha joylandi")
                    }
                    refresh()
                }
                is Resource.Error -> _state.update {
                    it.copy(publishing = false, publishProgress = null, message = result.message)
                }
                else -> _state.update { it.copy(publishing = false, publishProgress = null) }
            }
        }
    }

    fun delete(storyId: String) {
        viewModelScope.launch {
            when (val res = repository.delete(storyId)) {
                is Resource.Error -> _state.update { it.copy(message = res.message) }
                else -> {
                    _viewer.value = StoryViewerState()
                    refresh()
                }
            }
        }
    }

    /**
     * Video **siqilmoqda** — yuklash boshlanishidan oldingi bosqich.
     *
     * Katakcha shu vaqt ichida ham band ko'rinadi: 4K lavha bir necha o'n soniya siqiladi
     * va busiz foydalanuvchi "bosdim, hech nima bo'lmadi" deb ikkinchi marta tanlardi.
     */
    fun messageShown() = _state.update { it.copy(message = null) }

    /**
     * Ekranga xabar chiqaradi — masalan tanlangan video chegaradan uzun bo'lsa.
     *
     * Yuklash **boshlanmaydi**: sabab foydalanuvchiga tanlagan zahoti aytiladi, serverdan
     * xato kutib emas.
     */
    /**
     * Lavha muallifi **men**mi.
     *
     * Profilni ochishdan oldin tekshiriladi: o'z lavhangda muallif ustiga bosish
     * «bog'lanish» tugmalari bilan o'z profilingizni ochardi.
     */
    fun isMine(authorId: String): Boolean = _state.value.mine.any { it.authorId == authorId }

    fun showMessage(text: String) = _state.update { it.copy(message = text) }

    /**
     * `POST /v1/stories/{id}/view` — **idempotent** va fon amali: javobi kutilmaydi, xatosi
     * ko'rsatilmaydi. O'z lavhangizni ko'rish umuman hisoblanmaydi (server tomonda).
     */
    private fun markViewed() {
        val id = _viewer.value.story?.id ?: return
        viewModelScope.launch { repository.markViewed(id) }
    }

    private companion object {
        /** Foiz shundan kam o'zgarsa ekran qayta chizilmaydi. */
        const val PROGRESS_STEP = 0.01f

        /** `MediaUploader` fayl to'liq ketganda aynan shu qiymatni beradi. */
        const val UPLOAD_DONE = 0.99f

        /**
         * Halqaning siqishga ajratilgan qismi — chatdagi bilan bir xil.
         *
         * Yarmi ataylab: mobil qurilmada siqish odatda yuklashdan tez emas, ya'ni
         * ikkalasiga teng ulush berish halqani eng silliq to'ldiradi.
         */
        const val PREPARE_SHARE = 0.5f
    }
}
