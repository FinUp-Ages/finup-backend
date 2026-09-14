package br.com.finup.service;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Regras do fluxo de cadastro de uma transacao financeira. */
@Service
public class TransactionService {

  private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

  private final TransactionRepository transactionRepository;

  public TransactionService(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /**
   * Registra uma transacao depois de confirmar que suas referencias existem.
   *
   * @throws ResourceNotFoundException se usuario, categoria ou meio de pagamento nao existir
   */
  @Transactional
  public Transaction register(
      UUID userId,
      UUID categoryId,
      UUID paymentMethodId,
      TransactionType type,
      String description,
      BigDecimal amount,
      LocalDate transactionDate,
      boolean recurring) {
    requireExistingReferences(userId, categoryId, paymentMethodId);

    Transaction transaction =
        transactionRepository.save(
            Transaction.register(
                userId,
                categoryId,
                paymentMethodId,
                type,
                description,
                amount,
                transactionDate,
                recurring));
    log.info("Transacao cadastrada: id={}, userId={}", transaction.getId(), userId);
    return transaction;
  }

  private void requireExistingReferences(UUID userId, UUID categoryId, UUID paymentMethodId) {
    if (!transactionRepository.existsUserById(userId)) {
      throw new ResourceNotFoundException("Usuario", userId);
    }
    if (!transactionRepository.existsCategoryById(categoryId)) {
      throw new ResourceNotFoundException("Categoria", categoryId);
    }
    if (paymentMethodId != null
        && !transactionRepository.existsPaymentMethodById(paymentMethodId)) {
      throw new ResourceNotFoundException("Meio de pagamento", paymentMethodId);
    }
  }
}
