package com.danzig.crashreport.model;

import com.google.gson.annotations.SerializedName;

public class TelegramSyncRequest {

    @SerializedName("telegramChatId")
    public long telegramChatId;

    @SerializedName("first_name")
    public String first_name;

    @SerializedName("last_name")
    public String last_name;

    @SerializedName("language_code")
    public String language_code;

    public TelegramSyncRequest(long telegramChatId, String first_name, String last_name, String language_code) {
        this.telegramChatId = telegramChatId;
        this.first_name     = first_name;
        this.last_name      = last_name;
        this.language_code  = language_code;
    }
}