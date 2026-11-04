package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import br.com.finup.dto.FinancialProfileQuestionResponse;
import br.com.finup.model.FinancialProfileAnswerOption;
import br.com.finup.model.FinancialProfileQuestion;
import br.com.finup.repository.FinancialProfileQuestionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinancialProfileQuestionServiceTest {

  @Mock private FinancialProfileQuestionRepository questionRepository;

  @InjectMocks private FinancialProfileQuestionService service;

  @Test
  @DisplayName("sem pergunta cadastrada devolve lista vazia")
  void returnsEmptyListWhenNoQuestionExists() {
    when(questionRepository.findAllWithOptionsOrderByOrderIndex()).thenReturn(List.of());

    assertThat(service.findAll()).isEmpty();
  }

  @Test
  @DisplayName("mapeia pergunta e opcoes, preservando weight nulo")
  void mapsQuestionAndOptionsPreservingNullWeight() {
    FinancialProfileQuestion question =
        new FinancialProfileQuestion("SPENDING_HABIT", "Como voce gasta?", 1);
    new FinancialProfileAnswerOption(
        question, "CONTROLLED", "Controlado", new BigDecimal("100"), 1);
    new FinancialProfileAnswerOption(question, "IMPULSIVE", "Impulsivo", null, 2);
    when(questionRepository.findAllWithOptionsOrderByOrderIndex()).thenReturn(List.of(question));

    List<FinancialProfileQuestionResponse> result = service.findAll();

    assertThat(result).hasSize(1);
    FinancialProfileQuestionResponse response = result.get(0);
    assertThat(response.code()).isEqualTo("SPENDING_HABIT");
    assertThat(response.text()).isEqualTo("Como voce gasta?");
    assertThat(response.options()).hasSize(2);
    assertThat(response.options().get(0).code()).isEqualTo("CONTROLLED");
    assertThat(response.options().get(0).weight()).isEqualByComparingTo("100");
    assertThat(response.options().get(1).code()).isEqualTo("IMPULSIVE");
    assertThat(response.options().get(1).weight()).isNull();
  }
}
