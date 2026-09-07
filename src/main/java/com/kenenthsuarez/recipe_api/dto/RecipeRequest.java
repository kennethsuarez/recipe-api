package com.kenenthsuarez.recipe_api.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Complete recipe payload used for creation and replacement")
public record RecipeRequest(
        @Schema(example = "Garden rice bowl") @NotBlank @Size(max = 200) String title,
        @Schema(example = "A simple fictional weekday meal.", nullable = true)
        @Size(max = 10000) String description,
        @Schema(example = "4", minimum = "1") @NotNull @Positive Integer servings,
        @Schema(example = "true", defaultValue = "false") Boolean vegetarian,
        @ArraySchema(minItems = 1, maxItems = 100,
                schema = @Schema(example = "200g rice", maxLength = 1000))
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 1000) String> ingredients,
        @ArraySchema(maxItems = 100, schema = @Schema(example = "Cook the rice", maxLength = 2000),
                arraySchema = @Schema(description = "Optional; null is normalized to an empty list"))
        @Size(max = 100) List<@NotBlank @Size(max = 2000) String> instructions) {
}
