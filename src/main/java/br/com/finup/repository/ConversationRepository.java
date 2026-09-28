package br.com.finup.repository;

import br.com.finup.model.Conversation;
import br.com.finup.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

  List<Conversation> findByUserOrderByUpdatedAtDesc(User user);
}
