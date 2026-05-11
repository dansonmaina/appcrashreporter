package com.danzig.crashreport.model;

import java.io.Serializable;

public class TelegramUser implements Serializable {

    public String chatId;
    public String firstName;
    public String lastName;

    public TelegramUser() {}

    public TelegramUser(String chatId, String firstName, String lastName) {
        this.chatId     = chatId;
        this.firstName  = firstName;
        this.lastName   = lastName;
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