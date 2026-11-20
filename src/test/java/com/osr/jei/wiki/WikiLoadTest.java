package com.osr.jei.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WikiLoadTest {

    @Test
    public void cachedPageIsNotFetchedTwice() {
        FakeHttp http = new FakeHttp();
        try {
            WikiPages pages = new WikiPages(http, () -> 1_000L);
            PageContent first = pages.load("Rune platebody").join();
            PageContent second = pages.load("rune platebody").join();
            assertEquals(1, http.calls.get());
            assertEquals("Rune platebody", first.wikitext);
            assertEquals(first.wikitext, second.wikitext);
            assertFalse(second.missing);
            assertTrue(pages.load("Missing page").join().missing);
            assertTrue(pages.load("Missing page").join().missing);
            assertEquals(2, http.calls.get());
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void concurrentRequestsForTheSamePageShareOneCall() throws Exception {
        FakeHttp http = new FakeHttp();
        try {
            WikiPages pages = new WikiPages(http, () -> 1_000L);
            CompletableFuture<PageContent> first = pages.load("Abyssal whip");
            assertTrue(http.entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<PageContent> second = pages.load("Abyssal whip");
            assertEquals(1, http.calls.get());
            assertSame(first, second);
            http.blocked.complete(page("Abyssal whip"));
            assertEquals("Abyssal whip", first.join().wikitext);
            assertEquals("Abyssal whip", second.join().wikitext);
            assertEquals(1, http.calls.get());
            assertTrue(pages.size() <= WikiPages.CACHE_SIZE);
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void pageCacheIsBoundedAndExpires() {
        FakeHttp http = new FakeHttp();
        try {
            long[] now = {5_000L};
            WikiPages pages = new WikiPages(http, () -> now[0]);
            for (int i = 0; i < WikiPages.CACHE_SIZE + 3; i++) {
                pages.load("Item " + i).join();
            }
            assertEquals(WikiPages.CACHE_SIZE + 3, http.calls.get());
            assertEquals(WikiPages.CACHE_SIZE, pages.size());
            pages.load("Item " + (WikiPages.CACHE_SIZE + 2)).join();
            assertEquals(WikiPages.CACHE_SIZE + 3, http.calls.get());
            pages.load("Item 0").join();
            assertEquals(WikiPages.CACHE_SIZE + 4, http.calls.get());

            int beforeExpiry = http.calls.get();
            pages.load("Item " + (WikiPages.CACHE_SIZE + 2)).join();
            assertEquals(beforeExpiry, http.calls.get());
            now[0] += WikiPages.TTL_MS;
            pages.load("Item " + (WikiPages.CACHE_SIZE + 2)).join();
            assertEquals(beforeExpiry + 1, http.calls.get());
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void failureBackoffDoesNotCallAgainUntilRetry() {
        FakeHttp http = new FakeHttp();
        try {
            long[] now = {20_000L};
            WikiPages pages = new WikiPages(http, () -> now[0]);
            http.failure = new IOException("HTTP 503");
            assertFailed(pages.load("Coal"));
            assertEquals(1, http.calls.get());
            now[0] += 1_000L;
            assertFailed(pages.load("Coal"));
            assertEquals(1, http.calls.get());

            http.failure = new RequestCancelledException();
            pages.allowRetry("Coal");
            assertFailed(pages.load("Coal"));
            assertEquals(2, http.calls.get());
            http.failure = null;
            assertFalse(pages.load("Coal").join().missing);
            assertEquals(3, http.calls.get());
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void httpClientKeepsTheInjectedDispatcherAndSetsTimeouts() {
        OkHttpClient base = new OkHttpClient();
        WikiHttp http = new WikiHttp(base, new Gson());
        try {
            assertSame(base.dispatcher(), http.httpClient().dispatcher());
            assertSame(base.connectionPool(), http.httpClient().connectionPool());
            assertEquals(WikiHttp.CONNECT_TIMEOUT_SECONDS * 1000, http.httpClient().connectTimeoutMillis());
            assertEquals(WikiHttp.READ_TIMEOUT_SECONDS * 1000, http.httpClient().readTimeoutMillis());
            assertEquals(WikiHttp.CALL_TIMEOUT_SECONDS * 1000, http.httpClient().callTimeoutMillis());
            assertTrue(WikiHttp.USER_AGENT.contains("OSRS-Item-Browser"));
            assertTrue(WikiHttp.USER_AGENT.contains("RuneLite"));
            assertTrue(WikiHttp.USER_AGENT.contains("github.com/Vivialdi/osrs-item-browser"));
        } finally {
            http.shutdown();
        }
    }

    @Test
    public void failureCopyNamesTimeoutsBusyPagesAndBackoff() {
        assertTrue(WikiFailures.page(new InterruptedIOException("timeout")).contains("timed out"));
        assertTrue(WikiFailures.page(new IOException("HTTP 503")).contains("HTTP 503"));
        assertTrue(WikiFailures.page(new IOException("wiki maxlag")).contains("busy"));
        assertTrue(WikiFailures.page(new IOException("connection reset")).contains("could not be reached"));
        assertTrue(WikiFailures.page(new WikiBackoffException()).contains("Retry"));
        assertTrue(WikiFailures.monster(new IOException("down")).contains("monster"));
        assertTrue(com.osr.jei.service.ItemDetails.from(PageContent.MISSING).missing);
        assertFalse(com.osr.jei.service.ItemDetails.EMPTY.missing);
    }

    private static void assertFailed(CompletableFuture<PageContent> future) {
        try {
            future.join();
            fail("expected the wiki load to fail");
        } catch (CompletionException expected) {
            assertTrue(expected.getCause() != null);
        }
    }

    private static JsonObject page(String marker) {
        JsonObject star = new JsonObject();
        star.addProperty("*", marker);
        JsonObject parse = new JsonObject();
        parse.add("wikitext", star);
        parse.add("text", star);
        JsonObject root = new JsonObject();
        root.add("parse", parse);
        return root;
    }

    private static final class FakeHttp extends WikiHttp {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CompletableFuture<JsonObject> blocked = new CompletableFuture<>();
        private volatile Throwable failure;
        private int loads;

        private FakeHttp() {
            super(new OkHttpClient(), new Gson());
        }

        @Override
        public CompletableFuture<JsonObject> getWiki(String url) {
            int n = calls.incrementAndGet();
            entered.countDown();
            if (blocked != null && loads == 0 && failure == null && url.contains("Abyssal_whip")) {
                loads++;
                return blocked;
            }
            if (failure != null) {
                CompletableFuture<JsonObject> failed = new CompletableFuture<>();
                failed.completeExceptionally(failure);
                return failed;
            }
            if (url.toLowerCase().contains("missing")) {
                return CompletableFuture.completedFuture(new JsonObject());
            }
            String marker = "page-" + n;
            int item = url.lastIndexOf("Item");
            if (item >= 0) {
                marker = url.substring(item).replace('_', ' ');
            } else if (url.toLowerCase().contains("rune")) {
                marker = "Rune platebody";
            }
            return CompletableFuture.completedFuture(page(marker));
        }
    }
}
