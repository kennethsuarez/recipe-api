package com.kenenthsuarez.recipe_api.repository;

import com.kenenthsuarez.recipe_api.dto.RecipeSearch;
import java.util.List;
import java.util.Optional;

public interface RecipeReadRepository {
    List<RecipeRoot> searchRoots(RecipeSearch search);
    Optional<RecipeRoot> findRoot(long id);
}
