package com.kenenthsuarez.recipe_api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A bounded recipe page without an expensive exact total")
public record RecipeSlice(List<RecipeResponse> results,
                          @Schema(example = "0") int page,
                          @Schema(example = "20") int size,
                          @Schema(example = "false") boolean hasNext) {
    public RecipeSlice {
        results = List.copyOf(results);
    }
}
