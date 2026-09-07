package com.kenenthsuarez.recipe_api.mapper;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.entity.Recipe;
import com.kenenthsuarez.recipe_api.repository.RecipeRoot;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class RecipeMapper {
    public Recipe toEntity(RecipeRequest request) {
        Recipe recipe = new Recipe();
        replace(recipe, request);
        return recipe;
    }

    public void replace(Recipe recipe, RecipeRequest request) {
        recipe.setTitle(request.title());
        recipe.setDescription(request.description());
        recipe.setServings(request.servings());
        recipe.setVegetarian(Boolean.TRUE.equals(request.vegetarian()));
        recipe.getIngredients().clear();
        recipe.getIngredients().addAll(request.ingredients());
        recipe.getInstructions().clear();
        recipe.getInstructions().addAll(request.instructions());
    }

    public RecipeResponse toResponse(Recipe recipe) {
        return new RecipeResponse(recipe.getId(), recipe.getTitle(), recipe.getDescription(),
                recipe.getServings(), recipe.isVegetarian(), recipe.getIngredients(), recipe.getInstructions());
    }

    public RecipeResponse toResponse(RecipeRoot root, List<String> ingredients, List<String> instructions) {
        return new RecipeResponse(root.id(), root.title(), root.description(), root.servings(),
                root.vegetarian(), ingredients, instructions);
    }
}
