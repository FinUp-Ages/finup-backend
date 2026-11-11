package br.com.finup.service;

import br.com.finup.dto.CategoryListResponse;
import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ConflictException;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.exception.UnsafeAiInputException;
import br.com.finup.mapper.CategoryMapper;
import br.com.finup.model.Category;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  static final String BLOCKED_NAME_MESSAGE =
      "Esse nome de categoria nao e permitido. Use um nome curto, como \"Mercado\".";

  private final CategoryRepository categoryRepository;
  private final UserService userService;
  private final PromptAttackGuard promptAttackGuard;

  public CategoryService(
      CategoryRepository categoryRepository,
      UserService userService,
      PromptAttackGuard promptAttackGuard) {
    this.categoryRepository = categoryRepository;
    this.userService = userService;
    this.promptAttackGuard = promptAttackGuard;
  }

  @Transactional
  public CategoryResponse create(AuthenticatedIdentity identity, CategoryRequest request) {
    User user = userService.findByAuthenticatedIdentity(identity);
    requireSafeName(request.name());
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
    if (!request.name().equals(category.getName())) {
      requireSafeName(request.name());
    }

    category.rename(request.name(), request.type());
    return CategoryMapper.toResponse(categoryRepository.save(category));
  }

  @Transactional(readOnly = true)
  public List<CategoryListResponse> findAvailable(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);
    Collator collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"));
    Comparator<CategoryListResponse> byName =
        Comparator.comparing(CategoryListResponse::name, collator);

    return categoryRepository.findByUserOrIsDefaultTrue(user).stream()
        .map(CategoryMapper::toListResponse)
        .sorted(byName)
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
        .orElseThrow(
            () -> new ResourceNotFoundException("Categoria nao encontrada: " + categoryId));
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
      throw new ResourceNotFoundException("Categoria nao encontrada: " + category.getId());
    }
  }

  /**
   * O nome da categoria entra nos prompts do assistente e do consultor: instrucao plantada nele
   * seria lida pelo modelo em toda conversa. Por isso passa pelo guardrail antes de ser gravado.
   */
  private void requireSafeName(String name) {
    if (promptAttackGuard.isAttack(name)) {
      throw new UnsafeAiInputException(BLOCKED_NAME_MESSAGE);
    }
  }
}
