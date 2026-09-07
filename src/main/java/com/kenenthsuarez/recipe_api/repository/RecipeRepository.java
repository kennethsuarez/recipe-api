package com.kenenthsuarez.recipe_api.repository;

import com.kenenthsuarez.recipe_api.entity.Recipe;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface RecipeRepository extends JpaRepository<Recipe, Long>, RecipeReadRepository {
    // Advance/check the aggregate version even for identical or child-only replacements.
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select r from Recipe r where r.id = :id")
    Optional<Recipe> findForReplacement(@Param("id") long id);

    @Query("select new com.kenenthsuarez.recipe_api.repository.RecipeChild(r.id, index(i), i) "
            + "from Recipe r join r.ingredients i where r.id in :ids order by r.id, index(i)")
    List<RecipeChild> findIngredients(@Param("ids") List<Long> ids);

    @Query("select new com.kenenthsuarez.recipe_api.repository.RecipeChild(r.id, index(s), s) "
            + "from Recipe r join r.instructions s where r.id in :ids order by r.id, index(s)")
    List<RecipeChild> findInstructions(@Param("ids") List<Long> ids);
}
