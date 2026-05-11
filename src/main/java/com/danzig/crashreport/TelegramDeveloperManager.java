package com.danzig.crashreport;

// TelegramDeveloperManager.java
import android.util.Log;

import com.danzig.crashreport.model.TelegramUpdatesResponse;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Response;

public class TelegramDeveloperManager {

    private static final String TAG = "TelegramDevManager";

    // Returns a list of all Chat IDs of developers who messaged the bot
    public static List<Long> fetchAllDeveloperChatIds() {
        List<Long> chatIds = new ArrayList<>();

        try {
            String botToken = CrashReporter.config.telegramBotToken;
            if (botToken.isEmpty()) {
                Log.w(TAG, "Telegram skipped — botToken not configured.");
                return chatIds;
            }
            String GET_UPDATES_URL = "https://api.telegram.org/bot" + botToken + "/getUpdates";

            TelegramApiService api = TelegramRetrofitClient.getInstance().getApiService();
            Call<TelegramUpdatesResponse> call = api.getUpdates(GET_UPDATES_URL, -1);
            Response<TelegramUpdatesResponse> response = call.execute();

            if (response.isSuccessful() && response.body() != null
                    && response.body().isOk()) {

                for (TelegramUpdatesResponse.Update update : response.body().getResult()) {
                    if (update.getMessage() != null
                            && update.getMessage().getChat() != null) {

                        long chatId = update.getMessage().getChat().getId();
                        String name  = update.getMessage().getChat().getFirstName();

                        if (!chatIds.contains(chatId)) {
                            chatIds.add(chatId);
                            Log.d(TAG, "Found developer: " + name + " | Chat ID: " + chatId);
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch developer chat IDs: " + e.getMessage());
        }

        return chatIds;
    }
}
