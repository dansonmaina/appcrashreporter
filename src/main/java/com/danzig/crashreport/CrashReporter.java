package com.danzig.crashreport;

import android.content.Context;

import com.danzig.crashreport.model.Developer;

/**
 * Public entry point for the Danzig CrashReport library.
 *
 * Typical usage from a host app's Application.onCreate():
 *
 *     CrashReporter.install(this, developer,
 *         new CrashReporterConfig.Builder()
 *             .whatsappToken("EAAUo...")
 *             .whatsappPhoneNumberId("107011...")
 *             .telegramBotToken("8290414422:AAEv...")
 *             .gpsProvider(() -> new double[]{lat, lon})
 *             .usernameSharedPrefsName(svars.sharedprefsname)
 *             .usernameKey("username")
 *             .build()
 *     );
 */
public final class CrashReporter {

    /** Application context — used by CrashHandler, TelegramSender, etc. */
    public static Context appContext;

    /** Shared config — read by CrashHandler, TelegramSender, TelegramUserFetcher. */
    static CrashReporterConfig config = new CrashReporterConfig.Builder().build();

    private CrashReporter() {}

    /**
     * Install the crash handler with a Developer and API config.
     * Call this in Application.onCreate() after Realm is initialised (if used by the host app).
     */
    public static void install(Context context, Developer developer, CrashReporterConfig cfg) {
        appContext = context.getApplicationContext();
        config     = cfg != null ? cfg : new CrashReporterConfig.Builder().build();

        CrashReportDatabase db = CrashReportDatabase.getInstance(appContext);
        if (config.defaultDeveloper != null && db.getAllDevelopers().isEmpty()) {
            db.insertDeveloper(config.defaultDeveloper);
        }
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(appContext, developer));
    }

    /**
     * Install using raw developer metadata with API config.
     */
    public static void install(Context context,
                               String developerName,
                               String developerPhone,
                               String appName,
                               String emailAddress,
                               String clientName,
                               String appCode,
                               CrashReporterConfig cfg) {
        appContext = context.getApplicationContext();
        config     = cfg != null ? cfg : new CrashReporterConfig.Builder().build();
        CrashReportDatabase.getInstance(appContext);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(
                appContext, developerName, developerPhone, appName, emailAddress, clientName, appCode));
    }

    // ── Legacy overloads (no config — WhatsApp / Telegram channels skipped) ──

    /** @deprecated Pass a {@link CrashReporterConfig} to supply API credentials. */
    public static void install(Context context, Developer developer) {
        appContext = context.getApplicationContext();
        CrashReportDatabase.getInstance(appContext);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(appContext, developer));
    }

    /** @deprecated Pass a {@link CrashReporterConfig} to supply API credentials. */
    public static void install(Context context,
                               String developerName,
                               String developerPhone,
                               String appName,
                               String emailAddress,
                               String clientName,
                               String appCode) {
        appContext = context.getApplicationContext();
        CrashReportDatabase.getInstance(appContext);
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(
                appContext, developerName, developerPhone, appName, emailAddress, clientName, appCode));
    }
}
