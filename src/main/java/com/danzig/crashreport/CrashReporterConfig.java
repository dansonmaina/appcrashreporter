package com.danzig.crashreport;

import com.danzig.crashreport.model.Developer;

/**
 * Holds the API credentials and optional providers needed by the crashreport library.
 *
 * Usage in Application.onCreate():
 *
 *     CrashReporter.install(this, developer,
 *         new CrashReporterConfig.Builder()
 *             .whatsappToken("EAAUo...")
 *             .whatsappPhoneNumberId("107011...")
 *             .telegramBotToken("8290414422:AAEv...")
 *             .gpsProvider(() -> new double[]{DatabaseManager.gps.getLatitude(),
 *                                             DatabaseManager.gps.getLongitude()})
 *             .usernameSharedPrefsName("MY_PREFS_NAME")
 *             .usernameKey("username")
 *             .build()
 *     );
 */
public final class CrashReporterConfig {

    /** Supplies the device's current lat/lon at crash time. Return null if unavailable. */
    public interface GpsProvider {
        /** @return double[]{lat, lon}, or null if location is unavailable. */
        double[] getLatLon();
    }

    // ── Channel enable/disable flags ─────────────────────────────────────────

    /** Whether to POST a crash ticket to the Support Capture backend. */
    public final boolean enableCrashTicket;

    /** Whether to send crash reports via Telegram. */
    public final boolean enableTelegram;

    /** Whether to send via the WhatsApp Cloud API template. */
    public final boolean enableWhatsAppCloud;

    /** Whether to send crash reports via email (AWS SES). */
    public final boolean enableEmail;

    /** Whether to send crash reports via Maytapi WhatsApp API. */
    public final boolean enableMaytapi;

    /** Whether to request a Claude AI analysis of the crash and append it to notifications. */
    public final boolean enableClaudeAnalysis;

    /** Anthropic API key for Claude crash analysis. Leave empty to disable. */
    public final String claudeApiKey;

    // ── Branding ─────────────────────────────────────────────────────────────

    /** Organization/company name shown in notification messages and emails. */
    public final String organizationName;

    /** Support email address shown in the crash email footer. */
    public final String supportEmail;

    // ── Email (SMTP / AWS SES) ───────────────────────────────────────────────

    /** SMTP host. Defaults to AWS SES eu-west-2. */
    public final String smtpHost;

    /** SMTP port. Defaults to 587 (STARTTLS). */
    public final int smtpPort;

    /** SMTP auth username (AWS SES SMTP credential). */
    public final String smtpUsername;

    /** SMTP auth password (AWS SES SMTP credential). */
    public final String smtpPassword;

    /** Sender address shown in the From field. */
    public final String smtpFromAddress;

    /** Sender display name shown in the From field. */
    public final String smtpFromName;

    // ── Support Capture backend ──────────────────────────────────────────────

    /** Endpoint that receives crash tickets. */
    public final String crashTicketEndpoint;

    // ── API credentials ──────────────────────────────────────────────────────

    /** WhatsApp Cloud API permanent token from Meta Developer Console. */
    public final String whatsappToken;

    /** WhatsApp Business Phone Number ID from Meta Developer Console. */
    public final String whatsappPhoneNumberId;

    /** Telegram Bot token from @BotFather. */
    public final String telegramBotToken;

    /** Maytapi Product ID from maytapi.com dashboard. */
    public final String maytapiProductId;

    /** Maytapi Phone ID (the WhatsApp instance ID) from maytapi.com dashboard. */
    public final String maytapiPhoneId;

    /** Maytapi API key from maytapi.com dashboard. */
    public final String maytapiApiKey;

    // ── Optional providers ───────────────────────────────────────────────────

    /** Called at crash time to get the device GPS coordinates. */
    public final GpsProvider gpsProvider;

    /** SharedPreferences file name where the signed-in username is stored. */
    public final String usernameSharedPrefsName;

    /** Key inside the SharedPreferences file that holds the username. */
    public final String usernameKey;

    /**
     * Fallback developer inserted into the DB on first launch when the table is empty.
     * Ensures crash reports are always delivered even before the server populates the list.
     */
    public final Developer defaultDeveloper;

    private CrashReporterConfig(Builder b) {
        this.enableCrashTicket      = b.enableCrashTicket;
        this.enableTelegram         = b.enableTelegram;
        this.enableWhatsAppCloud    = b.enableWhatsAppCloud;
        this.enableEmail            = b.enableEmail;
        this.enableMaytapi          = b.enableMaytapi;
        this.enableClaudeAnalysis   = b.enableClaudeAnalysis;
        this.claudeApiKey           = b.claudeApiKey;
        this.organizationName       = b.organizationName;
        this.supportEmail           = b.supportEmail;
        this.smtpHost               = b.smtpHost;
        this.smtpPort               = b.smtpPort;
        this.smtpUsername           = b.smtpUsername;
        this.smtpPassword           = b.smtpPassword;
        this.smtpFromAddress        = b.smtpFromAddress;
        this.smtpFromName           = b.smtpFromName;
        this.crashTicketEndpoint    = b.crashTicketEndpoint;
        this.whatsappToken          = b.whatsappToken;
        this.whatsappPhoneNumberId  = b.whatsappPhoneNumberId;
        this.telegramBotToken       = b.telegramBotToken;
        this.maytapiProductId       = b.maytapiProductId;
        this.maytapiPhoneId         = b.maytapiPhoneId;
        this.maytapiApiKey          = b.maytapiApiKey;
        this.gpsProvider            = b.gpsProvider;
        this.usernameSharedPrefsName = b.usernameSharedPrefsName;
        this.usernameKey            = b.usernameKey;
        this.defaultDeveloper       = b.defaultDeveloper;
    }

    public static final class Builder {

        private boolean     enableCrashTicket      = true;
        private boolean     enableTelegram         = true;
        private boolean     enableWhatsAppCloud    = true;
        private boolean     enableEmail            = true;
        private boolean     enableMaytapi          = true;
        private boolean     enableClaudeAnalysis   = true;
        private String      claudeApiKey           = "";

        private String      organizationName       = "";
        private String      supportEmail           = "";

        private String      smtpHost               = "";
        private int         smtpPort               = 587;
        private String      smtpUsername           = "";
        private String      smtpPassword           = "";
        private String      smtpFromAddress        = "";
        private String      smtpFromName           = "";
        private String      crashTicketEndpoint    = "";

        private String      whatsappToken          = "";
        private String      whatsappPhoneNumberId  = "";
        private String      telegramBotToken       = "";
        private String      maytapiProductId       = "";
        private String      maytapiPhoneId         = "";
        private String      maytapiApiKey          = "";
        private GpsProvider gpsProvider            = null;
        private String      usernameSharedPrefsName = "";
        private String      usernameKey            = "username";
        private Developer   defaultDeveloper       = null;

        public Builder enableCrashTicket(boolean enabled) {
            this.enableCrashTicket = enabled;
            return this;
        }

        public Builder enableTelegram(boolean enabled) {
            this.enableTelegram = enabled;
            return this;
        }

        public Builder enableWhatsAppCloud(boolean enabled) {
            this.enableWhatsAppCloud = enabled;
            return this;
        }

        public Builder enableEmail(boolean enabled) {
            this.enableEmail = enabled;
            return this;
        }

        public Builder enableMaytapi(boolean enabled) {
            this.enableMaytapi = enabled;
            return this;
        }

        public Builder enableClaudeAnalysis(boolean enabled) {
            this.enableClaudeAnalysis = enabled;
            return this;
        }

        public Builder claudeApiKey(String apiKey) {
            this.claudeApiKey = apiKey != null ? apiKey : "";
            return this;
        }

        public Builder organizationName(String name) {
            this.organizationName = name != null ? name : "";
            return this;
        }

        public Builder supportEmail(String email) {
            this.supportEmail = email != null ? email : "";
            return this;
        }

        public Builder smtpHost(String host) {
            this.smtpHost = host != null ? host : "";
            return this;
        }

        public Builder smtpPort(int port) {
            this.smtpPort = port;
            return this;
        }

        public Builder smtpUsername(String username) {
            this.smtpUsername = username != null ? username : "";
            return this;
        }

        public Builder smtpPassword(String password) {
            this.smtpPassword = password != null ? password : "";
            return this;
        }

        public Builder smtpFromAddress(String address) {
            this.smtpFromAddress = address != null ? address : "";
            return this;
        }

        public Builder smtpFromName(String name) {
            this.smtpFromName = name != null ? name : "";
            return this;
        }

        public Builder crashTicketEndpoint(String url) {
            this.crashTicketEndpoint = url != null ? url : "";
            return this;
        }

        public Builder whatsappToken(String token) {
            this.whatsappToken = token != null ? token : "";
            return this;
        }

        public Builder whatsappPhoneNumberId(String id) {
            this.whatsappPhoneNumberId = id != null ? id : "";
            return this;
        }

        public Builder telegramBotToken(String token) {
            this.telegramBotToken = token != null ? token : "";
            return this;
        }

        /** Maytapi Product ID from maytapi.com dashboard. */
        public Builder maytapiProductId(String productId) {
            this.maytapiProductId = productId != null ? productId : "";
            return this;
        }

        /** Maytapi Phone ID (the WhatsApp instance ID) from maytapi.com dashboard. */
        public Builder maytapiPhoneId(String phoneId) {
            this.maytapiPhoneId = phoneId != null ? phoneId : "";
            return this;
        }

        /** Maytapi API key from maytapi.com dashboard. */
        public Builder maytapiApiKey(String apiKey) {
            this.maytapiApiKey = apiKey != null ? apiKey : "";
            return this;
        }

        /** Provide a lambda/callback that returns [lat, lon] at crash time. */
        public Builder gpsProvider(GpsProvider provider) {
            this.gpsProvider = provider;
            return this;
        }

        /** Name of the SharedPreferences file where the signed-in username is saved. */
        public Builder usernameSharedPrefsName(String name) {
            this.usernameSharedPrefsName = name != null ? name : "";
            return this;
        }

        /** Key inside the SharedPreferences file. Defaults to "username". */
        public Builder usernameKey(String key) {
            this.usernameKey = key != null ? key : "username";
            return this;
        }

        /** Fallback developer used when the developer table is empty on first launch. */
        public Builder defaultDeveloper(Developer developer) {
            this.defaultDeveloper = developer;
            return this;
        }

        public CrashReporterConfig build() {
            return new CrashReporterConfig(this);
        }
    }
}
