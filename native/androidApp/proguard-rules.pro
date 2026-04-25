# Reported release shrinking rules.
#
# Keep this file narrow: most AndroidX, Compose, Maps, Credential Manager, Ktor,
# OkHttp, Coil, Lottie, and kotlinx.serialization artifacts ship consumer rules.
# These project rules cover reflection/native edges that are easy to miss.

# Preserve Kotlin metadata used by kotlinx.serialization and KMP model code.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class ** {
    public static ** Companion;
}

# Shared KMP API/session models are serialized and persisted.
-keep class com.reported.shared.model.** { *; }
-keep class com.reported.shared.api.** { *; }

# ONNX Runtime loads native-backed classes by name.
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Google identity/credential responses cross binder/reflection boundaries.
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-dontwarn com.google.android.gms.**

# Maps SDK and maps-compose ship rules, but keep public model classes stable.
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.compose.** { *; }

# Media3/ExoPlayer may reference optional decoder integrations.
-dontwarn androidx.media3.**

# Ktor/OkHttp optional platform integrations.
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Image/vector/animation libraries mostly ship consumer rules; suppress optional
# integrations we do not include.
-dontwarn coil.**
-dontwarn com.airbnb.lottie.**
