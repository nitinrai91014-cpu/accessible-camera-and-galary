# Keep CameraX extensions and ML Kit model classes.
-keep class androidx.camera.extensions.** { *; }
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room generated implementations.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Hilt generated code.
-keep class dagger.hilt.** { *; }

# Media3 transformer.
-dontwarn androidx.media3.**

# Kotlin coroutines internals used reflectively by WorkManager.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
