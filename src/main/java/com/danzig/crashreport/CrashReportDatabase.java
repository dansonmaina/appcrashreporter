package com.danzig.crashreport;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.danzig.crashreport.model.CrashReport;
import com.danzig.crashreport.model.Developer;
import com.danzig.crashreport.model.TelegramUser;

import java.util.ArrayList;

/**
 * Self-contained SQLite database for the crashreport library.
 * No dependency on Realm or any other ORM.
 */
public class CrashReportDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "danzig_crashreport.db";
    private static final int    DB_VERSION = 4;

    static final String TABLE_DEVELOPER     = "developer_table";
    static final String TABLE_CRASH_REPORT  = "crash_report_table";
    static final String TABLE_TELEGRAM_USER = "telegram_user_table";

    private static volatile CrashReportDatabase instance;

    public static CrashReportDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (CrashReportDatabase.class) {
                if (instance == null) {
                    instance = new CrashReportDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private CrashReportDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DEVELOPER + " ("
                + "_id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "member_no    TEXT,"
                + "developerName  TEXT,"
                + "developerPhone TEXT,"
                + "appName        TEXT,"
                + "appCode        TEXT,"
                + "clientName     TEXT,"
                + "email_address  TEXT,"
                + "telegramChatId TEXT,"
                + "sync_status    TEXT"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TELEGRAM_USER + " ("
                + "_id           INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "chatId        TEXT UNIQUE,"
                + "firstName     TEXT,"
                + "lastName      TEXT,"
                + "language_code TEXT,"
                + "sync_status   TEXT DEFAULT 'pending'"
                + ")");

        db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_CRASH_REPORT + " ("
                + "_id             INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "app_name        TEXT,"
                + "client_name     TEXT,"
                + "time            TEXT,"
                + "android_version TEXT,"
                + "app_version     TEXT,"
                + "device_info     TEXT,"
                + "crash_report    TEXT,"
                + "app_code        TEXT,"
                + "ticket_priority TEXT"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_DEVELOPER + " ADD COLUMN sync_status TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_CRASH_REPORT + " ADD COLUMN ticket_priority TEXT");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE " + TABLE_TELEGRAM_USER + " ADD COLUMN language_code TEXT");
            db.execSQL("ALTER TABLE " + TABLE_TELEGRAM_USER + " ADD COLUMN sync_status TEXT DEFAULT 'pending'");
        }
    }

    // ── Developer ────────────────────────────────────────────────────────────

    public void insertDeveloper(Developer dev) {
        getWritableDatabase().insert(TABLE_DEVELOPER, null, developerToContentValues(dev));
    }

    public void deleteAllDevelopers() {
        getWritableDatabase().delete(TABLE_DEVELOPER, null, null);
    }

    public ArrayList<Developer> getAllDevelopers() {
        ArrayList<Developer> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE_DEVELOPER, null, null, null, null, null, null)) {
            while (c.moveToNext()) {
                list.add(developerFromCursor(c));
            }
        }
        return list;
    }

    public Developer getFirstDeveloper() {
        ArrayList<Developer> devs = getAllDevelopers();
        return devs.isEmpty() ? null : devs.get(0);
    }

    public void updateTelegramChatId(String developerPhone, String chatId) {
        ContentValues cv = new ContentValues();
        cv.put("telegramChatId", chatId);
        getWritableDatabase().update(TABLE_DEVELOPER, cv, "developerPhone=?", new String[]{developerPhone});
    }

    public void clearTelegramChatId(String developerPhone) {
        updateTelegramChatId(developerPhone, "");
    }

    private ContentValues developerToContentValues(Developer dev) {
        ContentValues cv = new ContentValues();
        cv.put("member_no",      dev.member_no);
        cv.put("developerName",  dev.developerName);
        cv.put("developerPhone", dev.developerPhone);
        cv.put("appName",        dev.appName);
        cv.put("appCode",        dev.appCode);
        cv.put("clientName",     dev.clientName);
        cv.put("email_address",  dev.email_address);
        cv.put("telegramChatId", dev.telegramChatId);
        cv.put("sync_status",    dev.sync_status);
        return cv;
    }

    private Developer developerFromCursor(Cursor c) {
        Developer dev = new Developer();
        dev.member_no      = c.getString(c.getColumnIndexOrThrow("member_no"));
        dev.developerName  = c.getString(c.getColumnIndexOrThrow("developerName"));
        dev.developerPhone = c.getString(c.getColumnIndexOrThrow("developerPhone"));
        dev.appName        = c.getString(c.getColumnIndexOrThrow("appName"));
        dev.appCode        = c.getString(c.getColumnIndexOrThrow("appCode"));
        dev.clientName     = c.getString(c.getColumnIndexOrThrow("clientName"));
        dev.email_address  = c.getString(c.getColumnIndexOrThrow("email_address"));
        dev.telegramChatId = c.getString(c.getColumnIndexOrThrow("telegramChatId"));
        dev.sync_status    = c.getString(c.getColumnIndexOrThrow("sync_status"));
        return dev;
    }

    // ── TelegramUser ─────────────────────────────────────────────────────────

    public void insertOrReplaceTelegramUser(TelegramUser user) {
        ContentValues cv = new ContentValues();
        cv.put("chatId",        user.chatId);
        cv.put("firstName",     user.firstName);
        cv.put("lastName",      user.lastName);
        cv.put("language_code", user.languageCode != null ? user.languageCode : "en");
        cv.put("sync_status",   user.syncStatus   != null ? user.syncStatus   : "pending");
        getWritableDatabase().insertWithOnConflict(TABLE_TELEGRAM_USER, null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public ArrayList<TelegramUser> getUnsyncedTelegramUsers() {
        ArrayList<TelegramUser> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(
                TABLE_TELEGRAM_USER, null,
                "sync_status != ?", new String[]{"synced"},
                null, null, null)) {
            while (c.moveToNext()) {
                TelegramUser u = new TelegramUser();
                u.chatId       = c.getString(c.getColumnIndexOrThrow("chatId"));
                u.firstName    = c.getString(c.getColumnIndexOrThrow("firstName"));
                u.lastName     = c.getString(c.getColumnIndexOrThrow("lastName"));
                u.languageCode = c.getString(c.getColumnIndexOrThrow("language_code"));
                u.syncStatus   = c.getString(c.getColumnIndexOrThrow("sync_status"));
                list.add(u);
            }
        }
        return list;
    }

    public void updateTelegramUserSyncStatus(String chatId, String status) {
        ContentValues cv = new ContentValues();
        cv.put("sync_status", status);
        getWritableDatabase().update(TABLE_TELEGRAM_USER, cv, "chatId=?", new String[]{chatId});
    }

    public void deleteTelegramUserByChatId(String chatId) {
        getWritableDatabase().delete(TABLE_TELEGRAM_USER, "chatId=?", new String[]{chatId});
    }

    // ── CrashReport ──────────────────────────────────────────────────────────

    public void insertCrashReport(CrashReport report) {
        ContentValues cv = new ContentValues();
        cv.put("app_name",        report.app_name);
        cv.put("client_name",     report.client_name);
        cv.put("time",            report.time);
        cv.put("android_version", report.android_version);
        cv.put("app_version",     report.app_version);
        cv.put("device_info",     report.device_info);
        cv.put("crash_report",    report.crash_report);
        cv.put("app_code",        report.app_code);
        cv.put("ticket_priority", report.ticket_priority);
        getWritableDatabase().insert(TABLE_CRASH_REPORT, null, cv);
    }
}