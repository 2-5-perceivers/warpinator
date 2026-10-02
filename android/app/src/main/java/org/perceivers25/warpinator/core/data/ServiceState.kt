package org.perceivers25.warpinator.core.data

/**
 * Represents the various states of the background service.
 */
sealed interface ServiceState {
    data object Ok : ServiceState
    data object Stopped : ServiceState
    data object Restart : ServiceState
    data object NetworkChangeRestart : ServiceState
    data class InitializationFailed(
        val interfaces: String?,
        val exception: String,
    ) : ServiceState
}