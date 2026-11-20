package com.osr.jei.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
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
    private final Set<Call> stableCalls = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledFuture<?>> pendingRetries = ConcurrentHashMap.newKeySet();
    private final Set<ScheduledFuture<?>> stableRetries = ConcurrentHashMap.newKeySet();
    private final AtomicInteger epoch = new AtomicInteger();
    /** Bumped only on shutdown, so a session-long fetch survives item changes. */
    private final AtomicInteger lifetime = new AtomicInteger();

    @Inject
    public WikiHttp(OkHttpClient okHttpClient, Gson gson) {
        this.okHttpClient = okHttpClient;
        this.gson = gson;
    }

    /** Cancels item-page calls and their retries. Session-long fetches stay up. */
    public void beginRequest() {
        epoch.incrementAndGet();
        cancel(calls, pendingRetries);
    }

    /** Stop the retry scheduler. Does not wait for tasks to finish. */
    public void shutdown() {
        epoch.incrementAndGet();
        lifetime.incrementAndGet();
        cancel(calls, pendingRetries);
        cancel(stableCalls, stableRetries);
        retries.shutdownNow();
    }

    public CompletableFuture<JsonObject> getJson(String url) {
        return requestObject(url, false, false);
    }

    public CompletableFuture<JsonObject> getWiki(String url) {
        return requestObject(withMaxlag(url), true, false);
    }

    /**
     * One JSON value, array or object, that is not cancelled when the open
     * item changes. Shutdown still cancels it. Same User-Agent and retry as
     * {@link #getJson(String)}.
     */
    public CompletableFuture<JsonElement> getStableElement(String url) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        dispatch(url, false, true, future, 0, lifetime.get());
        return future;
    }

    private CompletableFuture<JsonObject> requestObject(String url, boolean mediawiki, boolean stable) {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        int token = stable ? lifetime.get() : epoch.get();
        dispatch(url, mediawiki, stable, future, 0, token);
        return future.thenApply(WikiHttp::requireObject);
    }

    private static JsonObject requireObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalStateException("Unexpected JSON");
        }
        return element.getAsJsonObject();
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

    private void dispatch(String url, boolean mediawiki, boolean stable, CompletableFuture<JsonElement> future, int attempt, int token) {
        if (token != tokenNow(stable) || future.isDone()) {
            future.completeExceptionally(new RequestCancelledException());
            return;
        }
        Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();
        Call call = okHttpClient.newCall(request);
        Set<Call> bucket = stable ? stableCalls : calls;
        bucket.add(call);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                bucket.remove(call);
                if (token != tokenNow(stable)) {
                    future.completeExceptionally(new RequestCancelledException());
                    return;
                }
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                bucket.remove(call);
                try (Response responseToClose = response) {
                    if (token != tokenNow(stable)) {
                        future.completeExceptionally(new RequestCancelledException());
                        return;
                    }
                    int code = responseToClose.code();
                    if (code == 429 || code == 503 || code >= 500) {
                        if (attempt == 0) {
                            scheduleRetry(url, mediawiki, stable, future, token,
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
                    JsonElement element = gson.fromJson(body.string(), JsonElement.class);
                    if (element == null || element.isJsonNull()) {
                        future.completeExceptionally(new IOException("Unexpected JSON"));
                        return;
                    }
                    if (mediawiki && element.isJsonObject() && isMaxlag(element.getAsJsonObject())) {
                        if (attempt == 0) {
                            scheduleRetry(url, mediawiki, stable, future, token, 5_000L);
                            return;
                        }
                        future.completeExceptionally(new IOException("wiki maxlag"));
                        return;
                    }
                    future.complete(element);
                } catch (Exception e) {
                    if (token != tokenNow(stable)) {
                        future.completeExceptionally(new RequestCancelledException());
                    } else {
                        future.completeExceptionally(e);
                    }
                }
            }
        });
    }

    private void scheduleRetry(
        String url,
        boolean mediawiki,
        boolean stable,
        CompletableFuture<JsonElement> future,
        int token,
        long waitMs
    ) {
        if (token != tokenNow(stable)) {
            future.completeExceptionally(new RequestCancelledException());
            return;
        }
        long delay = Math.min(MAX_BACKOFF_MS, Math.max(0L, waitMs));
        Set<ScheduledFuture<?>> bucket = stable ? stableRetries : pendingRetries;
        ScheduledFuture<?> scheduled = retries.schedule(() -> {
            bucket.removeIf(ScheduledFuture::isDone);
            dispatch(url, mediawiki, stable, future, 1, token);
        }, delay, TimeUnit.MILLISECONDS);
        bucket.add(scheduled);
    }

    private int tokenNow(boolean stable) {
        return stable ? lifetime.get() : epoch.get();
    }

    private static void cancel(Set<Call> calls, Set<ScheduledFuture<?>> retries) {
        for (Call call : calls) {
            call.cancel();
        }
        calls.clear();
        for (ScheduledFuture<?> scheduled : retries) {
            scheduled.cancel(false);
        }
        retries.clear();
    }

    private static boolean isMaxlag(JsonObject obj) {
        if (obj == null || !obj.has("error") || !obj.get("error").isJsonObject()) {
            return false;
        }
        JsonObject error = obj.getAsJsonObject("error");
        return error.has("code") && "maxlag".equals(error.get("code").getAsString());
    }
}
