import Foundation
import Shared
import GoogleSignIn
import UIKit

/// Kotlin `IosGoogleSignInDelegate` ning Swift implementatsiyasi — GoogleSignIn SDK orqali
/// **ID token** oladi va uni Kotlin tomonga qaytaradi. Token backendga
/// (`POST /v1/auth/student/oauth/google`) yuboriladi va sessiyani o'sha ochadi.
///
/// Firebase bu yerda ISHLATILMAYDI: sessiya to'liq backend tokenlariga tayanadi.
///
/// Sozlash:
///  1. `Info.plist` → `GIDClientID` = Google Cloud'dagi **iOS** turidagi OAuth client ID;
///  2. `Info.plist` → `CFBundleURLSchemes` = o'sha ID ning teskarisi
///     (`com.googleusercontent.apps.<ID>`);
///  3. backend `oauth/google` da iOS client ID ni ham qabul qilinadigan `audience` ro'yxatiga
///     qo'shishi kerak (Android/Web client ID lari bilan birga).
final class GoogleSignInBridge: NSObject, IosGoogleSignInDelegate {

    func signIn(onResult: @escaping (String?, String?) -> Void) {
        guard let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String,
              !clientID.isEmpty else {
            onResult(nil, "Google kirish sozlanmagan: Info.plist da GIDClientID yo'q")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let rootVC = Self.rootViewController() else {
            onResult(nil, "Root view controller topilmadi")
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { signInResult, error in
            if let error = error {
                // Bekor qilish xato emas — Kotlin tomonda `Cancelled` bo'lishi uchun ikkala
                // qiymat ham nil ketadi.
                let nsError = error as NSError
                if nsError.code == GIDSignInError.canceled.rawValue {
                    onResult(nil, nil)
                } else {
                    onResult(nil, error.localizedDescription)
                }
                return
            }
            guard let idToken = signInResult?.user.idToken?.tokenString else {
                onResult(nil, "Google ID token olinmadi")
                return
            }
            onResult(idToken, nil)
        }
    }

    private static func rootViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first { $0.activationState == .foregroundActive }
        guard let windowScene = scene as? UIWindowScene else { return nil }
        return windowScene.windows.first { $0.isKeyWindow }?.rootViewController
    }
}
