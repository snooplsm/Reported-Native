import XCTest
@testable import ReportediOS

final class ComposerSubmissionCompletionTests: XCTestCase {
    @MainActor
    func testMockedSuccessClearsReportAndStopsSpinnerTwice() async throws {
        var state = ComposerState()
        var storedDraft: String? = nil
        var clearCount = 0
        var events: [String] = []
        for index in 1...2 {
            state.plate = "ABC1234"
            state.primaryMedia = .init(fileURL: URL(fileURLWithPath: "/tmp/test.jpg"), displayName: "test.jpg", isVideo: false)
            state.extraMedia = [.init(fileURL: URL(fileURLWithPath: "/tmp/extra.jpg"), displayName: "extra.jpg", isVideo: false)]
            state.stage = .verify
            state.address = "123 Test Street"
            state.addressQuery = state.address
            state.description = "Blocked lane"
            state.notes = "Test notes"
            state.selectedComplaintIds = ["test-complaint"]
            state.latitude = 40.7
            state.longitude = -74
            state.occurredAtIso = "2026-10-12T12:00:00Z"
            state.loading = true
            state.submitProgress = 0.96
            state.submitMessage = "Cleaning up"
            state.error = "Old error"
            storedDraft = state.plate
            let mockedSubmit: () async throws -> String = { "report-\(index)" }
            let objectId = try await mockedSubmit()
            await completeComposerSubmission(objectId: objectId, clearDraft: {
                storedDraft = nil
                clearCount += 1
            }, publish: { freshState, id in
                state = freshState
                events.append(id)
            })
            XCTAssertNil(storedDraft)
            XCTAssertTrue(state.draftLoaded)
            XCTAssertFalse(state.loading)
            XCTAssertNil(state.submitProgress)
            XCTAssertNil(state.submitMessage)
            XCTAssertNil(state.error)
            XCTAssertEqual(state.plate, "")
            XCTAssertEqual(state.address, "")
            XCTAssertEqual(state.addressQuery, "")
            XCTAssertEqual(state.description, "")
            XCTAssertEqual(state.notes, "")
            XCTAssertEqual(state.occurredAtIso, "")
            XCTAssertTrue(state.selectedComplaintIds.isEmpty)
            XCTAssertNil(state.latitude)
            XCTAssertNil(state.longitude)
            XCTAssertNil(state.primaryMedia)
            XCTAssertEqual(state.stage, .pickMedia)
            XCTAssertTrue(state.extraMedia.isEmpty)
        }
        XCTAssertEqual(clearCount, 2)
        XCTAssertEqual(events, ["report-1", "report-2"])
    }
}
