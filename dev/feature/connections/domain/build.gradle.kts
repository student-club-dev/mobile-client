// Bog'lanishlar (Connections) domen qatlami — chat'ning "eshigi".
// Spetsifikatsiya: handoff/connections.md + openapi `student-club.json` (tag `Connections`).
plugins { id("sc.kmp-library") }
kotlin {
    sourceSets {
        commonMain.dependencies {
            // `Instant` domen modellarining ochiq turida (createdAt, lastSeenAt) — `api`.
            api(libs.kotlinx.datetime)
        }
    }
}
