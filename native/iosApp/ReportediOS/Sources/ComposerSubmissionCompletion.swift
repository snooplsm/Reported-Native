import Foundation

/// Shared by the real submission flow and offline success-path regression tests.
@MainActor
func completeComposerSubmission(
    objectId: String,
    clearDraft: () async throws -> Void,
    publish: (ComposerState, String) -> Void
) async {
    // A local storage failure must not leave a successfully submitted report spinning.
    try? await clearDraft()
    publish(ComposerState(draftLoaded: true), objectId)
}
