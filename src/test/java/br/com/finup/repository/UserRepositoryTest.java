package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.User;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/** Valida o mapeamento JPA de usuario e as buscas por identidade do Cognito. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserRepositoryTest {

  @Autowired private UserRepository userRepository;

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
}
