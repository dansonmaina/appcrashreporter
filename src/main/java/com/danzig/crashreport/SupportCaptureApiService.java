package com.danzig.crashreport;

import com.danzig.crashreport.model.TelegramSyncRequest;
import com.danzig.crashreport.model.TelegramSyncResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;

interface SupportCaptureApiService {

    @POST
    Call<TelegramSyncResponse> syncTelegramUser(
            @Url String url,
            @Body TelegramSyncRequest request
    );
}