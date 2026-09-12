package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Category;
import br.com.finup.model.CategoryType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private UserRepository userRepository;

  @InjectMocks private CategoryService categoryService;

  private User mockUser() {
    return User.register("Ana Souza", "ana@exemplo.com");
  }

  private Category mockCategory(User user, boolean isDefault) {
    Category category = new Category();
    category.setUser(user);
    category.setName("Alimentação");
    category.setType(CategoryType.EXPENSE);
    category.setIsDefault(isDefault);
    return category;
  }

  @Test
  @DisplayName("cadastra categoria vinculada ao usuario")
  void createsCategoryForUser() {
    User user = mockUser();
    CategoryRequest request = new CategoryRequest("Academia", CategoryType.EXPENSE);

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

    CategoryResponse response = categoryService.create(user.getId(), request);

    assertThat(response.name()).isEqualTo("Academia");
    assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
    assertThat(response.isDefault()).isFalse();
  }

  @Test
  @DisplayName("criar categoria com usuario inexistente lanca ResourceNotFoundException")
  void createThrowsWhenUserNotFound() {
    UUID userId = UUID.randomUUID();
    CategoryRequest request = new CategoryRequest("Academia", CategoryType.EXPENSE);

    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> categoryService.create(userId, request))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("edita categoria do usuario")
  void updatesCategoryForUser() {
    User user = mockUser();
    Category category = mockCategory(user, false);
    CategoryRequest request = new CategoryRequest("Academia e Esportes", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

    CategoryResponse response = categoryService.update(user.getId(), UUID.randomUUID(), request);

    assertThat(response.name()).isEqualTo("Academia e Esportes");
  }

  @Test
  @DisplayName("nao permite editar categoria padrao")
  void updateThrowsWhenCategoryIsDefault() {
    User user = mockUser();
    Category category = mockCategory(user, true);
    CategoryRequest request = new CategoryRequest("Outro nome", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));

    assertThatThrownBy(() -> categoryService.update(user.getId(), UUID.randomUUID(), request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("padrão");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("nao permite editar categoria de outro usuario")
  void updateThrowsWhenCategoryBelongsToAnotherUser() {
    User owner = mockUser();
    User other = User.register("Carlos", "carlos@exemplo.com");
    Category category = mockCategory(owner, false);
    CategoryRequest request = new CategoryRequest("Outro nome", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));

    assertThatThrownBy(() -> categoryService.update(other.getId(), UUID.randomUUID(), request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permissão");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("consulta retorna categorias do usuario e as padroes")
  void findAvailableReturnsCategoriesForUser() {
    User user = mockUser();
    Category userCategory = mockCategory(user, false);
    Category defaultCategory = mockCategory(null, true);

    when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    when(categoryRepository.findByUserOrIsDefaultTrue(user))
        .thenReturn(List.of(userCategory, defaultCategory));

    List<CategoryResponse> result = categoryService.findAvailable(user.getId());

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("nao permite deletar categoria padrao")
  void deleteThrowsWhenCategoryIsDefault() {
    User user = mockUser();
    Category category = mockCategory(user, true);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));

    assertThatThrownBy(() -> categoryService.delete(user.getId(), UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("padrão");

    verify(categoryRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("nao permite deletar categoria de outro usuario")
  void deleteThrowsWhenCategoryBelongsToAnotherUser() {
    User owner = mockUser();
    User other = User.register("Carlos", "carlos@exemplo.com");
    Category category = mockCategory(owner, false);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));

    assertThatThrownBy(() -> categoryService.delete(other.getId(), UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("permissão");

    verify(categoryRepository, never()).deleteById(any());
  }
}
