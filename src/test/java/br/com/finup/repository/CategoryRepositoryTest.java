package br.com.finup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.finup.model.Category;
import br.com.finup.model.CategoryType;
import br.com.finup.model.User;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class CategoryRepositoryTest {

  @Autowired private CategoryRepository categoryRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("busca categorias do usuario e categorias padrao, sem incluir outro usuario")
  void findsUserCategoriesAndDefaultsOnly() {
    User user =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("category-sub", "Ana Souza", "ana@example.com"));
    User otherUser =
        userRepository.saveAndFlush(
            User.createFromCognitoIdentity("other-category-sub", "Carlos", "carlos@example.com"));

    categoryRepository.saveAndFlush(Category.createForUser(user, "Academia", CategoryType.EXPENSE));
    categoryRepository.saveAndFlush(
        Category.createForUser(otherUser, "Outro usuario", CategoryType.EXPENSE));

    jdbcTemplate.update(
        "INSERT INTO categories (id, user_id, name, type, is_default, created_at, updated_at) "
            + "VALUES (random_uuid(), NULL, ?, ?, ?, now(), now())",
        "Padrao",
        "EXPENSE",
        true);

    List<Category> result = categoryRepository.findByUserOrIsDefaultTrue(user);

    assertThat(result)
        .extracting(Category::getName)
        .containsExactlyInAnyOrder("Academia", "Padrao");
    assertThat(result).extracting(Category::getName).doesNotContain("Outro usuario");
  }
}
