# R8 qoidalari — release build (`isMinifyEnabled = true`).
#
# Umumiy tamoyil: ko'pchilik kutubxonalar (Compose, Coil, Media3, Firebase, OkHttp,
# SQLDelight) o'z `consumer-rules.pro` fayllarini AAR ichida olib keladi — ular uchun
# bu yerda hech narsa yozish shart emas. Quyida faqat **reflection / JNI / ServiceLoader**
# orqali ishlaydigan, ya'ni R8 statik tahlil bilan ko'ra olmaydigan joylar sanab o'tilgan.

# --- Kotlin / Coroutines ---------------------------------------------------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
# `kotlin.Metadata` — reflection ishlatadigan kutubxonalar (Koin, serialization) uchun.
-keep class kotlin.Metadata { *; }

# --- kotlinx.serialization ------------------------------------------------
# R8 3+ da qisman avtomatik, lekin generatsiya qilingan `$serializer` va `Companion`
# larni aniq saqlash xavfsizroq (api-client'dagi barcha DTO'lar shunga tayanadi).
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisible*Annotations
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    static **$* *;
    <fields>;
}
-keepclasseswithmembers class ** {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class **$$serializer { *; }
-dontnote kotlinx.serialization.**

# --- Enum'lar --------------------------------------------------------------
# Kodda `enumValueOf<T>(value)` / `Enum.valueOf(...)` ishlatiladi (FeatureMappers,
# SettingsRepositoryImpl, ApiAuthRepository, NotificationRepositoryImpl) — konstanta
# NOMLARI o'zgarsa, ular jimgina `default` qiymatga tushib qolardi.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
}

# --- Ktor ------------------------------------------------------------------
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.network.sockets.**
-dontwarn org.slf4j.**
# Ktor JVM tomonida bir nechta ixtiyoriy bog'liqlikka murojaat qiladi.
-dontwarn java.lang.management.**
-dontwarn javax.naming.**

# --- WebRTC (JNI) ----------------------------------------------------------
# `libjingle_peerconnection_so.so` Java klasslarni NOM bo'yicha topadi va callback'larni
# JNI orqali chaqiradi. Bu paket obfuskatsiya qilinsa — qo'ng'iroq ishga tushishida
# `UnsatisfiedLinkError` / NPE beradi.
-keep class org.webrtc.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-dontwarn org.webrtc.**

# --- Koin ------------------------------------------------------------------
-keep class org.koin.core.** { *; }

# --- Firebase Messaging ----------------------------------------------------
# Servis manifest'da e'lon qilingan (AGP uni avtomatik saqlaydi), lekin FCM ba'zi
# ichki klasslarni reflection bilan qidiradi.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Chucker (release'da no-op) --------------------------------------------
-dontwarn com.chuckerteam.chucker.**

# --- Xatolik hisobotlari uchun satr raqamlari ------------------------------
# Obfuskatsiyadan keyingi stacktrace'ni o'qib bo'ladigan qilish uchun. Mapping fayli:
# `androidApp/build/outputs/mapping/release/mapping.txt` — har bir relizda SAQLANSIN.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
