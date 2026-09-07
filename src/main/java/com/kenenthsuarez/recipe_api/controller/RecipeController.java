package com.kenenthsuarez.recipe_api.controller;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.service.RecipeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import org.springframework.util.MultiValueMap;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
public class RecipeController {
    private final RecipeService recipeService;

    @PostMapping
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody RecipeRequest request) {
        RecipeResponse result = recipeService.create(request);
        return ResponseEntity.created(URI.create("/api/recipes/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    public RecipeResponse get(@PathVariable long id) {
        return recipeService.get(id);
    }

    @PutMapping("/{id}")
    public RecipeResponse replace(@PathVariable long id, @Valid @RequestBody RecipeRequest request) {
        return recipeService.replace(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        recipeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public RecipeSlice search(@RequestParam(required = false) Boolean vegetarian,
                              @RequestParam(required = false) Integer servings,
                              @RequestParam MultiValueMap<String, String> parameters,
                              @RequestParam(required = false) String instruction,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return recipeService.search(new RecipeSearch(vegetarian, servings, parameters.get("includeIngredient"),
                parameters.get("excludeIngredient"), instruction, page, size));
    }
}
