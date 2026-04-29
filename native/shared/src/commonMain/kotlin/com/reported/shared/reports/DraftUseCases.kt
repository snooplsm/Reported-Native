package com.reported.shared.reports

import com.reported.shared.model.ReportDraft

class LoadDraftUseCase(private val repository: DraftRepository) {
    @Throws(Exception::class)
    suspend fun execute(): ReportDraft? = repository.load()
}

class SaveDraftUseCase(private val repository: DraftRepository) {
    @Throws(Exception::class)
    suspend fun execute(draft: ReportDraft) = repository.save(draft)
}

class ClearDraftUseCase(private val repository: DraftRepository) {
    @Throws(Exception::class)
    suspend fun execute() = repository.clear()
}
