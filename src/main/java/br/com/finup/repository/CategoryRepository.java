package br.com.finup.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.finup.model.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    // Categorias do usuário + padrões do sistema
    List<Category> findByUserIdOrIsDefaultTrue(UUID userId);
}
