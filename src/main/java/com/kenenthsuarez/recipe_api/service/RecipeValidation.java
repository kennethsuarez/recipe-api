package com.kenenthsuarez.recipe_api.service;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.exception.InvalidRecipeException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RecipeValidation {
    private final Validator validator;
    @Value("${recipe.max-offset:10000}")
    private int maxOffset;

    public RecipeRequest normalize(RecipeRequest request) {
        RecipeRequest normalized = new RecipeRequest(text(request.title()), request.description(), request.servings(),
                Boolean.TRUE.equals(request.vegetarian()), normalizeEntries(request.ingredients()),
                request.instructions() == null ? List.of() : normalizeEntries(request.instructions()));
        var violations = validator.validate(normalized);
        if (!violations.isEmpty()) {
            throw new InvalidRecipeException(violations.iterator().next().getPropertyPath() + ": "
                    + violations.iterator().next().getMessage());
        }
        return normalized;
    }

    private List<String> normalizeEntries(List<String> entries) {
        return entries == null ? null : entries.stream().map(this::text).toList();
    }

    private String text(String value) {
        if (value == null) return null;
        return value.replaceAll("\\R", " ").strip();
    }

    public RecipeSearch normalize(RecipeSearch search) {
        if (search.page() < 0 || search.size() < 1 || search.size() > 100
                || (long) search.page() * search.size() > maxOffset) {
            throw new InvalidRecipeException("page must be nonnegative, size must be 1–100, and offset must not exceed " + maxOffset);
        }
        if (search.servings() != null && search.servings() < 1) {
            throw new InvalidRecipeException("servings must be positive");
        }
        return new RecipeSearch(term(search.title()), search.vegetarian(), search.servings(), terms(search.includeIngredient()),
                terms(search.excludeIngredient()), term(search.instruction()), search.page(), search.size());
    }

    private List<String> terms(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > 10) throw new InvalidRecipeException("At most 10 terms per ingredient filter are allowed");
        return values.stream().map(this::term).filter(s -> s != null).distinct().toList();
    }

    private String term(String value) {
        if (value == null) return null;
        if (value.length() > 200) throw new InvalidRecipeException("Search terms must not exceed 200 characters");
        String result = value.strip().toLowerCase(Locale.ROOT);
        return result.isEmpty() ? null : result;
    }
}
