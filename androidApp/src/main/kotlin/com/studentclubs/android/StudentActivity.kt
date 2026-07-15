package com.studentclubs.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.shared.StudentApp

/**
 * Talaba Activity — talaba login oqimi + StudentShell (Home/Chegirma/Ishlar/Student).
 * Chiqishда ildiz router'ga (MainActivity) qaytadi.
 */
class StudentActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudentApp(onExit = ::backToLauncher) }
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
