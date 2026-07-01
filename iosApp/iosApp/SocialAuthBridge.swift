import Foundation
import Shared
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import UIKit

/// Kotlin `IosSocialAuthDelegate` ni Firebase + GoogleSignIn orqali amalga oshiradi.
/// `iOSApp.init()` da `IosSocialAuthBridge.shared.delegate = SocialAuthBridge()` deb ulanadi.
final class SocialAuthBridge: NSObject, IosSocialAuthDelegate {

    private var phoneVerificationId: String?

    // MARK: - Google

    func signInWithGoogle(onResult: @escaping (IosAuthUser?, String?) -> Void) {
        guard let clientID = FirebaseApp.app()?.options.clientID else {
            onResult(nil, "Firebase clientID topilmadi")
            return
        }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)

        guard let rootVC = Self.rootViewController() else {
            onResult(nil, "Root view controller topilmadi")
            return
        }

        GIDSignIn.sharedInstance.signIn(withPresenting: rootVC) { signInResult, error in
            if let error = error {
                // Foydalanuvchi bekor qilsa ham error keladi -> user=nil, error=nil (Cancelled)
                let nsError = error as NSError
                if nsError.code == GIDSignInError.canceled.rawValue {
                    onResult(nil, nil)
                } else {
                    onResult(nil, error.localizedDescription)
                }
                return
            }
            guard
                let user = signInResult?.user,
                let idToken = user.idToken?.tokenString
            else {
                onResult(nil, "Google token olinmadi")
                return
            }
            let credential = GoogleAuthProvider.credential(
                withIDToken: idToken,
                accessToken: user.accessToken.tokenString
            )
            Auth.auth().signIn(with: credential) { authResult, err in
                if let err = err {
                    onResult(nil, err.localizedDescription)
                    return
                }
                onResult(Self.map(authResult?.user, provider: "google"), nil)
            }
        }
    }

    // MARK: - Phone (OTP)

    func sendOtp(phoneNumber: String, onResult: @escaping (Bool, IosAuthUser?, String?) -> Void) {
        PhoneAuthProvider.provider().verifyPhoneNumber(phoneNumber, uiDelegate: nil) { [weak self] verificationID, error in
            if let error = error {
                onResult(false, nil, error.localizedDescription)
                return
            }
            self?.phoneVerificationId = verificationID
            onResult(true, nil, nil) // SMS yuborildi
        }
    }

    func confirmOtp(code: String, onResult: @escaping (IosAuthUser?, String?) -> Void) {
        guard let verificationID = phoneVerificationId else {
            onResult(nil, "Avval kod yuboring")
            return
        }
        let credential = PhoneAuthProvider.provider().credential(
            withVerificationID: verificationID,
            verificationCode: code
        )
        Auth.auth().signIn(with: credential) { authResult, error in
            if let error = error {
                onResult(nil, error.localizedDescription)
                return
            }
            self.phoneVerificationId = nil
            onResult(Self.map(authResult?.user, provider: "phone"), nil)
        }
    }

    // MARK: - Helpers

    private static func map(_ user: User?, provider: String) -> IosAuthUser? {
        guard let user = user else { return nil }
        return IosAuthUser(
            uid: user.uid,
            provider: provider,
            fullName: user.displayName,
            email: user.email,
            phoneNumber: user.phoneNumber,
            photoUrl: user.photoURL?.absoluteString
        )
    }

    private static func rootViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene
        return scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController
    }
}
