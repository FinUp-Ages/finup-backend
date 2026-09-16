package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Garante as validacoes de referencias e o cadastro de recorrencias de transacao. */
@ExtendWith(MockitoExtension.class)
class TransactionRecurrenceServiceTest {

  @Mock private TransactionRecurrenceRepository transactionRecurrenceRepository;

  private TransactionRecurrenceService transactionRecurrenceService;

  @BeforeEach
  void setUp() {
    transactionRecurrenceService =
        new TransactionRecurrenceService(transactionRecurrenceRepository);
  }

  @Test
  @DisplayName("cadastra a recorrencia quando usuario e categoria existem")
  void registersWhenReferencesExist() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.save(any(TransactionRecurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TransactionRecurrence recurrence =
        register(userId, categoryId, null, LocalDate.of(2026, 9, 1), null);

    assertThat(recurrence.getId()).isNotNull();
    assertThat(recurrence.getUserId()).isEqualTo(userId);
    assertThat(recurrence.getCategoryId()).isEqualTo(categoryId);
    verify(transactionRecurrenceRepository, never()).existsPaymentMethodById(any());
  }

  @Test
  @DisplayName("associa o meio de pagamento quando ele e informado e existe")
  void registersWithExistingPaymentMethod() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId)).thenReturn(true);
    when(transactionRecurrenceRepository.save(any(TransactionRecurrence.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    TransactionRecurrence recurrence =
        register(userId, categoryId, paymentMethodId, LocalDate.of(2026, 9, 1), null);

    assertThat(recurrence.getPaymentMethodId()).isEqualTo(paymentMethodId);
  }

  @Test
  @DisplayName("recusa quando o usuario nao existe")
  void rejectsUnknownUser() {
    UUID userId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(false);

    assertThatThrownBy(() -> register(userId, UUID.randomUUID(), null, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Usuario");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando a categoria nao existe")
  void rejectsUnknownCategory() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(false);

    assertThatThrownBy(() -> register(userId, categoryId, null, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Categoria");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando o meio de pagamento informado nao existe")
  void rejectsUnknownPaymentMethod() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsPaymentMethodById(paymentMethodId))
        .thenReturn(false);

    assertThatThrownBy(() -> register(userId, categoryId, paymentMethodId, LocalDate.now(), null))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Meio de pagamento");
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("recusa quando a data de termino e anterior a data de inicio")
  void rejectsEndDateBeforeStartDate() {
    UUID userId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    when(transactionRecurrenceRepository.existsUserById(userId)).thenReturn(true);
    when(transactionRecurrenceRepository.existsCategoryById(categoryId)).thenReturn(true);

    assertThatThrownBy(
            () ->
                register(
                    userId, categoryId, null, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 1, 1)))
        .isInstanceOf(InvalidRecurrencePeriodException.class);
    verify(transactionRecurrenceRepository, never()).save(any());
  }

  @Test
  @DisplayName("findDueOn devolve apenas as recorrencias do usuario que caem na data informada")
  void findDueOnFiltersByDate() {
    UUID userId = UUID.randomUUID();
    TransactionRecurrence dueToday =
        TransactionRecurrence.register(
            userId,
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Conta de luz",
            new BigDecimal("250.00"),
            RecurrenceFrequency.MONTHLY,
            5,
            LocalDate.of(2026, 1, 1),
            null);
    TransactionRecurrence dueLater =
        TransactionRecurrence.register(
            userId,
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Internet",
            new BigDecimal("120.00"),
            RecurrenceFrequency.MONTHLY,
            20,
            LocalDate.of(2026, 1, 1),
            null);
    when(transactionRecurrenceRepository.findByUserId(userId))
        .thenReturn(List.of(dueToday, dueLater));

    List<TransactionRecurrence> due =
        transactionRecurrenceService.findDueOn(userId, LocalDate.of(2026, 9, 5));

    assertThat(due).containsExactly(dueToday);
  }

  private TransactionRecurrence register(
      UUID userId, UUID categoryId, UUID paymentMethodId, LocalDate startDate, LocalDate endDate) {
    return transactionRecurrenceService.register(
        userId,
        categoryId,
        paymentMethodId,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        RecurrenceFrequency.MONTHLY,
        5,
        startDate,
        endDate);
  }
}
