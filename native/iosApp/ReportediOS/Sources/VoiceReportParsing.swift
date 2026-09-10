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

func generateVoiceReportDraft(
    transcript: String,
    complaintOptions: [ComplaintOption]
) -> VoiceReportDraft {
    let cleaned = transcript.trimmingCharacters(in: .whitespacesAndNewlines)
    let complaint = inferVoiceComplaint(cleaned, complaintOptions: complaintOptions)
    return VoiceReportDraft(
        complaintId: complaint?.id,
        complaintTitle: complaint?.title,
        plate: inferVoicePlate(cleaned),
        plateRegion: inferVoicePlateRegion(cleaned),
        address: inferVoiceAddress(cleaned),
        occurredAtIso: inferVoiceOccurredAt(cleaned),
        make: inferVoiceVehicleMake(cleaned),
        model: inferVoiceVehicleModel(cleaned),
        yearRange: inferVoiceVehicleYearRange(cleaned),
        description: inferVoiceDescription(cleaned),
        notes: inferVoiceNotes(cleaned)
    )
}

func buildVoiceReportGemmaPrompt(
    complaintOptions: [ComplaintOption],
    voiceContext: VoiceReportContext
) -> String {
    let complaints = complaintOptions
        .map { "- \($0.id): \($0.title)" }
        .joined(separator: "\n")
    let contextBlock = voiceContext.promptBlock
    let now = Date()
    let currentYear = Calendar.current.component(.year, from: now)
    let currentDateFormatter = DateFormatter()
    currentDateFormatter.locale = Locale(identifier: "en_US_POSIX")
    currentDateFormatter.dateFormat = "yyyy-MM-dd"
    let currentDate = currentDateFormatter.string(from: now)
    return """
    You are filling a Reported traffic complaint form from one spoken audio recording and an optional attached report photo.
    Transcribe the speech, then return only one strict JSON object.
    Do not include markdown or prose.

    Available complaints:
    \(complaints)

    Current form and device context:
    \(contextBlock)

    JSON keys:
    {
      "transcript": string or null,
      "complaint": one available complaint title or null,
      "complaintId": one available complaint ID or null,
      "timeofincident": ISO-8601 incident datetime with timezone or null,
      "occurredAtIso": same value as timeofincident or null,
      "plate": uppercase license plate letters/numbers only, max 8 characters, or null,
      "state": two-letter US plate state, default "NY" only when the speaker implies New York or says no state,
      "address": incident address or null,
      "make": vehicle make, for example "Honda" from "2024 Honda Acura", or null,
      "model": vehicle model, for example "Acura" from "2024 Honda Acura", or null,
      "yearRange": vehicle year or spoken year range, for example "2024" or "2021-2024", or null,
      "description": vehicle description and public-facing incident details or null,
      "notes": extra private details that do not fit another field or null
    }

    Rules:
    - Prefer exact spoken values over guesses.
    - If a report photo is attached, use it as supporting visual context for visible plate text, vehicle details, location clues, and complaint type before producing JSON.
    - If imageVisualContext is present, treat it as a pre-read summary of the attached photo.
    - Spoken values win when audio conflicts with the photo. Do not invent fields from the photo unless they are clearly visible.
    - The current date is \(currentDate) and the current year is \(currentYear). For spoken dates without an explicit year: if the current month is January and the spoken incident month is December, use \(currentYear - 1). Otherwise, use \(currentYear).
    - timeofincident and occurredAtIso must use that month/year rule; do not roll non-December dates back to a previous year.
    - Use current context only when the speaker explicitly refers to it, such as "here", "this location", "current address", "same address", "now", "today", "same time as the photo", "the image time", "same plate", or "keep the state".
    - If the speaker says "here" or "current address", use currentAddress when present; otherwise use imageAddress when present; otherwise use currentCoordinates when present; otherwise use null.
    - If the speaker says "now" or gives a relative time, resolve it against currentDeviceTimeIso.
    - If the speaker says "time from the photo" or "same time as the photo", use imageOccurredAtIso when present.
    - If a field was not spoken, use null.
    - Extract vehicle make, model, and yearRange only when the speaker says them. Do not infer trim, color, or vehicle type into these keys.
    - If the speaker says a phrase like "2024 Honda Acura", set yearRange to "2024", make to "Honda", and model to "Acura".
    - Put any extra information in notes.
    - Pick complaintId only from the available IDs and complaint only from the available titles.
    """
}

func parseVoiceReportGemmaJson(
    _ jsonText: String,
    complaintOptions: [ComplaintOption]
) throws -> VoiceReportGemmaResult {
    let objectText = try extractFirstJsonObject(from: jsonText)
    let data = Data(objectText.utf8)
    guard let object = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
        throw VoiceReportGemmaError.message("Gemma did not return a JSON object.")
    }
    let transcript = nullableVoiceString(object["transcript"])
    let transcriptFallback = transcript.map {
        generateVoiceReportDraft(transcript: $0, complaintOptions: complaintOptions)
    }
    let complaintId = resolveVoiceComplaintId(
        rawComplaintId: nullableVoiceString(object["complaintId"]),
        rawComplaint: nullableVoiceString(object["complaint"]),
        complaintOptions: complaintOptions
    )
        ?? transcriptFallback?.complaintId
    let complaintTitle = complaintOptions.first(where: { $0.id == complaintId })?.title
    let plate = nullableVoiceString(object["plate"])
        .map(normalizeVoicePlateText)
        .flatMap { $0.isEmpty ? nil : $0 }
        ?? transcriptFallback?.plate
    let draft = VoiceReportDraft(
        complaintId: complaintId,
        complaintTitle: complaintTitle,
        plate: plate,
        plateRegion: nullableVoiceString(object["state"]).map { String($0.uppercased().prefix(2)) } ?? transcriptFallback?.plateRegion,
        address: nullableVoiceString(object["address"]) ?? transcriptFallback?.address,
        occurredAtIso: sanitizeVoiceOccurredAt(
            nullableVoiceString(object["occurredAtIso"])
                ?? nullableVoiceString(object["timeofincident"])
                ?? nullableVoiceString(object["timeOfIncident"])
                ?? nullableVoiceString(object["time_of_incident"])
        ) ?? transcriptFallback?.occurredAtIso,
        make: nullableVoiceString(object["make"])
            ?? nullableVoiceString(object["vehicleMake"])
            ?? nullableVoiceString(object["vehicle_make"])
            ?? transcriptFallback?.make,
        model: nullableVoiceString(object["model"])
            ?? nullableVoiceString(object["vehicleModel"])
            ?? nullableVoiceString(object["vehicle_model"])
            ?? transcriptFallback?.model,
        yearRange: nullableVoiceString(object["yearRange"])
            ?? nullableVoiceString(object["vehicleYearRange"])
            ?? nullableVoiceString(object["vehicle_year_range"])
            ?? nullableVoiceString(object["year"])
            ?? transcriptFallback?.yearRange,
        description: nullableVoiceString(object["description"])
            ?? transcript.flatMap { extractVoiceSection($0, label: "description") },
        notes: nullableVoiceString(object["notes"]) ?? transcriptFallback?.notes
    )
    return VoiceReportGemmaResult(
        transcript: transcript,
        draft: draft
    )
}

func resolveVoiceReportAddress(_ result: VoiceReportGemmaResult) async -> VoiceReportGemmaResult {
    guard let rawAddress = result.draft.address?.trimmingCharacters(in: .whitespacesAndNewlines),
          !rawAddress.isEmpty else {
        return result
    }
    guard let resolvedAddress = await resolveVoiceAddressLabel(rawAddress),
          resolvedAddress.caseInsensitiveCompare(rawAddress) != .orderedSame else {
        return result
    }
    return VoiceReportGemmaResult(
        transcript: result.transcript,
        draft: result.draft.replacingAddress(resolvedAddress)
    )
}

func resolveVoiceAddressLabel(_ rawAddress: String) async -> String? {
    if let coordinate = parseVoiceCoordinate(rawAddress) {
        return await reverseGeocodeAddress(latitude: coordinate.latitude, longitude: coordinate.longitude)?.label
            ?? formattedCoordinateText(latitude: coordinate.latitude, longitude: coordinate.longitude)
    }
    for query in voiceAddressSearchQueries(rawAddress) {
        let suggestions = await searchReportAddresses(
            query: query,
            latitude: nil,
            longitude: nil,
            address: rawAddress
        )
        if let chosen = chooseVoiceAddressSuggestion(rawAddress: rawAddress, suggestions: suggestions) {
            return chosen.label
        }
    }
    return nil
}

func parseVoiceCoordinate(_ rawAddress: String) -> (latitude: Double, longitude: Double)? {
    guard let match = firstVoiceRegexMatch(
        in: rawAddress,
        pattern: #"^\s*(-?\d{1,2}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)\s*$"#
    ),
          let latitudeText = voiceRegexGroup(in: rawAddress, match: match, group: 1),
          let longitudeText = voiceRegexGroup(in: rawAddress, match: match, group: 2),
          let latitude = Double(latitudeText),
          let longitude = Double(longitudeText),
          abs(latitude) <= 90,
          abs(longitude) <= 180 else {
        return nil
    }
    return (latitude, longitude)
}

func voiceAddressSearchQueries(_ rawAddress: String) -> [String] {
    let cleaned = cleanVoiceAddressQuery(rawAddress)
    var variants: [String] = []
    func add(_ value: String?) {
        guard let value else { return }
        let normalized = value
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        guard normalized.count >= 4 else { return }
        if !variants.contains(where: { $0.caseInsensitiveCompare(normalized) == .orderedSame }) {
            variants.append(normalized)
        }
    }
    add(cleaned)
    if let match = firstVoiceRegexMatch(
        in: cleaned,
        pattern: #"\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\s+(.+)$"#
    ),
       let houseNumber = voiceRegexGroup(in: cleaned, match: match, group: 1),
       let streetText = voiceRegexGroup(in: cleaned, match: match, group: 2) {
        let street = voiceAddressPrefixBeforeStopWords(streetText)
            .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
        add("\(houseNumber) \(street)")
    }
    return variants
}

func cleanVoiceAddressQuery(_ rawAddress: String) -> String {
    rawAddress
        .replacingOccurrences(
            of: #"\b(?:address\s+is|address|near|at)\b"#,
            with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        .replacingOccurrences(
            of: #"\b(?:new\s+york|nyc|ny|usa|united\s+states)\b"#,
            with: " ",
            options: [.regularExpression, .caseInsensitive]
        )
        .replacingOccurrences(of: #"[^A-Za-z0-9\s-]"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .trimmingCharacters(in: .whitespacesAndNewlines)
}

func voiceAddressPrefixBeforeStopWords(_ value: String) -> String {
    guard let range = value.range(
        of: #"\b(?:new\s+york|ny|usa|united\s+states|apt|apartment|unit|floor|fl)\b"#,
        options: [.regularExpression, .caseInsensitive]
    ) else {
        return value
    }
    return String(value[..<range.lowerBound])
}

func chooseVoiceAddressSuggestion(
    rawAddress: String,
    suggestions: [ComposerState.AddressSuggestion]
) -> ComposerState.AddressSuggestion? {
    guard !suggestions.isEmpty else { return nil }
    let expectedHouse = firstVoiceRegexGroup(
        in: rawAddress,
        pattern: #"\b(\d{1,6}(?:-\d{1,6})?[A-Za-z]?)\b"#
    )?.lowercased()
    let rawTokens = cleanVoiceAddressQuery(rawAddress)
        .lowercased()
        .components(separatedBy: CharacterSet.alphanumerics.inverted)
        .filter { $0.count >= 3 && !$0.allSatisfy(\.isNumber) }

    func score(_ suggestion: ComposerState.AddressSuggestion) -> Int {
        let label = suggestion.label.lowercased()
        var value = 0
        if let expectedHouse, label.contains(expectedHouse) { value += 6 }
        value += rawTokens.filter { label.contains($0) }.count
        return value
    }
    let chosen = suggestions.max { score($0) < score($1) }
    guard let chosen else { return nil }
    return score(chosen) >= (expectedHouse == nil ? 1 : 6) ? chosen : nil
}

func extractFirstJsonObject(from text: String) throws -> String {
    guard let start = text.firstIndex(of: "{"), let end = text.lastIndex(of: "}"), start < end else {
        throw VoiceReportGemmaError.message("Gemma did not return a JSON object.")
    }
    return String(text[start...end])
}

func nullableVoiceString(_ value: Any?) -> String? {
    guard let string = value as? String else { return nil }
    let cleaned = string.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !cleaned.isEmpty, cleaned.lowercased() != "null" else { return nil }
    return cleaned
}

let voicePlateMaxLength = 8

func resolveVoiceComplaintId(
    rawComplaintId: String?,
    rawComplaint: String?,
    complaintOptions: [ComplaintOption]
) -> String? {
    if let rawComplaintId,
       complaintOptions.contains(where: { $0.id == rawComplaintId }) {
        return rawComplaintId
    }
    guard let rawComplaint else { return nil }
    let normalizedComplaint = normalizeVoiceComplaintText(rawComplaint)
    guard !normalizedComplaint.isEmpty else { return nil }
    return complaintOptions.first { option in
        option.id.caseInsensitiveCompare(rawComplaint) == .orderedSame ||
            normalizeVoiceComplaintText(option.title) == normalizedComplaint
    }?.id
        ?? inferVoiceComplaint(normalizedComplaint, complaintOptions: complaintOptions)?.id
}

func normalizeVoiceComplaintText(_ value: String) -> String {
    value
        .lowercased()
        .components(separatedBy: CharacterSet.alphanumerics.inverted)
        .filter { !$0.isEmpty }
        .joined(separator: " ")
}

func sanitizeVoiceOccurredAt(_ rawValue: String?) -> String? {
    guard let cleaned = rawValue?.trimmingCharacters(in: .whitespacesAndNewlines),
          !cleaned.isEmpty,
          cleaned.lowercased() != "null" else {
        return nil
    }
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let date = formatter.date(from: cleaned) ?? ISO8601DateFormatter().date(from: cleaned)
    guard let date else { return cleaned }
    return voiceIsoString(from: normalizeVoiceIncidentYear(for: date))
}

func normalizeVoiceIncidentYear(for date: Date) -> Date {
    let calendar = Calendar.current
    let now = Date()
    let currentYear = calendar.component(.year, from: now)
    let currentMonth = calendar.component(.month, from: now)
    let incidentMonth = calendar.component(.month, from: date)
    let targetYear = currentMonth == 1 && incidentMonth == 12 ? currentYear - 1 : currentYear
    var components = calendar.dateComponents([.month, .day, .hour, .minute, .second, .nanosecond], from: date)
    components.year = targetYear
    return calendar.date(from: components) ?? date
}

func normalizeVoicePlateText(_ raw: String) -> String {
    String(
        raw
            .uppercased()
            .filter { $0.isLetter || $0.isNumber }
            .prefix(voicePlateMaxLength)
    )
}

func inferVoiceComplaint(
    _ transcript: String,
    complaintOptions: [ComplaintOption]
) -> ComplaintOption? {
    let normalized = transcript.lowercased()
    let aliases: [([String], [String])] = [
        (["blocked bike lane", "bike lane"], ["bike", "lane"]),
        (["blocked crosswalk", "crosswalk"], ["crosswalk"]),
        (["ran red light", "red light", "stop sign"], ["red", "light"]),
        (["parked illegally", "illegal parking"], ["park"]),
        (["reckless driving", "reckless"], ["reckless"])
    ]
    for (phrases, optionTerms) in aliases {
        if phrases.contains(where: { normalized.contains($0) }) {
            if let option = complaintOptions.first(where: { option in
                let haystack = "\(option.id) \(option.title)".lowercased()
                return optionTerms.allSatisfy { haystack.contains($0) }
            }) {
                return option
            }
        }
    }
    return complaintOptions.first { option in
        let words = option.title
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { $0.count > 2 }
        return !words.isEmpty && words.allSatisfy { normalized.contains($0) }
    }
}

func inferVoicePlate(_ transcript: String) -> String? {
    let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:license\s+plate|plate|tag)\s*(?:is|number|#|:)?\s*([a-z0-9][a-z0-9 -]{1,12}?)(?=\s+(?:state|address|complaint|description|notes|time|at|near)\b|[.,;]|$)"#
    )
    let fallback = allVoiceRegexGroups(in: transcript, pattern: #"\b([a-z0-9]{5,10})\b"#)
        .first { token in
            token.contains { $0.isNumber } && token.contains { $0.isLetter }
        }
    let raw = explicit ?? fallback ?? ""
    let normalized = raw
        .uppercased()
        .filter { $0.isLetter || $0.isNumber }
        .prefix(voicePlateMaxLength)
    let plate = String(normalized)
    return plate.isEmpty ? nil : plate
}

func inferVoicePlateRegion(_ transcript: String) -> String? {
    let normalized = transcript.lowercased()
    let explicit = firstVoiceRegexGroup(
        in: normalized,
        pattern: #"\b(?:state|plate\s+state)\s*(?:is|:)?\s*([a-z]{2}|new york|new jersey|connecticut|pennsylvania)\b"#
    )
    let state = explicit ?? {
        if normalized.contains("new york") { return "new york" }
        if normalized.contains("new jersey") { return "new jersey" }
        if normalized.contains("connecticut") { return "connecticut" }
        if normalized.contains("pennsylvania") { return "pennsylvania" }
        return nil
    }()
    switch state?.trimmingCharacters(in: .whitespacesAndNewlines) {
    case "new york", "ny":
        return "NY"
    case "new jersey", "nj":
        return "NJ"
    case "connecticut", "ct":
        return "CT"
    case "pennsylvania", "pa":
        return "PA"
    default:
        let upper = state?.uppercased()
        return upper?.count == 2 ? upper : nil
    }
}

func inferVoiceAddress(_ transcript: String) -> String? {
    guard let match = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:address\s+is|address|near|at)\s+(.+)$"#
    ) else {
        return nil
    }
    let candidate = voicePrefixBeforeLabels(match)
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
    if firstVoiceRegexGroup(in: candidate, pattern: #"^(\d{1,2}(?::\d{2})?\s*(?:am|pm).*)$"#) != nil {
        return nil
    }
    return candidate.count >= 4 ? candidate : nil
}

func inferVoiceOccurredAt(_ transcript: String) -> String? {
    let normalized = transcript.lowercased()
    if normalized.contains("right now") || normalized.contains("now") {
        return voiceIsoString(from: Date())
    }

    var date = Date()
    if normalized.contains("yesterday"), let yesterday = Calendar.current.date(byAdding: .day, value: -1, to: date) {
        date = yesterday
    }

    if let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b(?:at\s*)?(\d{1,2})(?::(\d{2}))?\s*(a\.?m\.?|p\.?m\.?)\b"#
    ) {
        let hourText = voiceRegexGroup(in: transcript, match: match, group: 1)
        let minuteText = voiceRegexGroup(in: transcript, match: match, group: 2)
        let meridiem = voiceRegexGroup(in: transcript, match: match, group: 3)?.lowercased() ?? ""
        let rawHour = Int(hourText ?? "") ?? 0
        let minute = Int(minuteText ?? "") ?? 0
        let isPm = meridiem.hasPrefix("p")
        let hour: Int
        if isPm && rawHour < 12 {
            hour = rawHour + 12
        } else if !isPm && rawHour == 12 {
            hour = 0
        } else {
            hour = rawHour
        }

        var components = Calendar.current.dateComponents([.year, .month, .day], from: date)
        components.hour = hour
        components.minute = minute
        components.second = 0
        if let incidentDate = Calendar.current.date(from: components) {
            return voiceIsoString(from: incidentDate)
        }
    }

    if normalized.contains("today") || normalized.contains("yesterday") {
        return voiceIsoString(from: Calendar.current.startOfDay(for: date))
    }
    return nil
}

func inferVoiceVehicleYearRange(_ transcript: String) -> String? {
    guard let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b((?:19|20)\d{2})(?:\s*(?:-|to|through)\s*((?:19|20)\d{2}))?\b"#
    ),
          let firstYear = voiceRegexGroup(in: transcript, match: match, group: 1) else {
        return nil
    }
    if let secondYear = voiceRegexGroup(in: transcript, match: match, group: 2) {
        return "\(firstYear)-\(secondYear)"
    }
    return firstYear
}

func inferVoiceVehicleMake(_ transcript: String) -> String? {
    if let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:make|vehicle\s+make)\s*(?:is|:)?\s*([A-Za-z][A-Za-z -]{1,28}?)(?=\s+(?:model|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)"#
    ) {
        return voiceVehicleToken(explicit)
    }
    return voiceVehicleMakeModelPhrase(transcript)?.make
}

func inferVoiceVehicleModel(_ transcript: String) -> String? {
    if let explicit = firstVoiceRegexGroup(
        in: transcript,
        pattern: #"\b(?:model|vehicle\s+model)\s*(?:is|:)?\s*([A-Za-z0-9][A-Za-z0-9 -]{1,32}?)(?=\s+(?:make|year|plate|state|address|complaint|description|notes|time)\b|[.,;]|$)"#
    ) {
        return voiceVehicleToken(explicit)
    }
    return voiceVehicleMakeModelPhrase(transcript)?.model
}

func voiceVehicleMakeModelPhrase(_ transcript: String) -> (make: String, model: String)? {
    guard let match = firstVoiceRegexMatch(
        in: transcript,
        pattern: #"\b(?:19|20)\d{2}(?:\s*(?:-|to|through)\s*(?:19|20)\d{2})?\s+([A-Za-z][A-Za-z-]+)\s+([A-Za-z][A-Za-z0-9-]+)\b"#
    ),
          let makeText = voiceRegexGroup(in: transcript, match: match, group: 1),
          let modelText = voiceRegexGroup(in: transcript, match: match, group: 2),
          let make = voiceVehicleToken(makeText),
          let model = voiceVehicleToken(modelText) else {
        return nil
    }
    return (make, model)
}

func voiceVehicleToken(_ raw: String) -> String? {
    let cleaned = raw
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;:"))
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    guard !cleaned.isEmpty else { return nil }
    return cleaned
        .split(separator: " ")
        .map { token in
            let tokenText = String(token)
            if tokenText.count <= 4, tokenText.uppercased() == tokenText {
                return tokenText
            }
            return tokenText.prefix(1).uppercased() + tokenText.dropFirst().lowercased()
        }
        .joined(separator: " ")
}

func inferVoiceDescription(_ transcript: String) -> String? {
    let value = extractVoiceSection(transcript, label: "description") ?? transcript.trimmingCharacters(in: .whitespacesAndNewlines)
    let limited = String(value.prefix(280))
    return limited.isEmpty ? nil : limited
}

func inferVoiceNotes(_ transcript: String) -> String? {
    extractVoiceSection(transcript, label: "notes") ?? extractVoiceSection(transcript, label: "note")
}

func extractVoiceSection(_ transcript: String, label: String) -> String? {
    let pattern = "\\b\(NSRegularExpression.escapedPattern(for: label))\\s*(?:are|is|:)?\\s+(.+)$"
    guard let match = firstVoiceRegexGroup(in: transcript, pattern: pattern) else { return nil }
    let value = voicePrefixBeforeLabels(match)
        .trimmingCharacters(in: CharacterSet(charactersIn: " ,.;"))
    return value.isEmpty ? nil : value
}

func voicePrefixBeforeLabels(_ value: String) -> String {
    let pattern = #"\b(?:plate|license\s+plate|state|complaint|description|notes|address|time|when|occurred)\b"#
    guard let range = value.range(of: pattern, options: [.regularExpression, .caseInsensitive]) else {
        return value
    }
    return String(value[..<range.lowerBound])
}

func firstVoiceRegexGroup(in text: String, pattern: String, group: Int = 1) -> String? {
    guard let match = firstVoiceRegexMatch(in: text, pattern: pattern) else { return nil }
    return voiceRegexGroup(in: text, match: match, group: group)
}

func allVoiceRegexGroups(in text: String, pattern: String, group: Int = 1) -> [String] {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
    let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.matches(in: text, options: [], range: nsRange).compactMap { match in
        voiceRegexGroup(in: text, match: match, group: group)
    }
}

func firstVoiceRegexMatch(in text: String, pattern: String) -> NSTextCheckingResult? {
    guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return nil }
    let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
    return regex.firstMatch(in: text, options: [], range: nsRange)
}

func voiceRegexGroup(in text: String, match: NSTextCheckingResult, group: Int) -> String? {
    guard match.numberOfRanges > group else { return nil }
    let nsRange = match.range(at: group)
    guard nsRange.location != NSNotFound, let range = Range(nsRange, in: text) else { return nil }
    let value = String(text[range]).trimmingCharacters(in: .whitespacesAndNewlines)
    return value.isEmpty ? nil : value
}

func voiceIsoString(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter.string(from: date)
}

#if DEBUG
@MainActor
func runReportedVoiceSmokeTestIfRequested() async {
    let arguments = ProcessInfo.processInfo.arguments
    guard arguments.contains("--reported-voice-smoke-test") else { return }

    let fileManager = FileManager.default
    guard let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first else {
        return
    }

    let audioURL = arguments
        .first { $0.hasPrefix("--reported-voice-smoke-audio=") }
        .flatMap { argument -> URL? in
            let path = String(argument.dropFirst("--reported-voice-smoke-audio=".count))
            guard !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return URL(fileURLWithPath: path)
        }
        ?? documentsURL.appendingPathComponent("reported-voice-smoke.wav")
    let resultURL = arguments
        .first { $0.hasPrefix("--reported-voice-smoke-result=") }
        .flatMap { argument -> URL? in
            let path = String(argument.dropFirst("--reported-voice-smoke-result=".count))
            guard !path.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
            return URL(fileURLWithPath: path)
        }
        ?? documentsURL.appendingPathComponent("reported-voice-smoke-result.json")

    func write(_ object: [String: Any]) {
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted, .sortedKeys]) else {
            return
        }
        try? data.write(to: resultURL, options: .atomic)
    }

    guard fileManager.fileExists(atPath: audioURL.path) else {
        write([
            "status": "error",
            "error": "Smoke audio file was not found.",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
        ])
        return
    }

    write([
        "status": "running",
        "audioPath": audioURL.path,
        "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled()
    ])

    do {
        var state = ComposerState()
        state.latitude = 40.7128
        state.longitude = -74.0060
        let context = VoiceReportContext(
            state: state,
            complaintOptions: complaintOptionsList,
            imageAddressSuggestion: nil
        )
        let result = try await OnDeviceGemmaVoiceDraftEngine.generateDraft(
            audioURL: audioURL,
            complaintOptions: complaintOptionsList,
            voiceContext: context
        )
        write([
            "status": "success",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled(),
            "transcript": result.transcript ?? NSNull(),
            "draft": voiceSmokeJsonObject(from: result.draft)
        ])
    } catch {
        write([
            "status": "error",
            "audioPath": audioURL.path,
            "modelInstalled": OnDeviceGemmaVoiceDraftEngine.isModelInstalled(),
            "error": error.localizedDescription
        ])
    }
}

func voiceSmokeJsonObject(from draft: VoiceReportDraft) -> [String: Any] {
    [
        "complaint": draft.complaintTitle ?? NSNull(),
        "complaintId": draft.complaintId ?? NSNull(),
        "plate": draft.plate ?? NSNull(),
        "state": draft.plateRegion ?? NSNull(),
        "address": draft.address ?? NSNull(),
        "timeofincident": draft.occurredAtIso ?? NSNull(),
        "occurredAtIso": draft.occurredAtIso ?? NSNull(),
        "make": draft.make ?? NSNull(),
        "model": draft.model ?? NSNull(),
        "yearRange": draft.yearRange ?? NSNull(),
        "description": draft.description ?? NSNull(),
        "notes": draft.notes ?? NSNull()
    ]
}
#endif
