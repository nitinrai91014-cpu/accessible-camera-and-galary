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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Location tagging, off by default. Uses only the platform location manager, so the app has
 * no dependency on Google Play services for coordinates. Nothing leaves the device except the
 * optional reverse geocoding call made by the platform Geocoder.
 */
@Singleton
class LocationProvider @Inject constructor(
    private val context: Context
) {

    data class GeoPoint(val latitude: Double, val longitude: Double, val placeName: String?)

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
            manager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()
        location?.let { toGeoPoint(it) }
    }

    @SuppressLint("MissingPermission")
    suspend fun current(): GeoPoint? = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext null
        val fresh = requestSingleUpdate()
        if (fresh != null) return@withContext toGeoPoint(fresh)
        lastKnown()
    }

    /**
     * Asks the platform for one fresh fix with a short timeout. Returns null when nothing
     * arrives quickly, so a geotagged capture never stalls.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestSingleUpdate(): Location? {
        val locationManager = manager ?: return null
        val provider = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).firstOrNull { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        return withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeoutOrNull(4_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }

                        @Deprecated("Required by the platform interface", ReplaceWith(""))
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

                        override fun onProviderEnabled(provider: String) = Unit

                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                    runCatching {
                        locationManager.requestLocationUpdates(provider, 0L, 0f, listener, android.os.Looper.getMainLooper())
                    }.onFailure {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        }
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
