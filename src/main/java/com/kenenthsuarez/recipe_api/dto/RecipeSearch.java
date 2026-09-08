package com.kenenthsuarez.recipe_api.dto;

import java.util.List;

public record RecipeSearch(String title, Boolean vegetarian, Integer servings, List<String> includeIngredient,
                           List<String> excludeIngredient, String instruction, int page, int size) {
}
