package br.com.finup.service;

import br.com.finup.exception.InvalidRecurrencePeriodException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.model.TransactionType;
import br.com.finup.repository.TransactionRecurrenceRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras de cadastro e consulta de recorrencias de transacao. */
@Service
public class TransactionRecurrenceService {

  private static final Logger log = LoggerFactory.getLogger(TransactionRecurrenceService.class);

  private final TransactionRecurrenceRepository transactionRecurrenceRepository;

  public TransactionRecurrenceService(
      TransactionRecurrenceRepository transactionRecurrenceRepository) {
    this.transactionRecurrenceRepository = transactionRecurrenceRepository;
  }

  /**
   * Cadastra uma recorrencia depois de confirmar que suas referencias existem.
   *
   * @throws ResourceNotFoundException se usuario, categoria ou meio de pagamento nao existir
   * @throws InvalidRecurrencePeriodException se a data de termino for anterior a de inicio
   */
  @Transactional
  public TransactionRecurrence register(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      RecurrenceFrequency frequency,
      int dayOfMonth,
      LocalDate startDate,
      LocalDate endDate) {
    requireExistingReferences(userId, categoryId, paymentMethodId);
    if (endDate != null && endDate.isBefore(startDate)) {
      throw new InvalidRecurrencePeriodException();
    }

    TransactionRecurrence recurrence =
        transactionRecurrenceRepository.save(
            TransactionRecurrence.register(
                userId,
                categoryId,
                paymentMethodId,
                type,
                description,
                amount,
                frequency,
                dayOfMonth,
                startDate,
                endDate));
    log.info("Recorrencia de transacao cadastrada: id={}, userId={}", recurrence.getId(), userId);
    return recurrence;
  }

  /** Recorrencias do usuario que devem gerar uma transacao exatamente na data informada. */
  public List<TransactionRecurrence> findDueOn(UUID userId, LocalDate date) {
    return transactionRecurrenceRepository.findByUserId(userId).stream()
        .filter(
            recurrence -> recurrence.nextOccurrenceOnOrAfter(date).map(date::isEqual).orElse(false))
        .toList();
  }

  private void requireExistingReferences(UUID userId, UUID categoryId, UUID paymentMethodId) {
    if (!transactionRecurrenceRepository.existsUserById(userId)) {
      throw new ResourceNotFoundException("Usuario", userId);
    }
    if (!transactionRecurrenceRepository.existsCategoryById(categoryId)) {
      throw new ResourceNotFoundException("Categoria", categoryId);
    }
    if (paymentMethodId != null
        && !transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId)) {
      throw new ResourceNotFoundException("Meio de pagamento", paymentMethodId);
    }
  }
}
