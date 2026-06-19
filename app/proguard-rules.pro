# WebRTC
-keep class org.webrtc.** { *; }
# Socket.IO
-keep class io.socket.** { *; }
-keep class io.engine.** { *; }
# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
# GrinMain models
-keep class com.grinmain.data.** { *; }
