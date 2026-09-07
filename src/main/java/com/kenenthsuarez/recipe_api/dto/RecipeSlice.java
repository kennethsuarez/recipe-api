package com.kenenthsuarez.recipe_api.dto;

import java.util.List;

public record RecipeSlice(List<RecipeResponse> results, int page, int size, boolean hasNext) {
    public RecipeSlice {
        results = List.copyOf(results);
    }
}
