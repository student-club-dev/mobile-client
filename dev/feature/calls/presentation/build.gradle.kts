// Qo'ng'iroq UI'si — kiruvchi/chiquvchi/jonli qo'ng'iroq ekrani va qo'ng'iroqlar tarixi.
//
// Video render qilish platformaga xos (`SurfaceViewRenderer`), shuning uchun `CallVideo`
// composable'i `expect/actual` bilan berilgan; Android tomoni `:dev:feature:calls:data`
// dagi `WebRtcVideoBus` dan trekni oladi.
plugins { id("sc.module-ui") }

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.dev.feature.calls.domain)
            implementation(projects.dev.feature.calls.data)
            // Koin moduli `CallsApi` ni va WS klientini quradi.
            implementation(projects.dev.core.network)
            // Qo'ng'iroq davomiyligi va jiringlash taymeri.
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.webrtc.android)
            // Mikrofon/kamera ruxsatini so'rash — `rememberLauncherForActivityResult`.
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            // `androidContext()` — media fabrikasi `Context` talab qiladi.
            implementation(libs.koin.android)
        }
    }
}
