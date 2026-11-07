/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Decorative thumbnail. The content description is intentionally null because the
 * surrounding row or grid cell always supplies one combined description; repeating it
 * would make TalkBack say the same thing twice.
 */
@Composable
fun MediaThumbnail(
    item: MediaItem?,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.ThumbnailSize,
    cornerRadius: Dp = 12.dp
) {
    val context = LocalContext.current
    val imageLoader = androidx.compose.runtime.remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item == null) {
                Text(text = "", style = MaterialTheme.typography.bodySmall)
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .videoFrameMillis(if (item.isVideo) 1000L else 0L)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun MediaThumbnailFill(
    item: MediaItem?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageLoader = androidx.compose.runtime.remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
    }
    if (item == null) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(text = "", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(item.uri)
            .videoFrameMillis(if (item.isVideo) 1000L else 0L)
            .crossfade(true)
            .build(),
        imageLoader = imageLoader,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}
