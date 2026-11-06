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
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Valida o mapeamento JPA de usuario e as buscas por identidade do Cognito. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private TestEntityManager entityManager;

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

  /**
   * Cria o usuario com nome, e-mail e {@code last_seen_at} escolhidos (gravado direto via SQL, fora
   * do throttle de {@code touchLastSeen}) — usado pelos testes de {@code search}.
   */
  private User saveUserWithLastSeen(String cognitoId, String name, String lastSeenAt) {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity(cognitoId, name, cognitoId + "@exemplo.com"));
    if (lastSeenAt != null) {
      jdbcTemplate.update(
          "UPDATE users SET last_seen_at = ? WHERE id = ?",
          OffsetDateTime.ofInstant(Instant.parse(lastSeenAt), ZoneOffset.UTC),
          user.getId());
    }
    entityManager.clear();
    return user;
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
    // entityManager.clear() forca uma leitura de verdade do banco: o driver arredonda (nao so
    // trunca) os nanossegundos do Instant.now() para a precisao de microssegundos da coluna, e
    // sem isso o findById devolveria a entidade do cache de sessao, com o valor em memoria - a
    // comparacao ficaria flaky mesmo truncando os dois lados.
    entityManager.clear();
    Instant updatedAtBeforeAccess =
        userRepository.findById(user.getId()).orElseThrow().getUpdatedAt();
    Instant now = Instant.parse("2026-10-09T12:00:00Z");

    int rowsUpdated =
        userRepository.touchLastSeen(user.getId(), now, now.minus(1, ChronoUnit.HOURS));

    assertThat(rowsUpdated).isEqualTo(1);
    User reloaded = userRepository.findById(user.getId()).orElseThrow();
    assertThat(reloaded.getLastSeenAt()).isEqualTo(now);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(updatedAtBeforeAccess);
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

  @Test
  @DisplayName("search pagina corretamente, sem filtro")
  void searchPaginatesWithoutFilter() {
    saveUserWithLastSeen("page-a", "Usuario A", null);
    saveUserWithLastSeen("page-b", "Usuario B", null);
    saveUserWithLastSeen("page-c", "Usuario C", null);

    Page<User> firstPage =
        userRepository.search(
            null, null, Instant.parse("2026-10-09T12:00:00Z"), PageRequest.of(0, 2));
    Page<User> secondPage =
        userRepository.search(
            null, null, Instant.parse("2026-10-09T12:00:00Z"), PageRequest.of(1, 2));

    assertThat(firstPage.getContent()).hasSize(2);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(secondPage.getContent()).hasSize(1);
  }

  @Test
  @DisplayName("search com status ACTIVE devolve so quem acessou nos ultimos 7 dias")
  void searchFiltersActiveStatus() {
    Instant now = Instant.parse("2026-10-09T12:00:00Z");
    Instant activeSince = User.activeSince(now);
    saveUserWithLastSeen("ativo", "Usuario Ativo", now.minus(1, ChronoUnit.DAYS).toString());
    saveUserWithLastSeen(
        "inativo-antigo", "Usuario Inativo", now.minus(8, ChronoUnit.DAYS).toString());
    saveUserWithLastSeen("nunca-acessou", "Usuario Sem Acesso", null);

    Page<User> active = userRepository.search(null, true, activeSince, PageRequest.of(0, 20));
    Page<User> inactive = userRepository.search(null, false, activeSince, PageRequest.of(0, 20));

    assertThat(active.getContent()).extracting(User::getCognitoId).containsExactly("ativo");
    assertThat(inactive.getContent())
        .extracting(User::getCognitoId)
        .containsExactlyInAnyOrder("inativo-antigo", "nunca-acessou");
  }

  @Test
  @DisplayName("search busca por nome, sem diferenciar maiusculas de minusculas")
  void searchFiltersByNameCaseInsensitive() {
    saveUserWithLastSeen("carlos", "Carlos Jose", null);
    saveUserWithLastSeen("ana", "Ana Souza", null);

    Page<User> found =
        userRepository.search(
            "%carlos%", null, Instant.parse("2026-10-09T12:00:00Z"), PageRequest.of(0, 20));

    assertThat(found.getContent()).extracting(User::getCognitoId).containsExactly("carlos");
  }

  @Test
  @DisplayName("search busca por e-mail, sem diferenciar maiusculas de minusculas")
  void searchFiltersByEmailCaseInsensitive() {
    saveUserWithLastSeen("carlos", "Carlos Jose", null);
    saveUserWithLastSeen("ana", "Ana Souza", null);

    Page<User> found =
        userRepository.search(
            "%ana@exemplo%", null, Instant.parse("2026-10-09T12:00:00Z"), PageRequest.of(0, 20));

    assertThat(found.getContent()).extracting(User::getCognitoId).containsExactly("ana");
  }

  @Test
  @DisplayName("search combina busca por texto e filtro de status")
  void searchCombinesTextAndStatusFilters() {
    Instant now = Instant.parse("2026-10-09T12:00:00Z");
    Instant activeSince = User.activeSince(now);
    saveUserWithLastSeen("carlos-ativo", "Carlos Jose", now.minus(1, ChronoUnit.DAYS).toString());
    saveUserWithLastSeen("carlos-inativo", "Carlos Pereira", null);
    saveUserWithLastSeen("ana-ativa", "Ana Souza", now.minus(1, ChronoUnit.DAYS).toString());

    Page<User> found = userRepository.search("%carlos%", true, activeSince, PageRequest.of(0, 20));

    assertThat(found.getContent()).extracting(User::getCognitoId).containsExactly("carlos-ativo");
  }
}
