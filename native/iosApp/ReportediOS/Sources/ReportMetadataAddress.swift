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

func extractSubmissionMetadata(from media: ComposerState.SubmissionMedia) async -> ExtractedSubmissionMetadata {
    if media.isVideo {
        let asset = AVURLAsset(url: media.fileURL)
        let creationDateMetadata = try? await asset.load(.creationDate)
        let creationDate: Date?
        if let creationDateMetadata {
            creationDate = try? await creationDateMetadata.load(.dateValue)
        } else {
            creationDate = nil
        }
        let metadataItems = (try? await asset.load(.metadata)) ?? []
        let locationItem = AVMetadataItem
            .metadataItems(from: metadataItems, filteredByIdentifier: .commonIdentifierLocation)
            .first
        let rawLocation: String?
        if let locationItem {
            rawLocation = try? await locationItem.load(.stringValue)
        } else {
            rawLocation = nil
        }
        let coordinate = parseIso6709Location(rawLocation)
        return ExtractedSubmissionMetadata(
            occurredAtIso: creationDate?.ISO8601Format(),
            latitude: coordinate?.latitude,
            longitude: coordinate?.longitude
        )
    }

    guard let source = CGImageSourceCreateWithURL(media.fileURL as CFURL, nil),
          let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any] else {
        return ExtractedSubmissionMetadata(occurredAtIso: nil, latitude: nil, longitude: nil)
    }

    let exif = properties[kCGImagePropertyExifDictionary] as? [CFString: Any]
    let tiff = properties[kCGImagePropertyTIFFDictionary] as? [CFString: Any]
    let gps = properties[kCGImagePropertyGPSDictionary] as? [CFString: Any]

    var latitude = gps?[kCGImagePropertyGPSLatitude] as? Double
    var longitude = gps?[kCGImagePropertyGPSLongitude] as? Double

    if let latitudeRef = gps?[kCGImagePropertyGPSLatitudeRef] as? String, latitudeRef.uppercased() == "S" {
        latitude = latitude.map(-)
    }
    if let longitudeRef = gps?[kCGImagePropertyGPSLongitudeRef] as? String, longitudeRef.uppercased() == "W" {
        longitude = longitude.map(-)
    }

    return ExtractedSubmissionMetadata(
        occurredAtIso: extractExifDateTimeIso(exif: exif, tiff: tiff, gps: gps),
        latitude: latitude,
        longitude: longitude
    )
}

func parseIso6709Location(_ raw: String?) -> (latitude: Double, longitude: Double)? {
    guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
    let pattern = #"([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)"#
    guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
    let nsRange = NSRange(raw.startIndex..<raw.endIndex, in: raw)
    guard let match = regex.firstMatch(in: raw, range: nsRange), match.numberOfRanges >= 3 else { return nil }

    func group(_ index: Int) -> String? {
        let range = match.range(at: index)
        guard let swiftRange = Range(range, in: raw) else { return nil }
        return String(raw[swiftRange])
    }

    guard let latitude = group(1).flatMap(Double.init),
          let longitude = group(2).flatMap(Double.init),
          isValidReportCoordinate(latitude: latitude, longitude: longitude) else {
        return nil
    }
    return (latitude, longitude)
}

func extractExifDateTimeIso(
    exif: [CFString: Any]?,
    tiff: [CFString: Any]?,
    gps: [CFString: Any]?
) -> String? {
    let candidates: [(CFString, CFString?, CFString?)] = [
        (kCGImagePropertyExifDateTimeOriginal, kCGImagePropertyExifOffsetTimeOriginal, kCGImagePropertyExifSubsecTimeOriginal),
        (kCGImagePropertyExifDateTimeDigitized, kCGImagePropertyExifOffsetTimeDigitized, kCGImagePropertyExifSubsecTimeDigitized),
        (kCGImagePropertyExifDateTimeOriginal, kCGImagePropertyExifOffsetTime, kCGImagePropertyExifSubsecTime),
    ]
    for (dateKey, offsetKey, subsecondKey) in candidates {
        let rawDate = exif?[dateKey] as? String
        let rawOffset = offsetKey.flatMap { exif?[$0] as? String }
        let rawSubsecond = subsecondKey.flatMap { exif?[$0] as? String }
        if let iso = parseExifDateTimeIso(rawDate, offset: rawOffset, subsecond: rawSubsecond) {
            return iso
        }
    }
    if let iso = parseExifDateTimeIso(
        tiff?[kCGImagePropertyTIFFDateTime] as? String,
        offset: nil,
        subsecond: nil
    ) {
        return iso
    }
    return parseExifGpsDateTimeIso(
        dateStamp: gps?[kCGImagePropertyGPSDateStamp] as? String,
        timeStamp: gps?[kCGImagePropertyGPSTimeStamp] as? String
    )
}

func parseExifDateTimeIso(
    _ raw: String?,
    offset rawOffset: String?,
    subsecond rawSubsecond: String?
) -> String? {
    guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    formatter.timeZone = .current
    guard var date = formatter.date(from: raw) else { return nil }
    if let fraction = normalizedSubsecond(rawSubsecond) {
        date = date.addingTimeInterval(fraction)
    }
    if let offset = normalizedExifOffset(rawOffset) {
        let offsetFormatter = DateFormatter()
        offsetFormatter.locale = Locale(identifier: "en_US_POSIX")
        offsetFormatter.dateFormat = "yyyy:MM:dd HH:mm:ssXXXXX"
        offsetFormatter.timeZone = TimeZone(secondsFromGMT: 0)
        if let offsetDate = offsetFormatter.date(from: "\(raw)\(offset)") {
            date = offsetDate.addingTimeInterval(normalizedSubsecond(rawSubsecond) ?? 0)
        }
    }
    return isoStringUTC(from: date)
}

func parseExifGpsDateTimeIso(dateStamp: String?, timeStamp: String?) -> String? {
    guard let dateStamp, let timeStamp else { return nil }
    let raw = "\(dateStamp.trimmingCharacters(in: .whitespacesAndNewlines)) \(timeStamp.trimmingCharacters(in: .whitespacesAndNewlines))"
    let formatter = DateFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.dateFormat = "yyyy:MM:dd HH:mm:ss"
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.date(from: raw).map(isoStringUTC)
}

func normalizedExifOffset(_ value: String?) -> String? {
    guard let value else { return nil }
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.range(of: #"^[+-]\d{2}:\d{2}$"#, options: .regularExpression) != nil {
        return trimmed
    }
    if trimmed.range(of: #"^[+-]\d{4}$"#, options: .regularExpression) != nil {
        let index = trimmed.index(trimmed.startIndex, offsetBy: 3)
        return "\(trimmed[..<index]):\(trimmed[index...])"
    }
    return nil
}

func normalizedSubsecond(_ value: String?) -> TimeInterval? {
    guard let value else { return nil }
    let digits = value.filter(\.isNumber)
    guard !digits.isEmpty, let whole = Double(digits) else { return nil }
    return whole / pow(10, Double(digits.count))
}

func isoStringUTC(from date: Date) -> String {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    return formatter.string(from: date)
}

extension String {
    var photoTimeDisplay: String {
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fallbackIsoFormatter = ISO8601DateFormatter()
        fallbackIsoFormatter.formatOptions = [.withInternetDateTime]
        let date = isoFormatter.date(from: self) ?? fallbackIsoFormatter.date(from: self)
        guard let date else { return self }
        return date.formatted(date: .abbreviated, time: .shortened)
    }
}

func reportAddressProvider(latitude: Double?, longitude: Double?, address: String) -> ReportAddressProvider {
    if let latitude, let longitude, isValidReportCoordinate(latitude: latitude, longitude: longitude) {
        if CityBoundaryIndex.shared.contains(cityId: "philadelphia", latitude: latitude, longitude: longitude) {
            return .philadelphia
        }
        if CityBoundaryIndex.shared.contains(cityId: "nyc", latitude: latitude, longitude: longitude) {
            return .newYorkCity
        }
        return .newYorkCity
    }
    return isPhiladelphiaAddressText(address) ? .philadelphia : .newYorkCity
}

func isValidReportCoordinate(latitude: Double, longitude: Double) -> Bool {
    latitude.isFinite &&
        longitude.isFinite &&
        abs(latitude) <= 90 &&
        abs(longitude) <= 180 &&
        (abs(latitude) > 0.000001 || abs(longitude) > 0.000001)
}

func isPhiladelphiaAddressText(_ address: String) -> Bool {
    let value = address.lowercased()
    if value.contains("philadelphia") || value.contains("phila") || value.contains("philly") {
        return true
    }
    return philadelphiaZipCodes.contains { value.contains($0) }
}

let philadelphiaZipCodes: Set<String> = [
    "19102", "19103", "19104", "19106", "19107", "19111", "19112", "19113",
    "19114", "19115", "19116", "19118", "19119", "19120", "19121", "19122",
    "19123", "19124", "19125", "19126", "19127", "19128", "19129", "19130",
    "19131", "19132", "19133", "19134", "19135", "19136", "19137", "19138",
    "19139", "19140", "19141", "19142", "19143", "19144", "19145", "19146",
    "19147", "19148", "19149", "19150", "19151", "19152", "19153", "19154"
]

func reverseGeocodeAddress(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    let provider = reportAddressProvider(latitude: latitude, longitude: longitude, address: "")
    switch provider {
    case .philadelphia:
        if let suggestion = await reverseGeocodePhiladelphiaAis(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        if let suggestion = await reverseGeocodeNyc(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        return await reverseGeocodePlatform(latitude: latitude, longitude: longitude)
    case .newYorkCity:
        if let suggestion = await reverseGeocodeNyc(latitude: latitude, longitude: longitude) {
            return suggestion
        }
        return await reverseGeocodePlatform(latitude: latitude, longitude: longitude)
    }
}

func searchReportAddresses(
    query: String,
    latitude: Double?,
    longitude: Double?,
    address: String
) async -> [ComposerState.AddressSuggestion] {
    switch reportAddressProvider(latitude: latitude, longitude: longitude, address: address.isEmpty ? query : address) {
    case .philadelphia:
        let suggestions = await searchPhiladelphiaAisAddresses(query: query)
        return suggestions.isEmpty ? await searchNycAddresses(query: query) : suggestions
    case .newYorkCity:
        return await searchNycAddresses(query: query)
    }
}

func reverseGeocodeNyc(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/reverse")
    components?.queryItems = [
        URLQueryItem(name: "point.lat", value: String(latitude)),
        URLQueryItem(name: "point.lon", value: String(longitude)),
        URLQueryItem(name: "size", value: "1")
    ]
    guard let url = components?.url,
          let (data, _) = try? await URLSession.shared.data(from: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let features = json["features"] as? [[String: Any]],
          let first = features.first,
          let properties = first["properties"] as? [String: Any] else {
        return nil
    }
    let label = (properties["label"] as? String)
        ?? (properties["name"] as? String)
        ?? (properties["address"] as? String)
    guard let label else { return nil }
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: "NY",
        providerId: ReportAddressProvider.newYorkCity.id
    )
}

func addressSuggestionFromImageMetadata(_ metadata: ExtractedSubmissionMetadata) async -> ComposerState.AddressSuggestion? {
    guard let latitude = metadata.latitude, let longitude = metadata.longitude else { return nil }
    guard latitude.isFinite, longitude.isFinite else { return nil }
    guard abs(latitude) <= 90, abs(longitude) <= 180 else { return nil }
    guard abs(latitude) > 0.000001 || abs(longitude) > 0.000001 else { return nil }
    let coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    guard CLLocationCoordinate2DIsValid(coordinate) else { return nil }
    return await reverseGeocodeAddress(latitude: latitude, longitude: longitude)
        ?? coordinateAddressSuggestion(latitude: latitude, longitude: longitude)
}

func coordinateAddressSuggestion(latitude: Double, longitude: Double) -> ComposerState.AddressSuggestion {
    ComposerState.AddressSuggestion(
        label: formattedCoordinateText(latitude: latitude, longitude: longitude),
        latitude: latitude,
        longitude: longitude
    )
}

func formattedCoordinateText(latitude: Double, longitude: Double) -> String {
    String(format: "%.5f, %.5f", latitude, longitude)
}

func searchNycAddresses(query: String) async -> [ComposerState.AddressSuggestion] {
    var components = URLComponents(string: "https://geosearch.planninglabs.nyc/v2/search")
    components?.queryItems = [
        URLQueryItem(name: "text", value: query),
        URLQueryItem(name: "size", value: "5")
    ]
    guard let url = components?.url,
          let (data, _) = try? await URLSession.shared.data(from: url),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let features = json["features"] as? [[String: Any]] else {
        return []
    }

    return features.compactMap { feature in
        guard let properties = feature["properties"] as? [String: Any],
              let geometry = feature["geometry"] as? [String: Any],
              let coordinates = geometry["coordinates"] as? [Double],
              coordinates.count >= 2 else {
            return nil
        }
        let label = (properties["label"] as? String)
            ?? (properties["name"] as? String)
            ?? (properties["address"] as? String)
        guard let label else { return nil }
        return ComposerState.AddressSuggestion(
            label: label,
            latitude: coordinates[1],
            longitude: coordinates[0],
            region: "NY",
            providerId: ReportAddressProvider.newYorkCity.id
        )
    }
}

func searchPhiladelphiaAisAddresses(query: String) async -> [ComposerState.AddressSuggestion] {
    let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmed.isEmpty,
          let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed),
          let json = await fetchPhiladelphiaAisJson("https://api.phila.gov/ais/v1/search/\(encoded)"),
          let features = json["features"] as? [[String: Any]] else {
        return []
    }
    return features.compactMap { philadelphiaAisFeatureToSuggestion($0) }
}

func reverseGeocodePhiladelphiaAis(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    guard let json = await fetchPhiladelphiaAisJson(
        "https://api.phila.gov/ais/v1/reverse_geocode/\(longitude),\(latitude)?srid=4326&search_radius=500"
    ),
          let features = json["features"] as? [[String: Any]],
          let first = features.first else {
        return nil
    }
    return philadelphiaAisFeatureToSuggestion(first, fallbackLatitude: latitude, fallbackLongitude: longitude)
}

func fetchPhiladelphiaAisJson(_ urlString: String) async -> [String: Any]? {
    guard let url = URL(string: urlString) else { return nil }
    var request = URLRequest(url: url)
    request.timeoutInterval = 5
    if let key = philadelphiaAisGatekeeperKey() {
        request.setValue("Gatekeeper-Key \(key)", forHTTPHeaderField: "Authorization")
    }
    guard let (data, response) = try? await URLSession.shared.data(for: request),
          let httpResponse = response as? HTTPURLResponse,
          (200...299).contains(httpResponse.statusCode) else {
        return nil
    }
    return try? JSONSerialization.jsonObject(with: data) as? [String: Any]
}

func philadelphiaAisGatekeeperKey() -> String? {
    let environmentValue = ProcessInfo.processInfo.environment["REPORTED_PHILADELPHIA_AIS_GATEKEEPER_KEY"]
    let plistValue = Bundle.main.object(forInfoDictionaryKey: "REPORTED_PHILADELPHIA_AIS_GATEKEEPER_KEY") as? String
    return (environmentValue ?? plistValue)?.nilIfBlank
}

func philadelphiaAisFeatureToSuggestion(
    _ feature: [String: Any],
    fallbackLatitude: Double? = nil,
    fallbackLongitude: Double? = nil
) -> ComposerState.AddressSuggestion? {
    guard let properties = feature["properties"] as? [String: Any] else { return nil }
    let coordinates = (feature["geometry"] as? [String: Any])?["coordinates"] as? [Any]
    let longitude = doubleValue(coordinates?.first) ?? fallbackLongitude
    let latitude = doubleValue((coordinates?.count ?? 0) > 1 ? coordinates?[1] : nil) ?? fallbackLatitude
    guard let latitude, let longitude else { return nil }
    let streetAddress = stringValue(properties["street_address"])
        ?? stringValue(properties["address"])
        ?? stringValue(properties["opa_address"])
    let zipCode = stringValue(properties["zip_code"]) ?? stringValue(properties["zip"])
    var label = streetAddress ?? "Philadelphia address"
    if !label.localizedCaseInsensitiveContains("Philadelphia") {
        label += ", Philadelphia"
    }
    if !label.localizedCaseInsensitiveContains(", PA") {
        label += ", PA"
    }
    if let zipCode, !label.contains(zipCode) {
        label += " \(zipCode)"
    }
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: "PA",
        providerId: ReportAddressProvider.philadelphia.id,
        blockNumber: stringValue(properties["address_low"]) ?? streetAddress?.firstStreetNumber,
        streetName: stringValue(properties["street_full"]),
        zipCode: zipCode
    )
}

func reverseGeocodePlatform(latitude: Double, longitude: Double) async -> ComposerState.AddressSuggestion? {
    let location = CLLocation(latitude: latitude, longitude: longitude)
    guard let placemark = try? await CLGeocoder().reverseGeocodeLocation(location).first else {
        return nil
    }
    let label = [
        placemark.name,
        placemark.locality,
        placemark.administrativeArea,
        placemark.postalCode
    ]
        .compactMap { $0?.nilIfBlank }
        .joined(separator: ", ")
    guard !label.isEmpty else { return nil }
    return ComposerState.AddressSuggestion(
        label: label,
        latitude: latitude,
        longitude: longitude,
        region: placemark.administrativeArea?.uppercased()
    )
}

final class CityBoundaryIndex {
    static let shared = CityBoundaryIndex()

    private let lock = NSLock()
    private var cache: [String: CityBoundary] = [:]
    private var missing: Set<String> = []

    func contains(cityId: String, latitude: Double, longitude: Double) -> Bool {
        guard isValidReportCoordinate(latitude: latitude, longitude: longitude),
              let boundary = boundary(for: cityId) else {
            return false
        }
        return boundary.contains(longitude: longitude, latitude: latitude)
    }

    private func boundary(for cityId: String) -> CityBoundary? {
        lock.lock()
        if let cached = cache[cityId] {
            lock.unlock()
            return cached
        }
        if missing.contains(cityId) {
            lock.unlock()
            return nil
        }
        lock.unlock()

        let loaded = load(cityId: cityId)

        lock.lock()
        if let loaded {
            cache[cityId] = loaded
        } else {
            missing.insert(cityId)
        }
        lock.unlock()
        return loaded
    }

    private func load(cityId: String) -> CityBoundary? {
        let fileName: String
        switch cityId {
        case "philadelphia":
            fileName = "philadelphia"
        case "nyc":
            fileName = "nyc"
        default:
            return nil
        }
        let url = Bundle.main.url(forResource: fileName, withExtension: "geojson", subdirectory: "CityBoundaries")
            ?? Bundle.main.url(forResource: fileName, withExtension: "geojson")
        guard let url,
              let data = try? Data(contentsOf: url),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let features = json["features"] as? [[String: Any]] else {
            return nil
        }
        let bbox = ((json["properties"] as? [String: Any])?["bbox"] as? [Any])?
            .compactMap(doubleValue)
        let polygons = features.flatMap { feature -> [[[BoundaryPoint]]] in
            guard let geometry = feature["geometry"] as? [String: Any] else { return [] }
            return parseBoundaryGeometry(geometry)
        }
        return CityBoundary(bbox: bbox?.count == 4 ? bbox : nil, polygons: polygons)
    }
}

struct CityBoundary {
    let bbox: [Double]?
    let polygons: [[[BoundaryPoint]]]

    func contains(longitude: Double, latitude: Double) -> Bool {
        if let bbox,
           longitude < bbox[0] || latitude < bbox[1] || longitude > bbox[2] || latitude > bbox[3] {
            return false
        }
        return polygons.contains { polygonContains($0, longitude: longitude, latitude: latitude) }
    }
}

struct BoundaryPoint {
    let longitude: Double
    let latitude: Double
}

func parseBoundaryGeometry(_ geometry: [String: Any]) -> [[[BoundaryPoint]]] {
    guard let type = geometry["type"] as? String else { return [] }
    let coordinates = geometry["coordinates"]
    switch type {
    case "Polygon":
        return parseBoundaryPolygon(coordinates).map { [$0] } ?? []
    case "MultiPolygon":
        guard let polygons = coordinates as? [Any] else { return [] }
        return polygons.compactMap(parseBoundaryPolygon)
    default:
        return []
    }
}

func parseBoundaryPolygon(_ value: Any?) -> [[BoundaryPoint]]? {
    guard let rings = value as? [Any] else { return nil }
    let parsed = rings.compactMap { ringValue -> [BoundaryPoint]? in
        guard let points = ringValue as? [Any] else { return nil }
        let ring = points.compactMap { pointValue -> BoundaryPoint? in
            guard let point = pointValue as? [Any],
                  point.count >= 2,
                  let longitude = doubleValue(point[0]),
                  let latitude = doubleValue(point[1]) else {
                return nil
            }
            return BoundaryPoint(longitude: longitude, latitude: latitude)
        }
        return ring.isEmpty ? nil : ring
    }
    return parsed.isEmpty ? nil : parsed
}

func polygonContains(_ polygon: [[BoundaryPoint]], longitude: Double, latitude: Double) -> Bool {
    guard let outer = polygon.first, ringContains(outer, longitude: longitude, latitude: latitude) else {
        return false
    }
    return polygon.dropFirst().allSatisfy { !ringContains($0, longitude: longitude, latitude: latitude) }
}

func ringContains(_ ring: [BoundaryPoint], longitude: Double, latitude: Double) -> Bool {
    guard ring.count >= 3 else { return false }
    var inside = false
    var previous = ring[ring.count - 1]
    for current in ring {
        let intersects = (current.latitude > latitude) != (previous.latitude > latitude) &&
            longitude < (previous.longitude - current.longitude) *
            (latitude - current.latitude) /
            (previous.latitude - current.latitude) +
            current.longitude
        if intersects {
            inside.toggle()
        }
        previous = current
    }
    return inside
}

func doubleValue(_ value: Any?) -> Double? {
    if let value = value as? Double { return value }
    if let value = value as? NSNumber { return value.doubleValue }
    if let value = value as? String { return Double(value) }
    return nil
}

func stringValue(_ value: Any?) -> String? {
    if let value = value as? String {
        return value.nilIfBlank
    }
    if let value = value as? NSNumber {
        return value.stringValue.nilIfBlank
    }
    return nil
}

extension String {
    var firstStreetNumber: String? {
        let pattern = #"^\s*(\d+[A-Za-z]?)"#
        guard let range = range(of: pattern, options: .regularExpression) else { return nil }
        return String(self[range]).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
