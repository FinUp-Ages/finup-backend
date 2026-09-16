package br.com.finup.service;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.model.User;
import br.com.finup.repository.TransactionRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.math.BigDecimal;
import java.time.LocalDate;
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
   * @throws ResourceNotFoundException se a identidade nao tiver usuario local, ou se a categoria ou
   *     o meio de pagamento nao existir
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
      boolean recurring) {
    User user = userService.findByAuthenticatedIdentity(identity);
    requireExistingReferences(categoryId, paymentMethodId);

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
                recurring));
    log.info("Transacao cadastrada: id={}, userId={}", transaction.getId(), user.getId());
    return transaction;
  }

  private void requireExistingReferences(UUID categoryId, UUID paymentMethodId) {
    if (!transactionRepository.existsCategoryById(categoryId)) {
      throw new ResourceNotFoundException("Categoria", categoryId);
    }
    if (paymentMethodId != null
        && !transactionRepository.existsPaymentMethodById(paymentMethodId)) {
      throw new ResourceNotFoundException("Meio de pagamento", paymentMethodId);
    }
  }
}
