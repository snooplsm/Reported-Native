import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

final class SessionViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = SessionState()

    func onAction(_ action: SessionAction) {
        switch action {
        case .load:
            load()
        case .authenticated:
            didAuthenticate()
        case .continueAsGuest:
            continueAsGuest()
        case .logout:
            logout()
        }
    }

    func load() {
        Task {
            do {
                let session = try await SharedBridge.shared.container.loadSessionUseCase.execute()
                let isGuest = try await SharedBridge.shared.container.loadGuestModeUseCase.execute()
                ReportedAnalytics.setUser(session)
                state = SessionState(loading: false, session: session, isGuest: session == nil ? isGuest.boolValue : false)
            } catch {
                let isGuest = (try? await SharedBridge.shared.container.loadGuestModeUseCase.execute())?.boolValue ?? false
                ReportedAnalytics.setUser(nil)
                state = SessionState(loading: false, session: nil, isGuest: isGuest)
            }
        }
    }

    func didAuthenticate() {
        Task {
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: false)
            load()
        }
    }

    func continueAsGuest() {
        Task {
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: true)
            ReportedAnalytics.setUser(nil)
            state = SessionState(loading: false, session: nil, isGuest: true)
        }
    }

    func logout() {
        Task {
            try? await SharedBridge.shared.container.logoutUseCase.execute()
            try? await SharedBridge.shared.container.saveGuestModeUseCase.execute(enabled: false)
            ReportedAnalytics.logLogout()
            state = SessionState(loading: false, session: nil, isGuest: false)
        }
    }
}

@MainActor
final class LoginViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = LoginState()

    func onAction(_ action: LoginAction) {
        switch action {
        case .emailChanged(let value):
            update(email: value)
        case .passwordChanged(let value):
            update(password: value)
        case .loginPressed(let onSuccess):
            login(onSuccess: onSuccess)
        case .googleSignInPressed(let onSuccess):
            signInWithGoogle(onSuccess: onSuccess)
        case .appleSignInPressed(let onSuccess):
            signInWithApple(onSuccess: onSuccess)
        case .forgotPasswordPressed:
            forgotPassword()
        case .passwordResetMessageDismissed:
            dismissPasswordResetMessage()
        }
    }

    func update(email: String? = nil, password: String? = nil) {
        if let email { state.email = email }
        if let password { state.password = password }
    }

    func login(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let session = try await SharedBridge.shared.container.loginUseCase.execute(
                    email: state.email,
                    password: state.password
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: "password", session: session)
                onSuccess()
            } catch {
                print("ReportedAuth: login failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
            }
        }
    }

    func signInWithGoogle(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithGoogle()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Google sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func signInWithApple(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithApple()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Apple sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func completeSocialSignIn(_ profile: SocialAuthProfile, onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let session = try await SharedBridge.shared.container.socialLoginUseCase.execute(
                    provider: profile.provider,
                    providerUserId: profile.providerUserId,
                    idToken: profile.idToken,
                    email: profile.email,
                    firstName: profile.firstName,
                    lastName: profile.lastName,
                    phone: "",
                    testify: false
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: profile.provider, session: session)
                onSuccess()
            } catch {
                print("ReportedAuth: social login failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
            }
        }
    }

    func forgotPassword() {
        guard !state.email.isEmpty else {
            state.error = "Enter your email first."
            return
        }
        Task {
            do {
                try await SharedBridge.shared.container.forgotPasswordUseCase.execute(email: state.email)
                state.error = nil
                state.passwordResetMessage = "We sent password reset instructions to \(state.email)."
            } catch {
                state.error = "Couldn't send reset email."
            }
        }
    }

    func dismissPasswordResetMessage() {
        state.passwordResetMessage = nil
    }
}

@MainActor
final class RegisterViewModel: ObservableObject, UdfStore {
    @Published private(set) var state = RegisterState()

    func onAction(_ action: RegisterAction) {
        switch action {
        case .fieldsChanged(let firstName, let lastName, let phone, let email, let password, let testify):
            update(
                firstName: firstName,
                lastName: lastName,
                phone: phone,
                email: email,
                password: password,
                testify: testify
            )
        case .registerPressed(let onSuccess):
            register(onSuccess: onSuccess)
        case .googleSignInPressed(let onSuccess):
            signInWithGoogle(onSuccess: onSuccess)
        case .appleSignInPressed(let onSuccess):
            signInWithApple(onSuccess: onSuccess)
        }
    }

    func update(
        firstName: String? = nil,
        lastName: String? = nil,
        phone: String? = nil,
        email: String? = nil,
        password: String? = nil,
        testify: Bool? = nil
    ) {
        if let firstName { state.firstName = firstName }
        if let lastName { state.lastName = lastName }
        if let phone { state.phone = phone }
        if let email { state.email = email }
        if let password { state.password = password }
        if let testify { state.testify = testify }
    }

    func register(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let session = try await SharedBridge.shared.container.registerUseCase.execute(
                    firstName: state.firstName,
                    lastName: state.lastName,
                    phone: state.phone,
                    testify: state.testify,
                    email: state.email,
                    password: state.password
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: "password", session: session)
                onSuccess()
            } catch {
                print("ReportedAuth: registration failed \(error)")
                state.loading = false
                state.error = "There was an error creating your account. Please try again."
            }
        }
    }

    func signInWithGoogle(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithGoogle()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Google registration sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func signInWithApple(onSuccess: @escaping () -> Void) {
        state.loading = true
        state.error = nil
        Task {
            do {
                let profile = try await NativeSocialAuth.signInWithApple()
                completeSocialSignIn(profile, onSuccess: onSuccess)
            } catch {
                state.loading = false
                if !NativeSocialAuth.isCancellation(error) {
                    print("ReportedAuth: Apple registration sign-in failed \(error)")
                    state.error = "There was an error signing in. Please try again."
                }
            }
        }
    }

    func completeSocialSignIn(_ profile: SocialAuthProfile, onSuccess: @escaping () -> Void) {
        let fallbackFirstName = state.firstName
        let fallbackLastName = state.lastName
        state.loading = true
        state.error = nil
        Task {
            do {
                let session = try await SharedBridge.shared.container.socialLoginUseCase.execute(
                    provider: profile.provider,
                    providerUserId: profile.providerUserId,
                    idToken: profile.idToken,
                    email: profile.email,
                    firstName: profile.firstName.isEmpty ? fallbackFirstName : profile.firstName,
                    lastName: profile.lastName.isEmpty ? fallbackLastName : profile.lastName,
                    phone: state.phone,
                    testify: state.testify
                )
                state.loading = false
                ReportedAnalytics.logLogin(method: profile.provider, session: session)
                onSuccess()
            } catch {
                print("ReportedAuth: social registration failed \(error)")
                state.loading = false
                state.error = "There was an error signing in. Please try again."
            }
        }
    }
}
