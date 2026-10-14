package com.taha1337.btclottery;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LotteryApp extends Application {
    static final String ENDPOINT = "https://btc-hash-credential-server.btc-nonce-668442ed.workers.dev";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    SharedPreferences prefs;
    boolean busy;
    String message = "";
    JSONObject latest;
    Runnable listener;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("checker", MODE_PRIVATE);
        if (prefs.getBoolean("pending", false)) {
            message = "The app closed before a check returned. Its outcome is unknown; it was not automatically retried.";
            prefs.edit().putBoolean("pending", false).apply();
        }
    }

    JSONArray history() {
        try { return new JSONArray(prefs.getString("history", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    void clear() {
        prefs.edit().clear().apply();
        latest = null;
        message = "Saved address and history cleared.";
    }

    void check(String address, List<Long> values) {
        if (busy) return;
        busy = true;
        message = "";
        latest = null;
        prefs.edit().putBoolean("pending", true).apply();
        executor.execute(() -> {
            JSONObject result = null;
            String failure = "";
            HttpURLConnection connection = null;
            try {
                JSONObject body = new JSONObject().put("payout_address", address).put("nonces", new JSONArray(values));
                connection = (HttpURLConnection) new URL(ENDPOINT + "/check-nonces").openConnection();
                connection.setRequestMethod("POST");
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(150000);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                if (stream == null) throw new IllegalStateException("The service returned no response.");
                String response;
                try (stream; var output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = stream.read(buffer)) != -1) {
                        if (output.size() + count > 131072) throw new IllegalStateException("The service returned an oversized response.");
                        output.write(buffer, 0, count);
                    }
                    response = output.toString(StandardCharsets.UTF_8.name());
                }
                JSONObject data;
                try { data = new JSONObject(response); }
                catch (Exception invalid) { throw new IllegalStateException("The service returned an unreadable response. Outcome unknown; no automatic retry."); }
                if (status != 200) {
                    throw new IllegalStateException(status == 404 ? "The app endpoint is not deployed yet." : data.optString("error", "The service is unavailable. Please try later."));
                }
                if (data.optJSONArray("results") == null || data.optJSONObject("submission") == null) throw new IllegalStateException("Incomplete service response. Outcome unknown; no automatic retry.");
                result = data;
            } catch (IllegalStateException error) {
                failure = error.getMessage();
            } catch (Exception error) {
                failure = "Connection interrupted. The check may have reached the service; its outcome is unknown. No automatic retry was sent.";
            } finally {
                if (connection != null) connection.disconnect();
            }
            JSONObject completed = result;
            String completedMessage = failure;
            handler.post(() -> {
                busy = false;
                latest = completed;
                message = completedMessage;
                try {
                    JSONObject record = new JSONObject().put("time", System.currentTimeMillis()).put("address", address)
                            .put("count", values.size()).put("error", completedMessage);
                    if (completed != null) record.put("response", completed);
                    JSONArray previous = history();
                    JSONArray next = new JSONArray().put(record);
                    for (int i = 0; i < Math.min(previous.length(), 19); i++) next.put(previous.get(i));
                    prefs.edit().putBoolean("pending", false).putString("history", next.toString()).apply();
                } catch (Exception ignored) { prefs.edit().putBoolean("pending", false).apply(); }
                if (listener != null) listener.run();
            });
        });
    }
}
