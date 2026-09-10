import Foundation
import FirebaseAnalytics
import FirebaseCrashlytics
import ImageIO
import SharedCore
import UIKit
import UniformTypeIdentifiers

extension ComposerState {
    var isPhiladelphiaSubmission: Bool {
        reportAddressProvider(
            latitude: latitude,
            longitude: longitude,
            address: addressQuery.isEmpty ? address : addressQuery
        ) == .philadelphia
    }
}
