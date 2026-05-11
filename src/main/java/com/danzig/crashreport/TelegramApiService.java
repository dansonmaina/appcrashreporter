package com.danzig.crashreport;

// TelegramApiService.java
import com.danzig.crashreport.model.TelegramResponse;
import com.danzig.crashreport.model.TelegramUpdatesResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;

public interface TelegramApiService {

    @GET
    Call<TelegramResponse> sendMessage(
            @Url String url,
            @Query("chat_id") String chatId,
            @Query("text") String text,
            @Query("parse_mode") String parseMode
    );

    // ← New: fetch all users who messaged the bot
    @GET
    Call<TelegramUpdatesResponse> getUpdates(
            @Url String url,
            @Query("offset") int offset  // ← add offset param

    );
}
