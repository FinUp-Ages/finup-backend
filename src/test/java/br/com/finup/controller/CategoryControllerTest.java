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
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.TransactionType;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
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

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  private static final AuthenticatedIdentity IDENTITY =
      new AuthenticatedIdentity("mock-sub", "Ana Souza", "ana@exemplo.com");

  @org.junit.jupiter.api.BeforeEach
  void mockIdentity() {
    when(authenticatedIdentityResolver.resolveCurrent()).thenReturn(IDENTITY);
  }

  @Test
  @DisplayName("POST valido devolve 201 com a categoria criada")
  void validCreateReturns201() throws Exception {
    CategoryResponse response =
        new CategoryResponse(UUID.randomUUID(), "Academia", TransactionType.EXPENSE, false);

    when(categoryService.create(eq(IDENTITY), any())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
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
            post("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"type\":null}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT valido devolve 200 com a categoria atualizada")
  void validUpdateReturns200() throws Exception {
    UUID categoryId = UUID.randomUUID();
    CategoryResponse response =
        new CategoryResponse(categoryId, "Academia e Esportes", TransactionType.EXPENSE, false);

    when(categoryService.update(eq(IDENTITY), eq(categoryId), any())).thenReturn(response);

    mockMvc
        .perform(
            put("/api/v1/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Academia e Esportes\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Academia e Esportes"));
  }

  @Test
  @DisplayName("PUT de categoria inexistente devolve 404")
  void updateUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();

    when(categoryService.update(eq(IDENTITY), eq(categoryId), any()))
        .thenThrow(new ResourceNotFoundException("Category", categoryId));

    mockMvc
        .perform(
            put("/api/v1/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Academia e Esportes\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET devolve 200 com lista de categorias")
  void findAvailableReturns200() throws Exception {
    List<CategoryResponse> response =
        List.of(
            new CategoryResponse(UUID.randomUUID(), "Alimentação", TransactionType.EXPENSE, true),
            new CategoryResponse(UUID.randomUUID(), "Academia", TransactionType.EXPENSE, false));

    when(categoryService.findAvailable(IDENTITY)).thenReturn(response);

    mockMvc
        .perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  @DisplayName("DELETE devolve 204 para categoria valida")
  void validDeleteReturns204() throws Exception {
    UUID categoryId = UUID.randomUUID();

    mockMvc
        .perform(delete("/api/v1/categories/{id}", categoryId))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("DELETE de categoria inexistente devolve 404")
  void deleteUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();

    doThrow(new ResourceNotFoundException("Category", categoryId))
        .when(categoryService)
        .delete(eq(IDENTITY), eq(categoryId));

    mockMvc.perform(delete("/api/v1/categories/{id}", categoryId)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PUT de categoria padrao devolve 403")
  void updateDefaultCategoryReturns403() throws Exception {
    UUID categoryId = UUID.randomUUID();

    when(categoryService.update(eq(IDENTITY), eq(categoryId), any()))
        .thenThrow(new ForbiddenOperationException("Não é possível editar uma categoria padrão"));

    mockMvc
        .perform(
            put("/api/v1/categories/{id}", categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Outro nome\",\"type\":\"EXPENSE\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Não é possível editar uma categoria padrão"));
  }

  @Test
  @DisplayName("DELETE de categoria padrao devolve 403")
  void deleteDefaultCategoryReturns403() throws Exception {
    UUID categoryId = UUID.randomUUID();

    doThrow(new ForbiddenOperationException("Não é possível excluir uma categoria padrão"))
        .when(categoryService)
        .delete(eq(IDENTITY), eq(categoryId));

    mockMvc
        .perform(delete("/api/v1/categories/{id}", categoryId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail").value("Não é possível excluir uma categoria padrão"));
  }

  @Test
  @DisplayName("DELETE de categoria em uso devolve 409")
  void deleteCategoryInUseReturns409() throws Exception {
    UUID categoryId = UUID.randomUUID();

    doThrow(new ConflictException("A categoria não pode ser excluída pois está em uso"))
        .when(categoryService)
        .delete(eq(IDENTITY), eq(categoryId));

    mockMvc
        .perform(delete("/api/v1/categories/{id}", categoryId))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.detail").value("A categoria não pode ser excluída pois está em uso"));
  }
}
