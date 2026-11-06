/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import com.blindtechnexus.accessiblecamera.data.media.MediaPermissionEvaluator
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location tagging, off by default. Uses the fused provider when Play services is present
 * and falls back to the platform location manager. Nothing is sent off the device except
 * the optional reverse geocoding call made by the platform Geocoder.
 */
@Singleton
class LocationProvider @Inject constructor(
    private val context: Context
) {

    data class GeoPoint(val latitude: Double, val longitude: Double, val placeName: String?)

    private val fused by lazy { runCatching { LocationServices.getFusedLocationProviderClient(context) }.getOrNull() }
    private val manager by lazy { context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager }

    fun hasPermission(): Boolean =
        MediaPermissionEvaluator.isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            MediaPermissionEvaluator.isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun permissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    @SuppressLint("MissingPermission")
    suspend fun lastKnown(): GeoPoint? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val location = runCatching {
            fused?.lastLocation?.await()
        }.getOrNull() ?: runCatching {
            manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        location?.let { toGeoPoint(it) }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val client = fused
        if (client != null) {
            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setGranularity(Granularity.GRANULARITY_COARSE)
                .setMaxUpdateAgeMillis(120_000)
                .setDurationMillis(4_000)
                .build()
            val location = runCatching { client.getCurrentLocation(request, null).await() }.getOrNull()
            if (location != null) return@withContext toGeoPoint(location)
        }
        lastKnown()
    }

    private suspend fun toGeoPoint(location: Location): GeoPoint {
        val placeName = reverseGeocode(location.latitude, location.longitude)
        return GeoPoint(location.latitude, location.longitude, placeName)
    }

    private suspend fun reverseGeocode(latitude: Double, longitude: Double): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            runCatching {
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    geocoder.getFromLocation(latitude, longitude, 1)
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)
                }
                results?.firstOrNull()?.let { address ->
                    listOfNotNull(address.locality, address.adminArea, address.countryName)
                        .joinToString(", ")
                        .ifBlank { null }
                }
            }.getOrNull()
        }

    /** Coarse spoken description used by the watermark and item details. */
    fun describe(point: GeoPoint?): String? = point?.let {
        it.placeName ?: String.format(Locale.US, "%.4f, %.4f", it.latitude, it.longitude)
    }
}
