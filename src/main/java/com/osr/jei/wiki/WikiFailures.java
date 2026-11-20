package com.osr.jei.wiki;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.CompletionException;

/** User-facing copy for a failed wiki or price read. No request is started here. */
public final class WikiFailures {

    private WikiFailures() {
    }

    public static String page(Throwable error) {
        return describe(error, "this page");
    }

    public static String monster(Throwable error) {
        return describe(error, "this monster");
    }

    private static String describe(Throwable error, String subject) {
        Throwable current = unwrap(error);
        if (current instanceof WikiBackoffException) {
            return "That wiki request was not sent again. Use Retry to try now.";
        }
        if (isTimeout(current)) {
            return "The OSRS Wiki timed out while loading " + subject + ". Use Retry to try again.";
        }
        if (current instanceof IOException) {
            String message = current.getMessage() == null ? "" : current.getMessage();
            if (message.startsWith("HTTP ")) {
                return "The OSRS Wiki returned an error (" + message + ") for " + subject + ". Use Retry to try again.";
            }
            if (message.toLowerCase(Locale.ROOT).contains("maxlag")) {
                return "The OSRS Wiki is busy loading " + subject + ". Use Retry in a moment.";
            }
            return "The OSRS Wiki could not be reached for " + subject + ". Use Retry when you are back online.";
        }
        return "The OSRS Wiki could not load " + subject + ". Use Retry to try again.";
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean isTimeout(Throwable error) {
        if (error instanceof SocketTimeoutException) {
            return true;
        }
        if (error instanceof InterruptedIOException) {
            String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("timeout") || message.contains("timed out");
        }
        return false;
    }
}
