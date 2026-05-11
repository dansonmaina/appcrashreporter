# Consumer ProGuard rules for the crashreport library.
# These rules are automatically applied to any app that consumes this library.

# Keep Gson model classes so Retrofit/Gson can deserialize Telegram responses
-keep class com.danzig.crashreport.model.** { *; }
-keepclassmembers class com.danzig.crashreport.model.** { *; }

# Gson generic type hints
-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keepattributes Exceptions
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# JavaMail — large reflective surface, just ignore warnings
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }

# Keep public entrypoints
-keep public class com.danzig.crashreport.CrashReporter { *; }
-keep public class com.danzig.crashreport.CrashHandler { *; }
-keep public class com.danzig.crashreport.TelegramSender { *; }
-keep public class com.danzig.crashreport.TelegramUserFetcher { *; }
-keep public class com.danzig.crashreport.TelegramUserFetcher$OnUsersFetchedListener { *; }
-keep public class com.danzig.crashreport.TelegramDeveloperManager { *; }
