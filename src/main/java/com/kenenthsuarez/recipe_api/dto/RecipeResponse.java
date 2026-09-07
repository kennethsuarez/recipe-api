package com.kenenthsuarez.recipe_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Stored recipe")
public record RecipeResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "Garden rice bowl") String title,
        @Schema(example = "A simple fictional weekday meal.", nullable = true) String description,
        @Schema(example = "4") int servings,
        @Schema(example = "true") boolean vegetarian,
        @Schema(example = "[\"200g rice\", \"1 cucumber\"]") List<String> ingredients,
        @Schema(example = "[\"Cook the rice\", \"Top with cucumber\"]") List<String> instructions) {
    public RecipeResponse {
        ingredients = List.copyOf(ingredients);
        instructions = List.copyOf(instructions);
    }
}
