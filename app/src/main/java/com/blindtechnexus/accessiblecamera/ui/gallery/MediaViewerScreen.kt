/*
 * Accessible Camera and Gallery
 * Copyright (C) 2026 Sujan Rai, Blind Tech Nexus team.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.blindtechnexus.accessiblecamera.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.blindtechnexus.accessiblecamera.core.util.TimeFormat
import com.blindtechnexus.accessiblecamera.domain.model.GallerySection
import com.blindtechnexus.accessiblecamera.domain.model.MediaItem
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleConfirmDialog
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleIconButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessiblePrimaryButton
import com.blindtechnexus.accessiblecamera.ui.components.AccessibleSecondaryButton
import com.blindtechnexus.accessiblecamera.ui.components.MediaDescriptions
import com.blindtechnexus.accessiblecamera.ui.theme.AppColors
import com.blindtechnexus.accessiblecamera.ui.theme.Dimens

/**
 * Full screen viewer for one item.
 *
 * Swiping is available for sighted users, and explicit Previous and Next buttons exist so
 * the same navigation works with TalkBack and Switch Access. Videos use Media3 with
 * accessible transport controls plus spoken position feedback.
 */
@Composable
fun MediaViewerScreen(
    item: MediaItem,
    initialIndex: Int,
    fromTrash: Boolean,
    onBack: () -> Unit,
    onEdit: (MediaItem) -> Unit,
    announce: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var currentItem by remember { mutableStateOf(item) }
    var sessionItems by remember { mutableStateOf(listOf(item)) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMillis by remember { mutableFloatStateOf(0f) }
    var durationMillis by remember { mutableFloatStateOf(0f) }

    // The trash flag can arrive from navigation or from the item itself when it was opened
    // through item details, so both are honoured.
    val inTrash = fromTrash || currentItem.inTrash

    // One player instance for the whole viewer session so switching items is cheap.
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isPlaying) {
        while (isPlaying) {
            positionMillis = exoPlayer.currentPosition.toFloat()
            durationMillis = (exoPlayer.duration.coerceAtLeast(0L)).toFloat()
            kotlinx.coroutines.delay(500)
        }
    }

    LaunchedEffect(Unit) {
        val recent = runCatching { viewModel.recentForViewer() }.getOrDefault(emptyList())
        if (recent.isNotEmpty()) {
            sessionItems = recent
        }
    }

    LaunchedEffect(currentItem, sessionItems) {
        val index = sessionItems.indexOfFirst { it.id == currentItem.id }
        announce(
            MediaDescriptions.full(
                currentItem,
                if (index >= 0) index + 1 else initialIndex + 1,
                sessionItems.size.takeIf { it > 0 }
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.SmallSpacing, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AccessibleIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "Back to gallery",
                onClick = onBack,
                tint = Color.White
            )
            Text(
                text = currentItem.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            AccessibleIconButton(
                icon = if (currentItem.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (currentItem.isFavorite) "Remove from favorites" else "Add to favorites",
                onClick = { viewModel.toggleFavorite(listOf(currentItem)) },
                tint = Color.White
            )
            AccessibleIconButton(
                icon = Icons.Filled.Info,
                contentDescription = "Item details",
                onClick = { viewModel.loadDetails(currentItem) },
                tint = Color.White
            )
            AccessibleIconButton(
                icon = Icons.Filled.Edit,
                contentDescription = "Edit this item",
                onClick = { onEdit(currentItem) },
                tint = Color.White
            )
            CastButton(announce = announce)
            AccessibleIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share this item",
                onClick = { viewModel.shareUri(context, currentItem.uri, currentItem.mimeType) },
                tint = Color.White
            )
            AccessibleIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = if (inTrash) "Delete permanently" else "Move to recently deleted",
                onClick = { showDeleteDialog = true },
                tint = Color.White
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(currentItem) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        zoom = (zoom * zoomChange).coerceIn(1f, 5f)
                    }
                }
                .semantics {
                    contentDescription = MediaDescriptions.full(currentItem)
                },
            contentAlignment = Alignment.Center
        ) {
            if (currentItem.isVideo) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            controllerShowTimeoutMs = 0
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = currentItem.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom
                        )
                )
            }
        }

        if (currentItem.isVideo) {
            VideoTransportPanel(
                isPlaying = isPlaying,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                onTogglePlay = {
                    if (exoPlayer.isPlaying) {
                        exoPlayer.pause()
                        announce("Paused")
                    } else {
                        exoPlayer.play()
                        announce("Playing")
                    }
                },
                onSeek = { fraction ->
                    val target = (durationMillis * fraction).toLong()
                    exoPlayer.seekTo(target)
                    positionMillis = target.toFloat()
                    announce("Position ${TimeFormat.spoken(target)}")
                },
                onRewind = {
                    val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0)
                    exoPlayer.seekTo(target)
                    positionMillis = target.toFloat()
                    announce("Rewound 10 seconds")
                },
                onForward = {
                    val target = (exoPlayer.currentPosition + 10_000)
                        .coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                    exoPlayer.seekTo(target)
                    positionMillis = target.toFloat()
                    announce("Forward 10 seconds")
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.ItemSpacing),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AccessibleIconButton(
                    icon = Icons.Filled.ZoomOut,
                    contentDescription = "Zoom out",
                    onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) },
                    tint = Color.White
                )
                AccessiblePrimaryButton(
                    text = "Reset zoom",
                    onClick = { zoom = 1f },
                    modifier = Modifier.weight(1f)
                )
                AccessibleIconButton(
                    icon = Icons.Filled.ZoomIn,
                    contentDescription = "Zoom in",
                    onClick = { zoom = (zoom + 0.5f).coerceAtMost(5f) },
                    tint = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.ItemSpacing),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
        ) {
            AccessibleSecondaryButton(
                text = "Previous item",
                onClick = {
                    val index = sessionItems.indexOfFirst { it.id == currentItem.id }
                    val next = sessionItems.getOrNull(index - 1)
                    if (index <= 0 || next == null) {
                        announce("This is the first item in the list.")
                    } else {
                        currentItem = next
                        zoom = 1f
                        exoPlayer.apply {
                            stop()
                            clearMediaItems()
                            setMediaItem(Media3Item.fromUri(next.uri))
                            prepare()
                            playWhenReady = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
            AccessibleSecondaryButton(
                text = "Next item",
                onClick = {
                    val index = sessionItems.indexOfFirst { it.id == currentItem.id }
                    val next = sessionItems.getOrNull(index + 1)
                    if (next == null) {
                        announce("This is the last item in the list.")
                    } else {
                        currentItem = next
                        zoom = 1f
                        exoPlayer.apply {
                            stop()
                            clearMediaItems()
                            setMediaItem(Media3Item.fromUri(next.uri))
                            prepare()
                            playWhenReady = false
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = MediaDescriptions.full(currentItem),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = Dimens.ItemSpacing, vertical = 4.dp)
        )
    }

    state.details?.let { details ->
        MediaDetailsSheet(
            details = details,
            onDismiss = viewModel::closeDetails,
            onDeleteTrashed = {
                // Restore first, then leave the trash view.
                viewModel.restoreFromTrash(listOf(details.item))
                viewModel.closeDetails()
                onBack()
            },
            onDelete = { showDeleteDialog = true }
        )
    }

    if (showDeleteDialog) {
        val trashed = inTrash || state.details?.item?.inTrash == true
        AccessibleConfirmDialog(
            title = if (trashed) "Delete Selected Items Permanently" else "Move to recently deleted?",
            message = if (trashed) {
                "Delete ${currentItem.displayName} permanently? This cannot be undone."
            } else {
                "This moves ${currentItem.displayName} to recently deleted. You can restore it."
            },
            confirmLabel = if (trashed) "Delete" else "Move to trash",
            supporting = if (trashed) "Cannot be undone." else null,
            onConfirm = {
                showDeleteDialog = false
                if (trashed) {
                    viewModel.deletePermanently(listOf(currentItem))
                } else {
                    viewModel.moveToTrash(listOf(currentItem))
                }
                viewModel.closeDetails()
                onBack()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    LaunchedEffect(Unit) {
        announce(
            buildString {
                append(MediaDescriptions.full(currentItem))
                append(" Use the previous and next buttons, or double tap the screen edge, to move between items.")
            }
        )
    }
}

@Composable
private fun VideoTransportPanel(
    isPlaying: Boolean,
    positionMillis: Float,
    durationMillis: Float,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.CameraChrome)
            .padding(Dimens.ItemSpacing),
        verticalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SmallSpacing),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccessibleSecondaryButton(
                text = "Rewind 10 seconds",
                onClick = onRewind,
                modifier = Modifier.weight(1f)
            )
            AccessibleIconButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause video" else "Play video",
                onClick = onTogglePlay,
                tint = Color.White
            )
            AccessibleSecondaryButton(
                text = "Forward 10 seconds",
                onClick = onForward,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = "${TimeFormat.clock(positionMillis.toLong())} of ${TimeFormat.clock(durationMillis.toLong())}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
        Slider(
            value = if (durationMillis > 0f) (positionMillis / durationMillis).coerceIn(0f, 1f) else 0f,
            onValueChange = onSeek,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "Video position, ${TimeFormat.clock(positionMillis.toLong())}"
                }
        )
    }
}

/** Slideshow controller used by the gallery tab. */
@Composable
fun SlideshowOverlay(
    items: List<MediaItem>,
    onClose: () -> Unit,
    announce: (String) -> Unit
) {
    var index by remember { mutableStateOf(0) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(playing, index) {
        if (!playing || items.isEmpty()) return@LaunchedEffect
        kotlinx.coroutines.delay(4000)
        index = (index + 1) % items.size
        items.getOrNull(index)?.let { announce("Slideshow item ${index + 1} of ${items.size}") }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(Dimens.ItemSpacing),
        verticalArrangement = Arrangement.Center
    ) {
        if (items.isEmpty()) {
            Text(text = "Nothing to show", color = Color.White)
            return@Column
        }
        val item = items[index]
        AsyncImage(
            model = item.uri,
            contentDescription = MediaDescriptions.full(item),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        Spacer(Modifier.height(Dimens.ItemSpacing))
        Text(
            text = "Item ${index + 1} of ${items.size}",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        AccessibleSecondaryButton(
            text = if (playing) "Pause slideshow" else "Resume slideshow",
            onClick = { playing = !playing }
        )
        AccessibleSecondaryButton(text = "Stop slideshow", onClick = onClose)
    }
}

/**
 * Route video playback to a nearby cast device.
 *
 * Uses the Media Router button that ships with Play services, so there is no Cast SDK
 * dependency. The custom wrapper keeps a spoken description of the current route so a blind
 * user is never left guessing whether playback moved to a television. When no route exists the
 * control is not composed at all, which keeps the toolbar short.
 */
@Composable
private fun CastButton(announce: (String) -> Unit) {
    val context = LocalContext.current
    var routeCount by remember { mutableIntStateOf(0) }
    var selectedName by remember { mutableStateOf<String?>(null) }

    val selector = remember {
        androidx.mediarouter.media.MediaRouteSelector.Builder()
            .addControlCategory(
                androidx.mediarouter.media.MediaControlIntent.CATEGORY_REMOTE_PLAYBACK
            )
            .build()
    }

    DisposableEffect(Unit) {
        val router = androidx.mediarouter.media.MediaRouter.getInstance(context)
        val callback = object : androidx.mediarouter.media.MediaRouter.Callback() {
            override fun onRouteSelected(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo
            ) {
                selectedName = route.name
                announce("Playing on ${route.name}")
            }

            override fun onRouteUnselected(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo
            ) {
                selectedName = null
                announce("Playing on this device")
            }

            override fun onRouteAdded(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo
            ) {
                routeCount = router.routes.count { it.matchesSelector(selector) }
            }

            override fun onRouteRemoved(
                router: androidx.mediarouter.media.MediaRouter,
                route: androidx.mediarouter.media.MediaRouter.RouteInfo
            ) {
                routeCount = router.routes.count { it.matchesSelector(selector) }
            }
        }
        router.addCallback(selector, callback)
        routeCount = router.routes.count { it.matchesSelector(selector) }
        onDispose { router.removeCallback(callback) }
    }

    if (routeCount == 0) return

    Box(
        modifier = Modifier
            .size(Dimens.MinimumTouchTarget)
            .semantics {
                contentDescription = selectedName?.let { "Playing on $it. Choose another device" }
                    ?: "Play on another device"
            }
    ) {
        AndroidView(
            factory = { viewContext ->
                androidx.mediarouter.app.MediaRouteButton(viewContext).apply {
                    setRouteSelector(selector)
                    isFocusable = true
                }
            },
            modifier = Modifier.matchParentSize()
        )
    }
}
