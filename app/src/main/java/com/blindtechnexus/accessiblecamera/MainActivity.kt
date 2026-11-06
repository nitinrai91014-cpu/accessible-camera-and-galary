package com.blindtechnexus.accessiblecamera

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }
}

private fun hasPermission(context: android.content.Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guidance", 0) }
    var guidance by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var tts: TextToSpeech? by remember { mutableStateOf(null) }
    DisposableEffect(context) {
        val engine = TextToSpeech(context) { }
        tts = engine
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    val accessibility = remember { context.getSystemService(AccessibilityManager::class.java) }
    fun announce(message: String) {
        if (guidance && !accessibility.isTouchExplorationEnabled) tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "guidance")
    }
    var started by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Ready") }
    fun report(message: String) { status = message; announce(message) }
    if (!started) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Accessible Camera and Gallery", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text("An accessible, open-source camera and gallery app for Android.")
            Text("Camera access is needed to take photos and videos. Microphone access is optional for video sound. Media access is needed to browse your library.")
            Button(onClick = { started = true; announce("Photo tab") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text("Start") }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
            listOf("Photo", "Video", "Gallery", "Settings").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index; announce("$title tab") }, text = { Text(title) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        Text(status, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite })
        when (tab) {
            0, 1 -> CameraScreen(video = tab == 1, onStatus = ::report)
            2 -> GalleryScreen(onStatus = ::report)
            else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Voice guidance", modifier = Modifier.padding(top = 16.dp))
                    Switch(checked = guidance, onCheckedChange = { guidance = it; prefs.edit().putBoolean("enabled", it).apply(); if (it) announce("Voice guidance on") }, modifier = Modifier.semantics { contentDescription = "Voice guidance" })
                }
                Text("TalkBack speaks controls when touch exploration is on. Voice guidance is local to your device.")
                Spacer(Modifier.height(16.dp))
                Text("Developed by Sujan Rai, Blind Tech Nexus.")
            }
        }
    }
}

@Composable
private fun CameraScreen(video: Boolean, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = it
        onStatus(if (it) "Camera permission granted" else "Camera permission denied. Open app settings to allow it.")
    }
    var audio by rememberSaveable { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        audio = it
        onStatus(if (it) "Video sound on" else "Microphone permission denied. Video will be silent.")
    }
    var storageGranted by remember { mutableStateOf(Build.VERSION.SDK_INT >= 29 || hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        storageGranted = it
        onStatus(if (it) "Storage permission granted" else "Storage permission is needed to save on this Android version.")
    }
    if (!permission || !storageGranted) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (!permission) "Allow camera access to use the camera." else "Allow storage access to save photos and videos on this Android version.")
            Button(onClick = { if (!permission) cameraPermission.launch(Manifest.permission.CAMERA) else storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow access") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
        }
        return
    }
    val controller = remember { LifecycleCameraController(context) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var front by remember { mutableStateOf(false) }
    DisposableEffect(owner, controller) {
        try {
            controller.setEnabledUseCases(CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
            controller.bindToLifecycle(owner)
            val future = controller.initializationFuture
            future.addListener({
                try { future.get(); ready = true; onStatus("Camera ready") }
                catch (_: Exception) { onStatus("Camera unavailable. Close other camera apps and try again.") }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) { onStatus("Camera unavailable. Close other camera apps and try again.") }
        onDispose { recording?.stop(); recording = null; controller.unbind() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AndroidView(factory = { PreviewView(it).apply { this.controller = controller } }, modifier = Modifier.fillMaxWidth().height(240.dp).semantics { contentDescription = if (video) "Video camera preview" else "Photo camera preview" })
        if (video) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Record sound", modifier = Modifier.padding(top = 16.dp))
                Switch(checked = audio, enabled = recording == null, onCheckedChange = { if (it && !hasPermission(context, Manifest.permission.RECORD_AUDIO)) audioPermission.launch(Manifest.permission.RECORD_AUDIO) else audio = it }, modifier = Modifier.semantics { contentDescription = "Record sound" })
            }
            Button(enabled = ready && !busy, onClick = {
                if (recording != null) { busy = true; recording?.stop() }
                else {
                    try {
                        val values = ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, "VID_${System.currentTimeMillis()}"); put(MediaStore.Video.Media.MIME_TYPE, "video/mp4") }
                        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build()
                        busy = true
                        val pending = if (audio && hasPermission(context, Manifest.permission.RECORD_AUDIO)) androidx.camera.view.video.AudioConfig.create(true) else androidx.camera.view.video.AudioConfig.AUDIO_DISABLED
                        recording = controller.startRecording(options, pending, ContextCompat.getMainExecutor(context)) { event ->
                            when (event) {
                                is VideoRecordEvent.Start -> { busy = false; onStatus("Recording started") }
                                is VideoRecordEvent.Finalize -> { recording = null; busy = false; onStatus(if (event.hasError()) "Video could not be saved. Check storage and try again." else "Video saved") }
                            }
                        }
                    } catch (_: Exception) { busy = false; onStatus("Could not start recording. Try again.") }
                }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text(if (recording == null) "Start recording" else "Stop recording") }
        } else {
            Button(enabled = ready && !busy, onClick = {
                busy = true
                val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}"); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") }
                val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
                try { controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(result: ImageCapture.OutputFileResults) { busy = false; onStatus("Photo saved") }
                    override fun onError(exception: ImageCaptureException) { busy = false; onStatus("Photo could not be saved. Check storage and try again.") }
                }) } catch (_: Exception) { busy = false; onStatus("Could not take photo. Try again.") }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) { Text("Take photo") }
        }
        OutlinedButton(enabled = ready && !busy && recording == null, onClick = {
            try { front = !front; controller.cameraSelector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA; onStatus(if (front) "Front camera" else "Back camera") }
            catch (_: Exception) { front = !front; onStatus("Other camera is not available") }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Switch camera") }
        Text("Advanced camera controls are not available in this version.")
    }
}

private data class MediaItem(val uri: Uri, val title: String, val date: Long, val type: String)

@Composable
private fun GalleryScreen(onStatus: (String) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(if (Build.VERSION.SDK_INT >= 33) hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) && hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) else hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = if (Build.VERSION.SDK_INT >= 33) hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES) && hasPermission(context, Manifest.permission.READ_MEDIA_VIDEO) else hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        onStatus(if (granted) "Media access granted" else "Full media access denied. Open app settings to allow access.")
    }
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (granted) {
            try {
                media = withContext(Dispatchers.IO) {
                    val result = mutableListOf<MediaItem>()
                    val collections = listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "image/*", MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "video/*")
                    collections.forEach { (collection, type) ->
                        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED), null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                            while (cursor.moveToNext()) result.add(MediaItem(android.content.ContentUris.withAppendedId(collection, cursor.getLong(0)), cursor.getString(1) ?: "Untitled", cursor.getLong(2), type))
                        }
                    }
                    result.sortedByDescending { it.date }
                }
            } catch (_: Exception) { error = true; onStatus("Could not load media. Check access and try again.") }
        }
    }
    if (!granted) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Allow access to photos and videos to browse your library.")
            Button(onClick = { launcher.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Allow media access") }
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open app settings") }
        }
    } else if (error) Text("Unable to load media. Return to Gallery to retry.", Modifier.padding(16.dp))
    else if (media.isEmpty()) Text("No photos or videos found.", Modifier.padding(16.dp))
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(media, key = { it.uri.toString() }) { item ->
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (_: Exception) { Toast.makeText(context, "No app can open this item", Toast.LENGTH_LONG).show(); onStatus("No app can open this item") }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("${if (item.type.startsWith("video")) "Video" else "Photo"}: ${item.title}, ${DateFormat.getDateInstance().format(Date(item.date * 1000))}")
            }
        }
    }
}
