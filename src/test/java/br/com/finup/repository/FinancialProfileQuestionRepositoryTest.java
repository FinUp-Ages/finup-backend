package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.FinancialProfileAnswerOption;
import br.com.finup.model.FinancialProfileQuestion;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Valida o mapeamento JPA das perguntas de perfil financeiro e a ordenacao da consulta. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class FinancialProfileQuestionRepositoryTest {

  @Autowired private FinancialProfileQuestionRepository questionRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("devolve vazio quando nao ha pergunta cadastrada")
  void returnsEmptyWhenNoQuestionExists() {
    assertThat(questionRepository.findAllWithOptionsOrderByOrderIndex()).isEmpty();
  }

  @Test
  @DisplayName("ordena perguntas por orderIndex e opcoes dentro da pergunta tambem")
  void ordersQuestionsAndOptionsByOrderIndex() {
    FinancialProfileQuestion habit =
        new FinancialProfileQuestion("SPENDING_HABIT", "Como voce gasta?", 2);
    new FinancialProfileAnswerOption(habit, "IMPULSIVE", "Impulsivo", new BigDecimal("20"), 3);
    new FinancialProfileAnswerOption(habit, "CONTROLLED", "Controlado", new BigDecimal("100"), 1);
    new FinancialProfileAnswerOption(habit, "MODERATE", "Moderado", new BigDecimal("60"), 2);

    FinancialProfileQuestion income =
        new FinancialProfileQuestion("INCOME_EXPENSE_RELATION", "Renda x gastos?", 1);
    new FinancialProfileAnswerOption(
        income, "SPENDS_LESS", "Gasto menos", new BigDecimal("0.25"), 1);

    questionRepository.saveAll(List.of(habit, income));
    entityManager.flush();
    entityManager.clear();

    List<FinancialProfileQuestion> found = questionRepository.findAllWithOptionsOrderByOrderIndex();

    assertThat(found)
        .extracting(FinancialProfileQuestion::getCode)
        .containsExactly("INCOME_EXPENSE_RELATION", "SPENDING_HABIT");
    assertThat(found.get(1).getOptions())
        .extracting(FinancialProfileAnswerOption::getCode)
        .containsExactly("CONTROLLED", "MODERATE", "IMPULSIVE");
  }

  @Test
  @DisplayName("pergunta sem nenhuma opcao aparece com lista vazia, nao desaparece do resultado")
  void questionWithoutOptionsStillAppears() {
    questionRepository.saveAndFlush(
        new FinancialProfileQuestion("SEM_OPCOES", "Pergunta sem opcoes", 1));
    entityManager.clear();

    List<FinancialProfileQuestion> found = questionRepository.findAllWithOptionsOrderByOrderIndex();

    assertThat(found).hasSize(1);
    assertThat(found.get(0).getOptions()).isEmpty();
  }

  @Test
  @DisplayName("weight nulo e preservado, para perguntas que ainda nao entram no calculo")
  void nullWeightIsPreserved() {
    FinancialProfileQuestion question =
        new FinancialProfileQuestion("HAS_INVESTMENTS", "Possui investimentos?", 1);
    new FinancialProfileAnswerOption(question, "YES", "Sim", null, 1);
    questionRepository.saveAndFlush(question);
    entityManager.clear();

    List<FinancialProfileQuestion> found = questionRepository.findAllWithOptionsOrderByOrderIndex();

    assertThat(found.get(0).getOptions().get(0).getWeight()).isNull();
  }
}
