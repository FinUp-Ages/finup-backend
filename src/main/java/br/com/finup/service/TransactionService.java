package br.com.finup.service;

import br.com.finup.dto.TransactionListItemResponse;
import br.com.finup.dto.TransactionListResponse;
import br.com.finup.exception.IncompatibleTransactionCategoryException;
import br.com.finup.exception.InvalidTransactionPeriodException;
import br.com.finup.exception.InvalidTransactionRecurrenceException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.TransactionRepository;
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
 * Regras do fluxo de cadastro de uma transacao financeira.
 *
 * <p>O dono da transacao vem sempre da identidade autenticada, nunca do corpo da requisicao — mesmo
 * contrato que o {@link CategoryService} segue.
 */
@Service
public class TransactionService {

  private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

  private final TransactionRepository transactionRepository;
  private final UserService userService;

  public TransactionService(TransactionRepository transactionRepository, UserService userService) {
    this.transactionRepository = transactionRepository;
    this.userService = userService;
  }

  /**
   * Registra uma transacao no nome do usuario autenticado, depois de confirmar que as referencias
   * informadas existem e estao disponiveis para ele.
   *
   * @throws InvalidTransactionRecurrenceException se {@code recurring} e {@code
   *     recurrenceFrequency} se contradizem
   * @throws IncompatibleTransactionCategoryException se o tipo da categoria nao for o mesmo tipo da
   *     transacao
   * @throws ResourceNotFoundException se a identidade nao tiver usuario local, ou se a categoria ou
   *     o meio de pagamento nao existir ou pertencer a outro usuario
   */
  @Transactional
  public Transaction register(
      AuthenticatedIdentity identity,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      LocalDate transactionDate,
      boolean recurring,
      RecurrenceFrequency recurrenceFrequency) {

    validateRecurrence(recurring, recurrenceFrequency);

    User user = userService.findByAuthenticatedIdentity(identity);
    requireAvailableReferences(user.getId(), categoryId, type, paymentMethodId);

    Transaction transaction =
        transactionRepository.save(
            Transaction.register(
                user.getId(),
                categoryId,
                paymentMethodId,
                type,
                description,
                amount,
                transactionDate,
                recurrenceFrequency));

    log.info("Transacao cadastrada: id={}, userId={}", transaction.getId(), user.getId());
    return transaction;
  }

  @Transactional(readOnly = true)
  public TransactionListResponse list(
      AuthenticatedIdentity identity, LocalDate from, LocalDate to) {

    if (from.isAfter(to)) {
      throw new InvalidTransactionPeriodException();
    }

    User user = userService.findByAuthenticatedIdentity(identity);

    List<Transaction> transactions =
        transactionRepository.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            user.getId(), from, to);

    BigDecimal balance =
        transactions.stream()
            .map(
                transaction ->
                    transaction.getType() == TransactionType.INCOME
                        ? transaction.getAmount()
                        : transaction.getAmount().negate())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new TransactionListResponse(
        balance,
        transactions.stream()
            .map(
                transaction ->
                    new TransactionListItemResponse(
                        transaction.getId(),
                        transaction.getCategoryId(),
                        transaction.getPaymentMethodId(),
                        transaction.getType(),
                        transaction.getDescription(),
                        transaction.getAmount(),
                        transaction.getTransactionDate(),
                        transaction.isRecurring(),
                        transaction.getCreatedAt()))
            .toList());
  }

  /**
   * Referencia de outro usuario responde 404, e nao 403: um 403 confirmaria a existencia do id para
   * quem nao deveria saber dela. Mesma decisao tomada no CRUD de categorias.
   *
   * <p>Por isso a indisponibilidade da categoria (404) e avaliada antes da incompatibilidade de
   * tipo (422): na ordem inversa, um 422 em categoria privada de outro usuario confirmaria a
   * existencia daquele id — exatamente o vazamento que o 404 evita.
   */
  private void requireAvailableReferences(
      UUID userId, UUID categoryId, TransactionType type, UUID paymentMethodId) {

    TransactionType categoryType =
        transactionRepository
            .findAvailableCategoryTypeForUser(categoryId, userId)
            .map(TransactionType::valueOf)
            .orElseThrow(() -> new ResourceNotFoundException("Categoria", categoryId));

    if (categoryType != type) {
      throw new IncompatibleTransactionCategoryException(categoryType, type);
    }

    if (paymentMethodId != null
        && !transactionRepository.existsPaymentMethodForUser(paymentMethodId, userId)) {
      throw new ResourceNotFoundException("Meio de pagamento", paymentMethodId);
    }
  }

  private void validateRecurrence(boolean recurring, RecurrenceFrequency recurrenceFrequency) {

    if (recurring && recurrenceFrequency == null) {
      throw new InvalidTransactionRecurrenceException(
          "Transacao recorrente precisa informar recurrenceFrequency");
    }

    if (!recurring && recurrenceFrequency != null) {
      throw new InvalidTransactionRecurrenceException(
          "Transacao nao recorrente nao pode informar recurrenceFrequency");
    }
  }
}
