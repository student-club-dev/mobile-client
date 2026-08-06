package uz.studentclub.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import dev.core.common.push.PushRoute
import dev.shared.App
import uz.studentclub.app.push.PushNotifications

/**
 * Yagona Activity — to'g'ridan-to'g'ri login oqimi + StudentShell'ni ochadi.
 * Rol tanlash yo'q (bu faqat talaba ilovasi; biznes tomoni alohida ElonUz ilovasida).
 *
 * Chiqish Activity'ni QAYTA ISHGA TUSHIRMAYDI — navigatsiya grafining o'zida kirish
 * ekraniga qaytiladi (qarang: `AuthNavHost` HOME marshruti).
 */
class MainActivity : FragmentActivity() {

    /**
     * Bildirishnoma ruxsati (Android 13+). Launcher **shart-sharoitsiz**, konstruksiya
     * paytida ro'yxatdan o'tishi kerak — `onCreate` ichida shartli ro'yxatdan o'tkazish
     * holat tiklanganda `IllegalStateException` beradi. Natija kerak emas: rad etilsa
     * ilova o'zgarishsiz ishlayveradi.
     */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tizim splash'i (Android 12+ SplashScreen API) — statik StudentClub logotipi.
        // `super.onCreate()`dan OLDIN chaqirilishi shart. Ushlab turmaymiz: ilk Compose
        // kadri chizilishi bilan yopiladi va `AnimatedSplashScreen` davom ettiradi.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Butun ilova edge-to-edge: tizim panellari shaffof, kontent ular ostidan ham
        // chiziladi. Splash panelni YASHIRMAYDI — yashirilsa, tizim uni sirg'antirib
        // olib qo'yadi va o'sha animatsiya splash ustida ko'rinib qolardi (bu animatsiyani
        // ilova tomonidan o'chirib bo'lmaydi). Buning o'rniga splash panel ostidan ham
        // chiziladi va faqat tugma belgilari ko'k fon ustida qoladi.
        // `navigationBarStyle`ga shaffof scrim beramiz: standart sozlamada tizim panel
        // ortiga qoraytiruvchi parda qo'yadi va splash gradienti pastda to'satdan to'qlashib
        // ketardi. Belgilar rangini ekranning o'zi (`NavigationBarAppearance`) hal qiladi.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        // Android 10+ da tizim 3 tugmali panel ortiga o'zi kontrast pardasini qo'yadi
        // (Android 15+ da `navigationBarColor` umuman e'tiborsiz qoldiriladi, faqat shu
        // bayroq qoladi). Busiz splash gradienti pastda to'satdan to'qlashib ketardi.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        // Push bosilib ochilgan bo'lsa — qaysi suhbat kerakligini eslab qo'yamiz.
        routeFromPush(intent)
        requestNotificationPermission()
        setContent { App() }
    }

    /**
     * Ilova allaqachon ochiq bo'lganda push bosilsa shu chaqiriladi (`launchMode=singleTop`).
     * `intent` ni ham yangilaymiz, aks holda keyingi `getIntent()` eskisini qaytarardi.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromPush(intent)
    }

    /**
     * Push konverti — fonda kelgan bildirishnoma bosilganda tizim `data` ni intent
     * "extra"lari qilib beradi; old planda esa ularni [PushNotifications] o'zi qo'yadi.
     * Kerakli ekranni `StudentShell` ochadi va o'sha qatorni o'qilgan deb belgilaydi
     * (UI tayyor bo'lgach `PushRoute` ni o'qiydi).
     */
    private fun routeFromPush(intent: Intent?) {
        if (intent == null) return
        PushRoute.set(
            PushRoute.Payload(
                notificationId = intent.getStringExtra(PushNotifications.EXTRA_NOTIFICATION_ID),
                targetType = intent.getStringExtra(PushNotifications.EXTRA_TARGET_TYPE),
                targetId = intent.getStringExtra(PushNotifications.EXTRA_TARGET_ID),
                conversationId = intent.getStringExtra(PushNotifications.EXTRA_CONVERSATION_ID),
            ),
        )
    }

    /**
     * Android 13+ da bildirishnoma ko'rsatish uchun ish vaqti ruxsati kerak.
     * Rad etilsa ilova to'liq ishlayveradi — faqat push ko'rinmaydi, shuning uchun
     * natijani kuzatmaymiz va tushuntiruvchi dialog ham chizmaymiz.
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
