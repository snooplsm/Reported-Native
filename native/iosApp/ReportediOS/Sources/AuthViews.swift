import AVFoundation
import CoreGraphics
import Darwin
import FirebaseAnalytics
import ImageIO
import LiteRTLM
import Lottie
import MapKit
import PhotosUI
import SharedCore
import SwiftUI
import UniformTypeIdentifiers
import UIKit
import WebKit

struct LoginScreen: View {
    @StateObject private var viewModel = LoginViewModel()
    let onSuccess: () -> Void
    let onRegister: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            AuthToolbar(title: "Login", onBack: onBack, onDismiss: onDismiss)
            ScrollView {
                ScreenCard {
                if let error = viewModel.state.error { MessageView(text: error) }
                ProviderSignInButton(
                    title: "Sign in with Google",
                    systemImage: nil,
                    iconText: "G",
                    foregroundColor: Color(red: 60 / 255, green: 64 / 255, blue: 67 / 255),
                    backgroundColor: .white,
                    borderColor: Color(.separator),
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.onAction(.googleSignInPressed(onSuccess: onSuccess))
                }
                ProviderSignInButton(
                    title: "Sign in with Apple",
                    systemImage: "apple.logo",
                    iconText: nil,
                    foregroundColor: .white,
                    backgroundColor: .black,
                    borderColor: .black,
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.onAction(.appleSignInPressed(onSuccess: onSuccess))
                }
                AuthDivider()
                InputField(title: "Email", text: Binding(
                    get: { viewModel.state.email },
                    set: { viewModel.onAction(.emailChanged($0)) }
                ), keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
                PasswordInputField(title: "Password", text: Binding(
                    get: { viewModel.state.password },
                    set: { viewModel.onAction(.passwordChanged($0)) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Logging In..." : "Login", size: .compact) {
                    viewModel.onAction(.loginPressed(onSuccess: onSuccess))
                }
                Button("Forgot Password?") {
                    viewModel.onAction(.forgotPasswordPressed)
                }
                    .foregroundStyle(Color.reportedOrange)
                Button("Need an account? Register", action: onRegister)
                    .foregroundStyle(Color.reportedOrange)
                }
                .padding()
            }
        }
        .navigationBarBackButtonHidden(true)
        .alert(
            "Check your email",
            isPresented: Binding(
                get: { viewModel.state.passwordResetMessage != nil },
                set: { if !$0 { viewModel.onAction(.passwordResetMessageDismissed) } }
            )
        ) {
            Button("OK", role: .cancel) {
                viewModel.onAction(.passwordResetMessageDismissed)
            }
        } message: {
            Text(viewModel.state.passwordResetMessage ?? "")
        }
    }
}

struct RegisterScreen: View {
    @StateObject private var viewModel = RegisterViewModel()
    let onSuccess: () -> Void
    let onLogin: () -> Void
    var onBack: (() -> Void)? = nil
    var onDismiss: (() -> Void)? = nil

    var body: some View {
        VStack(spacing: 0) {
            AuthToolbar(title: "Register", onBack: onBack, onDismiss: onDismiss)
            ScrollView {
                ScreenCard {
                if let error = viewModel.state.error { MessageView(text: error) }
                ProviderSignInButton(
                    title: "Sign in with Google",
                    systemImage: nil,
                    iconText: "G",
                    foregroundColor: Color(red: 60 / 255, green: 64 / 255, blue: 67 / 255),
                    backgroundColor: .white,
                    borderColor: Color(.separator),
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.onAction(.googleSignInPressed(onSuccess: onSuccess))
                }
                ProviderSignInButton(
                    title: "Sign in with Apple",
                    systemImage: "apple.logo",
                    iconText: nil,
                    foregroundColor: .white,
                    backgroundColor: .black,
                    borderColor: .black,
                    enabled: !viewModel.state.loading
                ) {
                    viewModel.onAction(.appleSignInPressed(onSuccess: onSuccess))
                }
                AuthDivider()
                InputField(title: "First Name", text: Binding(get: { viewModel.state.firstName }, set: { viewModel.onAction(.fieldsChanged(firstName: $0)) }))
                InputField(title: "Last Name", text: Binding(get: { viewModel.state.lastName }, set: { viewModel.onAction(.fieldsChanged(lastName: $0)) }))
                InputField(title: "Phone", text: Binding(get: { viewModel.state.phone }, set: { viewModel.onAction(.fieldsChanged(phone: $0)) }))
                InputField(title: "Email", text: Binding(get: { viewModel.state.email }, set: { viewModel.onAction(.fieldsChanged(email: $0)) }), keyboardType: .emailAddress, textContentType: .emailAddress, autocapitalizationType: .none, autocorrectionDisabled: true)
                PasswordInputField(title: "Password", text: Binding(get: { viewModel.state.password }, set: { viewModel.onAction(.fieldsChanged(password: $0)) }))
                Toggle("I'm willing to testify by phone if needed.", isOn: Binding(
                    get: { viewModel.state.testify },
                    set: { viewModel.onAction(.fieldsChanged(testify: $0)) }
                ))
                PrimaryButton(title: viewModel.state.loading ? "Creating..." : "Create Account", size: .compact) {
                    viewModel.onAction(.registerPressed(onSuccess: onSuccess))
                }
                Button("Already registered? Login", action: onLogin)
                    .foregroundStyle(Color.reportedOrange)
                }
                .padding()
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}

struct AuthToolbar: View {
    let title: String
    let onBack: (() -> Void)?
    let onDismiss: (() -> Void)?

    var body: some View {
        ZStack {
            Text(title)
                .font(.title2.weight(.semibold))
            HStack {
                if let onBack {
                    Button(action: onBack) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 24, weight: .semibold))
                            .foregroundStyle(.primary)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(.plain)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
                Spacer()
                if let onDismiss {
                    Button("Close", action: onDismiss)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                } else {
                    Color.clear.frame(width: 44, height: 44)
                }
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 14)
        .background(Color(.systemBackground))
    }
}
