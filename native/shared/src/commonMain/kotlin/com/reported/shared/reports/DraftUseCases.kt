package com.reported.shared.reports

import com.reported.shared.model.ReportDraft

class LoadDraftUseCase(private val repository: DraftRepository) {
    suspend fun execute(): ReportDraft? = repository.load()
}

class SaveDraftUseCase(private val repository: DraftRepository) {
    suspend fun execute(draft: ReportDraft) = repository.save(draft)
}

class ClearDraftUseCase(private val repository: DraftRepository) {
    suspend fun execute() = repository.clear()
}
