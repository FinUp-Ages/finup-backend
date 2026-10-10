package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.User;
import br.com.finup.model.UserFinancialProfile;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/** Valida o mapeamento JPA de {@code user_financial_profiles} e a busca por usuario. */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class UserFinancialProfileRepositoryTest {

  @Autowired private UserRepository userRepository;
  @Autowired private UserFinancialProfileRepository profileRepository;
  @Autowired private TestEntityManager entityManager;

  @Test
  @DisplayName("persiste todos os campos e recupera pelo id do usuario")
  void persistsAndFindsByUserId() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("sub-1", "Ana Souza", "ana@exemplo.com"));
    UserFinancialProfile profile = UserFinancialProfile.createFor(user);
    profile.apply("+5511999998888", "Analista", LocalDate.of(2000, 1, 31), new BigDecimal("3500"));
    profileRepository.saveAndFlush(profile);
    entityManager.clear();

    UserFinancialProfile found = profileRepository.findByUserId(user.getId()).orElseThrow();

    assertThat(found.getPhone()).isEqualTo("+5511999998888");
    assertThat(found.getProfession()).isEqualTo("Analista");
    assertThat(found.getBirthDate()).isEqualTo(LocalDate.of(2000, 1, 31));
    assertThat(found.getMonthlyIncome()).isEqualByComparingTo("3500.00");
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("usuario sem perfil devolve vazio")
  void returnsEmptyWithoutProfile() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("sub-2", "Bia", "bia@exemplo.com"));

    assertThat(profileRepository.findByUserId(user.getId())).isEmpty();
  }
}
