package com.kenenthsuarez.recipe_api.exception;

public class RecipeNotFoundException extends RuntimeException {
    public RecipeNotFoundException(long id) {
        super("Recipe " + id + " was not found");
    }
}
