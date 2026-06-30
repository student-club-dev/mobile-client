import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Koin DI ni iOS tomonida ishga tushirish
        KoinIosKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}
