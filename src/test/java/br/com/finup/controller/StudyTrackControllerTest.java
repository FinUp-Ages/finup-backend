package br.com.finup.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.StudyTrackCategoryResponse;
import br.com.finup.dto.StudyTrackQuestionResponse;
import br.com.finup.exception.CategoryNotFoundException;
import br.com.finup.model.QuestionType;
import br.com.finup.security.AuthenticatedIdentityResolver;
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

  @MockitoBean private AuthenticatedIdentityResolver authenticatedIdentityResolver;

  @Test
  @DisplayName("GET categories devolve as categorias disponibilizadas pelo service")
  void findCategoriesReturns200() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(studyTrackQueryService.findCategories())
        .thenReturn(
            List.of(new StudyTrackCategoryResponse(categoryId, "Investimentos", "Renda fixa", 0)));

    mockMvc
        .perform(get("/api/v1/study-tracks/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(categoryId.toString()))
        .andExpect(jsonPath("$[0].name").value("Investimentos"));
  }

  @Test
  @DisplayName("GET questions retorna apenas o campo de conteúdo published")
  void findQuestionsReturnsPublishedContentOnly() throws Exception {
    UUID categoryId = UUID.randomUUID();
    UUID questionId = UUID.randomUUID();
    StudyTrackQuestionResponse question =
        new StudyTrackQuestionResponse(
            questionId,
            QuestionType.MULTIPLE_CHOICE,
            Map.of("prompt", "O que é uma ação?", "correctOptionId", "A"),
            null);
    when(studyTrackQueryService.findPublishedQuestions(categoryId)).thenReturn(List.of(question));

    mockMvc
        .perform(get("/api/v1/study-tracks/categories/{id}/questions", categoryId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(questionId.toString()))
        .andExpect(jsonPath("$[0].published.prompt").value("O que é uma ação?"))
        .andExpect(jsonPath("$[0].published.correctOptionId").value("A"))
        .andExpect(jsonPath("$[0].draft").doesNotExist());
  }

  @Test
  @DisplayName("GET questions de categoria inexistente devolve 404 em RFC 7807")
  void findQuestionsForUnknownCategoryReturns404() throws Exception {
    UUID categoryId = UUID.randomUUID();
    when(studyTrackQueryService.findPublishedQuestions(categoryId))
        .thenThrow(
            new CategoryNotFoundException("Categoria da trilha não encontrada: " + categoryId));

    mockMvc
        .perform(
            get("/api/v1/study-tracks/categories/{id}/questions", categoryId)
                .accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
