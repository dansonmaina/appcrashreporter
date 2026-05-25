package com.danzig.crashreport;

// TelegramUserFetcher.java
import android.util.Log;

import com.danzig.crashreport.model.TelegramUpdatesResponse;
import com.danzig.crashreport.model.TelegramUser;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Response;

public class TelegramUserFetcher {

    private static final String TAG = "TelegramUserFetcher";

    public interface OnUsersFetchedListener {
        void onSuccess(List<TelegramUser> users);
        void onFailure(String errorMessage);
    }

    public static void fetchUsers(OnUsersFetchedListener listener) {

        // Run on background thread
        new Thread(() -> {
            try {
                String botToken = CrashReporter.config.telegramBotToken;
                if (botToken.isEmpty()) {
                    listener.onFailure("Telegram bot token not configured.");
                    return;
                }
                String GET_UPDATES_URL = "https://api.telegram.org/bot" + botToken + "/getUpdates";

                TelegramApiService api = TelegramRetrofitClient
                        .getInstance().getApiService();

                Call<TelegramUpdatesResponse> call = api.getUpdates(GET_UPDATES_URL, -1);

                Response<TelegramUpdatesResponse> response = call.execute();

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().isOk()) {

                    List<TelegramUser> users   = new ArrayList<>();
                    List<Long>         seenIds = new ArrayList<>();

                    CrashReportDatabase db = CrashReportDatabase.getInstance(CrashReporter.appContext);

                    for (TelegramUpdatesResponse.Update update
                            : response.body().getResult()) {

                        if (update.getMessage() != null && update.getMessage().getChat() != null) {

                            long   chatId    = update.getMessage().getChat().getId();
                            String firstName = update.getMessage().getChat().getFirstName();
                            String lastName  = update.getMessage().getChat().getLastName();
                            String username  = update.getMessage().getChat().getUsername();

                            String langCode = "en";
                            if (update.getMessage().getFrom() != null
                                    && update.getMessage().getFrom().getLanguageCode() != null) {
                                langCode = update.getMessage().getFrom().getLanguageCode();
                            }

                            if (!seenIds.contains(chatId)) {
                                seenIds.add(chatId);
                                TelegramUser user = new TelegramUser(
                                        String.valueOf(chatId), firstName, lastName, username, langCode);
                                users.add(user);
                                db.insertOrReplaceTelegramUser(user);
                                Log.d(TAG, "Found: " + firstName + " | " + chatId);
                            }
                        }
                    }

                    if (!users.isEmpty()) {
                        TelegramSyncService.syncPendingUsers();
                    }

                    if (users.isEmpty()) {
                        listener.onFailure("No Telegram users found. " +
                                "Ask developers to message the bot first.");
                    } else {
                        listener.onSuccess(users);
                    }

                } else {
                    listener.onFailure("Telegram API error: " + response.message());
                }

            } catch (Exception e) {
                listener.onFailure("Network error: " + e.getMessage());
            }
        }).start();
    }
}
