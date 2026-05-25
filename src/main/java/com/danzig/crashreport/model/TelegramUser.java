package com.danzig.crashreport.model;

import java.io.Serializable;

public class TelegramUser implements Serializable {

    public String chatId;
    public String firstName;
    public String lastName;
    public String username;
    public String languageCode;
    public String syncStatus;

    public TelegramUser() {}

    public TelegramUser(String chatId, String firstName, String lastName) {
        this.chatId        = chatId;
        this.firstName     = firstName;
        this.lastName      = lastName;
        this.username      = null;
        this.languageCode  = "en";
        this.syncStatus    = "pending";
    }

    public TelegramUser(String chatId, String firstName, String lastName, String languageCode) {
        this.chatId        = chatId;
        this.firstName     = firstName;
        this.lastName      = lastName;
        this.username      = null;
        this.languageCode  = languageCode != null ? languageCode : "en";
        this.syncStatus    = "pending";
    }

    public TelegramUser(String chatId, String firstName, String lastName, String username, String languageCode) {
        this.chatId        = chatId;
        this.firstName     = firstName;
        this.lastName      = lastName;
        this.username      = username;
        this.languageCode  = languageCode != null ? languageCode : "en";
        this.syncStatus    = "pending";
    }

    public String getChatId() { return chatId; }

    public String getFullName() {
        if (lastName != null && !lastName.isEmpty()) {
            return firstName + " " + lastName;
        }
        return firstName != null ? firstName : "";
    }

    @Override
    public String toString() {
        return getFullName() + "  [" + chatId + "]";
    }
}