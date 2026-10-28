package br.com.finup.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.StudyCategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.service.StudyCategoryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminStudyCategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminStudyCategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudyCategoryService studyCategoryService;

  @Test
  @DisplayName("GET lista categorias no formato do contrato")
  void listReturnsCategories() throws Exception {
    UUID id = UUID.randomUUID();
    StudyCategoryResponse response =
        new StudyCategoryResponse(
            id,
            "CDB",
            "",
            2,
            1,
            Instant.parse("2026-10-22T10:00:00Z"),
            Instant.parse("2026-10-22T10:00:00Z"));
    when(studyCategoryService.listCategories()).thenReturn(List.of(response));

    mockMvc
        .perform(get("/api/v1/admin/study-tracks/categories"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(id.toString()))
        .andExpect(jsonPath("$[0].description").value(""))
        .andExpect(jsonPath("$[0].draftCount").value(2))
        .andExpect(jsonPath("$[0].publishedCount").value(1));
  }

  @Test
  @DisplayName("POST válido devolve 201")
  void createReturns201() throws Exception {
    UUID id = UUID.randomUUID();
    StudyCategoryResponse response =
        new StudyCategoryResponse(id, "CDB", "Renda fixa", 0, 0, Instant.now(), Instant.now());
    when(studyCategoryService.create(any())).thenReturn(response);

    mockMvc
        .perform(
            post("/api/v1/admin/study-tracks/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\" CDB \",\"description\":\"Renda fixa\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.name").value("CDB"));
  }

  @Test
  @DisplayName("POST sem nome devolve 400")
  void createWithoutNameReturns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/admin/study-tracks/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"description\":\"Renda fixa\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("PUT válido devolve 200 com categoria atualizada")
  void updateReturns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(studyCategoryService.update(eq(id), any()))
        .thenReturn(
            new StudyCategoryResponse(
                id, "CDB atualizado", "", 0, 0, Instant.now(), Instant.now()));

    mockMvc
        .perform(
            put("/api/v1/admin/study-tracks/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CDB atualizado\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("CDB atualizado"));
  }

  @Test
  @DisplayName("PUT inexistente devolve 404")
  void updateUnknownReturns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(studyCategoryService.update(eq(id), any()))
        .thenThrow(new ResourceNotFoundException("Categoria da trilha não encontrada"));

    mockMvc
        .perform(
            put("/api/v1/admin/study-tracks/categories/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CDB\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }

  @Test
  @DisplayName("DELETE válido devolve 204")
  void deleteReturns204() throws Exception {
    mockMvc
        .perform(delete("/api/v1/admin/study-tracks/categories/{id}", UUID.randomUUID()))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""));
  }

  @Test
  @DisplayName("DELETE com perguntas devolve 409")
  void deleteWithQuestionsReturns409() throws Exception {
    UUID id = UUID.randomUUID();
    doThrow(
            new ConflictException(
                "A categoria possui perguntas e não pode ser removida. Remova as perguntas antes."))
        .when(studyCategoryService)
        .delete(id);

    mockMvc
        .perform(delete("/api/v1/admin/study-tracks/categories/{id}", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "A categoria possui perguntas e não pode ser removida. Remova as perguntas antes."));
  }
}
