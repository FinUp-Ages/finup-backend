package br.com.finup.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.finup.dto.FinancialProfileAnswerOptionResponse;
import br.com.finup.dto.FinancialProfileQuestionResponse;
import br.com.finup.service.FinancialProfileQuestionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(FinancialProfileQuestionController.class)
class FinancialProfileQuestionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FinancialProfileQuestionService financialProfileQuestionService;

  @Test
  @DisplayName("GET devolve as perguntas no formato do contrato")
  void listReturnsQuestionsInContractShape() throws Exception {
    UUID questionId = UUID.randomUUID();
    UUID optionId = UUID.randomUUID();
    when(financialProfileQuestionService.findAll())
        .thenReturn(
            List.of(
                new FinancialProfileQuestionResponse(
                    questionId,
                    "SPENDING_HABIT",
                    "Como voce gasta?",
                    List.of(
                        new FinancialProfileAnswerOptionResponse(
                            optionId, "CONTROLLED", "Controlado", new BigDecimal("100"))))));

    mockMvc
        .perform(get("/api/v1/financial-profile/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(questionId.toString()))
        .andExpect(jsonPath("$[0].code").value("SPENDING_HABIT"))
        .andExpect(jsonPath("$[0].text").value("Como voce gasta?"))
        .andExpect(jsonPath("$[0].options[0].id").value(optionId.toString()))
        .andExpect(jsonPath("$[0].options[0].code").value("CONTROLLED"))
        .andExpect(jsonPath("$[0].options[0].weight").value(100));
  }

  @Test
  @DisplayName("GET sem pergunta cadastrada devolve lista vazia, nao erro")
  void listReturnsEmptyArrayWhenNoQuestionExists() throws Exception {
    when(financialProfileQuestionService.findAll()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/financial-profile/questions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }
}
