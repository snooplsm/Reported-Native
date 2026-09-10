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

struct ContentView: View {
    @StateObject private var sessionViewModel = SessionViewModel()
    @StateObject private var themeViewModel = ThemeViewModel()
    @State private var authRoute: AuthRoute?
    @State private var sharedMediaImportId: String?
    @State private var detectedDraftOpenRequest: UUID?

    var body: some View {
        Group {
            if sessionViewModel.state.loading {
                StartupLoadingView()
            } else if sessionViewModel.state.session?.isAuthorized == true || sessionViewModel.state.isGuest {
                MainShellView(
                    sessionViewModel: sessionViewModel,
                    themeViewModel: themeViewModel,
                    onRequireLogin: { authRoute = .login },
                    sharedMediaImportId: $sharedMediaImportId,
                    detectedDraftOpenRequest: $detectedDraftOpenRequest
                )
            } else {
                AuthFlowView(sessionViewModel: sessionViewModel)
            }
        }
        .task {
            await ReportedRemoteConfigService.shared.configureAndFetch()
            sessionViewModel.onAction(.load)
            themeViewModel.onAction(.load)
        }
        .onOpenURL { url in
            if NativeSocialAuth.handle(url: url) {
                return
            }
            guard url.scheme == "reported",
                  url.host == "share",
                  let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
                  let importId = components.queryItems?.first(where: { $0.name == "import" })?.value,
                  !importId.isEmpty else {
                return
            }
            sharedMediaImportId = importId
            if sessionViewModel.state.session?.isAuthorized != true && !sessionViewModel.state.isGuest {
                sessionViewModel.onAction(.continueAsGuest)
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .reportedDetectedInfractionEditRequested)) { _ in
            detectedDraftOpenRequest = UUID()
            if sessionViewModel.state.session?.isAuthorized != true && !sessionViewModel.state.isGuest {
                sessionViewModel.onAction(.continueAsGuest)
            }
        }
        .preferredColorScheme(appPreferredColorScheme(for: themeViewModel.mode))
        .sheet(item: $authRoute) { route in
            AuthFlowView(
                sessionViewModel: sessionViewModel,
                initialRoute: route,
                allowSkip: false,
                onDismiss: { authRoute = nil }
            )
        }
    }
}

struct StartupLoadingView: View {
    var body: some View {
        ZStack {
            Color(.systemBackground)
                .ignoresSafeArea()
            ProgressView()
                .controlSize(.regular)
                .tint(Color.reportedOrange)
        }
    }
}

enum AuthRoute: String, Identifiable {
    case splash
    case login
    case register

    var id: String { rawValue }
}

struct AuthFlowView: View {
    @ObservedObject var sessionViewModel: SessionViewModel
    @State private var path: [AuthRoute]
    let allowSkip: Bool
    let onDismiss: (() -> Void)?

    init(
        sessionViewModel: SessionViewModel,
        initialRoute: AuthRoute = .splash,
        allowSkip: Bool = true,
        onDismiss: (() -> Void)? = nil
    ) {
        self.sessionViewModel = sessionViewModel
        self.allowSkip = allowSkip
        self.onDismiss = onDismiss
        _path = State(initialValue: initialRoute == .splash ? [] : [initialRoute])
    }

    var body: some View {
        NavigationStack(path: $path) {
            ZStack {
                SplashVideoBackground()
                    .ignoresSafeArea()
                VStack(spacing: 16) {
                    Spacer()
                    ScreenCard {
                        if let onDismiss {
                            HStack {
                                Spacer()
                                TertiaryButton(title: "Close", action: onDismiss)
                                    .frame(maxWidth: .none)
                            }
                        }
                        PrimaryButton(title: "Login", size: .compact) { path.append(.login) }
                        SecondaryButton(title: "Register", size: .compact) { path.append(.register) }
                    }
                    if allowSkip {
                        Button {
                            sessionViewModel.onAction(.continueAsGuest)
                        } label: {
                            Text("Skip")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.white)
                                .shadow(color: .black.opacity(0.35), radius: 4, x: 0, y: 1)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding()
            }
            .toolbarBackground(.hidden, for: .navigationBar)
            .navigationDestination(for: AuthRoute.self) { route in
                switch route {
                case .login:
                    LoginScreen {
                        sessionViewModel.onAction(.authenticated)
                        onDismiss?()
                    } onRegister: {
                        path.append(.register)
                    } onBack: {
                        handleAuthBack()
                    } onDismiss: {
                        onDismiss?()
                    }
                case .register:
                    RegisterScreen {
                        sessionViewModel.onAction(.authenticated)
                        onDismiss?()
                    } onLogin: {
                        path.append(.login)
                    } onBack: {
                        handleAuthBack()
                    } onDismiss: {
                        onDismiss?()
                    }
                case .splash:
                    EmptyView()
                }
            }
            .navigationBarBackButtonHidden(true)
        }
    }

    private func handleAuthBack() {
        if path.count <= 1, let onDismiss {
            onDismiss()
        } else if !path.isEmpty {
            path.removeLast()
        }
    }
}

struct SplashVideoBackground: UIViewRepresentable {
    func makeUIView(context: Context) -> PlayerContainerView {
        let view = PlayerContainerView()
        view.playerLayer.videoGravity = .resizeAspectFill

        guard let url = Bundle.main.url(forResource: "splash", withExtension: "mp4") else {
            view.backgroundColor = UIColor.systemBackground
            return view
        }

        let player = AVQueuePlayer()
        player.isMuted = true
        player.actionAtItemEnd = .none

        let item = AVPlayerItem(url: url)
        let looper = AVPlayerLooper(player: player, templateItem: item)

        view.playerLayer.player = player
        context.coordinator.player = player
        context.coordinator.looper = looper
        player.play()

        return view
    }

    func updateUIView(_ view: PlayerContainerView, context: Context) {
        context.coordinator.player?.play()
    }

    static func dismantleUIView(_ view: PlayerContainerView, coordinator: Coordinator) {
        coordinator.player?.pause()
        coordinator.player = nil
        coordinator.looper = nil
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    final class Coordinator {
        var player: AVQueuePlayer?
        var looper: AVPlayerLooper?
    }
}
