package br.com.finup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.model.Category;
import br.com.finup.model.CategoryType;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;
  @Mock private UserService userService;

  @InjectMocks private CategoryService categoryService;

  private User mockUser() {
    return User.createFromCognitoIdentity("cognito-sub-ana", "Ana Souza", "ana@exemplo.com");
  }

  private AuthenticatedIdentity identity(User user) {
    return new AuthenticatedIdentity(user.getCognitoId(), user.getName(), user.getEmail());
  }

  private Category mockCategory(User user) {
    return Category.createForUser(user, "Alimentação", CategoryType.EXPENSE);
  }

  private Category mockDefaultCategory() {
    Category category = org.mockito.Mockito.mock(Category.class);
    when(category.isDefault()).thenReturn(true);
    return category;
  }

  @Test
  @DisplayName("cadastra categoria vinculada ao usuario")
  void createsCategoryForUser() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    CategoryRequest request = new CategoryRequest("Academia", CategoryType.EXPENSE);

    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

    CategoryResponse response = categoryService.create(identity, request);

    assertThat(response.name()).isEqualTo("Academia");
    assertThat(response.type()).isEqualTo(CategoryType.EXPENSE);
    assertThat(response.isDefault()).isFalse();
  }

  @Test
  @DisplayName("criar categoria com usuario inexistente lanca ResourceNotFoundException")
  void createThrowsWhenUserNotFound() {
    AuthenticatedIdentity identity =
        new AuthenticatedIdentity("missing-sub", "Ana Souza", "ana@exemplo.com");
    CategoryRequest request = new CategoryRequest("Academia", CategoryType.EXPENSE);

    when(userService.findByAuthenticatedIdentity(identity))
        .thenThrow(new ResourceNotFoundException("Usuario nao encontrado"));

    assertThatThrownBy(() -> categoryService.create(identity, request))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("edita categoria do usuario")
  void updatesCategoryForUser() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    Category category = mockCategory(user);
    CategoryRequest request = new CategoryRequest("Academia e Esportes", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    CategoryResponse response = categoryService.update(identity, UUID.randomUUID(), request);

    assertThat(response.name()).isEqualTo("Academia e Esportes");
  }

  @Test
  @DisplayName("nao permite editar categoria padrao")
  void updateThrowsWhenCategoryIsDefault() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    Category category = mockDefaultCategory();
    CategoryRequest request = new CategoryRequest("Outro nome", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);

    assertThatThrownBy(() -> categoryService.update(identity, UUID.randomUUID(), request))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("padrão");

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("nao permite editar categoria de outro usuario")
  void updateThrowsWhenCategoryBelongsToAnotherUser() {
    User owner = mockUser();
    User other =
        User.createFromCognitoIdentity("cognito-sub-carlos", "Carlos", "carlos@exemplo.com");
    AuthenticatedIdentity identity = identity(other);
    Category category = mockCategory(owner);
    CategoryRequest request = new CategoryRequest("Outro nome", CategoryType.EXPENSE);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(other);

    assertThatThrownBy(() -> categoryService.update(identity, UUID.randomUUID(), request))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(categoryRepository, never()).save(any());
  }

  @Test
  @DisplayName("consulta retorna categorias do usuario e as padroes")
  void findAvailableReturnsCategoriesForUser() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    Category userCategory = mockCategory(user);
    Category defaultCategory = mockDefaultCategory();

    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(categoryRepository.findByUserOrIsDefaultTrue(user))
        .thenReturn(List.of(userCategory, defaultCategory));

    List<CategoryResponse> result = categoryService.findAvailable(identity);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("nao permite deletar categoria padrao")
  void deleteThrowsWhenCategoryIsDefault() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    Category category = mockDefaultCategory();

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);

    assertThatThrownBy(() -> categoryService.delete(identity, UUID.randomUUID()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("padrão");

    verify(categoryRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("nao permite deletar categoria de outro usuario")
  void deleteThrowsWhenCategoryBelongsToAnotherUser() {
    User owner = mockUser();
    User other =
        User.createFromCognitoIdentity("cognito-sub-carlos", "Carlos", "carlos@exemplo.com");
    AuthenticatedIdentity identity = identity(other);
    Category category = mockCategory(owner);

    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(other);

    assertThatThrownBy(() -> categoryService.delete(identity, UUID.randomUUID()))
        .isInstanceOf(ResourceNotFoundException.class);

    verify(categoryRepository, never()).deleteById(any());
  }

  @Test
  @DisplayName("deletar categoria em uso lanca conflito")
  void deleteThrowsConflictWhenCategoryIsInUse() {
    User user = mockUser();
    AuthenticatedIdentity identity = identity(user);
    Category category = mockCategory(user);

    when(userService.findByAuthenticatedIdentity(identity)).thenReturn(user);
    when(categoryRepository.findById(any())).thenReturn(Optional.of(category));
    org.mockito.Mockito.doThrow(new DataIntegrityViolationException("foreign key"))
        .when(categoryRepository)
        .flush();

    assertThatThrownBy(() -> categoryService.delete(identity, UUID.randomUUID()))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("em uso");
  }
}
