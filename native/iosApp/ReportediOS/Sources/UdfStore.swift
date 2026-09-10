import Combine
import Foundation

/// The single state/action boundary used by iOS features.
///
/// Views render `state` and send user or system events through `onAction`.
/// Implementations own all feature-state mutation and side effects.
@MainActor
protocol UdfStore: ObservableObject {
    associatedtype State
    associatedtype Action
    associatedtype ActionResult

    var state: State { get }
    func onAction(_ action: Action) -> ActionResult
}
