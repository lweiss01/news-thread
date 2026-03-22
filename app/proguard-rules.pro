# Preserve runtime metadata used by Room, Hilt, and generated code.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room database, entities, and DAO contracts.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# Hilt application/modules and WorkManager workers injected through Hilt.
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.Module class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @androidx.hilt.work.HiltWorker class * { *; }
-keep class * extends androidx.work.ListenableWorker {
    <init>(...);
}

# OkHttp and Google client stack warnings that are safe to suppress in release.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Gson is used by Google API client internals.
-keep class com.google.gson.** { *; }

# TensorFlow Lite runtime/JNI entry points must survive shrinking.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# Coroutines service lookups.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
