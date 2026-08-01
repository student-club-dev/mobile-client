package dev.core.uikit.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri

/**
 * Ilova kontekstini **kompozitsiyadan tashqarida** ishlatish uchun ushlab qoladi.
 *
 * Media saqlash ([saveToStudentClubFolder]) ViewModel'dan chaqiriladi — u yerda
 * `LocalContext` yo'q, kontekstni esa har bir chaqiruvga argument qilib sudrash butun
 * zanjirni (`ViewModel` → repository → domen) Android'ga bog'lab qo'yardi.
 *
 * `ContentProvider` — Android'ning o'zi ilova ishga tushganda, hatto `Application.onCreate`
 * dan **oldin** chaqiradigan yagona joyi. Kutubxonalar (Firebase, WorkManager, App Startup)
 * aynan shu usulni ishlatadi. Manifestga e'lon shu modulda turadi, ya'ni uikit'ni ulagan
 * ilova qo'shimcha qadam bajarmaydi.
 */
class ScMediaContextProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        scMediaContext = context?.applicationContext
        return true
    }

    // Bu provider ma'lumot bermaydi — u faqat ishga tushirish ilgagi.
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?): Int = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?): Int = 0
}

/** `null` — provider hali ishga tushmagan (amalda bo'lmaydi) yoki test muhiti. */
internal var scMediaContext: Context? = null
    private set
