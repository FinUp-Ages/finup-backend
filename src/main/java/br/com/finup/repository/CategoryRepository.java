package br.com.finup.repository;

import br.com.finup.model.Category;
import br.com.finup.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
  // Categorias do usuário + padrões do sistema
  List<Category> findByUserOrIsDefaultTrue(User user);
}
