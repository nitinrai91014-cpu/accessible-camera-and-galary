/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.voice

import android.Manifest
import android.content.Context
import androidx.core.content.ContextCompat
import com.blindtechnexus.accessiblecamera.data.voice.OnDeviceSpeechRecognizer
import com.blindtechnexus.accessiblecamera.data.voice.VoiceRepositoryImpl
import com.blindtechnexus.accessiblecamera.domain.model.AnnouncementPriority
import com.blindtechnexus.accessiblecamera.domain.model.VoiceCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges recognised phrases to app actions.
 *
 * The controller owns the confirmation requirement for destructive commands, so a
 * mis-recognition can never delete something. It also documents, in one place, which
 * commands are meaningful on which screen.
 */
@Singleton
class VoiceCommandController @Inject constructor(
    private @ApplicationContext val context: Context,
    private val recognizer: OnDeviceSpeechRecognizer,
    private val voice: VoiceRepositoryImpl
) {

    private val _state = MutableStateFlow(VoiceCommandUiState())

    init {
        refreshAvailability()
    }
    val state: StateFlow<VoiceCommandUiState> = _state.asStateFlow()

    private var handler: ((VoiceCommand) -> Boolean)? = null
    private var pendingDestructive: VoiceCommand? = null

    data class VoiceCommandUiState(
        val listening: Boolean = false,
        val lastHeard: String? = null,
        val lastCommand: VoiceCommand? = null,
        val awaitingConfirmation: VoiceCommand? = null,
        val error: String? = null,
        val available: Boolean = false,
        val onDeviceRecognition: Boolean = false
    )

    fun attachHandler(handler: (VoiceCommand) -> Boolean) {
        this.handler = handler
    }

    fun detachHandler() {
        handler = null
    }

    fun refreshAvailability() {
        val available = OnDeviceSpeechRecognizer.isSpeechRecognitionAvailable(context)
        val onDevice = OnDeviceSpeechRecognizer.isOnDeviceRecognitionSupported(context)
        _state.value = _state.value.copy(available = available, onDeviceRecognition = onDevice)
    }

    fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Starts listening. Returns false when the microphone is not permitted, so the caller
     * can request it.
     */
    fun startListening(): Boolean {
        if (!hasMicrophonePermission()) {
            voice.announce(
                "Microphone permission is needed for voice commands.",
                AnnouncementPriority.HIGH
            )
            return false
        }
        if (!_state.value.available) {
            voice.announce(
                "Speech recognition is not available on this device.",
                AnnouncementPriority.CRITICAL
            )
            return false
        }
        recognizer.setCallbacks(
            command = { command, utterance -> handleRecognised(command, utterance) },
            listeningChanged = { listening ->
                _state.value = _state.value.copy(listening = listening)
            }
        )
        recognizer.startListening(continuous = false)
        voice.announce(
            "Listening. Say a command, or say stop listening.",
            AnnouncementPriority.HIGH
        )
        return true
    }

    fun stopListening() {
        recognizer.stopListening()
        _state.value = _state.value.copy(listening = false)
        voice.announce("Voice commands stopped", AnnouncementPriority.HIGH)
    }

    fun cancelPending() {
        pendingDestructive = null
        _state.value = _state.value.copy(awaitingConfirmation = null)
    }

    private fun handleRecognised(command: VoiceCommand, utterance: String) {
        _state.value = _state.value.copy(lastHeard = utterance, lastCommand = command)
        when (command) {
            VoiceCommand.UNKNOWN -> {
                _state.value = _state.value.copy(error = "Command not recognised")
                voice.announce(
                    "Sorry, I did not understand that. Say help to hear the commands.",
                    AnnouncementPriority.HIGH
                )
            }
            VoiceCommand.HELP -> {
                voice.announce(VoiceCommand.helpText(), AnnouncementPriority.HIGH)
            }
            VoiceCommand.STOP_LISTENING -> stopListening()
            VoiceCommand.CONFIRM -> {
                val pending = pendingDestructive
                if (pending != null) {
                    pendingDestructive = null
                    _state.value = _state.value.copy(awaitingConfirmation = null)
                    handler?.invoke(pending)
                    voice.announce("Confirmed", AnnouncementPriority.HIGH)
                } else {
                    handler?.invoke(VoiceCommand.CONFIRM)
                }
            }
            VoiceCommand.CANCEL -> {
                if (pendingDestructive != null) {
                    pendingDestructive = null
                    _state.value = _state.value.copy(awaitingConfirmation = null)
                    voice.announce("Cancelled", AnnouncementPriority.HIGH)
                } else {
                    handler?.invoke(VoiceCommand.CANCEL)
                }
            }
            VoiceCommand.DELETE -> {
                pendingDestructive = VoiceCommand.DELETE
                _state.value = _state.value.copy(awaitingConfirmation = VoiceCommand.DELETE)
                voice.announce(
                    "Delete. Say confirm to delete, or cancel to stop.",
                    AnnouncementPriority.CRITICAL
                )
            }
            else -> {
                val handled = handler?.invoke(command) ?: false
                if (handled) {
                    voice.announce(command.spokenConfirmation, AnnouncementPriority.NORMAL)
                } else {
                    voice.announce(
                        "That command is not available on this screen.",
                        AnnouncementPriority.HIGH
                    )
                }
            }
        }
    }

    fun destroy() {
        recognizer.destroy()
    }
}
