# Add project specific ProGuard rules here.
-keep class com.flam.edgeviewer.NativeProcessor { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
