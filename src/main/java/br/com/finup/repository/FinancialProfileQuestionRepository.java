package br.com.finup.repository;

import br.com.finup.model.FinancialProfileQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FinancialProfileQuestionRepository
    extends JpaRepository<FinancialProfileQuestion, UUID> {

  /**
   * Todas as perguntas, com as opcoes de resposta ja carregadas numa unica consulta ({@code join
   * fetch}, evitando N+1). {@code distinct} descarta as linhas repetidas que o join produz (uma por
   * opcao); a ordem das opcoes dentro de cada pergunta vem do {@code @OrderBy} da entidade.
   */
  @Query(
      "select distinct q from FinancialProfileQuestion q"
          + " left join fetch q.options"
          + " order by q.orderIndex asc")
  List<FinancialProfileQuestion> findAllWithOptionsOrderByOrderIndex();
}
