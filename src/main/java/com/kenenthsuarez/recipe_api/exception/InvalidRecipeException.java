package com.kenenthsuarez.recipe_api.exception;

public class InvalidRecipeException extends RuntimeException {
    public InvalidRecipeException(String message) {
        super(message);
    }
}
