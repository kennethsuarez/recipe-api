package com.kenenthsuarez.recipe_api.repository;

public record RecipeRoot(Long id, String title, String description, int servings, boolean vegetarian) {
}
