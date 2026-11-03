package br.com.finup.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.service.StudyTrackQueryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(StudyTrackController.class)
class StudyTrackControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StudyTrackQueryService studyTrackQueryService;

  @Test
  @DisplayName("GET categories devolve as categorias disponibilizadas pelo service")
  void findCategoriesReturns200() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(studyTrackQueryService.findCategories())
        .thenReturn(
            List.of(
                new StudyTrackCategoryResponse(
                    categoryId, "Reserva de Emergência", "Conceitos de reserva", 2)));

    mockMvc
        .perform(get("/api/v1/study-tracks/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
        .andExpect(jsonPath("$[0].name").value("Reserva de Emergência"))
        .andExpect(jsonPath("$[0].description").value("Conceitos de reserva"))
        .andExpect(jsonPath("$[0].publishedCount").value(2));
  }

  @Test
  @DisplayName("GET questions retorna apenas o campo de conteúdo published")
  void findQuestionsReturnsPublishedContentOnly() throws Exception {
    UUID categoryId = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();
    Map<String, Object> published =
        Map.of(
            "type",
            "MULTIPLE_CHOICE",
            "statement",
            "O que é uma ação?",
            "options",
            List.of(
                Map.of("text", "Parte de uma empresa", "correct", true),
                Map.of("text", "Um empréstimo", "correct", false)));
    when(studyTrackQueryService.findPublishedQuestions(categoryId))
        .thenReturn(List.of(new StudyTrackQuestionResponse(questionId, published)));

    mockMvc
        .perform(get("/api/v1/study-tracks/categories/{id}/questions", categoryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(questionId.toString()))
        .andExpect(jsonPath("$[0].published.statement").value("O que é uma ação?"))
        .andExpect(jsonPath("$[0].published.options[0].correct").value(true))
        .andExpect(jsonPath("$[0].draft").doesNotExist());
  }

  @Test
  @DisplayName("GET questions de categoria inexistente devolve 404 em RFC 7807")
  void findQuestionsForUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(studyTrackQueryService.findPublishedQuestions(categoryId))
        .thenThrow(
            new ResourceNotFoundException("Categoria da trilha não encontrada: " + categoryId));

    mockMvc
        .perform(
            get("/api/v1/study-tracks/categories/{id}/questions", categoryId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
