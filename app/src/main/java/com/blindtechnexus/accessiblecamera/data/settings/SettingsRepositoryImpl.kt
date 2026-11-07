/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.blindtechnexus.accessiblecamera.domain.model.AppSettings
import com.blindtechnexus.accessiblecamera.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore backed implementation of [SettingsRepository]. */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .map { SettingsMapper.toSettings(it) }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(SettingsMapper.toSettings(preferences))
            preferences.writeAll(updated)
        }
    }

    override suspend fun resetAll() {
        dataStore.edit { preferences ->
            preferences.clear()
            preferences.writeAll(AppSettings.Defaults.copy(hasCompletedOnboarding = true))
        }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.ONBOARDING_COMPLETE] = complete
        }
    }
}
