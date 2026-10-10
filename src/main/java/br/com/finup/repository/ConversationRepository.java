package br.com.finup.repository;

import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

  Optional<Conversation> findByIdAndUser(UUID id, User user);

  /**
   * Mesma busca, mas com lock de escrita na linha: serializa duas gravacoes simultaneas na mesma
   * conversa, ja que ler-alterar-gravar o transcript no S3 nao e atomico.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Conversation c where c.id = :id and c.user = :user")
  Optional<Conversation> findByIdAndUserForUpdate(@Param("id") UUID id, @Param("user") User user);
}
