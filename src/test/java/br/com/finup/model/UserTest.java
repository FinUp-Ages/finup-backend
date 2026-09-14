package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Testa as invariantes de {@link User}, isolado de banco e de service. */
class UserTest {

  @Test
  @DisplayName("createFromCognitoIdentity normaliza email e aceita nome nulo")
  void createsWithNormalizedEmailAndOptionalName() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", null, "  Ana@Exemplo.COM ");

    assertThat(user.getName()).isNull();
    assertThat(user.getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
  }

  @Test
  @DisplayName("applyAdditionalInfo com todos os campos preenche tudo")
  void appliesAllFieldsWhenAllProvided() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");

    user.applyAdditionalInfo(
        LocalDate.of(1998, 4, 12), new BigDecimal("3500.00"), FinancialProfile.MODERATE);

    assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
    assertThat(user.getMonthlyIncome()).isEqualByComparingTo("3500.00");
    assertThat(user.getFinancialProfile()).isEqualTo(FinancialProfile.MODERATE);
  }

  @Test
  @DisplayName("applyAdditionalInfo com um campo nulo preserva o valor ja salvo dos outros")
  void preservesUntouchedFieldsOnPartialUpdate() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    user.applyAdditionalInfo(LocalDate.of(1998, 4, 12), new BigDecimal("3500.00"), null);

    // Segunda chamada: so financialProfile vem preenchido, o resto vem nulo (nao informado).
    user.applyAdditionalInfo(null, null, FinancialProfile.AGGRESSIVE);

    assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1998, 4, 12));
    assertThat(user.getMonthlyIncome()).isEqualByComparingTo("3500.00");
    assertThat(user.getFinancialProfile()).isEqualTo(FinancialProfile.AGGRESSIVE);
  }
}
