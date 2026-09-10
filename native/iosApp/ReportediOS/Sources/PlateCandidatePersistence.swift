import Foundation
import SharedCore
import UIKit

extension ComposerState.PlateCandidate {
    var cornerPointFloats: [KotlinFloat] {
        guard cornerPoints.count >= 4 else { return [] }
        return cornerPoints.prefix(4).flatMap { point in
            [
                KotlinFloat(float: Float(min(1, max(0, point.x)))),
                KotlinFloat(float: Float(min(1, max(0, point.y))))
            ]
        }
    }
}
