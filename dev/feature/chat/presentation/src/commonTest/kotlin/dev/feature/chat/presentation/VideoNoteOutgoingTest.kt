package dev.feature.chat.presentation

import dev.core.uikit.media.PickedVideo
import dev.core.uikit.media.VideoPreparer
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Dumaloq video xabar yuborishga qanday tayyorlanadi.
 *
 * Uchala tekshiruv ham serverning talabi: izohsiz, `VIDEO_NOTE` turida va **har doim**
 * tayyorlanadigan (kvadratga kesiladigan) bo'lishi kerak — aks holda `422`.
 */
class VideoNoteOutgoingTest {

    private val picked = PickedVideo(
        path = "/cache/capture_1.mp4",
        fileName = "capture.mp4",
        durationMs = 12_000,
        sizeBytes = 3_000_000,
    )

    private val preparer = VideoPreparer { video, _ -> video }

    @Test
    fun `dumaloq xabar izohsiz va VIDEO_NOTE turida ketadi`() {
        val outgoing = picked.toOutgoingVideoNote(preparer)
        assertTrue(outgoing.videoNote)
        assertNull(outgoing.caption)
    }

    /**
     * ⚠️ `needsPreparing` **doim `true`**: kesish fayl kichik bo'lganda ham kerak.
     * Oddiy videoda bu bayroq hajmga bog'liq (`videoNeedsPreparing`), bu yerda esa
     * shunga tayansak kvadrat bo'lmagan fayl serverga ketib `MEDIA_NOT_SQUARE` olardi.
     */
    @Test
    fun `kichik fayl ham tayyorlanadi`() {
        val small = PickedVideo(
            path = "/cache/tiny.mp4",
            fileName = "tiny.mp4",
            durationMs = 2_000,
            sizeBytes = 120_000,
        )
        assertTrue(small.toOutgoingVideoNote(preparer).needsPreparing)
    }
}
