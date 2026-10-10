package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Garante o reconhecimento por nome (sem acento/caixa) e que categorias comuns passam ilesas. */
class RiskySpendingTest {

  @ParameterizedTest
  @CsvSource({
    "Bets, BETS",
    "bet, BETS",
    "Apostas esportivas, BETS",
    "Apostas (Bets), BETS",
    "Cassino, BETS",
    "Bebidas alcoólicas, ALCOHOL",
    "bebida alcoolica, ALCOHOL",
    "Cerveja, ALCOHOL",
    "Drogas, DRUGS",
    "DROGA, DRUGS"
  })
  @DisplayName("reconhece o tipo de gasto de risco pelo nome da categoria")
  void recognisesRiskyCategories(String name, RiskySpending expected) {
    assertThat(RiskySpending.fromCategoryName(name)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Alimentação",
        "Educação",
        "Beterraba",
        "Better Life",
        "Bar da esquina",
        "Salário",
        ""
      })
  @DisplayName("categorias comuns nao sao marcadas como risco")
  void ignoresOrdinaryCategories(String name) {
    assertThat(RiskySpending.fromCategoryName(name)).isEmpty();
  }

  @Test
  @DisplayName("nome nulo nao quebra")
  void nullNameIsEmpty() {
    assertThat(RiskySpending.fromCategoryName(null)).isEqualTo(Optional.empty());
  }
}
