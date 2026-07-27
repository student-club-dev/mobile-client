# Autentifikatsiya — backend (`/v1/auth/student/*`) + Google

Ilova sessiyasi **backend tokenlariga** tayanadi (Firebase Auth EMAS). Shartnoma —
`dev/api-client-generator/student-club.json` (ElonUz — Student API), server:
`https://api.studentclub.uz/v1/`.

## Oqim

| Qadam | Endpoint | Izoh |
|---|---|---|
| Kirish | `POST /auth/student/login` | telefon **yoki** email + parol |
| Google | `POST /auth/student/oauth/google` | klient Google ID tokenini yuboradi |
| Ro'yxat | `POST /auth/student/register` | telefon + parol (min 8 belgi) → sessiya darrov ochiladi |
| Raqamni tasdiqlash | `POST /auth/student/otp/request` → `otp/verify` | **sessiya talab qiladi** (Bearer) |
| Parolni tiklash | `POST /auth/student/password/forgot` → `password/reset` | SMS kod + yangi parol |
| Token yangilash | `POST /auth/student/refresh` | avtomatik, tarmoq qatlamida |
| Chiqish | `POST /auth/student/logout` | refresh token bekor qilinadi |

> **SMS kod — kirish usuli EMAS.** `otp/request` va `otp/verify` `Authorization: Bearer` talab
> qiladi va javobi faqat `{verified}` — token bermaydi. Shuning uchun kod ro'yxatdan
> o'tgandan **keyin** so'raladi va uni o'tkazib yuborish mumkin (hisob allaqachon ochilgan).

## Arxitektura

```
AuthNavHost (Compose, commonMain)
  → AuthFlowViewModel
      • telefon/email + parol → LoginUseCase / RegisterUseCase
      • Google              → GoogleSignIn (expect/actual) → ID token → LoginWithGoogleUseCase
      • SMS kod             → RequestPhoneOtpUseCase / VerifyPhoneOtpUseCase
      • parolni tiklash     → ForgotPasswordUseCase / ResetPasswordUseCase
          → AuthRepository (ApiAuthRepository)
              → AuthStudentApi (OpenAPI'dan generatsiya)
              → TokenStore (SecureTokenStore: Android EncryptedSharedPreferences / iOS Keychain)
              → ProfileRepository.refresh()  → GET /profile/me
              → local sessiya keshi (SQLDelight `UserEntity`, `uid` = JWT `sub`)
```

- **Token yangilash** — `createHttpClient` ichida: 401 kelganda `auth/student/refresh` bilan
  yangi juftlik olinadi va so'rov takrorlanadi; refresh ham rad etilsa sessiya tozalanadi.
- **Xatolar** — `BaseResponse` konverti `EnvelopeUnwrapPlugin` bilan shaffof ochiladi, 422 dagi
  `error.fields` esa `AppException.Validation.fields` ga tushadi (forma maydon ostida ko'rsatadi).

## Google Sign-In sozlash

### Android

1. Google Cloud Console → proyekt **`studentclub-503706`** → **Credentials**:
   - **Web application** turidagi OAuth client ID — ilova ID token uchun aynan shuni ishlatadi;
   - **Android** turidagi client ID — package `uz.studentclub.app` + SHA-1
     (`./gradlew :androidApp:signingReport`, debug va release uchun alohida).

     Debug imzo loyihaning o'z kalitidan keladi — `androidApp/debug.keystore` (repoda),
     SHA-1 `DC:18:55:73:19:58:73:89:F7:89:D1:79:3E:E6:16:4A:B9:39:63:30`. Shuning uchun
     har bir ishlab chiquvchi uchun alohida client kerak emas.

   Ikkala client bir proyektda bo'lishi shart: Credential Manager chaqiruvchi ilovani
   package + SHA-1 bo'yicha Web client turgan proyektda qidiradi, topmasa so'rovni
   Google'ga umuman yubormaydi.

   OAuth uchun proyekt — `studentclub-503706`; Firebase proyekti (`studentclubs-d2905`)
   bundan alohida va faqat chat uchun. Auth backendда bo'lgani uchun ular bog'lanmaydi.
2. Web client ID ni `local.properties` ga yozing (fayl `.gitignore` da):
   ```
   GOOGLE_WEB_CLIENT_ID=...apps.googleusercontent.com
   ```
   Namuna — `local.properties.example`. CI'da xuddi shu nomdagi muhit o'zgaruvchisi o'qiladi.
   `androidApp/build.gradle.kts` uni `resValue` orqali `google_web_client_id` resursiga
   aylantiradi, `GoogleSignIn.android.kt` esa shu resursni o'qiydi.
3. Backend `oauth/google` da tokenni **aynan shu** client ID bo'yicha tekshirishi shart.

Bo'sh qoldirilsa tugma aniq xato beradi (jimgina ishlamay qolmaydi).

### iOS

1. `iosApp/iosApp/Info.plist`:
   - `GIDClientID` — Google Cloud'dagi **iOS** turidagi OAuth client ID;
   - `CFBundleURLSchemes` — o'sha ID ning teskarisi (`com.googleusercontent.apps.<ID>`).
2. Xcode'da **GoogleSignIn** SDK (Swift Package Manager:
   `https://github.com/google/GoogleSignIn-iOS`).
3. `iosApp/iosApp/GoogleSignInBridge.swift` Kotlin `IosGoogleSignInDelegate` ni amalga oshiradi
   va ID tokenni qaytaradi; `iOSApp.swift` uni `IosGoogleSignInBridge.shared.delegate` ga ulaydi.
4. Backend iOS client ID ni ham qabul qilinadigan `audience` ro'yxatiga qo'shishi kerak.

## Firebase

Firebase endi **faqat chat** (Firestore) uchun qoladi — `FirestoreChatRealtimeSource`,
bayroq `AuthModule.CHAT_REALTIME_ENABLED`. Autentifikatsiyada Firebase ishlatilmaydi,
`google-services.json` faqat chat uchun kerak.

## Baza migratsiyasi

`15.sqm` (sxema v16): `UserEntity.userId` olib tashlandi, `uid` endi JWT `sub`. Eski
(Firebase) sessiya qatorlari o'chiriladi — yangi backendda ular yaroqsiz, foydalanuvchi
bir marta qaytadan kiradi. Profil keshi saqlanib qoladi.
