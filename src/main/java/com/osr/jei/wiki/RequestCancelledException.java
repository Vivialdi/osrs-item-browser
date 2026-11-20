package com.osr.jei.wiki;

/** The player selected something else, or the plugin shut down, before this request finished. */
public class RequestCancelledException extends RuntimeException {
    public RequestCancelledException() {
        super("cancelled");
    }
}
