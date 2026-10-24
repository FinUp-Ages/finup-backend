package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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

  @Test
  @DisplayName("isActive e false quando o usuario nunca acessou (lastSeenAt nulo)")
  void isActiveIsFalseWhenNeverSeen() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");

    assertThat(user.isActive(Instant.now())).isFalse();
  }

  @Test
  @DisplayName("isActive e true quando o ultimo acesso foi dentro dos ultimos 7 dias")
  void isActiveIsTrueWithinSevenDays() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant lastSeen = Instant.parse("2026-10-02T12:00:00Z");
    // lastSeenAt e gravado direto no banco por UserRepository.touchLastSeen, sem setter na
    // entidade - o reflection simula aqui o valor que viria de uma leitura real do JPA.
    ReflectionTestUtils.setField(user, "lastSeenAt", lastSeen);

    assertThat(user.isActive(lastSeen.plus(7, ChronoUnit.DAYS))).isTrue();
  }

  @Test
  @DisplayName("isActive e false quando o ultimo acesso foi ha mais de 7 dias")
  void isActiveIsFalseAfterSevenDays() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant lastSeen = Instant.parse("2026-10-02T12:00:00Z");
    ReflectionTestUtils.setField(user, "lastSeenAt", lastSeen);

    assertThat(user.isActive(lastSeen.plus(7, ChronoUnit.DAYS).plusSeconds(1))).isFalse();
  }
}
