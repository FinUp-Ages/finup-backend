package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.User;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Valida o mapeamento JPA de usuario e as buscas por identidade do Cognito. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  /** Cria o usuario e fixa o {@code created_at}, que o dominio sempre define como "agora". */
  private void saveUserCreatedAt(String cognitoId, String createdAt) {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity(cognitoId, null, cognitoId + "@exemplo.com"));
    jdbcTemplate.update(
        "UPDATE users SET created_at = ? WHERE id = ?",
        OffsetDateTime.ofInstant(Instant.parse(createdAt), ZoneOffset.UTC),
        user.getId());
  }

  @Test
  @DisplayName("conta cadastros no intervalo [from, to): inicio entra, fim nao")
  void countsSignUpsInHalfOpenInterval() {
    saveUserCreatedAt("antes", "2026-09-30T23:59:59Z");
    saveUserCreatedAt("no-inicio", "2026-10-01T00:00:00Z");
    saveUserCreatedAt("no-fim-do-mes", "2026-10-31T23:59:59Z");
    saveUserCreatedAt("no-limite-superior", "2026-11-01T00:00:00Z");

    assertThat(
            userRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z")))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("conta o acumulado de cadastros anteriores a um instante, sem incluir o proprio")
  void countsSignUpsBeforeAnInstant() {
    saveUserCreatedAt("a", "2026-08-10T10:00:00Z");
    saveUserCreatedAt("b", "2026-09-30T23:59:59Z");
    saveUserCreatedAt("c", "2026-10-01T00:00:00Z");

    assertThat(userRepository.countByCreatedAtLessThan(Instant.parse("2026-10-01T00:00:00Z")))
        .isEqualTo(2);
    assertThat(userRepository.countByCreatedAtLessThan(Instant.parse("2026-01-01T00:00:00Z")))
        .isZero();
  }

  @Test
  @DisplayName("persiste e encontra usuario pelo cognitoId")
  void findsExistingUserByCognitoId() {
    User user = User.createFromCognitoIdentity("cognito-sub-123", "Ana Souza", "ana@exemplo.com");

    userRepository.saveAndFlush(user);

    Optional<User> found = userRepository.findByCognitoId("cognito-sub-123");
    assertThat(found).isPresent();
    assertThat(found.get().getEmail()).isEqualTo("ana@exemplo.com");
    assertThat(found.get().getName()).isEqualTo("Ana Souza");
    assertThat(found.get().getCreatedAt()).isNotNull();
    assertThat(found.get().getUpdatedAt()).isNotNull();
  }

  @Test
  @DisplayName("findByCognitoId devolve vazio quando a identidade nao tem usuario")
  void returnsEmptyWhenCognitoIdDoesNotExist() {
    assertThat(userRepository.findByCognitoId("cognito-sub-inexistente")).isEmpty();
  }

  @Test
  @DisplayName("existsByCognitoId e existsByEmail refletem o que foi persistido")
  void existsMethodsReflectPersistedState() {
    userRepository.saveAndFlush(
        User.createFromCognitoIdentity("cognito-sub-456", "Outra Ana", "outra@exemplo.com"));

    assertThat(userRepository.existsByCognitoId("cognito-sub-456")).isTrue();
    assertThat(userRepository.existsByCognitoId("cognito-sub-000")).isFalse();
    assertThat(userRepository.existsByEmail("outra@exemplo.com")).isTrue();
    assertThat(userRepository.existsByEmail("ninguem@exemplo.com")).isFalse();
  }

  @Test
  @DisplayName("permite name nulo, como no login com Apple sem o primeiro acesso")
  void allowsNullName() {
    User user = User.createFromCognitoIdentity("cognito-sub-789", null, "sem-nome@exemplo.com");

    User saved = userRepository.saveAndFlush(user);

    assertThat(userRepository.findById(saved.getId()))
        .get()
        .satisfies(found -> assertThat(found.getName()).isNull());
  }

  @Test
  @DisplayName("touchLastSeen grava o primeiro acesso sem alterar updatedAt")
  void touchLastSeenRecordsFirstAccessWithoutTouchingUpdatedAt() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("cognito-sub-321", "Ana Souza", "ana2@exemplo.com"));
    // Truncado para microssegundos: e a precisao que o banco guarda, mas o findById dentro da
    // mesma transacao devolve a entidade do cache de sessao do Hibernate (os nanossegundos em
    // memoria do Instant.now()), nao uma leitura de verdade - comparar sem truncar e flaky.
    Instant updatedAtBeforeAccess = user.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
    Instant now = Instant.parse("2026-10-09T12:00:00Z");

    int rowsUpdated =
        userRepository.touchLastSeen(user.getId(), now, now.minus(1, ChronoUnit.HOURS));

    assertThat(rowsUpdated).isEqualTo(1);
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getLastSeenAt()).isEqualTo(now);
    assertThat(reloaded.getUpdatedAt().truncatedTo(ChronoUnit.MICROS))
        .isEqualTo(updatedAtBeforeAccess);
  }

  @Test
  @DisplayName("touchLastSeen dentro de uma hora do ultimo registro nao atualiza")
  void touchLastSeenWithinThrottleWindowIsIgnored() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("cognito-sub-322", "Ana Souza", "ana3@exemplo.com"));
    Instant firstAccess = Instant.parse("2026-10-09T12:00:00Z");
    userRepository.touchLastSeen(user.getId(), firstAccess, firstAccess.minus(1, ChronoUnit.HOURS));
    Instant secondAccess = firstAccess.plus(30, ChronoUnit.MINUTES);

    int rowsUpdated =
        userRepository.touchLastSeen(
            user.getId(), secondAccess, secondAccess.minus(1, ChronoUnit.HOURS));

    assertThat(rowsUpdated).isZero();
    assertThat(userRepository.findById(user.getId()).orElseThrow().getLastSeenAt())
        .isEqualTo(firstAccess);
  }

  @Test
  @DisplayName("touchLastSeen depois de mais de uma hora do ultimo registro atualiza de novo")
  void touchLastSeenAfterThrottleWindowUpdatesAgain() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("cognito-sub-323", "Ana Souza", "ana4@exemplo.com"));
    Instant firstAccess = Instant.parse("2026-10-09T12:00:00Z");
    userRepository.touchLastSeen(user.getId(), firstAccess, firstAccess.minus(1, ChronoUnit.HOURS));
    Instant secondAccess = firstAccess.plus(61, ChronoUnit.MINUTES);

    int rowsUpdated =
        userRepository.touchLastSeen(
            user.getId(), secondAccess, secondAccess.minus(1, ChronoUnit.HOURS));

    assertThat(rowsUpdated).isEqualTo(1);
    assertThat(userRepository.findById(user.getId()).orElseThrow().getLastSeenAt())
        .isEqualTo(secondAccess);
  }
}
