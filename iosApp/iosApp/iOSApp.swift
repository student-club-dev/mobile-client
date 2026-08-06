import SwiftUI
import Shared
import FirebaseCore
import GoogleSignIn
import UserNotifications

@main
struct iOSApp: App {
    // Push uchun AppDelegate SHART: APNs tokeni va bosilgan bildirishnoma faqat shu yerga
    // keladi — SwiftUI'ning o'zida ularni ushlab bo'lmaydi.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // Firebase — konfiguratsiya (auth backendда, chat esa o'z WebSocket'ida).
        FirebaseApp.configure()

        // Koin DI ni iOS tomonida ishga tushirish
        KoinIosKt.doInitKoin()

        // Google Sign-In ko'prigi: Kotlin `GoogleSignIn` shu delegate orqali ID token oladi.
        IosGoogleSignInBridge.shared.delegate = GoogleSignInBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    // Google Sign-In qaytish URL'ini qayta ishlash
                    GIDSignIn.sharedInstance.handle(url)
                }
        }
    }
}

/**
 Oflayn push (`03-WEBSOCKET.md` §10).

 Oqim: ruxsat so'raymiz → APNs'ga ro'yxatdan o'tamiz → token `IosPushBridge` orqali Kotlin'ga
 o'tadi va `POST /v1/devices` ga yoziladi. Bildirishnoma bosilganda `data.conversationId`
 yana shu ko'prik orqali `PushRoute` ga tushadi, `StudentShell` esa o'sha suhbatni ochadi.

 ⚠️ Xcode'da **Push Notifications** capability yoqilgan bo'lishi shart, aks holda
 `registerForRemoteNotifications()` xato bilan qaytadi va token hech qachon kelmaydi.
 Simulyatorda ham token kelmaydi — sinash uchun haqiqiy qurilma kerak.
 */
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            // Rad etilsa ilova to'liq ishlayveradi — faqat bildirishnoma ko'rinmaydi.
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
        return true
    }

    /// APNs tokeni — backend uni o'n oltilik matn ko'rinishida kutadi.
    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        IosPushBridge.shared.setToken(token: token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        // Simulyatorda va capability yoqilmaganda odatiy holat — push shunchaki ishlamaydi.
        print("Push ro'yxatdan o'tmadi: \(error.localizedDescription)")
    }

    /// Ilova OLD PLANDA bo'lganda ham bildirishnoma ko'rinsin (standart holatda ko'rinmaydi).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .badge])
    }

    /// Bildirishnoma bosildi — sovuq ishga tushishda ham shu chaqiriladi.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        // Konvert — `02-PUSH_CATALOG_BACKEND.md` §2. Hamma qiymat `string`, qiymati yo'q
        // kalit umuman kelmaydi. `conversationId` chat va qo'ng'iroqda saqlanib qolgan.
        let userInfo = response.notification.request.content.userInfo
        IosPushBridge.shared.openTarget(
            notificationId: userInfo["notificationId"] as? String,
            targetType: userInfo["targetType"] as? String,
            targetId: userInfo["targetId"] as? String,
            conversationId: userInfo["conversationId"] as? String
        )
        completionHandler()
    }
}
