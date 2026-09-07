package com.kenenthsuarez.recipe_api.controller;

import com.kenenthsuarez.recipe_api.dto.RecipeRequest;
import com.kenenthsuarez.recipe_api.dto.RecipeResponse;
import com.kenenthsuarez.recipe_api.dto.RecipeSearch;
import com.kenenthsuarez.recipe_api.dto.RecipeSlice;
import com.kenenthsuarez.recipe_api.service.RecipeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/recipes")
@RequiredArgsConstructor
@Tag(name = "Recipes", description = "Create, retrieve, replace, delete, and search recipes")
public class RecipeController {
    private final RecipeService recipeService;

    @Operation(summary = "Create a recipe", description = "Creates a complete recipe and returns its generated ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Recipe created",
                    content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "413", ref = "#/components/responses/PayloadTooLarge"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    })
    @PostMapping
    public ResponseEntity<RecipeResponse> create(@Valid @RequestBody RecipeRequest request) {
        RecipeResponse result = recipeService.create(request);
        return ResponseEntity.created(URI.create("/api/recipes/" + result.id())).body(result);
    }

    @Operation(summary = "Get a recipe", description = "Returns one recipe by its generated identifier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe found",
                    content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    })
    @GetMapping("/{id}")
    public RecipeResponse get(@Parameter(description = "Recipe ID", example = "42") @PathVariable long id) {
        return recipeService.get(id);
    }

    @Operation(summary = "Replace a recipe", description = "Atomically replaces every field and both ordered lists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe replaced",
                    content = @Content(schema = @Schema(implementation = RecipeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "413", ref = "#/components/responses/PayloadTooLarge"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    })
    @PutMapping("/{id}")
    public RecipeResponse replace(@Parameter(description = "Recipe ID", example = "42") @PathVariable long id,
                                  @Valid @RequestBody RecipeRequest request) {
        return recipeService.replace(id, request);
    }

    @Operation(summary = "Delete a recipe", description = "Permanently deletes one recipe.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Recipe deleted"),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@Parameter(description = "Recipe ID", example = "42") @PathVariable long id) {
        recipeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Search recipes", description = "Combines all supplied filters with AND and returns ID-ascending results. Repeating an ingredient parameter requires every included term and excludes every excluded term.")
    @Parameters({
            @Parameter(name = "includeIngredient", description = "Literal ingredient substring; repeat up to 10 times",
                    in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    style = io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM, explode = io.swagger.v3.oas.annotations.enums.Explode.TRUE,
                    example = "rice", array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(type = "string", maxLength = 200))),
            @Parameter(name = "excludeIngredient", description = "Literal ingredient substring to exclude; repeat up to 10 times",
                    in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
                    style = io.swagger.v3.oas.annotations.enums.ParameterStyle.FORM, explode = io.swagger.v3.oas.annotations.enums.Explode.TRUE,
                    example = "chicken", array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(type = "string", maxLength = 200)))
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Matching recipe slice",
                    content = @Content(schema = @Schema(implementation = RecipeSlice.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/BadRequest"),
            @ApiResponse(responseCode = "503", ref = "#/components/responses/ServiceUnavailable")
    })
    @GetMapping
    public RecipeSlice search(@Parameter(description = "When true, return only vegetarian recipes")
                              @RequestParam(required = false) Boolean vegetarian,
                              @Parameter(description = "Exact positive serving count", example = "4", schema = @Schema(minimum = "1"))
                              @RequestParam(required = false) Integer servings,
                              @Parameter(hidden = true) @RequestParam MultiValueMap<String, String> parameters,
                              @Parameter(description = "Literal substring within a single instruction step", example = "simmer",
                                      schema = @Schema(maxLength = 200))
                              @RequestParam(required = false) String instruction,
                              @Parameter(description = "Zero-based page number; page multiplied by size may not exceed the configured maximum offset",
                                      example = "0", schema = @Schema(minimum = "0", defaultValue = "0"))
                              @RequestParam(defaultValue = "0") int page,
                              @Parameter(description = "Number of results", example = "20",
                                      schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
                              @RequestParam(defaultValue = "20") int size) {
        return recipeService.search(new RecipeSearch(vegetarian, servings, parameters.get("includeIngredient"),
                parameters.get("excludeIngredient"), instruction, page, size));
    }
}
