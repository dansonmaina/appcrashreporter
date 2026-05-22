package com.danzig.crashreport;

import android.util.Log;

import com.danzig.crashreport.model.TelegramSyncRequest;
import com.danzig.crashreport.model.TelegramSyncResponse;
import com.danzig.crashreport.model.TelegramUser;

import java.util.ArrayList;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TelegramSyncService {

    private static final String TAG = "TelegramSyncService";

    /**
     * POSTs all unsynced Telegram users in the local DB to the configured
     * telegramSyncEndpoint. Records where sync_status is already "synced" are skipped.
     * On a successful response (IsOkay == true), the record is marked "synced"
     * and will never be sent again.
     */
    public static void syncPendingUsers() {
        String endpoint = CrashReporter.config.telegramSyncEndpoint;
        if (endpoint == null || endpoint.isEmpty()) {
            Log.w(TAG, "telegramSyncEndpoint not configured — skipping sync.");
            return;
        }

        new Thread(() -> {
            CrashReportDatabase db = CrashReportDatabase.getInstance(CrashReporter.appContext);
            ArrayList<TelegramUser> pending = db.getUnsyncedTelegramUsers();

            if (pending.isEmpty()) {
                Log.d(TAG, "No pending Telegram users to sync.");
                return;
            }

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://placeholder.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            SupportCaptureApiService api = retrofit.create(SupportCaptureApiService.class);

            for (TelegramUser user : pending) {
                try {
                    long chatId = Long.parseLong(user.chatId);
                    TelegramSyncRequest req = new TelegramSyncRequest(
                            chatId,
                            user.firstName,
                            user.lastName,
                            user.languageCode != null ? user.languageCode : "en"
                    );

                    Call<TelegramSyncResponse> call = api.syncTelegramUser(endpoint, req);
                    Response<TelegramSyncResponse> response = call.execute();

                    if (response.isSuccessful()
                            && response.body() != null
                            && response.body().isOkay) {

                        db.updateTelegramUserSyncStatus(user.chatId, "synced");
                        Log.d(TAG, "Synced: " + user.chatId + " — " + response.body().message);
                    } else {
                        Log.w(TAG, "Sync rejected for chatId=" + user.chatId
                                + " | HTTP " + response.code());
                    }

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid chatId format: " + user.chatId);
                } catch (Exception e) {
                    Log.e(TAG, "Error syncing chatId=" + user.chatId + ": " + e.getMessage());
                }
            }
        }).start();
    }
}