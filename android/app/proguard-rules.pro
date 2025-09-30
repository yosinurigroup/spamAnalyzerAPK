# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep OkHttp classes and methods
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Keep Retrofit classes if using Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep accessibility service classes
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Keep broadcast receivers
-keep class * extends android.content.BroadcastReceiver { *; }

# Keep service classes
-keep class * extends android.app.Service { *; }

# Keep classes with native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep serialization classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Flutter classes
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**

# Keep your app's specific classes
-keep class com.example.spam_analyzer_v6.** { *; }

# Keep callback interfaces and their methods
-keep interface * {
    <methods>;
}

# Keep classes that use reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# If you use the official API artifact (recommended)
-keep class moe.shizuku.api.** { *; }
-keep interface moe.shizuku.api.** { *; }
-dontwarn moe.shizuku.**

# For older namespaces / helper libs (harmless if unused)
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**

# Keep Shizuku Manager package name if you open it via reflection/intents
-keep class moe.shizuku.manager.** { *; }
-dontwarn moe.shizuku.manager.**