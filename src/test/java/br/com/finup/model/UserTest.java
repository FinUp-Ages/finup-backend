package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

  @Test
  @DisplayName("touchLastSeen grava o primeiro acesso e devolve true")
  void touchLastSeenRecordsFirstAccess() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant now = Instant.parse("2026-10-09T12:00:00Z");

    boolean changed = user.touchLastSeen(now);

    assertThat(changed).isTrue();
    assertThat(user.getLastSeenAt()).isEqualTo(now);
  }

  @Test
  @DisplayName("touchLastSeen dentro de uma hora do ultimo registro nao atualiza e devolve false")
  void touchLastSeenWithinThrottleWindowIsIgnored() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant firstAccess = Instant.parse("2026-10-09T12:00:00Z");
    user.touchLastSeen(firstAccess);

    boolean changed = user.touchLastSeen(firstAccess.plus(59, ChronoUnit.MINUTES));

    assertThat(changed).isFalse();
    assertThat(user.getLastSeenAt()).isEqualTo(firstAccess);
  }

  @Test
  @DisplayName("touchLastSeen uma hora ou mais depois do ultimo registro atualiza de novo")
  void touchLastSeenAfterThrottleWindowUpdates() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant firstAccess = Instant.parse("2026-10-09T12:00:00Z");
    user.touchLastSeen(firstAccess);
    Instant secondAccess = firstAccess.plus(1, ChronoUnit.HOURS);

    boolean changed = user.touchLastSeen(secondAccess);

    assertThat(changed).isTrue();
    assertThat(user.getLastSeenAt()).isEqualTo(secondAccess);
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
    user.touchLastSeen(lastSeen);

    assertThat(user.isActive(lastSeen.plus(7, ChronoUnit.DAYS))).isTrue();
  }

  @Test
  @DisplayName("isActive e false quando o ultimo acesso foi ha mais de 7 dias")
  void isActiveIsFalseAfterSevenDays() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");
    Instant lastSeen = Instant.parse("2026-10-02T12:00:00Z");
    user.touchLastSeen(lastSeen);

    assertThat(user.isActive(lastSeen.plus(7, ChronoUnit.DAYS).plusSeconds(1))).isFalse();
  }
}
