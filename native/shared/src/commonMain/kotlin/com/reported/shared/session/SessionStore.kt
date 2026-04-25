package com.reported.shared.session

import com.reported.shared.model.UserSession

interface SessionStore {
    suspend fun read(): UserSession?
    suspend fun write(session: UserSession)
    suspend fun clear()
}

