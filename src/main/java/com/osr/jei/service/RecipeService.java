package com.osr.jei.service;

import com.osr.jei.model.RecipeInfo;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Recipes taken from the shared one-request item page. */
@Singleton
public class RecipeService {

    private final ItemDetailsService items;

    @Inject
    public RecipeService(ItemDetailsService items) {
        this.items = items;
    }

    public CompletableFuture<List<RecipeInfo>> getRecipes(String itemName) {
        return items.load(itemName).thenApply(details -> details.recipes);
    }
}
