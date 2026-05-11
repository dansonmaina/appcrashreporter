package com.danzig.crashreport;

// CrashHandler.java
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.danzig.crashreport.model.CrashReport;
import com.danzig.crashreport.model.Developer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";

    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    // ---- Developer info: pull these from your local DB ----
    private final String developerName;
    private final String appName, appCode;
    private final String clientName;

    private final String email_address;
    // -------------------------------------------------------

    public CrashHandler(Context context, String developerName, String developerPhone, String appName, String email_address, String clientName, String appCode) {
        this.context        = context.getApplicationContext();
        this.developerName  = developerName;
        this.appName        = appName;
        this.email_address = email_address;
        this.clientName = clientName;
        this.appCode= appCode;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }
    public CrashHandler(Context context, Developer dev) {
        this.context        = context.getApplicationContext();
        this.developerName  = dev.developerName  != null ? dev.developerName  : "Unknown";
        this.appName        = dev.appName        != null ? dev.appName        : context.getPackageName();
        this.email_address  = dev.email_address  != null ? dev.email_address  : "";
        this.clientName     = dev.clientName     != null ? dev.clientName     : "N/A";
        this.appCode        = dev.appCode        != null ? dev.appCode        : "";
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {

        try {
            // 1. Compute crash fields
            String timestamp = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                    .format(new Date()) + " | " + getLocationString();
            String androidVer = "Android " + Build.VERSION.RELEASE;
            String deviceInfo = Build.MANUFACTURER + "_" + Build.MODEL;
            String appVersion = getAppVersion();
            String stackTrace = getStackTrace(throwable);

            // 2. Build crash message (for Telegram / Email / WhatsApp intent)

            CrashReport report = buildCrashReport(throwable);

            // Markdown format — for Telegram / WhatsApp
            String crashMessage = buildCrashMessage(report);
            // Plain text format — for Email
            String emailMessage = buildCrashMessage(throwable);

            CrashReporterConfig cfg = CrashReporter.config;

            // Claude AI analysis — appended to messages before any channel sends
            if (cfg.enableClaudeAnalysis && !cfg.claudeApiKey.isEmpty()) {
                String crashSummary = summarizeCrash(stackTrace);
                String analysis = fetchClaudeAnalysis(crashSummary);
                if (analysis != null && !analysis.isEmpty()) {
                    crashMessage += "\n\n🤖 *Claude Analysis:*\n" + analysis;
                    emailMessage += "\n\n<b>🤖 Claude Analysis:</b>\n" + analysis;
                }
            }

            // 3. Save to local DB and send to Support Capture to create a ticket
            if (cfg.enableCrashTicket) sendCrashTicket(report, appCode);

            // 4. Send to Telegram automatically
            if (cfg.enableTelegram) TelegramSender.sendCrashReport(crashMessage);

            // 5. Send via WhatsApp Cloud API using approved template
            if (cfg.enableWhatsAppCloud) sendWhatsAppMessage(appName, clientName, timestamp, androidVer, appVersion, deviceInfo, stackTrace);

            if (cfg.enableEmail) sendEmailCrashReport(emailMessage);

            // 7. Send via Maytapi WhatsApp API
            if (cfg.enableMaytapi) sendMaytapiWhatsAppMessage(crashMessage);

            Log.e(TAG, "Crash captured and reported.", throwable);

        } catch (Exception e) {
            Log.e(TAG, "CrashHandler itself failed: " + e.getMessage());
        }

        // 4. Let the default handler finish (closes the app normally)
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        }
    }

    // ----------------------------------------------------------
    // Build the crash message matching your proposed format
    // ----------------------------------------------------------
    private String buildCrashMessage(Throwable throwable) {

        String timestamp   = new SimpleDateFormat(
                "dd-MM-yyyy HH:mm:ss", Locale.getDefault())
                .format(new Date());

        String androidVer  = "Android " + Build.VERSION.RELEASE;
        String deviceInfo  = Build.MANUFACTURER + "_" + Build.MODEL;
        String appVersion  = getAppVersion();
        String stackTrace  = getStackTrace(throwable);

        String appUsername;
        try {
            String prefsName = CrashReporter.config.usernameSharedPrefsName;
            String prefsKey  = CrashReporter.config.usernameKey;
            if (prefsName != null && !prefsName.isEmpty()) {
                SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                String saved = prefs.getString(prefsKey, null);
                appUsername = (saved != null && !saved.isEmpty()) ? saved : "No user set";
            } else {
                appUsername = "No user set";
            }
        } catch (Exception e) {
            appUsername = "No user set";
        }

        String location  = getLocationString();
        String mapsLink  = "";
        try {
            CrashReporterConfig.GpsProvider gps = CrashReporter.config.gpsProvider;
            if (gps != null) {
                double[] latLon = gps.getLatLon();
                if (latLon != null && !(latLon[0] == 0.0 && latLon[1] == 0.0)) {
                    String mapsUrl = "https://maps.google.com/?q=" + latLon[0] + "," + latLon[1];
                    mapsLink = "  <a href='" + mapsUrl + "' "
                            + "style='color:#cc0000;text-decoration:none;font-weight:600;'>"
                            + "&#x1F4CD; Open in Maps</a>";
                }
            }
        } catch (Exception ignored) {}

        return "Dear <b>" + developerName + "</b>, there was a crash on your system.\n\n"
                + "<b>System Name:</b> "     + appName             + "\n"
                + "<b>Client Name:</b> "     + clientName          + "\n"
                + "<b>App User:</b> "        + appUsername         + "\n"
                + "<b>Time:</b> "            + timestamp           + "\n"
                + "<b>Location:</b> "        + location + mapsLink + "\n"
                + "<b>Android Version:</b> " + androidVer   + "\n"
                + "<b>App Version:</b> "     + appVersion   + "\n"
                + "<b>Device Info:</b> "      + deviceInfo                    + "\n\n"
                + "<b>Where it crashed:</b> " + summarizeCrash(stackTrace)    + "\n\n"
                + "<b>Crash Report:</b>\n"    + stackTrace;
    }

    public String buildCrashMessage(CrashReport r) {
        // GPS coordinates
        String lat = "N/A", lon = "N/A";
        try {
            CrashReporterConfig.GpsProvider gps = CrashReporter.config.gpsProvider;
            if (gps != null) {
                double[] latLon = gps.getLatLon();
                if (latLon != null && !(latLon[0] == 0.0 && latLon[1] == 0.0)) {
                    lat = String.valueOf(latLon[0]);
                    lon = String.valueOf(latLon[1]);
                }
            }
        } catch (Exception ignored) {}

        String mapsUrl = "https://maps.google.com/?q=" + lat + "," + lon;
        String summary      = summarizeCrash(r.crash_report);
        String trimmedTrace = trimStack(r.crash_report);

        // App username from SharedPreferences
        String appUser;
        try {
            String prefsName = CrashReporter.config.usernameSharedPrefsName;
            String prefsKey  = CrashReporter.config.usernameKey;
            if (prefsName != null && !prefsName.isEmpty()) {
                SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
                String saved = prefs.getString(prefsKey, null);
                appUser = (saved != null && !saved.isEmpty()) ? saved : "No user set";
            } else {
                appUser = "No user set";
            }
        } catch (Exception e) {
            appUser = "No user set";
        }

        String org = CrashReporter.config.organizationName.isEmpty() ? "Crash Reporter" : CrashReporter.config.organizationName;
        StringBuilder m = new StringBuilder();
        m.append("\uD83D\uDEA8 *Automatic Crash Alert — ").append(org).append("*\n\n");
        m.append("Hi ").append(developerName).append(", a crash was reported on your system.\n\n");

        m.append("*\uD83D\uDCF1 App*\n");
        m.append("`").append(r.app_name).append("` — ").append(r.app_version).append("\n\n");

        m.append("*\uD83D\uDC64 Client / User*\n");
        m.append(r.client_name).append(" / ").append(appUser).append("\n\n");

        m.append("*\uD83D\uDD52 Time*\n").append(r.time.replace("T", " ")).append("\n\n");

        m.append("*\uD83D\uDCCD Location*\n");
        m.append(lat).append(", ").append(lon).append("\n");
        m.append(mapsUrl).append("\n\n");

        m.append("*\uD83D\uDD27 Device*\n");
        m.append(r.device_info).append(" \u00B7 ").append(r.android_version).append("\n\n");

        m.append("*\uD83D\uDC1E Where it crashed*\n").append(summary).append("\n\n");

        m.append("*Stack trace:*\n");
        m.append("```\n").append(trimmedTrace).append("\n```");

        return m.toString();
    }

    /**
     * Returns a one-line summary: first app-package frame + exception type.
     * e.g. "com.capsol.timecapture.activities.SignIn.onCreate(SignIn.java:229) — NullPointerException"
     */
    private static String summarizeCrash(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) return "Unknown";
        String[] lines = stackTrace.split("\n");
        String exceptionLine = lines[0].trim();
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("at com.capsol") || t.startsWith("at com.danzig")) {
                return t.substring(3) + " — " + exceptionLine;
            }
        }
        return exceptionLine;
    }

    /** Returns the first 15 lines of the stack trace. */
    private static String trimStack(String stackTrace) {
        if (stackTrace == null || stackTrace.isEmpty()) return "N/A";
        String[] lines = stackTrace.split("\n");
        int limit = Math.min(lines.length, 15);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(lines[i]).append("\n");
        }
        if (lines.length > limit) {
            sb.append("... ").append(lines.length - limit).append(" more lines");
        }
        return sb.toString().trim();
    }

    private static void sendWhatsAppMessage(String appName,
                                             String clientName,
                                             String timestamp,
                                             String androidVer,
                                             String appVersion,
                                             String deviceInfo,
                                             String stackTrace) {
        String WHATSAPP_TOKEN  = CrashReporter.config.whatsappToken;
        String PHONE_NUMBER_ID = CrashReporter.config.whatsappPhoneNumberId;

        if (WHATSAPP_TOKEN.isEmpty() || PHONE_NUMBER_ID.isEmpty()) {
            Log.w("CrashReporter", "WhatsApp skipped — token or phoneNumberId not configured.");
            return;
        }

        // Strategy: run the actual HTTP work on a fresh background thread (to avoid
        // NetworkOnMainThreadException when the crash originated on the main thread),
        // but block the crash-handler thread with a CountDownLatch until it finishes.
        // This ensures the JVM is still alive when OkHttp creates its internal threads,
        // while the HTTP call itself never touches the main thread.
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                ArrayList<Developer> developers = CrashReportDatabase.getInstance(CrashReporter.appContext).getAllDevelopers();

                if (developers == null || developers.isEmpty()) {
                    Log.w("CrashReporter", "WhatsApp: No developers in local DB — skipping.");
                    return;
                }

                // No connection pooling — crash reporters should not keep idle connections.
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build();

                for (Developer dev : developers) {

                    if (dev.developerPhone == null || dev.developerPhone.isEmpty()) {
                        Log.w("CrashReporter", "WhatsApp: Skipping " + dev.developerName + " — no phone number.");
                        continue;
                    }

                    try {
                        String[] paramValues = {
                                dev.developerName,               // {{1}}
                                appName,                         // {{2}} System Name
                                clientName,                      // {{3}} Client Name
                                timestamp,                       // {{4}} Time + LatLng
                                androidVer,                      // {{5}} Android Version
                                appVersion,                      // {{6}} App Version
                                deviceInfo,                      // {{7}} Device Info
                                sanitizeStackTrace(stackTrace)   // {{8}} Crash Report
                        };

                        org.json.JSONArray parameters = new org.json.JSONArray();
                        for (String value : paramValues) {
                            JSONObject param = new JSONObject();
                            param.put("type", "text");
                            param.put("text", sanitizeTemplateParam(value));
                            parameters.put(param);
                        }

                        JSONObject component = new JSONObject();
                        component.put("type", "body");
                        component.put("parameters", parameters);

                        org.json.JSONArray components = new org.json.JSONArray();
                        components.put(component);

                        JSONObject language = new JSONObject();
                        language.put("code", "en");

                        JSONObject template = new JSONObject();
                        template.put("name", "app_crash_report");
                        template.put("language", language);
                        template.put("components", components);

                        JSONObject body = new JSONObject();
                        body.put("messaging_product", "whatsapp");
                        body.put("to", formatPhone(dev.developerPhone));
                        body.put("type", "template");
                        body.put("template", template);

                        RequestBody requestBody = RequestBody.create(
                                body.toString(),
                                MediaType.parse("application/json")
                        );

                        Request request = new Request.Builder()
                                .url("https://graph.facebook.com/v19.0/" + PHONE_NUMBER_ID + "/messages")
                                .addHeader("Authorization", "Bearer " + WHATSAPP_TOKEN)
                                .addHeader("Content-Type", "application/json")
                                .post(requestBody)
                                .build();

                        try (Response response = client.newCall(request).execute()) {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                Log.d("CrashReporter", "✅ WhatsApp sent to: " + dev.developerName + " | " + responseBody);
                            } else {
                                Log.e("CrashReporter", "WhatsApp failed for: " + dev.developerName + " | " + responseBody);
                            }
                        }

                    } catch (Exception e) {
                        Log.e("CrashReporter", "WhatsApp error for " + dev.developerName
                                + ": " + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                Log.e("CrashReporter", "WhatsApp: Failed to load developers: "
                        + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        }).start();

        // Block until the background thread finishes (or 15 s timeout), so we don't
        // return before the HTTP call completes — defaultHandler would kill the process otherwise.
        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }



    private void sendEmailCrashReport(String message) {

        CrashReporterConfig cfg = CrashReporter.config;
        String FROM          = cfg.smtpFromAddress;
        String FROM_NAME     = cfg.smtpFromName;
        String HOST          = cfg.smtpHost;
        int    PORT          = cfg.smtpPort;
        String SMTP_USERNAME = cfg.smtpUsername;
        String SMTP_PASSWORD = cfg.smtpPassword;

        if (HOST.isEmpty() || SMTP_USERNAME.isEmpty() || SMTP_PASSWORD.isEmpty() || FROM.isEmpty()) {
            Log.w("Email", "Email skipped — SMTP credentials not configured.");
            return;
        }

        CountDownLatch emailLatch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                //ArrayList<Developer> developers = CrashReportDatabase.getInstance(CrashReporter.appContext).getAllDevelopers();

                // Testing: use default developer from config
                ArrayList<Developer> developers = new ArrayList<>();
                if (CrashReporter.config.defaultDeveloper != null) {
                    developers.add(CrashReporter.config.defaultDeveloper);
                }

                if (developers == null || developers.isEmpty()) {
                    Log.w("Email", "No developers in local DB — skipping email.");
                    return;
                }

                // ── Force TLSv1.2 SSL Socket Factory ─────────────────
                SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
                sslContext.init(null, null, null);
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                // ────────────────────────────────────────────────────

                // ── SMTP Properties — built once, reused for every recipient ──
                Properties props = new Properties();
                props.put("mail.smtp.auth",              "true");
                props.put("mail.smtp.starttls.enable",   "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.host",              HOST);
                props.put("mail.smtp.port",              String.valueOf(PORT));
                props.put("mail.smtp.ssl.trust",         HOST);
                props.put("mail.smtp.ssl.protocols",     "TLSv1.2");
                props.put("mail.smtp.ssl.socketFactory", sslSocketFactory);
                props.put("mail.smtp.connectiontimeout", "15000");
                props.put("mail.smtp.timeout",           "15000");
                props.put("mail.smtp.writetimeout",      "15000");
                // ────────────────────────────────────────────────────

                final String smtpUser = SMTP_USERNAME;
                final String smtpPass = SMTP_PASSWORD;

                Session session = Session.getInstance(props,
                        new javax.mail.Authenticator() {
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(smtpUser, smtpPass);
                            }
                        });

                for (Developer dev : developers) {

                    if (dev.email_address == null || dev.email_address.isEmpty()) {
                        Log.w("Email", "Skipping " + dev.developerName + " — no email address.");
                        continue;
                    }

                    try {
                        String subject   = "Crash Report - " + appName + " (" + dev.developerName + ")";
                        String htmlBody  = buildCrashEmailHtml(dev.developerName, message, appName);
                        String plainBody = message.replace("<b>", "").replace("</b>", "");

                        javax.mail.internet.MimeMultipart multipart =
                                new javax.mail.internet.MimeMultipart("alternative");

                        javax.mail.internet.MimeBodyPart textPart =
                                new javax.mail.internet.MimeBodyPart();
                        textPart.setText(plainBody, "UTF-8");

                        javax.mail.internet.MimeBodyPart htmlPart =
                                new javax.mail.internet.MimeBodyPart();
                        htmlPart.setContent(htmlBody, "text/html; charset=UTF-8");

                        multipart.addBodyPart(textPart);
                        multipart.addBodyPart(htmlPart);

                        MimeMessage email = new MimeMessage(session);
                        email.setFrom(new InternetAddress(FROM, FROM_NAME));
                        email.setRecipients(Message.RecipientType.TO,
                                InternetAddress.parse(dev.email_address));
                        email.setSubject(subject);
                        email.setContent(multipart);

                        Transport.send(email);

                        Log.d("Email", "✅ Crash report emailed to " + dev.developerName
                                + " <" + dev.email_address + ">");

                    } catch (Exception e) {
                        Log.e("Email", "❌ Email failed for " + dev.developerName
                                + ": " + e.getClass().getSimpleName() + " — " + e.getMessage());
                        if (e.getCause() != null) {
                            Log.e("Email", "❌ Caused by: " + e.getCause().getMessage());
                        }
                    }
                }

            } catch (Exception e) {
                Log.e("Email", "❌ Email: Failed to load developers: "
                        + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
            } finally {
                emailLatch.countDown();
            }
        }).start();

        try {
            emailLatch.await(60, TimeUnit.SECONDS); // email can be slower than HTTP
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
    private static void sendMaytapiWhatsAppMessage(String crashMessage) {
        String productId = CrashReporter.config.maytapiProductId;
        String phoneId   = CrashReporter.config.maytapiPhoneId;
        String apiKey    = CrashReporter.config.maytapiApiKey;

        if (productId.isEmpty() || phoneId.isEmpty() || apiKey.isEmpty()) {
            Log.w("CrashReporter", "Maytapi skipped — credentials not configured.");
            return;
        }

        String url = "https://api.maytapi.com/api/" + productId + "/" + phoneId + "/sendMessage";

        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                //ArrayList<Developer> developers = CrashReportDatabase.getInstance(CrashReporter.appContext).getAllDevelopers();

                // Testing: use default developer from config
                ArrayList<Developer> developers = new ArrayList<>();
                if (CrashReporter.config.defaultDeveloper != null) {
                    developers.add(CrashReporter.config.defaultDeveloper);
                }

                if (developers == null || developers.isEmpty()) {
                    Log.w("CrashReporter", "Maytapi: No developers in DB — skipping.");
                    return;
                }

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .writeTimeout(10, TimeUnit.SECONDS)
                        .build();

                for (Developer dev : developers) {

                    if (dev.developerPhone == null || dev.developerPhone.isEmpty()) {
                        Log.w("CrashReporter", "Maytapi: Skipping " + dev.developerName + " — no phone.");
                        continue;
                    }

                    try {
                        JSONObject body = new JSONObject();
                        body.put("to_number", formatPhone(dev.developerPhone));
                        body.put("type", "text");
                        body.put("message", crashMessage);

                        RequestBody requestBody = RequestBody.create(
                                body.toString(),
                                MediaType.parse("application/json")
                        );

                        Request request = new Request.Builder()
                                .url(url)
                                .addHeader("x-maytapi-key", apiKey)
                                .addHeader("Content-Type", "application/json")
                                .post(requestBody)
                                .build();

                        try (Response response = client.newCall(request).execute()) {
                            String responseBody = response.body() != null ? response.body().string() : "";
                            if (response.isSuccessful()) {
                                Log.d("CrashReporter", "✅ Maytapi sent to: " + dev.developerName + " | " + responseBody);
                            } else {
                                Log.e("CrashReporter", "Maytapi failed for: " + dev.developerName + " | " + responseBody);
                            }
                        }

                    } catch (Exception e) {
                        Log.e("CrashReporter", "Maytapi error for " + dev.developerName
                                + ": " + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
                    }
                }

            } catch (Exception e) {
                Log.e("CrashReporter", "Maytapi: Failed to load developers: "
                        + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        }).start();

        try {
            latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // Static system prompt — identical for every crash, eligible for prompt caching.
    private static final String CLAUDE_SYSTEM_PROMPT =
            "You are a senior Android developer. Analyze the Android crash summary provided and give:\n"
            + "1. Root cause: what likely caused this crash (1-2 sentences)\n"
            + "2. Suggested fix: how to resolve it (2-3 sentences)\n\n"
            + "Be concise and actionable.";

    private static String fetchClaudeAnalysis(String crashSummary) {
        String apiKey = CrashReporter.config.claudeApiKey;

        String[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .writeTimeout(15, TimeUnit.SECONDS)
                        .build();

                // ── System prompt with cache_control ─────────────────────
                // Marked ephemeral so Anthropic caches it for 5 min.
                // Cache hits cost 10% of normal input token price.
                JSONObject cacheControl = new JSONObject();
                cacheControl.put("type", "ephemeral");

                JSONObject systemBlock = new JSONObject();
                systemBlock.put("type", "text");
                systemBlock.put("text", CLAUDE_SYSTEM_PROMPT);
                systemBlock.put("cache_control", cacheControl);

                org.json.JSONArray systemArray = new org.json.JSONArray();
                systemArray.put(systemBlock);

                // ── User message — dynamic crash summary ─────────────────
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", "Crash summary:\n\n" + crashSummary);

                org.json.JSONArray messages = new org.json.JSONArray();
                messages.put(message);

                // ── Request body ─────────────────────────────────────────
                JSONObject body = new JSONObject();
                body.put("model", "claude-haiku-4-5-20251001");
                body.put("max_tokens", 512);
                body.put("system", systemArray);
                body.put("messages", messages);

                RequestBody requestBody = RequestBody.create(
                        body.toString(),
                        MediaType.parse("application/json")
                );

                Request request = new Request.Builder()
                        .url("https://api.anthropic.com/v1/messages")
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .addHeader("anthropic-beta", "prompt-caching-2024-07-31")
                        .addHeader("content-type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        JSONObject json = new JSONObject(response.body().string());
                        org.json.JSONArray content = json.getJSONArray("content");
                        if (content.length() > 0) {
                            result[0] = content.getJSONObject(0).getString("text");
                            // Log cache usage if available
                            if (json.has("usage")) {
                                JSONObject usage = json.getJSONObject("usage");
                                int cacheRead   = usage.optInt("cache_read_input_tokens", 0);
                                int cacheWrite  = usage.optInt("cache_creation_input_tokens", 0);
                                Log.d(TAG, "Claude analysis received. Cache read=" + cacheRead + " write=" + cacheWrite);
                            } else {
                                Log.d(TAG, "Claude analysis received successfully.");
                            }
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "no body";
                        Log.e(TAG, "Claude API error — HTTP " + response.code() + ": " + errorBody);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Claude analysis failed: " + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
            } finally {
                latch.countDown();
            }
        }).start();

        try {
            boolean completed = latch.await(20, TimeUnit.SECONDS);
            if (!completed) Log.w(TAG, "Claude analysis timed out after 20s — skipping.");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return result[0];
    }

    private String buildCrashEmailHtml(String developerName, String crashMessage, String appName) {

        CrashReporterConfig cfg2 = CrashReporter.config;
        String orgName     = cfg2.organizationName.isEmpty() ? "Crash Reporter" : cfg2.organizationName;
        String organizationName = orgName.toUpperCase();
        String supportEmail = cfg2.supportEmail;

        String year = String.valueOf(java.util.Calendar.getInstance()
                .get(java.util.Calendar.YEAR));

        // Split metadata from the stack trace
        String divider   = "<b>Crash Report:</b>\n";
        String metaPart  = crashMessage;
        String stackPart = "";
        int dividerIndex = crashMessage.indexOf(divider);
        if (dividerIndex >= 0) {
            metaPart  = crashMessage.substring(0, dividerIndex).trim();
            stackPart = crashMessage.substring(dividerIndex + divider.length()).trim();
        }

        // Split Claude analysis out of the stack part
        String claudeDivider = "<b>🤖 Claude Analysis:</b>\n";
        String claudePart    = "";
        int claudeIndex      = stackPart.indexOf(claudeDivider);
        if (claudeIndex >= 0) {
            claudePart = stackPart.substring(claudeIndex + claudeDivider.length()).trim();
            stackPart  = stackPart.substring(0, claudeIndex).trim();
        }

        // HTML-escape the raw stack trace so < > & don't corrupt the HTML
        String safeStack = stackPart
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");

        return "<!doctype html>" +
                "<html lang='en'>" +
                "<head>" +
                "  <meta charset='utf-8'>" +
                "  <meta name='viewport' content='width=device-width, initial-scale=1'>" +
                "  <title>Crash Report</title>" +
                "</head>" +
                "<body style='margin:0;padding:0;background:#f4f6f8;" +
                "font-family:Arial,Helvetica,sans-serif;'>" +
                "  <table role='presentation' width='100%' cellpadding='0'" +
                "cellspacing='0' style='background:#f4f6f8;padding:24px 0;'>" +
                "    <tr>" +
                "      <td align='center'>" +
                "        <table role='presentation' width='600' cellpadding='0'" +
                "cellspacing='0'" +
                "style='width:600px;max-width:600px;background:#ffffff;" +
                "border-radius:12px;overflow:hidden;" +
                "box-shadow:0 6px 18px rgba(0,0,0,0.08);'>" +

                // ── Header ──────────────────────────────────────────────
                "          <tr>" +
                "            <td style='background:#cc0000;padding:20px 24px;'>" +
                "              <div style='color:#ffffff;font-size:16px;" +
                "font-weight:700;letter-spacing:0.2px;'>" +
                organizationName.toUpperCase() +
                "              </div>" +
                "              <div style='color:#ffd5d5;font-size:13px;margin-top:4px;'>" +
                "                Crash Report Alert" +
                "              </div>" +
                "            </td>" +
                "          </tr>" +

                // ── Body ─────────────────────────────────────────────────
                "          <tr>" +
                "            <td style='padding:24px;'>" +
                "              <div style='font-size:18px;font-weight:700;color:#111827;'>" +
                "                &#x1F6A8; A crash was detected on your system" +
                "              </div>" +
                "              <div style='font-size:14px;color:#374151;" +
                "line-height:20px;margin-top:10px;'>" +
                "                System: <strong>" + appName + "</strong><br><br>" +
                "                The following crash was automatically detected " +
                "                and reported by the " + orgName + " crash monitoring system." +
                "              </div>" +

                // ── Crash Metadata Box ───────────────────────────────────
                "              <div style='margin-top:18px;background:#f9fafb;" +
                "border:1px solid #e5e7eb;border-radius:12px;padding:20px;'>" +
                "                <div style='font-size:13px;color:#6b7280;" +
                "letter-spacing:1px;text-transform:uppercase;margin-bottom:14px;" +
                "font-weight:700;'>" +
                "                  Crash Details" +
                "                </div>" +
                "                <div style='font-size:15px;color:#111827;" +
                "line-height:26px;white-space:pre-wrap;'>" +
                metaPart +
                "                </div>" +
                "              </div>" +

                // ── Stack Trace Box (navy blue) ──────────────────────────
                "              <div style='margin-top:16px;border-radius:10px;overflow:hidden;'>" +
                "                <div style='background:#001f3f;padding:12px 18px;" +
                "font-size:13px;font-weight:700;color:#7eb8f7;" +
                "letter-spacing:1px;text-transform:uppercase;'>" +
                "                  &#x1F41E; Stack Trace" +
                "                </div>" +
                "                <pre style='margin:0;background:#001f3f;color:#e8f4ff;" +
                "font-size:13px;line-height:22px;padding:16px 18px 18px;" +
                "white-space:pre-wrap;word-break:break-all;'>" +
                safeStack +
                "                </pre>" +
                "              </div>" +

                // ── Claude Analysis Box (green) ──────────────────────────
                (claudePart.isEmpty() ? "" :
                "              <div style='margin-top:16px;border-radius:10px;overflow:hidden;'>" +
                "                <div style='background:#064e3b;padding:12px 18px;" +
                "font-size:13px;font-weight:700;color:#6ee7b7;" +
                "letter-spacing:1px;text-transform:uppercase;'>" +
                "                  🤖 Claude Analysis" +
                "                </div>" +
                "                <div style='background:#001f3f;padding:16px 18px 18px;" +
                "font-size:14px;color:#ffffff;line-height:22px;white-space:pre-wrap;'>" +
                claudePart +
                "                </div>" +
                "              </div>") +

                "              <div style='font-size:14px;color:#6b7280;" +
                "line-height:20px;margin-top:16px;'>" +
                "                This report was automatically generated. " +
                "                Please investigate and resolve the issue as soon as possible." +
                "              </div>" +

                "              <hr style='border:none;border-top:1px solid #e5e7eb;" +
                "margin:20px 0;'>" +

                "              <div style='font-size:12px;color:#6b7280;line-height:18px;'>" +
                "                Need help? Contact support: " +
                "                <a href='mailto:" + supportEmail + "'" +
                "style='color:#cc0000;text-decoration:none;'>" +
                supportEmail +
                "                </a>" +
                "              </div>" +
                "            </td>" +
                "          </tr>" +

                // ── Footer ───────────────────────────────────────────────
                "          <tr>" +
                "            <td style='background:#f9fafb;padding:14px 24px;" +
                "font-size:11px;color:#6b7280;line-height:16px;'>" +
                "              This is an automated message from " + orgName + ". " +
                "              Please do not reply to this email.<br>" +
                "              &copy; " + year + " " + orgName + ". " +
                "              All rights reserved." +
                "            </td>" +
                "          </tr>" +

                "        </table>" +
                "        <div style='height:18px;'></div>" +
                "      </td>" +
                "    </tr>" +
                "  </table>" +
                "</body>" +
                "</html>";
    }


    // ----------------------------------------------------------
    // Build a CrashReport object from the current crash context
    // ----------------------------------------------------------
    private CrashReport buildCrashReport(Throwable throwable) {
        CrashReport report = new CrashReport();
        report.app_name        = appName;
        report.client_name     = clientName;
        report.time            = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date());
        report.android_version = "Android " + Build.VERSION.RELEASE;
        report.app_version     = getAppVersion();
        report.device_info     = Build.MANUFACTURER + "_" + Build.MODEL;
        report.crash_report    = getStackTrace(throwable);
        report.app_code        = appCode;
        return report;
    }

    // ----------------------------------------------------------
    // Save crash to local DB, then POST to Support Capture
    // ----------------------------------------------------------
    private void sendCrashTicket(CrashReport report, String appCode) {
        if (CrashReporter.config.crashTicketEndpoint.isEmpty()) {
            Log.w(TAG, "CrashTicket skipped — endpoint not configured.");
            return;
        }
        try {


            CrashReportDatabase.getInstance(CrashReporter.appContext).insertCrashReport(report);
            Log.d(TAG, "Crash report saved to local DB");

            // 2. POST to Support Capture
            final JSONObject jsonObject = new JSONObject();
            jsonObject.put("app_code", appCode);
            jsonObject.put("app_name", appName);
            jsonObject.put("app_id", appName);

            jsonObject.put("client_name", report.client_name);
            jsonObject.put("timestamp", report.time);
            jsonObject.put("device_version", report.android_version);
            jsonObject.put("app_version", report.app_version);
            jsonObject.put("device_info", report.device_info);
            jsonObject.put("crash_report", report.crash_report);


            Log.d(TAG, "CrashTicket REQUEST => " + jsonObject.toString().replace("\\/", "/"));

            // Same latch pattern as sendWhatsAppMessage: background thread avoids
            // NetworkOnMainThreadException; latch ensures it finishes before defaultHandler kills the process.
            final JSONObject finalJson = jsonObject;
            CountDownLatch ticketLatch = new CountDownLatch(1);
            new Thread(() -> {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectionPool(new okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .build();

                    RequestBody requestBody = RequestBody.create(
                            finalJson.toString(),
                            MediaType.parse("application/json; charset=utf-8")
                    );

                    Request request = new Request.Builder()
                            .url(CrashReporter.config.crashTicketEndpoint)
                            .post(requestBody)
                            .addHeader("Content-Type", "application/json")
                            .build();

                    try (Response response = client.newCall(request).execute()) {
                        if (response.isSuccessful()) {
                            String body = response.body() != null ? response.body().string() : "";
                            Log.d(TAG, "CrashTicket SUCCESS => " + body.replace("\\/", "/"));
                        } else {
                            Log.e(TAG, "CrashTicket: Request failed (HTTP " + response.code() + ")");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "CrashTicket: API connection failed — "
                            + e.getClass().getSimpleName() + " — " + e.getMessage(), e);
                } finally {
                    ticketLatch.countDown();
                }
            }).start();

            try {
                ticketLatch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

        } catch (JSONException e) {
            Log.e(TAG, "CrashTicket: Failed to build JSON", e);
        }
    }

    private static String getLocationString() {
        try {
            CrashReporterConfig.GpsProvider gps = CrashReporter.config.gpsProvider;
            if (gps == null) return "N/A (no GPS provider)";
            double[] latLon = gps.getLatLon();
            if (latLon == null || (latLon[0] == 0.0 && latLon[1] == 0.0)) return "N/A (no GPS fix)";
            return "Lat: " + latLon[0] + ", Lon: " + latLon[1];
        } catch (Exception e) {
            return "N/A";
        }
    }

    private static String sanitizeTemplateParam(String value) {
        if (value == null || value.trim().isEmpty()) return "N/A";
        String sanitized = value
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll(" {4,}", "   ")
                .trim();
        return sanitized.isEmpty() ? "N/A" : sanitized;
    }

    private static String sanitizeStackTrace(String value) {
        String sanitized = sanitizeTemplateParam(value);
        if (sanitized.length() > 700) {
            return sanitized.substring(0, 700) + "... [truncated]";
        }
        return sanitized;
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            return "v" + pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static String formatPhone(String phone) {
        // Converts 0733345345 → 254733345345
        if (phone.startsWith("0")) {
            return "254" + phone.substring(1);
        }
        return phone;
    }
}
