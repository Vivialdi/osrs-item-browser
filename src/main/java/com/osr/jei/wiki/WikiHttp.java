package com.osr.jei.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RuneLite's shared OkHttp client and Gson. Calls are enqueued on OkHttp's
 * dispatcher. A 429, 5xx, or maxlag response schedules one delayed retry and
 * never blocks a thread with sleep. Transient failures are not cached by callers.
 */
@Slf4j
@Singleton
public class WikiHttp {

    public static final String USER_AGENT =
        "OSRS-Item-Browser (RuneLite plugin; +https://github.com/Vivialdi/osrs-item-browser)";

    public static final long MAX_BACKOFF_MS = 8_000L;

    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jei-http-retry");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<Call> calls = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledFuture<?>> pendingRetries = ConcurrentHashMap.newKeySet();
    private final AtomicInteger epoch = new AtomicInteger();

    @Inject
    public WikiHttp(OkHttpClient okHttpClient, Gson gson) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    /** Cancels calls and scheduled retries. Newer selections start a new epoch. */
    public void beginRequest() {
        epoch.incrementAndGet();
        cancelOutstanding();
    }

    /** Stop the retry scheduler. Does not wait for tasks to finish. */
    public void shutdown() {
        epoch.incrementAndGet();
        cancelOutstanding();
        retries.shutdownNow();
    }

    public CompletableFuture<JsonObject> getJson(String url) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        dispatch(url, false, future, 0, epoch.get());
        return future;
    }

    public CompletableFuture<JsonObject> getWiki(String url) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        dispatch(withMaxlag(url), true, future, 0, epoch.get());
        return future;
    }

    public static String withMaxlag(String url) {
        HttpUrl parsed = HttpUrl.parse(url);
        if (parsed == null || parsed.queryParameter("maxlag") != null) {
            return url;
        }
        return parsed.newBuilder().addQueryParameter("maxlag", "5").build().toString();
    }

    public static long retryAfterMillis(String header, long fallbackMs) {
        if (header == null || header.isEmpty()) {
            return fallbackMs;
        }
        try {
            long seconds = Long.parseLong(header.trim());
            if (seconds < 0) {
                return fallbackMs;
            }
            return Math.min(MAX_BACKOFF_MS, seconds * 1000L);
        } catch (NumberFormatException e) {
            return fallbackMs;
        }
    }

    private void dispatch(String url, boolean mediawiki, CompletableFuture<JsonObject> future, int attempt, int token) {
        if (token != epoch.get() || future.isDone()) {
            future.completeExceptionally(new RequestCancelledException());
            return;
        }
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();
        Call call = okHttpClient.newCall(request);
        calls.add(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                calls.remove(call);
                if (token != epoch.get()) {
                    future.completeExceptionally(new RequestCancelledException());
                    return;
                }
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                calls.remove(call);
                try (Response responseToClose = response) {
                    if (token != epoch.get()) {
                        future.completeExceptionally(new RequestCancelledException());
                        return;
                    }
                    int code = responseToClose.code();
                    if (code == 429 || code == 503 || code >= 500) {
                        if (attempt == 0) {
                            scheduleRetry(url, mediawiki, future, token,
                                retryAfterMillis(responseToClose.header("Retry-After"), 2_000L));
                            return;
                        }
                        future.completeExceptionally(new IOException("HTTP " + code));
                        return;
                    }
                    if (!responseToClose.isSuccessful() || responseToClose.body() == null) {
                        future.completeExceptionally(new IOException("HTTP " + code));
                        return;
                    }
                    ResponseBody body = responseToClose.body();
                    JsonObject obj = gson.fromJson(body.string(), JsonObject.class);
                    if (obj == null) {
                        future.completeExceptionally(new IOException("Unexpected JSON"));
                        return;
                    }
                    if (mediawiki && isMaxlag(obj)) {
                        if (attempt == 0) {
                            scheduleRetry(url, mediawiki, future, token, 5_000L);
                            return;
                        }
                        future.completeExceptionally(new IOException("wiki maxlag"));
                        return;
                    }
                    future.complete(obj);
                } catch (Exception e) {
                    if (token != epoch.get()) {
                        future.completeExceptionally(new RequestCancelledException());
                    } else {
                        future.completeExceptionally(e);
                    }
                }
            }
        });
    }

    private void scheduleRetry(String url, boolean mediawiki, CompletableFuture<JsonObject> future, int token, long waitMs) {
        if (token != epoch.get()) {
            future.completeExceptionally(new RequestCancelledException());
            return;
        }
        long delay = Math.min(MAX_BACKOFF_MS, Math.max(0L, waitMs));
        ScheduledFuture<?> scheduled = retries.schedule(() -> {
            pendingRetries.removeIf(ScheduledFuture::isDone);
            dispatch(url, mediawiki, future, 1, token);
        }, delay, TimeUnit.MILLISECONDS);
        pendingRetries.add(scheduled);
    }

    private void cancelOutstanding() {
        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
        for (ScheduledFuture<?> scheduled : pendingRetries) {
            scheduled.cancel(false);
        }
        pendingRetries.clear();
    }

    private static boolean isMaxlag(JsonObject obj) {
        if (obj == null || !obj.has("error") || !obj.get("error").isJsonObject()) {
            return false;
        }
        JsonObject error = obj.getAsJsonObject("error");
        return error.has("code") && "maxlag".equals(error.get("code").getAsString());
    }
}
