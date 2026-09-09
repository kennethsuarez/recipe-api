package com.kenenthsuarez.recipe_api.service;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.entity.Recipe;
import com.kenenthsuarez.recipe_api.exception.RecipeNotFoundException;
import com.kenenthsuarez.recipe_api.mapper.RecipeMapper;
import com.kenenthsuarez.recipe_api.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RecipeServiceImpl implements RecipeService {
    private static final String SEARCH_CACHE = "recipeSearch";

    private final RecipeRepository repository;
    private final RecipeMapper mapper;
    private final RecipeValidation validation;
    private final CacheManager cacheManager;

    @Transactional
    @Override
    public RecipeResponse create(RecipeRequest request) {
        Recipe recipe = mapper.toEntity(validation.normalize(request));
        RecipeResponse response = mapper.toResponse(repository.save(recipe));
        evictSearchCacheAfterCommit();
        return response;
    }

    @Transactional
    @Override
    public RecipeResponse replace(long id, RecipeRequest request) {
        RecipeRequest normalized = validation.normalize(request);
        Recipe recipe = repository.findForReplacement(id).orElseThrow(() -> new RecipeNotFoundException(id));
        mapper.replace(recipe, normalized);
        RecipeResponse response = mapper.toResponse(recipe);
        evictSearchCacheAfterCommit();
        return response;
    }

    @Transactional
    @Override
    public void delete(long id) {
        repository.delete(findEntity(id));
        repository.flush();
        evictSearchCacheAfterCommit();
    }

    private Recipe findEntity(long id) {
        return repository.findById(id).orElseThrow(() -> new RecipeNotFoundException(id));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public RecipeResponse get(long id) {
        RecipeRoot root = repository.findRoot(id).orElseThrow(() -> new RecipeNotFoundException(id));
        return assemble(List.of(root)).get(0);
    }

    @Cacheable(cacheNames = SEARCH_CACHE)
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @Override
    public RecipeSlice search(RecipeSearch input) {
        RecipeSearch search = validation.normalize(input);
        List<RecipeRoot> roots = repository.searchRoots(search);
        boolean hasNext = roots.size() > search.size();
        List<RecipeRoot> page = hasNext ? roots.subList(0, search.size()) : roots;
        return new RecipeSlice(assemble(page), search.page(), search.size(), hasNext);
    }

    // Registered as an afterCommit hook rather than a stacked @CacheEvict, so a
    // write can never evict/repopulate the cache before its transaction commits
    // (or evict at all if it rolls back). Clears the whole region rather than a
    // single key: a write can affect an unbounded, unknown set of previously
    // cached filter combinations, not one addressable entry.
    private void evictSearchCacheAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clearSearchCache();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clearSearchCache();
            }
        });
    }

    private void clearSearchCache() {
        Cache cache = cacheManager.getCache(SEARCH_CACHE);
        if (cache != null) cache.clear();
    }

    private List<RecipeResponse> assemble(List<RecipeRoot> roots) {
        if (roots.isEmpty()) return List.of();
        List<Long> ids = roots.stream().map(RecipeRoot::id).toList();
        Map<Long, List<String>> ingredients = group(repository.findIngredients(ids));
        Map<Long, List<String>> instructions = group(repository.findInstructions(ids));
        return roots.stream().map(root -> mapper.toResponse(root,
                ingredients.getOrDefault(root.id(), List.of()),
                instructions.getOrDefault(root.id(), List.of()))).toList();
    }

    private Map<Long, List<String>> group(List<RecipeChild> children) {
        Map<Long, List<String>> result = new HashMap<>();
        for (RecipeChild child : children) {
            result.computeIfAbsent(child.recipeId(), ignored -> new ArrayList<>()).add(child.text());
        }
        return result;
    }
}
