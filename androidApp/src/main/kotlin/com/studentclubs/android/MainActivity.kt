package com.studentclubs.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dev.shared.RoleLauncherApp

/**
 * Ildiz (launcher) Activity — foydalanuvchi turini aniqlaydi va ALOHIDA Activity ochadi:
 * talaba -> [StudentActivity], biznesmen -> [BusinessActivity]. Sessiya bo'lsa rolga qarab
 * to'g'ridan-to'g'ri ochadi, aks holda rol tanlash ekranini ko'rsatadi.
 *
 * FragmentActivity — biometrik BiometricPrompt shuni talab qiladi (F1).
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoleLauncherApp(
                onStudent = { open(StudentActivity::class.java) },
                onBusiness = { open(BusinessActivity::class.java) },
            )
        }
    }

    private fun open(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }
}
