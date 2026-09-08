package com.kenenthsuarez.recipe_api.controller;

import com.kenenthsuarez.recipe_api.dto.*;
import com.kenenthsuarez.recipe_api.exception.RecipeNotFoundException;
import com.kenenthsuarez.recipe_api.service.RecipeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecipeController.class)
class RecipeControllerTest {
    @Test
    void bindsTitleAlongsideOtherFilters() throws Exception {
        when(service.search(any())).thenReturn(new RecipeSlice(List.of(), 0, 20, false));
        mvc.perform(get("/api/recipes").param("title", "Rice").param("vegetarian", "true"))
                .andExpect(status().isOk());
        verify(service).search(argThat(search -> "Rice".equals(search.title()) && Boolean.TRUE.equals(search.vegetarian())));
    }

    @Autowired MockMvc mvc;
    @MockitoBean
    RecipeServiceImpl service;

    @Test
    void createsWithLocationAndDto() throws Exception {
        when(service.create(any())).thenReturn(new RecipeResponse(1L, "Rice", null, 1, false, List.of("rice"), List.of()));
        mvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Rice\",\"servings\":1,\"ingredients\":[\"rice\"]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/recipes/1"))
                .andExpect(jsonPath("$.ingredients[0]").value("rice"));
    }

    @Test
    void validatesBeforeCallingService() throws Exception {
        mvc.perform(post("/api/recipes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"servings\":0,\"ingredients\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors").isArray());
        verifyNoInteractions(service);
    }

    @Test
    void errorsAndDeleteStatuses() throws Exception {
        when(service.get(99)).thenThrow(new RecipeNotFoundException(99));
        mvc.perform(get("/api/recipes/99")).andExpect(status().isNotFound());
        mvc.perform(get("/api/recipes?servings=abc")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/recipes/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(1);
    }

    @Test
    void oversizedBodyIsRejected() throws Exception {
        mvc.perform(post("/api/recipes").servletPath("/api/recipes").contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(524289))).andExpect(status().isPayloadTooLarge());
        verifyNoInteractions(service);
    }

    @Test
    void mapsOptimisticConflictsAndTemporaryDatabaseFailures() throws Exception {
        when(service.get(1)).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("conflict"));
        mvc.perform(get("/api/recipes/1")).andExpect(status().isConflict());
        when(service.get(2)).thenThrow(new org.springframework.dao.QueryTimeoutException("timeout"));
        mvc.perform(get("/api/recipes/2")).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    void repeatedIngredientParametersPreserveLiteralCommas() throws Exception {
        when(service.search(any())).thenReturn(new RecipeSlice(List.of(), 0, 20, false));
        mvc.perform(get("/api/recipes").param("includeIngredient", "rice, beans", "salt"))
                .andExpect(status().isOk());
        verify(service).search(argThat(search -> search.includeIngredient().equals(List.of("rice, beans", "salt"))));
    }
}
