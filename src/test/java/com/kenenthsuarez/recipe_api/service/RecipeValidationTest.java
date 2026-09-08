package com.kenenthsuarez.recipe_api.service;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.exception.InvalidRecipeException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RecipeValidationTest {
    @Test
    void titleSearchIsNormalizedAndBounded() {
        assertThat(validation.normalize(new RecipeSearch(" Rice ", null, null, null, null, null, 0, 20)).title())
                .isEqualTo("rice");
        assertThat(validation.normalize(new RecipeSearch("  ", null, null, null, null, null, 0, 20)).title()).isNull();
        assertThatThrownBy(() -> validation.normalize(new RecipeSearch("x".repeat(201), null, null, null, null, null, 0, 20)))
                .isInstanceOf(InvalidRecipeException.class);
    }

    private final RecipeValidation validation = new RecipeValidation(
            Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void normalizesTextAndDefaultsButPreservesDescriptionAndInternalSpaces() {
        var result = validation.normalize(new RecipeRequest("  Rice\r\nbowl ", "First\n\nSecond", 2,
                null, List.of("  rice\n and  beans "), null));
        assertThat(result.title()).isEqualTo("Rice bowl");
        assertThat(result.description()).isEqualTo("First\n\nSecond");
        assertThat(result.ingredients()).containsExactly("rice  and  beans");
        assertThat(result.instructions()).isEmpty();
        assertThat(result.vegetarian()).isFalse();
    }

    @Test
    void rejectsBlankNormalizedEntriesAndOversizedLists() {
        assertThatThrownBy(() -> validation.normalize(new RecipeRequest("Valid", null, 1, true,
                List.of("\u2003\n"), List.of()))).isInstanceOf(InvalidRecipeException.class);
        assertThatThrownBy(() -> validation.normalize(new RecipeRequest("Valid", null, 1, true,
                java.util.Collections.nCopies(101, "rice"), List.of()))).isInstanceOf(InvalidRecipeException.class);
    }

    @Test
    void filtersAreTrimmedDeduplicatedAndBounded() {
        ReflectionTestUtils.setField(validation, "maxOffset", 10000);
        var result = validation.normalize(new RecipeSearch(null, false, 4, List.of(" Rice ", "rice", " "), null, "  ", 0, 20));
        assertThat(result.includeIngredient()).containsExactly("rice");
        assertThat(result.instruction()).isNull();
        assertThatThrownBy(() -> validation.normalize(new RecipeSearch(null, null, null, null, null, null, 101, 100)))
                .isInstanceOf(InvalidRecipeException.class);
        assertThatThrownBy(() -> validation.normalize(new RecipeSearch(null, null, null, null, null, null, 0, 101)))
                .isInstanceOf(InvalidRecipeException.class);
        assertThatThrownBy(() -> validation.normalize(new RecipeSearch(null, null, 0, null, null, null, 0, 20)))
                .isInstanceOf(InvalidRecipeException.class);
        assertThatThrownBy(() -> validation.normalize(new RecipeSearch(null, null, null, List.of("a".repeat(201)), null, null, 0, 20)))
                .isInstanceOf(InvalidRecipeException.class);
    }
}
