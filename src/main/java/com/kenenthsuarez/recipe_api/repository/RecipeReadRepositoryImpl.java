package com.kenenthsuarez.recipe_api.repository;

import com.kenenthsuarez.recipe_api.dto.RecipeSearch;
import com.kenenthsuarez.recipe_api.entity.Recipe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RecipeReadRepositoryImpl implements RecipeReadRepository {
    private final EntityManager entityManager;

    @Override
    public List<RecipeRoot> searchRoots(RecipeSearch search) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RecipeRoot> query = cb.createQuery(RecipeRoot.class);
        Root<Recipe> root = query.from(Recipe.class);
        List<Predicate> predicates = new ArrayList<>();
        if (search.title() != null) {
            predicates.add(cb.like(cb.lower(root.get("title")), literalPattern(search.title()), '!'));
        }
        if (Boolean.TRUE.equals(search.vegetarian())) {
            predicates.add(cb.isTrue(root.get("vegetarian")));
        }
        if (search.servings() != null) {
            predicates.add(cb.equal(root.get("servings"), search.servings()));
        }
        for (String term : search.includeIngredient()) {
            predicates.add(contains(query, root, "ingredients", term, cb));
        }
        for (String term : search.excludeIngredient()) {
            predicates.add(cb.not(contains(query, root, "ingredients", term, cb)));
        }
        if (search.instruction() != null) {
            predicates.add(contains(query, root, "instructions", search.instruction(), cb));
        }
        query.select(projection(root, cb)).where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.asc(root.get("id")));
        return entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact((long) search.page() * search.size()))
                .setMaxResults(search.size() + 1).getResultList();
    }

    private Predicate contains(CriteriaQuery<?> query, Root<Recipe> root, String collection,
                               String term, CriteriaBuilder cb) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        ListJoin<Recipe, String> entry = subquery.correlate(root).joinList(collection);
        subquery.select(cb.literal(1)).where(cb.like(cb.lower(entry), literalPattern(term), '!'));
        return cb.exists(subquery);
    }

    private String literalPattern(String term) {
        return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    @Override
    public Optional<RecipeRoot> findRoot(long id) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<RecipeRoot> query = cb.createQuery(RecipeRoot.class);
        Root<Recipe> root = query.from(Recipe.class);
        query.select(projection(root, cb)).where(cb.equal(root.get("id"), id));
        return entityManager.createQuery(query).getResultList().stream().findFirst();
    }

    private Selection<RecipeRoot> projection(Root<Recipe> root, CriteriaBuilder cb) {
        return cb.construct(RecipeRoot.class, root.get("id"), root.get("title"), root.get("description"),
                root.get("servings"), root.get("vegetarian"));
    }
}
