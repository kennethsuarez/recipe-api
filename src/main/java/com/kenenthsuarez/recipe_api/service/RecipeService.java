package com.kenenthsuarez.recipe_api.service;

import com.kenenthsuarez.recipe_api.dto.RecipeRequest;
import com.kenenthsuarez.recipe_api.dto.RecipeResponse;
import com.kenenthsuarez.recipe_api.dto.RecipeSearch;
import com.kenenthsuarez.recipe_api.dto.RecipeSlice;

public interface RecipeService {
    RecipeResponse create(RecipeRequest request);

    RecipeResponse replace(long id, RecipeRequest request);

    void delete(long id);

    RecipeResponse get(long id);

    RecipeSlice search(RecipeSearch input);
}
