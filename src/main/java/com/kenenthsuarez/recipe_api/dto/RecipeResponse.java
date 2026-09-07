package com.kenenthsuarez.recipe_api.dto;

import java.util.List;

public record RecipeResponse(Long id, String title, String description, int servings,
                             boolean vegetarian, List<String> ingredients, List<String> instructions) {
    public RecipeResponse {
        ingredients = List.copyOf(ingredients);
        instructions = List.copyOf(instructions);
    }
}
