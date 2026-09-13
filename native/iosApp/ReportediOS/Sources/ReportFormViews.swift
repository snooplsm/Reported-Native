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

struct PickerField: View {
    let title: String
    let value: String
    var isError = false
    var showsChevron = true
    var alignment: Alignment = .leading
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title, isError: isError)
            Button(action: action) {
                HStack(spacing: 6) {
                    AutoFitFieldText(value.isEmpty ? "Select" : value, alignment: alignment)
                        .foregroundStyle(.primary)
                    if showsChevron {
                        Spacer(minLength: 0)
                    }
                    if showsChevron {
                        Image(systemName: "chevron.down")
                            .foregroundStyle(.secondary)
                            .layoutPriority(1)
                    }
                }
                .frame(maxWidth: .infinity, alignment: alignment)
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .background(isError ? Color.reportedFieldErrorBackground : Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }
}

enum ReportLongTextField: String, Identifiable {
    case description
    case notes

    var id: String { rawValue }

    var title: String {
        switch self {
        case .description: return "Description (public facing)"
        case .notes: return "Notes (for your records)"
        }
    }

    var placeholder: String {
        switch self {
        case .description: return "Describe what happened"
        case .notes: return "Add private notes"
        }
    }
}

struct ReportVerifyFields: View {
    let complaintValue: String
    let plateValue: String
    let plateCandidateCount: Int
    let plateRegionValue: String
    var vehicleLookupDetails: VehicleLookupDetails? = nil
    var vehicleLookupInFlight: Bool = false
    var vehicleLookupMessage: String? = nil
    let addressValue: String
    let occurredAtValue: String
    let validationErrors: ComposerState.ValidationErrors
    @Binding var descriptionText: String
    @Binding var notesText: String
    let philadelphiaDetails: PhiladelphiaMobilityAccessDetails?
    let isLandscape: Bool
    let isTablet: Bool
    var usesCompactFieldRow = false
    let onComplaintTapped: () -> Void
    let onPlateTapped: () -> Void
    let onStateTapped: () -> Void
    let onAddressTapped: () -> Void
    let onAddressClear: (() -> Void)?
    let onAddressMapTapped: (() -> Void)?
    let onOccurredAtTapped: () -> Void
    var onDescriptionTapped: (() -> Void)? = nil
    var onNotesTapped: (() -> Void)? = nil
    var onPhiladelphiaMobilityAccessChanged: (PhiladelphiaMobilityAccessDetails) -> Void
    var onDescriptionChanged: (() -> Void)? = nil
    var onNotesChanged: (() -> Void)? = nil

    var body: some View {
        let fieldSpacing: CGFloat = usesCompactFieldRow ? 8 : 12
        let complaintMaxWidth: CGFloat = isLandscape ? .infinity : (isTablet ? 312 : (usesCompactFieldRow ? 104 : 156))
        let complaintMinWidth: CGFloat = isLandscape ? 160 : (isTablet ? 260 : (usesCompactFieldRow ? 92 : 130))
        let plateWidth: CGFloat = isLandscape || isTablet ? 150 : (usesCompactFieldRow ? 120 : 134)
        let stateWidth: CGFloat = isLandscape || isTablet ? 76 : (usesCompactFieldRow ? 48 : 62)
        let multilineFieldMinHeight: CGFloat? = isLandscape || isTablet ? 104 : nil

        HStack(alignment: .top, spacing: fieldSpacing) {
            ComplaintPickerField(
                value: complaintValue,
                isError: validationErrors.complaint != nil,
                action: onComplaintTapped
            )
            .frame(minWidth: complaintMinWidth, maxWidth: complaintMaxWidth)
            PlateInputField(
                text: plateValue,
                isError: validationErrors.plate != nil,
                candidateCount: plateCandidateCount,
                onEdit: onPlateTapped
            )
            .frame(width: plateWidth)
            PickerField(
                title: "State",
                value: plateRegionValue,
                isError: validationErrors.plateRegion != nil,
                showsChevron: false,
                alignment: .center,
                action: onStateTapped
            )
            .frame(width: stateWidth)
        }
        FieldErrorGroup([
            validationErrors.complaint,
            validationErrors.plate,
            validationErrors.plateRegion
        ])
        vehicleLookupStatus
        if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                addressField
                occurredAtField
            }
        } else {
            addressField
            occurredAtField
        }
        if let philadelphiaDetails {
            descriptionField(multilineFieldMinHeight)
            PhiladelphiaMobilityAccessFields(
                details: philadelphiaDetails,
                isLandscape: isLandscape || isTablet,
                onChanged: onPhiladelphiaMobilityAccessChanged
            )
            notesField(multilineFieldMinHeight)
        } else if isLandscape {
            HStack(alignment: .top, spacing: 12) {
                descriptionField(multilineFieldMinHeight)
                notesField(multilineFieldMinHeight)
            }
        } else {
            descriptionField(multilineFieldMinHeight)
            notesField(multilineFieldMinHeight)
        }
    }

    @ViewBuilder
    private var vehicleLookupStatus: some View {
        if vehicleLookupInFlight || vehicleLookupDetails != nil || vehicleLookupMessage != nil {
            HStack(spacing: 10) {
                if vehicleLookupInFlight {
                    ProgressView()
                        .controlSize(.small)
                }
                VStack(alignment: .leading, spacing: 2) {
                    if vehicleLookupInFlight {
                        Text("Looking up vehicle details for \(plateValue) in \(plateRegionValue)…")
                    } else if let details = vehicleLookupDetails {
                        Text(details.summary)
                        Text("Vehicle details from LookupAPlate")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else if let vehicleLookupMessage {
                        Text(vehicleLookupMessage)
                    }
                }
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.secondary.opacity(0.1))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }

    private var addressField: some View {
        ReportAddressPickerField(
            value: addressValue,
            isError: validationErrors.address != nil,
            onSearch: onAddressTapped,
            onClear: addressValue.isEmpty ? nil : onAddressClear,
            onMap: onAddressMapTapped
        )
    }

    private var occurredAtField: some View {
        PickerField(
            title: "Occurred At",
            value: occurredAtValue,
            isError: validationErrors.occurredAt != nil,
            action: onOccurredAtTapped
        )
    }

    private func descriptionField(_ minHeight: CGFloat?) -> some View {
        Group {
            if let onDescriptionTapped {
                MultilineSelectionField(
                    title: ReportLongTextField.description.title,
                    value: descriptionText,
                    placeholder: ReportLongTextField.description.placeholder,
                    minHeight: minHeight ?? 62,
                    action: onDescriptionTapped
                )
            } else {
                InputField(
                    title: ReportLongTextField.description.title,
                    text: $descriptionText,
                    fieldMinHeight: minHeight
                )
            }
        }
        .onChange(of: descriptionText) { _, _ in onDescriptionChanged?() }
    }

    private func notesField(_ minHeight: CGFloat?) -> some View {
        Group {
            if let onNotesTapped {
                MultilineSelectionField(
                    title: ReportLongTextField.notes.title,
                    value: notesText,
                    placeholder: ReportLongTextField.notes.placeholder,
                    minHeight: minHeight ?? 62,
                    action: onNotesTapped
                )
            } else {
                InputField(
                    title: ReportLongTextField.notes.title,
                    text: $notesText,
                    fieldMinHeight: minHeight
                )
            }
        }
        .onChange(of: notesText) { _, _ in onNotesChanged?() }
    }
}

struct PhiladelphiaMobilityAccessFields: View {
    let details: PhiladelphiaMobilityAccessDetails
    let isLandscape: Bool
    let onChanged: (PhiladelphiaMobilityAccessDetails) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Philadelphia mobility access")
                .font(.headline)
                .foregroundStyle(Color.reportedOrange)
                .padding(.horizontal, reportedFieldLabelHorizontalInset)

            fieldRow {
                InputField(
                    title: "Block Number",
                    text: Binding(get: { details.blockNumber }, set: { update(blockNumber: $0) }),
                    keyboardType: .numberPad,
                    autocapitalizationType: .none,
                    autocorrectionDisabled: true
                )
                PhiladelphiaOptionMenuField(
                    title: "Zip Code",
                    value: details.zipCode,
                    options: ppaPhiladelphiaZipCodes,
                    onSelected: { update(zipCode: $0) }
                )
            }

            InputField(
                title: "Street Name",
                text: Binding(get: { details.streetName }, set: { update(streetName: $0) }),
                autocapitalizationType: .words
            )

            fieldRow {
                PhiladelphiaAutocompleteField(
                    title: "Vehicle Make",
                    value: details.vehicleMake,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.vehicleMakes),
                    onChanged: { update(vehicleMake: $0) }
                )
                PhiladelphiaAutocompleteField(
                    title: "Vehicle Model",
                    value: details.vehicleModel,
                    options: ppaVehicleModelSuggestions,
                    onChanged: { update(vehicleModel: $0) }
                )
            }

            fieldRow {
                PhiladelphiaOptionMenuField(
                    title: "Body Style",
                    value: details.bodyStyle,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.bodyStyles),
                    onSelected: { update(bodyStyle: $0) }
                )
                PhiladelphiaOptionMenuField(
                    title: "Vehicle Color",
                    value: details.vehicleColor,
                    options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.vehicleColors),
                    onSelected: { update(vehicleColor: $0) }
                )
            }

            PhiladelphiaOptionMenuField(
                title: "Violation Observed",
                value: details.violationObserved,
                options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.violationObservedOptions),
                onSelected: { update(violationObserved: $0) }
            )
            PhiladelphiaOptionMenuField(
                title: "How Frequently Does This Occur?",
                value: details.frequency,
                options: ppaCatalogArray(PhiladelphiaMobilityAccessCatalogs.shared.frequencyOptions),
                onSelected: { update(frequency: $0) }
            )
        }
    }

    @ViewBuilder
    private func fieldRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if isLandscape {
            HStack(alignment: .top, spacing: 12, content: content)
        } else {
            VStack(alignment: .leading, spacing: 10, content: content)
        }
    }

    private func update(
        blockNumber: String? = nil,
        streetName: String? = nil,
        zipCode: String? = nil,
        vehicleMake: String? = nil,
        vehicleModel: String? = nil,
        bodyStyle: String? = nil,
        vehicleColor: String? = nil,
        violationObserved: String? = nil,
        frequency: String? = nil
    ) {
        onChanged(PhiladelphiaMobilityAccessDetails(
            blockNumber: blockNumber ?? details.blockNumber,
            streetName: streetName ?? details.streetName,
            zipCode: zipCode ?? details.zipCode,
            vehicleMake: vehicleMake ?? details.vehicleMake,
            vehicleModel: vehicleModel ?? details.vehicleModel,
            bodyStyle: bodyStyle ?? details.bodyStyle,
            vehicleColor: vehicleColor ?? details.vehicleColor,
            violationObserved: violationObserved ?? details.violationObserved,
            frequency: frequency ?? details.frequency
        ))
    }
}

struct PhiladelphiaAutocompleteField: View {
    let title: String
    let value: String
    let options: [String]
    let onChanged: (String) -> Void
    @FocusState private var focused: Bool

    private var suggestions: [String] {
        let query = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let matches = query.isEmpty ? options : options.filter { $0.localizedCaseInsensitiveContains(query) }
        return Array(matches.removingCaseInsensitiveDuplicates().prefix(6))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            InputField(
                title: title,
                text: Binding(get: { value }, set: onChanged),
                autocapitalizationType: .words
            )
            .focused($focused)

            if focused && !suggestions.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(suggestions, id: \.self) { suggestion in
                            Button(suggestion) {
                                onChanged(suggestion)
                                focused = false
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Color.reportedOrange)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(Color.reportedOrange.opacity(0.10))
                            .clipShape(Capsule())
                        }
                    }
                    .padding(.horizontal, 2)
                }
            }
        }
    }
}

struct PhiladelphiaOptionMenuField: View {
    let title: String
    let value: String
    let options: [String]
    let onSelected: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title)
            Menu {
                ForEach(options, id: \.self) { option in
                    Button(option) { onSelected(option) }
                }
            } label: {
                HStack(spacing: 6) {
                    AutoFitFieldText(value.isEmpty ? "Select" : value)
                        .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                    Image(systemName: "chevron.down")
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                .background(Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

func ppaCatalogArray(_ value: Any) -> [String] {
    if let strings = value as? [String] {
        return strings
    }
    if let array = value as? NSArray {
        return array.compactMap { $0 as? String }
    }
    return []
}

let ppaPhiladelphiaZipCodes = [
    "19102", "19103", "19104", "19106", "19107", "19111", "19112", "19114",
    "19115", "19116", "19118", "19119", "19120", "19121", "19122", "19123",
    "19124", "19125", "19126", "19127", "19128", "19129", "19130", "19131",
    "19132", "19133", "19134", "19135", "19136", "19137", "19138", "19139",
    "19140", "19141", "19142", "19143", "19144", "19145", "19146", "19147",
    "19148", "19149", "19150", "19151", "19152", "19153", "19154"
]

let ppaVehicleModelSuggestions = [
    "3 Series", "5 Series", "Accord", "Acadia", "Altima", "Atlas", "Bolt", "Bronco",
    "Camaro", "Camry", "Canyon", "Civic", "Colorado", "Corolla", "CR-V", "CX-5",
    "CX-30", "CX-50", "Edge", "Elantra", "Equinox", "Escape", "Explorer", "F-150",
    "Focus", "Forester", "Forte", "Frontier", "Grand Caravan", "Grand Cherokee",
    "Highlander", "HR-V", "Impala", "Jetta", "K5", "Leaf", "Malibu", "Maxima",
    "Model 3", "Model S", "Model X", "Model Y", "Murano", "Odyssey", "Optima",
    "Outback", "Palisade", "Pathfinder", "Pilot", "Prius", "RAV4", "Rogue",
    "Santa Fe", "Savana", "Sentra", "Sienna", "Sierra", "Silverado", "Sonata",
    "Soul", "Sportage", "Suburban", "Tacoma", "Tahoe", "Telluride", "Terrain",
    "Tiguan", "Transit", "Traverse", "Tucson", "Tundra", "Versa", "Wrangler",
    "X1", "X3", "X5", "XC40", "XC60", "XC90", "Yukon"
]

extension Array where Element == String {
    func removingCaseInsensitiveDuplicates() -> [String] {
        var seen = Set<String>()
        return filter { value in
            let key = value.lowercased()
            if seen.contains(key) {
                return false
            }
            seen.insert(key)
            return true
        }
    }
}

struct MultilineSelectionField: View {
    let title: String
    let value: String
    let placeholder: String
    let minHeight: CGFloat
    let action: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: title)
            Button(action: action) {
                HStack(alignment: .top, spacing: 10) {
                    Text(value.isEmpty ? placeholder : value)
                        .font(.body)
                        .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                        .lineLimit(4)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .topLeading)
                    Image(systemName: "square.and.pencil")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(minHeight: minHeight, alignment: .topLeading)
                .frame(maxWidth: .infinity, alignment: .topLeading)
                .background(Color(.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .contentShape(RoundedRectangle(cornerRadius: 8))
            }
            .buttonStyle(.plain)
        }
    }
}

struct FullScreenReportTextEditor: View {
    let title: String
    let placeholder: String
    @Binding var text: String
    let onDone: () -> Void
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            ZStack(alignment: .topLeading) {
                TextEditor(text: $text)
                    .focused($focused)
                    .font(.body)
                    .textInputAutocapitalization(.sentences)
                    .autocorrectionDisabled(false)
                    .padding(16)
                    .scrollContentBackground(.hidden)
                    .background(Color(.systemBackground))

                if text.isEmpty {
                    Text(placeholder)
                        .font(.body)
                        .foregroundStyle(Color(.placeholderText))
                        .padding(.horizontal, 21)
                        .padding(.vertical, 24)
                        .allowsHitTesting(false)
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done", action: onDone)
                        .font(.body.weight(.semibold))
                        .foregroundStyle(Color.reportedOrange)
                }
            }
        }
        .onAppear {
            focused = true
        }
    }
}

struct ReportAddressPickerField: View {
    let value: String
    let isError: Bool
    let onSearch: () -> Void
    let onClear: (() -> Void)?
    let onMap: (() -> Void)?

    var body: some View {
        VStack(alignment: .leading, spacing: reportedFieldLabelSpacing) {
            ReportedFieldLabel(title: "Address", isError: isError)
            HStack(spacing: 10) {
                Button(action: onSearch) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                        Text(value.isEmpty ? "Search address" : value)
                            .foregroundStyle(value.isEmpty ? Color(.placeholderText) : .primary)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if let onClear {
                    Button(action: onClear) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Clear address")
                }
                if let onMap {
                    Button(action: onMap) {
                        Image(systemName: "map")
                            .font(.title3.weight(.semibold))
                            .foregroundStyle(.primary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Show Map")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 11)
            .frame(minHeight: 46)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(.systemBackground))
            .overlay(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .stroke(isError ? Color.red : Color(.separator), lineWidth: 1)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}

struct AutoFitFieldText: View {
    let text: String
    var alignment: Alignment = .leading

    init(_ text: String, alignment: Alignment = .leading) {
        self.text = text
        self.alignment = alignment
    }

    var body: some View {
        Text(text)
            .font(.body)
            .lineLimit(1)
            .minimumScaleFactor(0.55)
            .allowsTightening(true)
            .frame(maxWidth: .infinity, alignment: alignment)
    }
}
