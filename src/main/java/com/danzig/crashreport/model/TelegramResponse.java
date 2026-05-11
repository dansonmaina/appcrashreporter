package com.danzig.crashreport.model;

// TelegramResponse.java
import com.google.gson.annotations.SerializedName;


public class TelegramResponse {

    @SerializedName("ok")
    private boolean ok;

    @SerializedName("description")
    private String description;

    public boolean isOk() { return ok; }
    public String getDescription() { return description; }
}
