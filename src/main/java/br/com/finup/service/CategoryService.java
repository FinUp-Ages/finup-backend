package br.com.finup.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.finup.dto.CategoryRequest;
import br.com.finup.dto.CategoryResponse;
import br.com.finup.exception.ForbiddenOperationException;
import br.com.finup.exception.ResourceNotFoundException;
import br.com.finup.mapper.CategoryMapper;
import br.com.finup.model.Category;
import br.com.finup.model.User;
import br.com.finup.repository.CategoryRepository;
import br.com.finup.repository.UserRepository;

@Service
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final UserRepository userRepository;

  public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository) {
    this.categoryRepository = categoryRepository;
    this.userRepository = userRepository;
  }

  public CategoryResponse create(UUID userId, CategoryRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    return CategoryMapper.toResponse(
        categoryRepository.save(CategoryMapper.toEntity(request, user)));
  }

  public CategoryResponse update(UUID userId, UUID categoryId, CategoryRequest request) {
    Category category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

    if (category.getIsDefault()) {
      throw new ForbiddenOperationException("Nao e possivel editar uma categoria padrao");
    }

    if (!category.getUser().getId().equals(userId)) {
      throw new ForbiddenOperationException("Sem permissao para editar esta categoria");
    }

    category.setName(request.name());
    category.setType(request.type());

    return CategoryMapper.toResponse(categoryRepository.save(category));
  }

  public List<CategoryResponse> findAvailable(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User", userId));

    return categoryRepository.findByUserOrIsDefaultTrue(user).stream()
        .map(CategoryMapper::toResponse)
        .toList();
  }

  public void delete(UUID userId, UUID categoryId) {
    Category category =
        categoryRepository
            .findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));

    if (category.getIsDefault()) {
      throw new RuntimeException("Não é possível deletar uma categoria padrão");
    }

    if (!category.getUser().getId().equals(userId)) {
      throw new RuntimeException("Sem permissão para deletar esta categoria");
    }

    categoryRepository.deleteById(categoryId);
  }
}
