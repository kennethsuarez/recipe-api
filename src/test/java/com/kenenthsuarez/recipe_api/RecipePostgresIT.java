package com.kenenthsuarez.recipe_api;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.entity.Recipe;
import com.kenenthsuarez.recipe_api.service.RecipeServiceImpl;
import jakarta.persistence.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Testcontainers
class RecipePostgresIT {
    @Test
    void titleSearchIsLiteralCombinesFiltersAndPaginates() throws Exception {
        create("Rice bowl", true, List.of("beans"), List.of());
        create("Rice soup", false, List.of("beans"), List.of());
        create("Pasta", true, List.of("rice"), List.of());
        create("100%_! Rice", true, List.of("beans"), List.of());
        mvc.perform(get("/api/recipes").param("title", " RICE ").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results[0].title").value("Rice bowl"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/recipes").param("title", "rice").param("vegetarian", "true")
                        .param("includeIngredient", "beans"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(2));
        mvc.perform(get("/api/recipes").param("title", "%_!"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(1));
        mvc.perform(get("/api/recipes").param("title", " "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(4));
        mvc.perform(get("/api/recipes").param("title", "missing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty());
        mvc.perform(get("/api/recipes").param("title", "x".repeat(201)))
                .andExpect(status().isBadRequest());
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:14.17");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    RecipeServiceImpl service;
    @Autowired MockMvc mvc;
    @Autowired EntityManagerFactory factory;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE recipes CASCADE");
    }

    private RecipeResponse create(String title, boolean vegetarian, List<String> ingredients, List<String> steps) {
        return service.create(new RecipeRequest(title, null, 4, vegetarian, ingredients, steps));
    }

    private RecipeSlice search(List<String> include, List<String> exclude, String instruction) {
        return service.search(new RecipeSearch(null, false, 4, include, exclude, instruction, 0, 20));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void fullHttpSliceUsesThreeSelectsWithCompleteOrderedChildren(int size) throws Exception {
        for (int i = 0; i <= size; i++) {
            create("Recipe " + i, false, List.of("rice", "rice flour", "salt"), List.of("oven on", "oven off"));
        }
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get("/api/recipes").param("size", String.valueOf(size)).param("includeIngredient", "rice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(size))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.results[0].ingredients[2]").value("salt"))
                .andExpect(jsonPath("$.results[0].instructions[1]").value("oven off"));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void detailUsesThreeSelectsAndEmptySliceUsesOne() throws Exception {
        long id = create("Recipe", false, List.of("rice"), List.of()).id();
        var statistics = factory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get("/api/recipes/" + id)).andExpect(status().isOk());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        statistics.clear();
        mvc.perform(get("/api/recipes").param("includeIngredient", "absent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results").isEmpty());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void filtersUseAndWholeRecipeExclusionAndLiteralPatterns() {
        long id = create("Mixed", true, List.of("200g Chicken", "rice", "100%_! pure"), List.of("Preheat the Oven", "Bake", "well")).id();
        create("Rice", false, List.of("rice"), List.of());
        assertThat(search(List.of("CHICKEN", "rice"), List.of(), "oven").results())
                .extracting(RecipeResponse::id).containsExactly(id);
        assertThat(search(List.of("rice"), List.of("chicken"), null).results()).hasSize(1);
        assertThat(search(List.of("rice"), List.of("rice"), null).results()).isEmpty();
        assertThat(search(List.of("%_!"), List.of(), null).results()).hasSize(1);
        assertThat(search(List.of("missing%"), List.of(), null).results()).isEmpty();
        assertThat(search(List.of(), List.of(), "preheat oven").results()).isEmpty();
        assertThat(search(List.of(), List.of(), "bake well").results()).isEmpty();
        assertThat(search(List.of(), List.of(), "bake").results()).hasSize(1);
        assertThat(service.search(new RecipeSearch(null, true, null, null, null, null, 0, 20)).results()).hasSize(1);
        assertThat(service.search(new RecipeSearch(null, false, null, null, null, null, 0, 20)).results()).hasSize(2);
        assertThat(service.search(new RecipeSearch(null, null, 3, null, null, null, 0, 20)).results()).isEmpty();
    }

    @Test
    void replacementDefaultsNormalizationAndRollback() {
        long id = create(" A\nB ", true, List.of(" chicken "), List.of("old")).id();
        assertThat(service.get(id).title()).isEqualTo("A B");
        service.replace(id, new RecipeRequest("New", "a\n\nb", 2, null, List.of(" rice "), null));
        assertThat(service.get(id).instructions()).isEmpty();
        assertThat(service.get(id).vegetarian()).isFalse();
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.replace(id, new RecipeRequest("Rollback", null, 1, true, List.of("changed"), List.of("changed")));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(service.get(id).title()).isEqualTo("New");
        assertThat(service.get(id).ingredients()).containsExactly("rice");
    }

    @Test
    void overlappingChildReplacementAndDeletionDetectVersionConflict() {
        long id = create("Original", false, List.of("rice"), List.of()).id();
        EntityManager stale = factory.createEntityManager();
        try {
            stale.getTransaction().begin();
            Recipe recipe = stale.find(Recipe.class, id);
            service.replace(id, new RecipeRequest("Original", null, 4, false, List.of("beans"), List.of()));
            stale.remove(recipe);
            assertThatThrownBy(() -> stale.getTransaction().commit()).isInstanceOf(RollbackException.class);
            assertThat(service.get(id).ingredients()).containsExactly("beans");
        } finally {
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            stale.close();
        }
    }

    @Test
    void overlappingPutDoesNotOverwriteCommittedChildren() {
        long id = create("Original", false, List.of("rice"), List.of()).id();
        EntityManager stale = factory.createEntityManager();
        try {
            stale.getTransaction().begin();
            Recipe recipe = stale.find(Recipe.class, id);
            recipe.getIngredients().size();
            stale.lock(recipe, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            service.replace(id, new RecipeRequest("Original", null, 4, false, List.of("beans"), List.of()));
            recipe.getIngredients().clear();
            recipe.getIngredients().add("stale");
            assertThatThrownBy(() -> stale.getTransaction().commit()).isInstanceOf(RollbackException.class);
            assertThat(service.get(id).ingredients()).containsExactly("beans");
        } finally {
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            stale.close();
        }
    }

    @Test
    void statementTimeoutIsEnforced() {
        assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("3s");
        assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("1s");
        assertThatThrownBy(() -> jdbc.execute("SELECT pg_sleep(5)"))
                .isInstanceOf(org.springframework.dao.QueryTimeoutException.class);
    }
}
