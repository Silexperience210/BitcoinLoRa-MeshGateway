# ProGuard rules for BitcoinMesh

# Keep Bluetooth classes
-keep class android.bluetooth.** { *; }

# Keep coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep MainActivity
-keep class com.bitcoinmesh.lora.MainActivity { *; }
