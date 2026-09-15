package br.com.finup.service;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.CategoryMapper;
import br.com.finup.model.Category;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final UserService userService;

  public CategoryService(CategoryRepository categoryRepository, UserService userService) {
    this.categoryRepository = categoryRepository;
    this.userService = userService;
  }

  @Transactional
  public CategoryResponse create(AuthenticatedIdentity identity, CategoryRequest request) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Category category = Category.createForUser(user, request.name(), request.type());
    return CategoryMapper.toResponse(categoryRepository.save(category));
  }

  @Transactional
  public CategoryResponse update(
      AuthenticatedIdentity identity, UUID categoryId, CategoryRequest request) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Category category = findByIdOrThrow(categoryId);

    if (category.isDefault()) {
      throw new ForbiddenOperationException("Não é possível editar uma categoria padrão");
    }
    ensureOwnedByUser(user, category);

    category.rename(request.name(), request.type());
    return CategoryMapper.toResponse(categoryRepository.save(category));
  }

  public List<CategoryResponse> findAvailable(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    return categoryRepository.findByUserOrIsDefaultTrue(user).stream()
        .map(CategoryMapper::toResponse)
        .toList();
  }

  @Transactional
  public void delete(AuthenticatedIdentity identity, UUID categoryId) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Category category = findByIdOrThrow(categoryId);

    if (category.isDefault()) {
      throw new ForbiddenOperationException("Não é possível excluir uma categoria padrão");
    }
    ensureOwnedByUser(user, category);

    try {
      categoryRepository.deleteById(categoryId);
      categoryRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "A categoria não pode ser excluída pois está em uso por uma ou mais transações");
    }
  }

  /**
   * Busca a categoria pelo id e verifica que pertence ao usuário. Tratar categoria de outro usuário
   * como inexistente (404) evita confirmar a existência de um id para quem não tem acesso a ele.
   */
  private Category findByIdOrThrow(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
  }

  /**
   * Compara pelo id, e não por {@code equals}, de propósito: {@code category.getUser()} é uma
   * associação LAZY, então pode chegar aqui como proxy do Hibernate. O {@code User.equals} lê o
   * campo {@code id} direto, que num proxy ainda não inicializado vem nulo — a comparação passaria
   * a depender de a instância já estar no persistence context. Com {@code getId()} o proxy
   * inicializa e a resposta é a mesma em qualquer cenário.
   */
  private void ensureOwnedByUser(User user, Category category) {
    User owner = category.getUser();
    if (owner == null || !owner.getId().equals(user.getId())) {
      throw new ResourceNotFoundException("Category", category.getId());
    }
  }
}
