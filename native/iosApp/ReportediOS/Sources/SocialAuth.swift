import AuthenticationServices
import Foundation
import GoogleSignIn
import UIKit

struct SocialAuthProfile {
    let provider: String
    let providerUserId: String
    let idToken: String
    let email: String
    let firstName: String
    let lastName: String
}

enum NativeSocialAuth {
    static func handle(url: URL) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }

    @MainActor
    static func signInWithGoogle() async throws -> SocialAuthProfile {
        guard let clientId = googleClientId(), !clientId.isEmpty else {
            throw SocialAuthError.missingConfiguration("Google sign-in needs CLIENT_ID in GoogleService-Info.plist.")
        }
        guard let presentingViewController = UIApplication.shared.reportedRootViewController else {
            throw SocialAuthError.missingPresenter
        }

        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientId)
        let result: GIDSignInResult = try await withCheckedThrowingContinuation { continuation in
            GIDSignIn.sharedInstance.signIn(withPresenting: presentingViewController) { result, error in
                if let error {
                    continuation.resume(throwing: NativeSocialAuth.isCancellation(error) ? SocialAuthError.cancelled : error)
                } else if let result {
                    continuation.resume(returning: result)
                } else {
                    continuation.resume(throwing: SocialAuthError.cancelled)
                }
            }
        }

        let user = result.user
        guard let idToken = user.idToken?.tokenString, !idToken.isEmpty else {
            throw SocialAuthError.missingToken("Google sign-in did not return an ID token.")
        }
        let profile = user.profile
        return SocialAuthProfile(
            provider: "google",
            providerUserId: user.userID ?? profile?.email ?? "",
            idToken: idToken,
            email: profile?.email ?? "",
            firstName: profile?.givenName ?? "",
            lastName: profile?.familyName ?? ""
        )
    }

    @MainActor
    static func signInWithApple() async throws -> SocialAuthProfile {
        let coordinator = AppleSignInCoordinator()
        return try await coordinator.signIn()
    }

    static func isCancellation(_ error: Error) -> Bool {
        if case SocialAuthError.cancelled = error {
            return true
        }
        let nsError = error as NSError
        if nsError.domain == kGIDSignInErrorDomain,
           nsError.code == GIDSignInError.canceled.rawValue {
            return true
        }
        if nsError.domain == ASAuthorizationError.errorDomain,
           nsError.code == ASAuthorizationError.Code.canceled.rawValue {
            return true
        }
        return nsError.code == NSUserCancelledError
    }

    private static func googleClientId() -> String? {
        Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String
            ?? Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist")
                .flatMap { NSDictionary(contentsOfFile: $0) }
                .flatMap { $0["CLIENT_ID"] as? String }
    }
}

private final class AppleSignInCoordinator: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var continuation: CheckedContinuation<SocialAuthProfile, Error>?

    @MainActor
    func signIn() async throws -> SocialAuthProfile {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = self
            controller.presentationContextProvider = self
            controller.performRequests()
        }
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential else {
            continuation?.resume(throwing: SocialAuthError.missingToken("Apple sign-in returned an unsupported credential."))
            continuation = nil
            return
        }
        guard
            let tokenData = credential.identityToken,
            let idToken = String(data: tokenData, encoding: .utf8),
            !idToken.isEmpty
        else {
            continuation?.resume(throwing: SocialAuthError.missingToken("Apple sign-in did not return an ID token."))
            continuation = nil
            return
        }

        continuation?.resume(returning: SocialAuthProfile(
            provider: "apple",
            providerUserId: credential.user,
            idToken: idToken,
            email: credential.email ?? "",
            firstName: credential.fullName?.givenName ?? "",
            lastName: credential.fullName?.familyName ?? ""
        ))
        continuation = nil
    }

    func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        continuation?.resume(throwing: NativeSocialAuth.isCancellation(error) ? SocialAuthError.cancelled : error)
        continuation = nil
    }

    func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        UIApplication.shared.reportedRootViewController?.view.window ?? ASPresentationAnchor()
    }
}

enum SocialAuthError: LocalizedError {
    case cancelled
    case missingConfiguration(String)
    case missingPresenter
    case missingToken(String)

    var errorDescription: String? {
        switch self {
        case .cancelled:
            return "Sign-in was cancelled."
        case .missingConfiguration(let message), .missingToken(let message):
            return message
        case .missingPresenter:
            return "Sign-in needs an active screen."
        }
    }
}

private extension UIApplication {
    var reportedRootViewController: UIViewController? {
        connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }?
            .rootViewController?
            .topMostPresentedViewController
    }
}

private extension UIViewController {
    var topMostPresentedViewController: UIViewController {
        presentedViewController?.topMostPresentedViewController ?? self
    }
}
