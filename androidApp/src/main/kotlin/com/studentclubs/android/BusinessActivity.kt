package com.studentclubs.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.shared.BusinessApp

/**
 * Biznesmen Activity — biznes login oqimi + BusinessShell (E'lonlarim/Yangi e'lon/Skaner/Profil).
 * Talaba ekranlari bu yerda umuman yo'q. Chiqishda ildiz router'ga (MainActivity) qaytadi.
 */
class BusinessActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BusinessApp(onExit = ::backToLauncher) }
    }

    private fun backToLauncher() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }
}
