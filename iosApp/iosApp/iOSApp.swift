import SwiftUI
import Shared
import FirebaseCore
import GoogleSignIn

@main
struct iOSApp: App {
    init() {
        // Firebase — faqat chat (Firestore) uchun; autentifikatsiya backendда.
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
