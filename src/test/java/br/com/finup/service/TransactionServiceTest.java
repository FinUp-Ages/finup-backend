package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import br.com.finup.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Garante as validacoes de referencias e o cadastro executado pelo service de transacoes. */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

  @Mock private TransactionRepository transactionRepository;

  private TransactionService transactionService;

  @BeforeEach
  void setUp() {
    transactionService = new TransactionService(transactionRepository);
  }

  @Test
  @DisplayName("cadastra transacao sem consultar meio de pagamento quando ele nao e informado")
  void registersWithoutPaymentMethod() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(transactionRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Transaction transaction =
        transactionService.register(
            userId,
            categoryId,
            null,
            TransactionType.INCOME,
            "Salario",
            new BigDecimal("5000.00"),
            LocalDate.of(2026, 9, 12),
            true);

    assertThat(transaction.getId()).isNotNull();
    assertThat(transaction.getUserId()).isEqualTo(userId);
    assertThat(transaction.getCategoryId()).isEqualTo(categoryId);
    assertThat(transaction.getPaymentMethodId()).isNull();
    assertThat(transaction.getType()).isEqualTo(TransactionType.INCOME);
    verify(transactionRepository, never()).existsPaymentMethodById(any());
  }

  @Test
  @DisplayName("associa o meio de pagamento quando ele e informado e existe")
  void registersWithExistingPaymentMethod() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(transactionRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRepository.existsPaymentMethodById(paymentMethodId)).thenReturn(true);
    when(transactionRepository.save(any(Transaction.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Transaction transaction =
        transactionService.register(
            userId,
            categoryId,
            paymentMethodId,
            TransactionType.EXPENSE,
            "Supermercado",
            new BigDecimal("320.00"),
            LocalDate.of(2026, 9, 12),
            false);

    assertThat(transaction.getPaymentMethodId()).isEqualTo(paymentMethodId);
  }

  @Test
  @DisplayName("recusa transacao quando o usuario nao existe")
  void rejectsUnknownUser() {
    UUID userId = UUID.randomUUID();
    when(transactionRepository.existsUserById(userId)).thenReturn(false);

    assertThatThrownBy(() -> register(userId, UUID.randomUUID(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Usuario");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa transacao quando a categoria nao existe")
  void rejectsUnknownCategory() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(transactionRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRepository.existsCategoryById(categoryId)).thenReturn(false);

    assertThatThrownBy(() -> register(userId, categoryId, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Categoria");
    verify(transactionRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa transacao quando o meio de pagamento informado nao existe")
  void rejectsUnknownPaymentMethod() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(transactionRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRepository.existsPaymentMethodById(paymentMethodId)).thenReturn(false);

    assertThatThrownBy(() -> register(userId, categoryId, paymentMethodId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Meio de pagamento");
    verify(transactionRepository, never()).save(any());
  }

  private Transaction register(UUID userId, UUID categoryId, UUID paymentMethodId) {
    return transactionService.register(
        userId,
        categoryId,
        paymentMethodId,
        TransactionType.EXPENSE,
        "Compra",
        new BigDecimal("10.00"),
        LocalDate.of(2026, 9, 12),
        false);
  }
}
