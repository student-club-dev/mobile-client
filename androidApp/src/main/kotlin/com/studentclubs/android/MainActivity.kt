package com.studentclubs.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.shared.StudentApp

/**
 * Yagona Activity — to'g'ridan-to'g'ri talaba login oqimi + StudentShell'ni ochadi.
 * Rol tanlash yo'q (bu faqat talaba ilovasi; biznes tomoni alohida ElonUz ilovasida).
 * Logout'da Activity qayta ishga tushadi va login ekraniga qaytadi.
 *
 * FragmentActivity — biometrik BiometricPrompt shuni talab qiladi (F1).
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { StudentApp(onExit = ::recreate) }
    }
}
