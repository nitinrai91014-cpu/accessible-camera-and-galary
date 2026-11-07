/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** Room database holding the local media index, trash, capabilities and edit history. */
@Database(
    entities = [
        MediaEntity::class,
        TrashEntity::class,
        CapabilityEntity::class,
        UndoEntity::class,
        EditRecipeEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun trashDao(): TrashDao
    abstract fun capabilityDao(): CapabilityDao
    abstract fun undoDao(): UndoDao
    abstract fun editRecipeDao(): EditRecipeDao

    companion object {
        const val NAME = "accessible_camera.db"
    }
}
