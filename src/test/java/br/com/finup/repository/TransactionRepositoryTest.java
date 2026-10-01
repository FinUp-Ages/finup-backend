package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.finup.model.RecurrenceFrequency;
import br.com.finup.model.Transaction;
import br.com.finup.model.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Valida o mapeamento JPA de transacoes e as consultas das referencias no banco. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class TransactionRepositoryTest {

  @Autowired private TransactionRepository transactionRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void createReferenceTables() {
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id UUID PRIMARY KEY)");
    jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS categories (id UUID PRIMARY KEY)");
    jdbcTemplate.execute(
        "CREATE TABLE IF NOT EXISTS payment_methods (id UUID PRIMARY KEY, user_id UUID NOT NULL)");
  }

  private UUID insertUser() {
    UUID userId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO users (id, cognito_id, email, created_at, updated_at)"
            + " VALUES (?, ?, ?, now(), now())",
        userId,
        "cognito-" + userId,
        userId + "@example.com");
    return userId;
  }

  private UUID insertCategory(UUID ownerId, boolean isDefault) {
    return insertCategory(ownerId, isDefault, TransactionType.EXPENSE);
  }

  private UUID insertCategory(UUID ownerId, boolean isDefault, TransactionType type) {
    UUID categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO categories (id, user_id, name, type, is_default, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, now(), now())",
        categoryId,
        ownerId,
        "Alimentação",
        type.name(),
        isDefault);
    return categoryId;
  }

  private UUID insertPaymentMethod(UUID ownerId) {
    UUID paymentMethodId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO payment_methods (id, user_id) VALUES (?, ?)", paymentMethodId, ownerId);
    return paymentMethodId;
  }

  @Test
  @DisplayName("aceita a categoria do proprio usuario e a categoria padrao do sistema")
  void findsCategoriesAvailableForUser() {
    UUID userId = insertUser();
    UUID ownCategoryId = insertCategory(userId, false);
    UUID defaultCategoryId = insertCategory(null, true);

    assertThat(transactionRepository.findAvailableCategoryTypeForUser(ownCategoryId, userId))
        .contains("EXPENSE");
    assertThat(transactionRepository.findAvailableCategoryTypeForUser(defaultCategoryId, userId))
        .contains("EXPENSE");
    assertThat(transactionRepository.findAvailableCategoryTypeForUser(UUID.randomUUID(), userId))
        .isEmpty();
  }

  @Test
  @DisplayName("devolve o tipo real de cada categoria disponivel")
  void returnsTypeOfAvailableCategory() {
    UUID userId = insertUser();
    UUID incomeCategoryId = insertCategory(userId, false, TransactionType.INCOME);
    UUID expenseCategoryId = insertCategory(null, true, TransactionType.EXPENSE);

    assertThat(transactionRepository.findAvailableCategoryTypeForUser(incomeCategoryId, userId))
        .contains("INCOME");
    assertThat(transactionRepository.findAvailableCategoryTypeForUser(expenseCategoryId, userId))
        .contains("EXPENSE");
  }

  @Test
  @DisplayName("trata a categoria privada de outro usuario como inexistente")
  void ignoresCategoryOwnedByAnotherUser() {
    UUID userId = insertUser();
    UUID otherUserId = insertUser();
    UUID otherUsersCategoryId = insertCategory(otherUserId, false);

    assertThat(
            transactionRepository.findAvailableCategoryTypeForUser(
                otherUsersCategoryId, otherUserId))
        .contains("EXPENSE");
    assertThat(transactionRepository.findAvailableCategoryTypeForUser(otherUsersCategoryId, userId))
        .isEmpty();
  }

  @Test
  @DisplayName("aceita so o meio de pagamento do proprio usuario")
  void findsOnlyOwnPaymentMethod() {
    UUID userId = insertUser();
    UUID otherUserId = insertUser();
    UUID ownPaymentMethodId = insertPaymentMethod(userId);
    UUID otherUsersPaymentMethodId = insertPaymentMethod(otherUserId);

    assertThat(transactionRepository.existsPaymentMethodForUser(ownPaymentMethodId, userId))
        .isTrue();
    assertThat(transactionRepository.existsPaymentMethodForUser(otherUsersPaymentMethodId, userId))
        .isFalse();
    assertThat(transactionRepository.existsPaymentMethodForUser(UUID.randomUUID(), userId))
        .isFalse();
  }

  @Test
  @DisplayName("persiste todos os campos da transacao com meio de pagamento opcional")
  void persistsTransactionWithoutPaymentMethod() {
    Transaction transaction =
        Transaction.register(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            TransactionType.EXPENSE,
            "Supermercado",
            new BigDecimal("320.00"),
            LocalDate.of(2026, 9, 12),
            null);

    Transaction saved = transactionRepository.saveAndFlush(transaction);

    assertThat(transactionRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.getPaymentMethodId()).isNull();
              assertThat(found.getAmount()).isEqualByComparingTo("320.00");
              assertThat(found.getTransactionDate()).isEqualTo(LocalDate.of(2026, 9, 12));
              assertThat(found.isRecurring()).isFalse();
              assertThat(found.getRecurrenceFrequency()).isNull();
              assertThat(found.getLastOccurrenceDate()).isNull();
              assertThat(found.getRecurrenceOriginId()).isNull();
              assertThat(found.getCreatedAt()).isNotNull();
              assertThat(found.getUpdatedAt()).isNotNull();
            });
  }

  @Test
  @DisplayName("persiste a serie recorrente com periodicidade e data da ultima ocorrencia")
  void persistsRecurringSeries() {
    Transaction saved = transactionRepository.saveAndFlush(monthlySeries(LocalDate.of(2026, 9, 5)));

    assertThat(transactionRepository.findById(saved.getId()))
        .get()
        .satisfies(
            found -> {
              assertThat(found.isRecurring()).isTrue();
              assertThat(found.getRecurrenceFrequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
              assertThat(found.getLastOccurrenceDate()).isEqualTo(LocalDate.of(2026, 9, 5));
              assertThat(found.getRecurrenceOriginId()).isNull();
            });
  }

  @Test
  @DisplayName("aceita uma ocorrencia gerada por data e recusa a segunda na mesma data")
  void rejectsDuplicatedOccurrenceOfSameSeries() {
    Transaction origin =
        transactionRepository.saveAndFlush(monthlySeries(LocalDate.of(2026, 9, 5)));

    insertGeneratedOccurrence(origin, LocalDate.of(2026, 10, 5));
    insertGeneratedOccurrence(origin, LocalDate.of(2026, 11, 5));

    assertThatThrownBy(() -> insertGeneratedOccurrence(origin, LocalDate.of(2026, 10, 5)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Transaction monthlySeries(LocalDate transactionDate) {
    return Transaction.register(
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        TransactionType.EXPENSE,
        "Conta de luz",
        new BigDecimal("250.00"),
        transactionDate,
        RecurrenceFrequency.MONTHLY);
  }

  /** A geracao das ocorrencias ainda nao existe no codigo; aqui ela e simulada direto no banco. */
  private void insertGeneratedOccurrence(Transaction origin, LocalDate transactionDate) {
    jdbcTemplate.update(
        "INSERT INTO transactions (id, user_id, category_id, type, description, amount,"
            + " transaction_date, is_recurring, recurrence_origin_id, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, FALSE, ?, now(), now())",
        UUID.randomUUID(),
        origin.getUserId(),
        origin.getCategoryId(),
        origin.getType().name(),
        origin.getDescription(),
        origin.getAmount(),
        transactionDate,
        origin.getId());
  }
}
