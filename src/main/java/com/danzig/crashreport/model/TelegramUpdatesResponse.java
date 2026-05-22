package com.danzig.crashreport.model;

// TelegramUpdatesResponse.java
import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TelegramUpdatesResponse {

    @SerializedName("ok")
    private boolean ok;

    @SerializedName("result")
    private List<Update> result;

    public boolean isOk() { return ok; }
    public List<Update> getResult() { return result; }

    public static class Update {
        @SerializedName("message")
        private Message message;
        public Message getMessage() { return message; }
    }

    public static class Message {
        @SerializedName("chat")
        private Chat chat;

        @SerializedName("from")
        private From from;

        public Chat getChat() { return chat; }
        public From getFrom() { return from; }
    }

    public static class From {
        @SerializedName("language_code")
        private String languageCode;

        public String getLanguageCode() { return languageCode; }
    }

    public static class Chat {
        @SerializedName("id")
        private long id;

        @SerializedName("first_name")
        private String firstName;

        @SerializedName("last_name")
        private String lastName;

        @SerializedName("username")
        private String username;

        public long getId() { return id; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getUsername() { return username; }
    }
}
