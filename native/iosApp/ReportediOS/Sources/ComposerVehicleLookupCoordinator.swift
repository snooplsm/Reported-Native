import Foundation
import SharedCore

private let vehicleClassificationDebugPrefix = "[DEBUG] Vehicle classification"

@MainActor
final class ComposerVehicleLookupCoordinator {
    typealias StateMutation = (inout ComposerState) -> Void

    private let currentState: () -> ComposerState
    private let updateState: (StateMutation) -> Void
    private let onPersistDraft: () -> Void
    private var classificationDebugTask: Task<Void, Never>?
    private var detailsLookupTask: Task<Void, Never>?

    init(
        currentState: @escaping () -> ComposerState,
        updateState: @escaping (StateMutation) -> Void,
        onPersistDraft: @escaping () -> Void
    ) {
        self.currentState = currentState
        self.updateState = updateState
        self.onPersistDraft = onPersistDraft
    }

    deinit {
        classificationDebugTask?.cancel()
        detailsLookupTask?.cancel()
    }

    func refresh() {
        refreshVehicleDetailsLookup()
        refreshVehicleClassificationDebugNote()
    }

    var currentLookupKey: String? {
        vehicleLookupKey(for: currentState())
    }

    private func refreshVehicleClassificationDebugNote() {
        #if DEBUG
        let normalizedPlate = ComposerViewModel.normalizedPlateInput(currentState().plate)
        guard normalizedPlate.count >= 2,
              normalizedPlate.count <= 10,
              normalizedPlate.hasPrefix("T") || normalizedPlate.hasPrefix("Y") else {
            applyVehicleClassificationDebugNote(nil)
            return
        }
        classificationDebugTask?.cancel()
        classificationDebugTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 350_000_000)
            guard let self, !Task.isCancelled else { return }
            let debugNote = try? await SharedBridge.shared.container.previewVehicleEnrichmentDebugNoteUseCase.execute(
                plate: normalizedPlate
            )
            await MainActor.run { [weak self] in
                guard let self,
                      !Task.isCancelled,
                      ComposerViewModel.normalizedPlateInput(self.currentState().plate) == normalizedPlate else {
                    return
                }
                self.applyVehicleClassificationDebugNote(debugNote)
            }
        }
        #endif
    }

    private func vehicleLookupKey(for state: ComposerState) -> String? {
        let plate = ComposerViewModel.normalizedPlateInput(state.plate)
        let region = state.plateRegion.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard plate.count >= 2,
              plate.count <= 10,
              region.count == 2,
              region.allSatisfy(\.isLetter) else {
            return nil
        }
        return "\(region):\(plate)"
    }

    private func refreshVehicleDetailsLookup() {
        detailsLookupTask?.cancel()
        let snapshot = currentState()
        if let previousDetails = snapshot.vehicleLookupDetails {
            updateState { state in
                state.philadelphiaMobilityAccessDetails = Self.clearingLookupPrefill(
                    state.philadelphiaMobilityAccessDetails,
                    details: previousDetails
                )
            }
        }
        guard let lookupKey = vehicleLookupKey(for: currentState()) else {
            updateState { state in
                state.vehicleLookupDetails = nil
                state.vehicleLookupInFlight = false
                state.vehicleLookupMessage = nil
            }
            return
        }
        let components = lookupKey.split(separator: ":", maxSplits: 1).map(String.init)
        guard components.count == 2 else { return }
        let lookupState = components[0]
        let lookupPlate = components[1]

        updateState { state in
            state.vehicleLookupDetails = nil
            state.vehicleLookupInFlight = true
            state.vehicleLookupMessage = nil
        }
        detailsLookupTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 500_000_000)
            guard let self, !Task.isCancelled else { return }
            do {
                let details = try await SharedBridge.shared.container.lookupVehicleDetailsUseCase.execute(
                    plate: lookupPlate,
                    licenseState: lookupState
                )
                await MainActor.run { [weak self] in
                    guard let self,
                          !Task.isCancelled,
                          self.vehicleLookupKey(for: self.currentState()) == lookupKey else {
                        return
                    }
                    self.updateState { state in
                        state.vehicleLookupDetails = details
                        state.vehicleLookupInFlight = false
                        state.vehicleLookupMessage = nil
                        if let details {
                            state.philadelphiaMobilityAccessDetails = Self.prefilling(
                                state.philadelphiaMobilityAccessDetails,
                                from: details
                            )
                        }
                    }
                    if details != nil {
                        self.onPersistDraft()
                    }
                }
            } catch {
                await MainActor.run { [weak self] in
                    guard let self,
                          !Task.isCancelled,
                          self.vehicleLookupKey(for: self.currentState()) == lookupKey else {
                        return
                    }
                    self.updateState { state in
                        state.vehicleLookupDetails = nil
                        state.vehicleLookupInFlight = false
                        state.vehicleLookupMessage = "Vehicle details are unavailable right now. You can still submit the report."
                    }
                }
            }
        }
    }

    private func applyVehicleClassificationDebugNote(_ debugNote: String?) {
        #if DEBUG
        updateState { state in
            let existingLines = state.notes
                .components(separatedBy: .newlines)
                .filter { !$0.hasPrefix(vehicleClassificationDebugPrefix) }
            let nextLines = existingLines + [debugNote]
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty }
            state.notes = nextLines.joined(separator: "\n")
        }
        onPersistDraft()
        #endif
    }

    private static func prefilling(
        _ current: PhiladelphiaMobilityAccessDetails,
        from details: VehicleLookupDetails
    ) -> PhiladelphiaMobilityAccessDetails {
        PhiladelphiaMobilityAccessDetails(
            blockNumber: current.blockNumber,
            streetName: current.streetName,
            zipCode: current.zipCode,
            vehicleMake: current.vehicleMake.isEmpty ? (details.vehicleMake ?? "") : current.vehicleMake,
            vehicleModel: current.vehicleModel.isEmpty ? (details.vehicleModel ?? "") : current.vehicleModel,
            bodyStyle: current.bodyStyle.isEmpty ? (details.vehicleBody ?? "") : current.bodyStyle,
            vehicleColor: current.vehicleColor,
            violationObserved: current.violationObserved,
            frequency: current.frequency
        )
    }

    private static func clearingLookupPrefill(
        _ current: PhiladelphiaMobilityAccessDetails,
        details: VehicleLookupDetails
    ) -> PhiladelphiaMobilityAccessDetails {
        PhiladelphiaMobilityAccessDetails(
            blockNumber: current.blockNumber,
            streetName: current.streetName,
            zipCode: current.zipCode,
            vehicleMake: current.vehicleMake.caseInsensitiveCompare(details.vehicleMake ?? "") == .orderedSame
                ? ""
                : current.vehicleMake,
            vehicleModel: current.vehicleModel.caseInsensitiveCompare(details.vehicleModel ?? "") == .orderedSame
                ? ""
                : current.vehicleModel,
            bodyStyle: current.bodyStyle.caseInsensitiveCompare(details.vehicleBody ?? "") == .orderedSame
                ? ""
                : current.bodyStyle,
            vehicleColor: current.vehicleColor,
            violationObserved: current.violationObserved,
            frequency: current.frequency
        )
    }
}
