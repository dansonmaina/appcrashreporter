package com.danzig.crashreport;

// TelegramSender.java
import android.util.Log;

import com.danzig.crashreport.model.Developer;
import com.danzig.crashreport.model.TelegramResponse;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Response;

public class TelegramSender {

    private static final String TAG = "TelegramSender";

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void sendCrashReport(String message) {
        executor.execute(() -> {
            try {
                String botToken = CrashReporter.config.telegramBotToken;
                if (botToken.isEmpty()) {
                    Log.w(TAG, "Telegram skipped — botToken not configured.");
                    return;
                }
                String SEND_URL = "https://api.telegram.org/bot" + botToken + "/sendMessage";
                // Fetch all developers from your local DB

                ArrayList<Developer> developers = CrashReportDatabase.getInstance(CrashReporter.appContext).getAllDevelopers();

                if (developers == null || developers.isEmpty()) {
                    Log.w(TAG, "No developers found in local DB — skipping Telegram send.");
                    return;
                }

                TelegramApiService api = TelegramRetrofitClient.getInstance().getApiService();

                for (Developer dev : developers) {

                    // Only send if this developer has a Chat ID registered
                    if (dev.telegramChatId != null
                            && !dev.telegramChatId.isEmpty()) {

                        try {
                            Call<TelegramResponse> call = api.sendMessage(
                                    SEND_URL,
                                    dev.telegramChatId,
                                    message,
                                    ""
                            );

                            Response<TelegramResponse> response = call.execute();

                            if (response.isSuccessful()
                                    && response.body() != null
                                    && response.body().isOk()) {
                                Log.d(TAG, "Sent to: " + dev.developerName + " | Chat ID: " + dev.telegramChatId);
                            } else {
                                Log.e(TAG, "Failed for: " + dev.developerName + " | " + response.message());
                            }

                        } catch (Exception e) {
                            Log.e(TAG, "Error sending to " + dev.developerName + ": " + e.getMessage());
                        }

                    } else {
                        Log.w(TAG, "Skipping " + dev.developerName + " — no Telegram Chat ID registered.");
                    }
                }


            } catch (Exception e) {
                Log.e(TAG, "❌ sendCrashReport error: " + e.getMessage());
            }
        });
    }
}
