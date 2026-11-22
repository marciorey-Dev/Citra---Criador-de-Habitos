# Add project specific ProGuard rules here.
# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room Database, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Citra Data Models (used by Room and Backup JSON)
-keep class com.example.data.model.** { *; }
-keep class com.example.data.local.** { *; }

# PdfBox-Android (suppress harmless desktop AWT / BouncyCastle warnings and keep resource/charset classes)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn org.bouncycastle.**
-dontwarn com.tom_roush.pdfbox.**

# Coil Image Loader & Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn coil.**

# WorkManager & Android Components (Activities, Receivers, Services, AppWidgets)
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.example.ExternalReaderActivity { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.ui.screens.alarm.AlarmActivity { *; }
-keep class com.example.widget.** { *; }
-keep class com.example.notifications.** { *; }
-keep class com.example.service.** { *; }

# org.json
-keep class org.json.** { *; }

