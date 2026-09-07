package com.kenenthsuarez.recipe_api.service;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.entity.Recipe;
import com.kenenthsuarez.recipe_api.exception.RecipeNotFoundException;
import com.kenenthsuarez.recipe_api.mapper.RecipeMapper;
import com.kenenthsuarez.recipe_api.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecipeServiceTest {
    private final RecipeRepository repository = mock(RecipeRepository.class);
    private final RecipeValidation validation = mock(RecipeValidation.class);
    private final RecipeServiceImpl service = new RecipeServiceImpl(repository, new RecipeMapper(), validation);

    @Test
    void missingReadsAndWritesFail() {
        when(repository.findRoot(99)).thenReturn(Optional.empty());
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findForReplacement(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99)).isInstanceOf(RecipeNotFoundException.class);
        assertThatThrownBy(() -> service.replace(99, null)).isInstanceOf(RecipeNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99)).isInstanceOf(RecipeNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void replacementClearsChildrenAndChecksAggregateVersion() {
        Recipe recipe = new Recipe();
        recipe.setId(1L);
        recipe.setVegetarian(true);
        recipe.getInstructions().add("old");
        recipe.getIngredients().add("old");
        var request = new RecipeRequest("New", null, 1, false, List.of("rice"), List.of());
        when(validation.normalize(request)).thenReturn(request);
        when(repository.findForReplacement(1L)).thenReturn(Optional.of(recipe));
        var result = service.replace(1, request);
        assertThat(result.instructions()).isEmpty();
        assertThat(result.ingredients()).containsExactly("rice");
        assertThat(result.vegetarian()).isFalse();
        verify(repository).findForReplacement(1L);
    }

    @Test
    void sliceLoadsOnlyReturnedIdsAndPreservesRootOrder() {
        var search = new RecipeSearch(null, null, List.of(), List.of(), null, 0, 1);
        when(validation.normalize(search)).thenReturn(search);
        when(repository.searchRoots(search)).thenReturn(List.of(
                new RecipeRoot(1L, "A", null, 1, false), new RecipeRoot(2L, "B", null, 1, false)));
        when(repository.findIngredients(List.of(1L))).thenReturn(List.of(new RecipeChild(1L, 0, "rice")));
        when(repository.findInstructions(List.of(1L))).thenReturn(List.of());
        var result = service.search(search);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.results()).extracting(RecipeResponse::id).containsExactly(1L);
        assertThat(result.results().get(0).ingredients()).containsExactly("rice");
        verify(repository).findIngredients(List.of(1L));
        verify(repository).findInstructions(List.of(1L));
    }
}
