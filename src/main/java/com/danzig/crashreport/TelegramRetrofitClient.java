package com.danzig.crashreport;

// TelegramRetrofitClient.java
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TelegramRetrofitClient {

    private static final String BASE_URL = "https://api.telegram.org/";
    private static TelegramRetrofitClient instance;
    private final TelegramApiService apiService;

    private TelegramRetrofitClient() {
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(TelegramApiService.class);
    }

    public static synchronized TelegramRetrofitClient getInstance() {
        if (instance == null) {
            instance = new TelegramRetrofitClient();
        }
        return instance;
    }

    public TelegramApiService getApiService() {
        return apiService;
    }
}
