package br.com.finup.mapper;

import br.com.finup.dto.TransactionRecurrenceResponse;
import br.com.finup.model.TransactionRecurrence;
import java.time.LocalDate;

/** Converte a entidade de recorrencia de transacao para o contrato publico da API. */
public final class TransactionRecurrenceMapper {

  private TransactionRecurrenceMapper() {}

  public static TransactionRecurrenceResponse toResponse(TransactionRecurrence recurrence) {
    return new TransactionRecurrenceResponse(
        recurrence.getId(),
        recurrence.getUserId(),
        recurrence.getCategoryId(),
        recurrence.getPaymentMethodId(),
        recurrence.getType(),
        recurrence.getDescription(),
        recurrence.getAmount(),
        recurrence.getFrequency(),
        recurrence.getDayOfMonth(),
        recurrence.getStartDate(),
        recurrence.getEndDate(),
        recurrence.nextOccurrenceOnOrAfter(LocalDate.now()).orElse(null),
        recurrence.getCreatedAt(),
        recurrence.getUpdatedAt());
  }
}
