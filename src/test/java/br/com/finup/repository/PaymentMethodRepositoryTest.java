package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.PaymentMethod;
import br.com.finup.model.User;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class PaymentMethodRepositoryTest {

  private static final OffsetDateTime BASE = OffsetDateTime.parse("2026-11-12T10:00:00Z");

  @Autowired private PaymentMethodRepository paymentMethodRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  private UUID insertUser(String sub) {
    return userRepository
        .saveAndFlush(User.createFromCognitoIdentity(sub, "Ana Souza", sub + "@example.com"))
        .getId();
  }

  private void insert(UUID ownerId, String name, boolean active, int minutesAfterBase) {
    OffsetDateTime createdAt = BASE.plusMinutes(minutesAfterBase);
    jdbcTemplate.update(
        "INSERT INTO payment_methods"
            + " (id, user_id, name, is_active, is_default, created_at, updated_at)"
            + " VALUES (random_uuid(), ?, ?, ?, ?, ?, ?)",
        ownerId,
        name,
        active,
        ownerId == null,
        createdAt,
        createdAt);
  }

  @Test
  @DisplayName("lista as formas padrao na ordem do seed e depois as do proprio usuario")
  void listsDefaultsFirstThenOwn() {
    UUID userId = insertUser("payment-sub");
    insert(userId, "Nubank", true, 0);
    insert(null, "Cartão de crédito", true, 2);
    insert(null, "Pix", true, 1);

    List<PaymentMethod> result = paymentMethodRepository.findActiveAvailableForUser(userId);

    assertThat(result)
        .extracting(PaymentMethod::getName)
        .containsExactly("Pix", "Cartão de crédito", "Nubank");
  }

  @Test
  @DisplayName("usuario sem formas proprias recebe as formas padrao")
  void newUserGetsDefaults() {
    UUID userId = insertUser("new-payment-sub");
    insert(null, "Pix", true, 1);
    insert(null, "Dinheiro", true, 2);

    assertThat(paymentMethodRepository.findActiveAvailableForUser(userId))
        .extracting(PaymentMethod::getName)
        .containsExactly("Pix", "Dinheiro");
  }

  @Test
  @DisplayName("nao inclui forma inativa nem forma de outro usuario")
  void excludesInactiveAndOtherUsers() {
    UUID userId = insertUser("owner-payment-sub");
    UUID otherUserId = insertUser("other-payment-sub");
    insert(null, "Pix", true, 1);
    insert(null, "Boleto desativado", false, 2);
    insert(userId, "Cartão antigo", false, 3);
    insert(otherUserId, "Cartão do outro", true, 4);

    assertThat(paymentMethodRepository.findActiveAvailableForUser(userId))
        .extracting(PaymentMethod::getName)
        .containsExactly("Pix");
  }
}
