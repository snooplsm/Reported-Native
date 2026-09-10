package com.reported.nativeandroid.app

import kotlinx.coroutines.flow.StateFlow

/**
 * The single state/action boundary used by Android features.
 *
 * Views render [state] and send user or system events through [onAction]. State
 * mutations and side effects belong in the implementing store/ViewModel.
 */
interface UdfStore<State : Any, Action : Any> {
    val state: StateFlow<State>

    fun onAction(action: Action)
}
