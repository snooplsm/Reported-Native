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

struct ComposerPickerModifier: ViewModifier {
    @Binding var singlePickerPresented: Bool
    @Binding var multiPickerPresented: Bool
    @Binding var pickedItem: PhotosPickerItem?
    @Binding var pickedItems: [PhotosPickerItem]
    let maxSelectionCount: Int
    let allowsVideos: Bool
    let handlePickedItem: (PhotosPickerItem) async -> Void
    let handlePickedItems: ([PhotosPickerItem]) async -> Void

    func body(content: Self.Content) -> some View {
        content
            .photosPicker(
                isPresented: $singlePickerPresented,
                selection: $pickedItem,
                matching: mediaFilter,
                preferredItemEncoding: .current
            )
            .photosPicker(
                isPresented: $multiPickerPresented,
                selection: $pickedItems,
                maxSelectionCount: maxSelectionCount,
                matching: mediaFilter,
                preferredItemEncoding: .current
            )
            .onChange(of: pickedItem) { _, newItem in
                guard let newItem else { return }
                Task {
                    await handlePickedItem(newItem)
                    pickedItem = nil
                }
            }
            .onChange(of: pickedItems) { _, newItems in
                guard !newItems.isEmpty else { return }
                Task {
                    await handlePickedItems(newItems)
                    pickedItems = []
                }
            }
    }

    private var mediaFilter: PHPickerFilter {
        allowsVideos ? .any(of: [.images, .videos]) : .images
    }
}

struct ComplaintOption: Identifiable {
    let id: String
    let title: String
    let imageName: String
    let imageExtension: String
    var lottieName: String? = nil
}

let complaintOptionsList: [ComplaintOption] = [
    ComplaintOption(id: "blocked_bike_lane", title: "Blocked bike lane", imageName: "bikelane", imageExtension: "svg", lottieName: "bikelane"),
    ComplaintOption(id: "blocked_crosswalk", title: "Blocked crosswalk", imageName: "crosswalk", imageExtension: "svg", lottieName: "crosswalk"),
    ComplaintOption(id: "ran_red_light", title: "Ran red light", imageName: "ranredlight", imageExtension: "jpg", lottieName: "ranredlight"),
    ComplaintOption(id: "drove_recklessly", title: "Drove recklessly", imageName: "reckless", imageExtension: "png", lottieName: "reckless"),
    ComplaintOption(id: "parked_illegally", title: "Parked illegally", imageName: "parkedillegally", imageExtension: "jpg", lottieName: "parkedillegally")
]

func complaintOptionsFor(_ categories: [ComplaintCategory]) -> [ComplaintOption] {
    [
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "blocked_bike_lane", title: "Blocked bike lane", imageName: "bikelane", imageExtension: "svg", lottieName: "bikelane"),
            keywords: ["bike lane"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "blocked_crosswalk", title: "Blocked crosswalk", imageName: "crosswalk", imageExtension: "svg", lottieName: "crosswalk"),
            keywords: ["crosswalk"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "ran_red_light", title: "Ran red light", imageName: "ranredlight", imageExtension: "jpg", lottieName: "ranredlight"),
            keywords: ["red light", "stop sign"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "drove_recklessly", title: "Drove recklessly", imageName: "reckless", imageExtension: "png", lottieName: "reckless"),
            keywords: ["reckless", "aggressive"]
        ),
        complaintOptionFor(
            categories,
            fallback: ComplaintOption(id: "parked_illegally", title: "Parked illegally", imageName: "parkedillegally", imageExtension: "jpg", lottieName: "parkedillegally"),
            keywords: ["parked"]
        )
    ]
}

func complaintOptionFor(
    _ categories: [ComplaintCategory],
    fallback: ComplaintOption,
    keywords: [String]
) -> ComplaintOption {
    guard let category = categories.first(where: { category in
        let searchableText = "\(category.name) \(category.key)".lowercased()
        return keywords.contains { searchableText.contains($0) }
    }) else {
        return fallback
    }
    return ComplaintOption(
        id: category.id,
        title: category.name,
        imageName: fallback.imageName,
        imageExtension: fallback.imageExtension,
        lottieName: fallback.lottieName
    )
}

struct ExtractedSubmissionMetadata {
    let occurredAtIso: String?
    let latitude: Double?
    let longitude: Double?
}

struct ComplaintChooserSheet: View {
    let title: String
    let options: [ComplaintOption]
    let selectedComplaintId: String?

    let onSelected: (ComplaintOption) -> Void
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: isTabletLayout ? 20 : 16) {
                    Text(title)
                        .font(isTabletLayout ? .largeTitle.bold() : .title2.bold())
                    LazyVGrid(columns: columns(for: geometry.size.width), spacing: isTabletLayout ? 16 : 12) {
                        ForEach(options) { option in
                            ZStack(alignment: .topTrailing) {
                                ComplaintMediaTile(
                                    option: option,

                                    showImage: true,
                                    imageHeight: tileMetrics(for: geometry.size.width).imageHeight,
                                    minHeight: tileMetrics(for: geometry.size.width).minHeight,
                                    titleFont: isTabletLayout ? .title3.weight(.semibold) : .subheadline.weight(.semibold)
                                ) {
                                    onSelected(option)
                                }
                                if option.id == selectedComplaintId {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.title3.weight(.semibold))
                                        .foregroundStyle(Color.green)
                                        .background(Color(.systemBackground), in: Circle())
                                        .padding(10)
                                }
                            }
                        }
                    }
                }
                .frame(maxWidth: maxContentWidth(for: geometry.size.width), alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 16)
                .padding(.top, 16)
                .padding(.bottom, 8)
            }
        }
    }

    private func columns(for width: CGFloat) -> [GridItem] {
        let columnCount = width >= 700 ? 3 : 2
        return Array(repeating: GridItem(.flexible(), spacing: isTabletLayout ? 16 : 12, alignment: .top), count: columnCount)
    }

    private func maxContentWidth(for width: CGFloat) -> CGFloat {
        width >= 700 ? min(width, 780) : width
    }

    private func tileMetrics(for width: CGFloat) -> (imageHeight: CGFloat, minHeight: CGFloat) {
        width >= 700 ? (136, 218) : (96, 150)
    }

    private var isTabletLayout: Bool {
        UIDevice.current.userInterfaceIdiom == .pad || horizontalSizeClass == .regular
    }
}

struct ComplaintMediaTile: View {
    let option: ComplaintOption
    @Environment(\.scenePhase) private var scenePhase
    @State private var isVisible = false
    var showImage = true
    var imageHeight: CGFloat = 96
    var minHeight: CGFloat = 150
    var titleFont: Font = .subheadline.weight(.semibold)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                if showImage {
                    ComplaintOptionImage(option: option, animate: isVisible && scenePhase == .active)
                        .aspectRatio(1, contentMode: .fit)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
                Text(option.title)
                    .font(titleFont)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.primary)
                    .frame(maxWidth: .infinity)
                    .lineLimit(1)
                    .minimumScaleFactor(0.55)
            }
            .frame(maxWidth: .infinity, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onAppear { isVisible = true }
        .onDisappear { isVisible = false }
    }
}

struct ComplaintOptionImage: View {
    @Environment(\.colorScheme) private var colorScheme
    let option: ComplaintOption
    var animate = false

    var body: some View {
        if let lottieName = option.lottieName {
            ZStack {
                Color(red: colorScheme == .dark ? 37 / 255 : 227 / 255,
                      green: colorScheme == .dark ? 43 / 255 : 228 / 255,
                      blue: colorScheme == .dark ? 51 / 255 : 222 / 255)
                LottieAssetView(
                    animationName: colorScheme == .dark ? "\(lottieName)_dark" : lottieName,
                    isPlaying: animate,
                    contentMode: .scaleAspectFit,
                    previewProgress: option.lottieName == "ranredlight" ? 0.23 : 0.4
                )
                .clipped()
            }
        } else if option.imageExtension.lowercased() == "svg" {
            ZStack {
                Color(red: colorScheme == .dark ? 37 / 255 : 227 / 255,
                      green: colorScheme == .dark ? 43 / 255 : 228 / 255,
                      blue: colorScheme == .dark ? 51 / 255 : 222 / 255)
                SvgAssetView(name: option.imageName)
            }
        } else if let image = complaintUIImage(option: option) {
            ZStack {
                Color(red: colorScheme == .dark ? 37 / 255 : 227 / 255,
                      green: colorScheme == .dark ? 43 / 255 : 228 / 255,
                      blue: colorScheme == .dark ? 51 / 255 : 222 / 255)
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()
            }
        } else {
            ZStack {
                LinearGradient(
                    colors: [Color.reportedOrange.opacity(0.95), Color.red.opacity(0.7)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Image(systemName: "photo")
                    .font(.system(size: 34, weight: .medium))
                    .foregroundStyle(.white)
            }
        }
    }
}

extension ComplaintOption {
    var artBackground: Color {
        Color(red: 243 / 255, green: 245 / 255, blue: 238 / 255)
    }

    var animationDuration: TimeInterval? {
        guard let lottieName else { return nil }
        if let url = Bundle.main.url(forResource: lottieName, withExtension: "json", subdirectory: "complaints"),
           let animation = LottieAnimation.filepath(url.path) {
            return animation.duration
        }
        if let url = Bundle.main.url(forResource: lottieName, withExtension: "json"),
           let animation = LottieAnimation.filepath(url.path) {
            return animation.duration
        }
        return LottieAnimation.named(lottieName)?.duration
    }
}

struct SvgAssetView: UIViewRepresentable {
    let name: String

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView(frame: .zero)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.isUserInteractionEnabled = false
        loadSvg(in: webView)
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        loadSvg(in: webView)
    }

    private func loadSvg(in webView: WKWebView) {
        guard let url = svgUrl, let svg = try? String(contentsOf: url) else { return }
        let html = """
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
              html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: transparent; }
              svg { width: 100%; height: 100%; display: block; }
            </style>
          </head>
          <body>\(svg)</body>
        </html>
        """
        webView.loadHTMLString(html, baseURL: url.deletingLastPathComponent())
    }

    private var svgUrl: URL? {
        Bundle.main.url(forResource: name, withExtension: "svg", subdirectory: "complaints")
            ?? Bundle.main.url(forResource: name, withExtension: "svg")
    }
}

struct LottieAssetView: UIViewRepresentable {
    let animationName: String
    var isPlaying = true
    var contentMode: UIView.ContentMode = .scaleAspectFit
    var previewProgress: AnimationProgressTime = 0

    func makeUIView(context: Context) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear

        let animationView = LottieAnimationView()
        animationView.translatesAutoresizingMaskIntoConstraints = false
        animationView.contentMode = contentMode
        animationView.loopMode = .loop
        animationView.backgroundBehavior = .pauseAndRestore
        animationView.animation = loadAnimation()

        container.addSubview(animationView)
        NSLayoutConstraint.activate([
            animationView.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            animationView.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            animationView.topAnchor.constraint(equalTo: container.topAnchor),
            animationView.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])

        context.coordinator.animationName = animationName
        context.coordinator.animationView = animationView
        updatePlayback(animationView, coordinator: context.coordinator)
        return container
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        guard let animationView = context.coordinator.animationView else { return }
        animationView.contentMode = contentMode
        if context.coordinator.animationName != animationName {
            let progress = animationView.currentProgress
            animationView.animation = loadAnimation()
            animationView.currentProgress = progress
            context.coordinator.animationName = animationName
        }
        updatePlayback(animationView, coordinator: context.coordinator)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    private func loadAnimation() -> LottieAnimation? {
        if let url = Bundle.main.url(forResource: animationName, withExtension: "json", subdirectory: "complaints") {
            return LottieAnimation.filepath(url.path)
        }
        if let url = Bundle.main.url(forResource: animationName, withExtension: "json") {
            return LottieAnimation.filepath(url.path)
        }
        return LottieAnimation.named(animationName)
    }

    private func updatePlayback(_ animationView: LottieAnimationView, coordinator: Coordinator) {
        if isPlaying {
            if !animationView.isAnimationPlaying {
                coordinator.hasPlayed = true
                animationView.play()
            }
        } else {
            animationView.pause()
            if !coordinator.hasPlayed {
                animationView.currentProgress = previewProgress
            }
        }
    }

    final class Coordinator {
        var animationName: String?
        var hasPlayed = false
        weak var animationView: LottieAnimationView?
    }
}

struct UploadMediaTile: View {
    var minHeight: CGFloat = 150
    var iconSize: CGFloat = 30
    var titleFont: Font = .subheadline.weight(.semibold)
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(.secondarySystemBackground))
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: iconSize, weight: .medium))
                        .foregroundStyle(Color.reportedOrange)
                }
                .aspectRatio(1, contentMode: .fit)
                Text("Add Photo")
                    .font(titleFont)
                    .foregroundStyle(.primary)
            }
            .frame(maxWidth: .infinity, alignment: .top)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
