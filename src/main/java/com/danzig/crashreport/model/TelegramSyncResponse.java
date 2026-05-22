package com.danzig.crashreport.model;

import com.google.gson.annotations.SerializedName;

public class TelegramSyncResponse {

    @SerializedName("IsOkay")
    public boolean isOkay;

    @SerializedName("Message")
    public String message;

    @SerializedName("statusCode")
    public String statusCode;
}