package br.com.finup.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Garante o calculo da proxima ocorrencia de uma recorrencia mensal. */
class TransactionRecurrenceTest {

  @Test
  @DisplayName("aponta o mesmo mes quando o dia ainda nao passou")
  void samMonthWhenDayHasNotPassedYet() {
    TransactionRecurrence recurrence = monthly(5, LocalDate.of(2026, 1, 1), null);

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 9, 1)))
        .contains(LocalDate.of(2026, 9, 5));
  }

  @Test
  @DisplayName("avanca para o proximo mes quando o dia ja passou")
  void nextMonthWhenDayAlreadyPassed() {
    TransactionRecurrence recurrence = monthly(5, LocalDate.of(2026, 1, 1), null);

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 9, 6)))
        .contains(LocalDate.of(2026, 10, 5));
  }

  @Test
  @DisplayName("cai no proprio dia quando a referencia e a data da ocorrencia")
  void sameDayIsInclusive() {
    TransactionRecurrence recurrence = monthly(5, LocalDate.of(2026, 1, 1), null);

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 9, 5)))
        .contains(LocalDate.of(2026, 9, 5));
  }

  @Test
  @DisplayName("usa o ultimo dia do mes quando o dia configurado nao existe nele")
  void clampsToLastDayOfShorterMonth() {
    TransactionRecurrence recurrence = monthly(31, LocalDate.of(2026, 1, 1), null);

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 2, 1)))
        .contains(LocalDate.of(2026, 2, 28));
  }

  @Test
  @DisplayName("nao antecipa a data de inicio")
  void neverBeforeStartDate() {
    TransactionRecurrence recurrence = monthly(5, LocalDate.of(2026, 11, 1), null);

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 9, 1)))
        .contains(LocalDate.of(2026, 11, 5));
  }

  @Test
  @DisplayName("vazio quando a recorrencia ja terminou")
  void emptyWhenAlreadyEnded() {
    TransactionRecurrence recurrence =
        monthly(5, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 5));

    assertThat(recurrence.nextOccurrenceOnOrAfter(LocalDate.of(2026, 9, 1))).isEmpty();
  }

  private TransactionRecurrence monthly(int dayOfMonth, LocalDate startDate, LocalDate endDate) {
    return TransactionRecurrence.register(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        RecurrenceFrequency.MONTHLY,
        dayOfMonth,
        startDate,
        endDate);
  }
}
