package br.com.finup.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.CategoryType;
import br.com.finup.service.CategoryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private CategoryService categoryService;

  private static final UUID USER_ID = UUID.randomUUID();

  @Test
  @DisplayName("POST valido devolve 201 com a categoria criada")
  void validCreateReturns201() throws Exception {
    CategoryResponse response =
        new CategoryResponse(UUID.randomUUID(), "Academia", CategoryType.EXPENSE, false);

    when(categoryService.create(eq(USER_ID), any())).thenReturn(response);

    mockMvc
        .perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", USER_ID)
                .content("{\"name\":\"Academia\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Academia"))
        .andExpect(jsonPath("$.type").value("EXPENSE"))
        .andExpect(jsonPath("$.isDefault").value(false));
  }

  @Test
  @DisplayName("POST com campos invalidos devolve 400")
  void invalidCreateReturns400() throws Exception {
    mockMvc
        .perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", USER_ID)
                .content("{\"name\":\"\",\"type\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT valido devolve 200 com a categoria atualizada")
  void validUpdateReturns200() throws Exception {
    UUID categoryId = UUID.randomUUID();
    CategoryResponse response =
        new CategoryResponse(categoryId, "Academia e Esportes", CategoryType.EXPENSE, false);

    when(categoryService.update(eq(USER_ID), eq(categoryId), any())).thenReturn(response);

    mockMvc
        .perform(
            put("/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", USER_ID)
                .content("{\"name\":\"Academia e Esportes\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Academia e Esportes"));
  }

  @Test
  @DisplayName("PUT de categoria inexistente devolve 404")
  void updateUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();

    when(categoryService.update(eq(USER_ID), eq(categoryId), any()))
        .thenThrow(new ResourceNotFoundException("Category", categoryId));

    mockMvc
        .perform(
            put("/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-User-Id", USER_ID)
                .content("{\"name\":\"Academia e Esportes\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET devolve 200 com lista de categorias")
  void findAvailableReturns200() throws Exception {
    List<CategoryResponse> response =
        List.of(
            new CategoryResponse(UUID.randomUUID(), "Alimentação", CategoryType.EXPENSE, true),
            new CategoryResponse(UUID.randomUUID(), "Academia", CategoryType.EXPENSE, false));

    when(categoryService.findAvailable(USER_ID)).thenReturn(response);

    mockMvc
        .perform(get("/categories").header("X-User-Id", USER_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("DELETE devolve 204 para categoria valida")
  void validDeleteReturns204() throws Exception {
    UUID categoryId = UUID.randomUUID();

    mockMvc
        .perform(delete("/categories/{id}", categoryId).header("X-User-Id", USER_ID))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("DELETE de categoria inexistente devolve 404")
  void deleteUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();

    doThrow(new ResourceNotFoundException("Category", categoryId))
        .when(categoryService)
        .delete(eq(USER_ID), eq(categoryId));

    mockMvc
        .perform(delete("/categories/{id}", categoryId).header("X-User-Id", USER_ID))
        .andExpect(status().isNotFound());
  }
}
