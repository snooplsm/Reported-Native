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

struct ImageAddressPickerRow: View {
    let suggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshing: Bool
    let onSelect: (ComposerState.AddressSuggestion) -> Void

    var body: some View {
        if let suggestion {
            Button {
                onSelect(suggestion)
            } label: {
                rowContent(
                    title: "Use address from image",
                    subtitle: suggestion.label,
                    showsProgress: false
                )
            }
            .buttonStyle(.plain)
        } else if hasImageAddressSource {
            rowContent(
                title: isRefreshing ? "Reading address from image" : "No image address found",
                subtitle: isRefreshing ? "Checking the selected photo GPS." : "This image does not include readable GPS.",
                showsProgress: isRefreshing
            )
        }
    }

    private func rowContent(title: String, subtitle: String, showsProgress: Bool) -> some View {
        HStack(spacing: 10) {
            Image(systemName: "mappin.circle.fill")
                .font(.title3.weight(.semibold))
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
            }
            Spacer(minLength: 0)
            if showsProgress {
                ProgressView()
                    .tint(Color.reportedOrange)
            }
        }
        .foregroundStyle(Color.reportedOrange)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.reportedOrange.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

struct AddressSearchTextField: UIViewRepresentable {
    let placeholder: String
    @Binding var text: String
    @Binding var isFirstResponder: Bool
    let onSubmit: () -> Void

    func makeUIView(context: Context) -> UITextField {
        let textField = UITextField(frame: .zero)
        textField.delegate = context.coordinator
        textField.placeholder = placeholder
        textField.borderStyle = .none
        textField.font = .preferredFont(forTextStyle: .body)
        textField.adjustsFontForContentSizeCategory = true
        textField.textColor = .label
        textField.tintColor = .label
        textField.autocapitalizationType = .words
        textField.autocorrectionType = .yes
        textField.spellCheckingType = .yes
        textField.textContentType = .fullStreetAddress
        textField.returnKeyType = .done
        textField.setContentHuggingPriority(.defaultLow, for: .horizontal)
        textField.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        textField.addTarget(context.coordinator, action: #selector(Coordinator.textDidChange(_:)), for: .editingChanged)
        return textField
    }

    func updateUIView(_ textField: UITextField, context: Context) {
        context.coordinator.parent = self
        if textField.text != text {
            textField.text = text
        }

        if isFirstResponder {
            if !textField.isFirstResponder {
                DispatchQueue.main.async {
                    guard self.isFirstResponder else { return }
                    textField.becomeFirstResponder()
                    context.coordinator.placeCaretAtStartIfNeeded(in: textField)
                }
            } else {
                context.coordinator.placeCaretAtStartIfNeeded(in: textField)
            }
        } else if textField.isFirstResponder {
            textField.resignFirstResponder()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var parent: AddressSearchTextField
        private var shouldPlaceCaretAtStart = true

        init(parent: AddressSearchTextField) {
            self.parent = parent
        }

        @objc func textDidChange(_ textField: UITextField) {
            parent.text = textField.text ?? ""
        }

        func textFieldDidBeginEditing(_ textField: UITextField) {
            parent.isFirstResponder = true
            placeCaretAtStartIfNeeded(in: textField)
        }

        func textFieldDidEndEditing(_ textField: UITextField) {
            parent.isFirstResponder = false
        }

        func textFieldShouldReturn(_ textField: UITextField) -> Bool {
            parent.isFirstResponder = false
            parent.onSubmit()
            return false
        }

        func placeCaretAtStartIfNeeded(in textField: UITextField) {
            guard shouldPlaceCaretAtStart else { return }
            shouldPlaceCaretAtStart = false
            DispatchQueue.main.async {
                guard textField.isFirstResponder else { return }
                let start = textField.beginningOfDocument
                textField.selectedTextRange = textField.textRange(from: start, to: start)
            }
        }
    }
}

struct AddressSearchScreen: View {
    @Environment(\.dismiss) private var dismiss
    @Binding var query: String
    let suggestions: [ComposerState.AddressSuggestion]
    let addressProvider: ReportAddressProvider
    let isLoading: Bool
    let isError: Bool
    let imageAddressSuggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshingImageAddress: Bool
    let onCancel: () -> Void
    let onClear: () -> Void
    let onOpenMap: () -> Void
    let onSelect: (ComposerState.AddressSuggestion) -> Void
    let onUseImageAddress: (ComposerState.AddressSuggestion) -> Void
    @State private var isSearchFocused = false

    var body: some View {
        VStack(spacing: 0) {
            addressSearchHeader
            Divider()
            if isLoading {
                HStack(spacing: 10) {
                    ProgressView()
                        .controlSize(.small)
                    Text(addressProvider.searchingLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            if query.isEmpty && (imageAddressSuggestion != nil || hasImageAddressSource) {
                ImageAddressPickerRow(
                    suggestion: imageAddressSuggestion,
                    hasImageAddressSource: hasImageAddressSource,
                    isRefreshing: isRefreshingImageAddress
                ) { suggestion in
                    onUseImageAddress(suggestion)
                    dismissActiveKeyboard()
                    dismiss()
                }
                .padding(.horizontal, 16)
                .padding(.top, 12)
                .padding(.bottom, 6)
            }
            if !isLoading && !query.isEmpty && suggestions.isEmpty {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary)
                    Text(addressProvider.noMatchesLabel)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(suggestions) { suggestion in
                        Button {
                            onSelect(suggestion)
                            dismissActiveKeyboard()
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "mappin.circle.fill")
                                    .font(.title3)
                                    .foregroundStyle(Color.reportedOrange)
                                Text(suggestion.label)
                                    .font(.body)
                                    .foregroundStyle(.primary)
                                    .multilineTextAlignment(.leading)
                                    .lineLimit(2)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        if suggestion.id != suggestions.last?.id {
                            Divider()
                                .padding(.leading, 50)
                        }
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(Color(.systemBackground))
        .onAppear {
            DispatchQueue.main.async {
                isSearchFocused = true
            }
        }
    }

    private var addressSearchHeader: some View {
        HStack(spacing: 10) {
            Button {
                onCancel()
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 34, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(.secondary)
                AddressSearchTextField(
                    placeholder: "Search address",
                    text: $query,
                    isFirstResponder: $isSearchFocused
                ) {
                    dismissActiveKeyboard()
                }
                .frame(minWidth: 0, maxWidth: .infinity, minHeight: 24, maxHeight: 24)
                if !query.isEmpty {
                    Button {
                        onClear()
                        isSearchFocused = true
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear address")
                }
            }
            .padding(.horizontal, 12)
            .frame(height: 46)
            .frame(maxWidth: .infinity)
            .background(Color(.secondarySystemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .stroke(isError ? Color.red : Color.clear, lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            Button {
                onOpenMap()
                dismissActiveKeyboard()
                dismiss()
            } label: {
                Image(systemName: "map")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(.primary)
                    .frame(width: 34, height: 42)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Show Map")
        }
        .padding(.horizontal, 12)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .background(Color(.systemBackground))
    }
}

struct AddressMapSheet: View {
    private static let defaultCoordinate = CLLocationCoordinate2D(latitude: 40.71305, longitude: -74.00723)
    private static let defaultSpan = MKCoordinateSpan(latitudeDelta: 0.0045, longitudeDelta: 0.0045)

    let initialLatitude: Double?
    let initialLongitude: Double?
    let initialAddress: String
    let imageAddressSuggestion: ComposerState.AddressSuggestion?
    let hasImageAddressSource: Bool
    let isRefreshingImageAddress: Bool
    let onCancel: () -> Void
    let onDone: (ComposerState.AddressSuggestion) -> Void

    @State private var position: MapCameraPosition
    @State private var selectedCoordinate: CLLocationCoordinate2D
    @State private var resolvedAddress: String
    @State private var pendingSuggestion: ComposerState.AddressSuggestion?
    @State private var currentRegion: MKCoordinateRegion
    @State private var lookupInFlight = false
    @State private var lookupTask: Task<Void, Never>?
    @State private var addressCache: [String: ComposerState.AddressSuggestion] = [:]

    init(
        initialLatitude: Double?,
        initialLongitude: Double?,
        initialAddress: String,
        imageAddressSuggestion: ComposerState.AddressSuggestion?,
        hasImageAddressSource: Bool = false,
        isRefreshingImageAddress: Bool = false,
        onCancel: @escaping () -> Void,
        onDone: @escaping (ComposerState.AddressSuggestion) -> Void
    ) {
        self.initialLatitude = initialLatitude
        self.initialLongitude = initialLongitude
        self.initialAddress = initialAddress
        self.imageAddressSuggestion = imageAddressSuggestion
        self.hasImageAddressSource = hasImageAddressSource
        self.isRefreshingImageAddress = isRefreshingImageAddress
        self.onCancel = onCancel
        self.onDone = onDone
        let coordinate = Self.validCoordinate(latitude: initialLatitude, longitude: initialLongitude)
            ?? Self.defaultCoordinate
        let region = MKCoordinateRegion(center: coordinate, span: Self.defaultSpan)
        _selectedCoordinate = State(initialValue: coordinate)
        _resolvedAddress = State(initialValue: initialAddress)
        _pendingSuggestion = State(initialValue: initialAddress.isEmpty ? nil : ComposerState.AddressSuggestion(
            label: initialAddress,
            latitude: coordinate.latitude,
            longitude: coordinate.longitude
        ))
        _currentRegion = State(initialValue: region)
        _position = State(initialValue: .region(region))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Button("Cancel", action: onCancel)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                Spacer()
                Button("Done") {
                    onDone(pendingSuggestion ?? fallbackSuggestion(for: selectedCoordinate))
                }
                .font(.body.weight(.semibold))
                .foregroundStyle(Color.reportedOrange)
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)

            VStack(alignment: .leading, spacing: 6) {
                Text("Pan map to change address.")
                    .font(.title2.bold())
                Text(addressStatusText)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
            }
            .padding(.horizontal, 20)

            if hasImageAddressSource || imageAddressSuggestion != nil {
                ImageAddressPickerRow(
                    suggestion: imageAddressSuggestion,
                    hasImageAddressSource: hasImageAddressSource,
                    isRefreshing: isRefreshingImageAddress
                ) { suggestion in
                    useImageAddress(suggestion)
                }
                .padding(.horizontal, 20)
            }

            ZStack {
                Map(position: $position, interactionModes: [.pan, .zoom])
                    .onMapCameraChange(frequency: .onEnd) { context in
                        currentRegion = context.region
                        selectedCoordinate = context.region.center
                        scheduleLookup(for: context.region.center)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                VStack(spacing: 0) {
                    ZStack {
                        Circle()
                            .fill(Color.reportedOrange)
                            .frame(width: 56, height: 56)
                        Image(systemName: "mappin")
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                    Rectangle()
                        .fill(Color.reportedOrange)
                        .frame(width: 3, height: 24)
                }
                .offset(y: -18)
                .shadow(color: .black.opacity(0.25), radius: 8, y: 3)
                .allowsHitTesting(false)

                VStack(spacing: 8) {
                    Button {
                        zoom(by: 0.5)
                    } label: {
                        Image(systemName: "plus")
                            .font(.body.weight(.bold))
                            .frame(width: 42, height: 42)
                    }
                    Divider()
                        .frame(width: 30)
                    Button {
                        zoom(by: 2.0)
                    } label: {
                        Image(systemName: "minus")
                            .font(.body.weight(.bold))
                            .frame(width: 42, height: 42)
                    }
                }
                .foregroundStyle(.primary)
                .background(.regularMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .shadow(color: .black.opacity(0.16), radius: 10, y: 3)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .trailing)
                .padding(.trailing, 14)
                .padding(.top, 14)
                .frame(maxHeight: .infinity, alignment: .top)
                .buttonStyle(.plain)
            }
            .onAppear {
                if Self.validCoordinate(latitude: initialLatitude, longitude: initialLongitude) == nil {
                    let region = MKCoordinateRegion(center: Self.defaultCoordinate, span: Self.defaultSpan)
                    currentRegion = region
                    selectedCoordinate = region.center
                    position = .region(region)
                }
            }
            .padding(.horizontal, 20)
            .frame(maxHeight: .infinity)
        }
        .background(Color(.systemBackground))
        .onAppear {
            if initialAddress.isEmpty {
                scheduleLookup(for: selectedCoordinate, delayNanoseconds: 0)
            }
        }
        .onDisappear {
            lookupTask?.cancel()
        }
    }

    private func useImageAddress(_ suggestion: ComposerState.AddressSuggestion) {
        guard let coordinate = Self.validCoordinate(latitude: suggestion.latitude, longitude: suggestion.longitude) else {
            return
        }
        lookupTask?.cancel()
        let region = MKCoordinateRegion(center: coordinate, span: Self.defaultSpan)
        addressCache[addressCacheKey(for: coordinate)] = suggestion
        selectedCoordinate = coordinate
        pendingSuggestion = suggestion
        resolvedAddress = suggestion.label
        lookupInFlight = false
        currentRegion = region
        position = .region(region)
    }

    private func zoom(by factor: Double) {
        let span = MKCoordinateSpan(
            latitudeDelta: min(max(currentRegion.span.latitudeDelta * factor, 0.0012), 0.08),
            longitudeDelta: min(max(currentRegion.span.longitudeDelta * factor, 0.0012), 0.08)
        )
        let region = MKCoordinateRegion(center: selectedCoordinate, span: span)
        currentRegion = region
        position = .region(region)
        scheduleLookup(for: region.center)
    }

    private var addressStatusText: String {
        if lookupInFlight {
            return "Finding address..."
        }
        return resolvedAddress.isEmpty ? coordinateText(selectedCoordinate) : resolvedAddress
    }

    private func scheduleLookup(for coordinate: CLLocationCoordinate2D, delayNanoseconds: UInt64 = 700_000_000) {
        let key = addressCacheKey(for: coordinate)
        if let cached = addressCache[key] {
            selectedCoordinate = coordinate
            pendingSuggestion = cached
            resolvedAddress = cached.label
            lookupInFlight = false
            return
        }

        lookupTask?.cancel()
        lookupInFlight = true
        lookupTask = Task {
            if delayNanoseconds > 0 {
                try? await Task.sleep(nanoseconds: delayNanoseconds)
            }
            if Task.isCancelled { return }
            let suggestion = await reverseGeocodeAddress(latitude: coordinate.latitude, longitude: coordinate.longitude)
                ?? fallbackSuggestion(for: coordinate)
            if Task.isCancelled { return }
            await MainActor.run {
                addressCache[key] = suggestion
                selectedCoordinate = coordinate
                pendingSuggestion = suggestion
                resolvedAddress = suggestion.label
                lookupInFlight = false
            }
        }
    }

    private func addressCacheKey(for coordinate: CLLocationCoordinate2D) -> String {
        "\(Int((coordinate.latitude * 10_000).rounded())):\(Int((coordinate.longitude * 10_000).rounded()))"
    }

    private func fallbackSuggestion(for coordinate: CLLocationCoordinate2D) -> ComposerState.AddressSuggestion {
        coordinateAddressSuggestion(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    private func coordinateText(_ coordinate: CLLocationCoordinate2D) -> String {
        formattedCoordinateText(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }

    private static func validCoordinate(latitude: Double?, longitude: Double?) -> CLLocationCoordinate2D? {
        guard let latitude, let longitude else { return nil }
        guard latitude.isFinite, longitude.isFinite else { return nil }
        guard abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
        guard abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else { return nil }
        let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
        return CLLocationCoordinate2DIsValid(coordinate) ? coordinate : nil
    }
}

struct ReportedDateTimeSheet: View {
    let isoValue: String
    let photoIsoValue: String?
    let hasImageTimeSource: Bool
    let isRefreshingImageTime: Bool
    let onSelected: (String) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var month: Int
    @State private var day: Int
    @State private var year: Int
    @State private var hour: Int
    @State private var minute: Int
    @State private var meridiem: String

    init(
        isoValue: String,
        photoIsoValue: String?,
        hasImageTimeSource: Bool = false,
        isRefreshingImageTime: Bool = false,
        onSelected: @escaping (String) -> Void
    ) {
        self.isoValue = isoValue
        self.photoIsoValue = photoIsoValue
        self.hasImageTimeSource = hasImageTimeSource
        self.isRefreshingImageTime = isRefreshingImageTime
        self.onSelected = onSelected
        let components = Self.components(from: Self.date(from: isoValue) ?? Date())
        _month = State(initialValue: components.month)
        _day = State(initialValue: components.day)
        _year = State(initialValue: components.year)
        _hour = State(initialValue: components.hour)
        _minute = State(initialValue: components.minute)
        _meridiem = State(initialValue: components.meridiem)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Occurred At")
                        .font(.title2.bold())
                    Spacer()
                    Button("Done") {
                        onSelected(Self.isoString(from: selectedDate))
                        dismiss()
                    }
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.reportedOrange)
                }
                if let photoDate {
                    Button {
                        useImageTime(photoDate)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: "camera")
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Use time from image")
                                    .font(.subheadline.weight(.semibold))
                                Text(photoDate.formatted(.dateTime.month(.abbreviated).day().year().hour(.defaultDigits(amPM: .abbreviated)).minute()))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer(minLength: 0)
                        }
                        .foregroundStyle(Color.reportedOrange)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.reportedOrange.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                    .buttonStyle(.plain)
                } else if hasImageTimeSource {
                    HStack(spacing: 10) {
                        Image(systemName: "camera")
                        VStack(alignment: .leading, spacing: 2) {
                            Text(isRefreshingImageTime ? "Reading time from image" : "No image time found")
                                .font(.subheadline.weight(.semibold))
                            Text(isRefreshingImageTime ? "Checking the selected media metadata." : "This image does not include a readable capture time.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        if isRefreshingImageTime {
                            ProgressView()
                        }
                    }
                    .foregroundStyle(Color.reportedOrange)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.reportedOrange.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }
            }
            GeometryReader { proxy in
                let availableWidth = max(proxy.size.width, 1)
                let widths = datePickerColumnWidths(for: availableWidth)
                HStack(spacing: 0) {
                    wheelPicker("Month", selection: $month, values: Array(1...12), width: widths.month) { value in
                        Calendar.current.shortMonthSymbols[value - 1]
                    }
                    wheelPicker("Day", selection: $day, values: Array(1...daysInSelectedMonth), width: widths.day) { "\($0)" }
                    wheelPicker("Year", selection: $year, values: Array((currentYear - 1)...currentYear), width: widths.year) { "\($0)" }
                    wheelPicker("Hour", selection: $hour, values: Array(1...12), width: widths.hour) { "\($0)" }
                    wheelPicker("Minute", selection: $minute, values: Array(stride(from: 0, through: 59, by: 1)), width: widths.minute) { String(format: "%02d", $0) }
                    Picker("", selection: $meridiem) {
                        Text("AM")
                            .font(.system(size: 16))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .tag("AM")
                        Text("PM")
                            .font(.system(size: 16))
                            .lineLimit(1)
                            .minimumScaleFactor(0.75)
                            .tag("PM")
                    }
                    .pickerStyle(.wheel)
                    .frame(width: widths.meridiem, height: 126)
                    .compositingGroup()
                    .clipped()
                }
                .frame(width: availableWidth, height: 154, alignment: .center)
                .clipped()
            }
            .frame(height: 154)
            .onChange(of: month) { _, _ in clampDay() }
            .onChange(of: year) { _, _ in clampDay() }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
    }

    private var photoDate: Date? {
        guard let photoIsoValue else { return nil }
        return Self.date(from: photoIsoValue)
    }

    private var currentYear: Int {
        Calendar.current.component(.year, from: Date())
    }

    private var daysInSelectedMonth: Int {
        var components = DateComponents()
        components.year = year
        components.month = month
        let date = Calendar.current.date(from: components) ?? Date()
        return Calendar.current.range(of: .day, in: .month, for: date)?.count ?? 31
    }

    private var selectedDate: Date {
        var components = DateComponents()
        components.year = year
        components.month = month
        components.day = min(day, daysInSelectedMonth)
        let normalizedHour = hour % 12 + (meridiem == "PM" ? 12 : 0)
        components.hour = normalizedHour
        components.minute = minute
        return min(Calendar.current.date(from: components) ?? Date(), Date())
    }

    @ViewBuilder
    private func wheelPicker<Value: Hashable>(
        _ label: String,
        selection: Binding<Value>,
        values: [Value],
        width: CGFloat,
        formatter: @escaping (Value) -> String
    ) -> some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(.secondary)
            Picker(label, selection: selection) {
                ForEach(values, id: \.self) { value in
                    Text(formatter(value))
                        .font(.system(size: 17))
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                        .tag(value)
                }
            }
            .pickerStyle(.wheel)
            .frame(width: width, height: 126)
            .compositingGroup()
            .clipped()
        }
        .frame(width: width, height: 154)
        .clipped()
    }

    private func datePickerColumnWidths(for availableWidth: CGFloat) -> (
        month: CGFloat,
        day: CGFloat,
        year: CGFloat,
        hour: CGFloat,
        minute: CGFloat,
        meridiem: CGFloat
    ) {
        let weights: [CGFloat] = [0.2, 0.14, 0.2, 0.14, 0.18, 0.14]
        let base = max(availableWidth, 280)
        return (
            month: floor(base * weights[0]),
            day: floor(base * weights[1]),
            year: floor(base * weights[2]),
            hour: floor(base * weights[3]),
            minute: floor(base * weights[4]),
            meridiem: floor(base * weights[5])
        )
    }

    private func clampDay() {
        day = min(day, daysInSelectedMonth)
    }

    private func apply(_ date: Date) {
        let components = Self.components(from: date)
        month = components.month
        day = components.day
        year = components.year
        hour = components.hour
        minute = components.minute
        meridiem = components.meridiem
    }

    private func useImageTime(_ date: Date) {
        apply(date)
        onSelected(Self.isoString(from: date))
        dismiss()
    }

    private static func date(from iso: String) -> Date? {
        guard !iso.isEmpty else { return nil }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        return isoFormatter.date(from: iso) ?? fallbackIsoFormatter.date(from: iso)
    }

    private static func isoString(from date: Date) -> String {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return formatter.string(from: date)
    }

    private static func components(from date: Date) -> (month: Int, day: Int, year: Int, hour: Int, minute: Int, meridiem: String) {
        let calendar = Calendar.current
        let rawHour = calendar.component(.hour, from: date)
        let hour = rawHour % 12 == 0 ? 12 : rawHour % 12
        return (
            calendar.component(.month, from: date),
            calendar.component(.day, from: date),
            calendar.component(.year, from: date),
            hour,
            calendar.component(.minute, from: date),
            rawHour >= 12 ? "PM" : "AM"
        )
    }
}
