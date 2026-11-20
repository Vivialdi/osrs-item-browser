package com.osr.jei.wiki;

import java.io.IOException;

/** A repeat request was skipped because the previous attempt failed recently. */
public class WikiBackoffException extends IOException {
    public WikiBackoffException() {
        super("wiki backoff");
    }
}
