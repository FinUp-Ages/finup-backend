package br.com.finup.service;

import br.com.finup.exception.InvalidRecurrencePeriodException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.TransactionRecurrence;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.TransactionRecurrenceRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de cadastro e consulta de recorrencias de transacao.
 *
 * <p>O dono da recorrencia vem sempre da identidade autenticada, nunca do corpo nem da query string
 * — mesmo contrato que o {@link CategoryService} segue.
 */
@Service
public class TransactionRecurrenceService {

  private static final Logger log = LoggerFactory.getLogger(TransactionRecurrenceService.class);

  private final TransactionRecurrenceRepository transactionRecurrenceRepository;
  private final UserService userService;

  public TransactionRecurrenceService(
      TransactionRecurrenceRepository transactionRecurrenceRepository, UserService userService) {
    this.transactionRecurrenceRepository = transactionRecurrenceRepository;
    this.userService = userService;
  }

  /**
   * Cadastra uma recorrencia no nome do usuario autenticado, depois de confirmar que as referencias
   * informadas existem.
   *
   * @throws ResourceNotFoundException se a identidade nao tiver usuario local, ou se a categoria ou
   *     o meio de pagamento nao existir
   * @throws InvalidRecurrencePeriodException se a data de termino for anterior a de inicio
   */
  @Transactional
  public TransactionRecurrence register(
      AuthenticatedIdentity identity,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      RecurrenceFrequency frequency,
      int dayOfMonth,
      LocalDate startDate,
      LocalDate endDate) {
    User user = userService.findByAuthenticatedIdentity(identity);
    requireExistingReferences(categoryId, paymentMethodId);
    if (endDate != null && endDate.isBefore(startDate)) {
      throw new InvalidRecurrencePeriodException();
    }

    TransactionRecurrence recurrence =
        transactionRecurrenceRepository.save(
            TransactionRecurrence.register(
                user.getId(),
                categoryId,
                paymentMethodId,
                type,
                description,
                amount,
                frequency,
                dayOfMonth,
                startDate,
                endDate));
    log.info(
        "Recorrencia de transacao cadastrada: id={}, userId={}", recurrence.getId(), user.getId());
    return recurrence;
  }

  /**
   * Recorrencias do usuario autenticado que devem gerar uma transacao exatamente na data informada.
   *
   * @throws ResourceNotFoundException se a identidade nao tiver usuario local
   */
  public List<TransactionRecurrence> findDueOn(AuthenticatedIdentity identity, LocalDate date) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return transactionRecurrenceRepository.findByUserId(user.getId()).stream()
        .filter(
            recurrence -> recurrence.nextOccurrenceOnOrAfter(date).map(date::isEqual).orElse(false))
        .toList();
  }

  private void requireExistingReferences(UUID categoryId, UUID paymentMethodId) {
    if (!transactionRecurrenceRepository.existsCategoryById(categoryId)) {
      throw new ResourceNotFoundException("Categoria", categoryId);
    }
    if (paymentMethodId != null
        && !transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId)) {
      throw new ResourceNotFoundException("Meio de pagamento", paymentMethodId);
    }
  }
}
