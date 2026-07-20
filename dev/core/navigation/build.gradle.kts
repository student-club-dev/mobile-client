// Navigatsiya yordamchilari — barcha shell'lar (auth / student / business) shu primitivlarni
// ishlatadi. Maqsad: ikki marta bosishdan kelib chiqadigan dublikat ekranlar, noto'g'ri
// kodlangan argumentlardan kelib chiqadigan "destination not found" crash'i va bo'sh
// back-stack'da pop qilishdan kelib chiqadigan qotishlarni butunlay yo'q qilish.
plugins { id("sc.module-ui") }
kotlin { sourceSets { commonMain.dependencies {
    api(libs.androidx.navigation.compose)
} } }
