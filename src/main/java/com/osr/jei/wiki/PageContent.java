package com.osr.jei.wiki;

/** One successful MediaWiki parse. Not retained after it has been turned into structured data. */
public final class PageContent {

    public static final PageContent MISSING = new PageContent("", "", true);

    public final String wikitext;
    public final String html;
    public final boolean missing;

    public PageContent(String wikitext, String html, boolean missing) {
        this.wikitext = wikitext == null ? "" : wikitext;
        this.html = html == null ? "" : html;
        this.missing = missing;
    }
}
