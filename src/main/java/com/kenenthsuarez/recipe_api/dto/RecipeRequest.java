package com.kenenthsuarez.recipe_api.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record RecipeRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 10000) String description,
        @NotNull @Positive Integer servings,
        Boolean vegetarian,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 1000) String> ingredients,
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> instructions) {
}
