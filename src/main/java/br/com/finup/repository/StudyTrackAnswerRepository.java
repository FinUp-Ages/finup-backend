package br.com.finup.repository;

import br.com.finup.model.StudyTrackAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyTrackAnswerRepository extends JpaRepository<StudyTrackAnswer, UUID> {
  List<StudyTrackAnswer> findByUserIdOrderBySubmittedAtAsc(UUID userId);

  List<StudyTrackAnswer> findByUserIdAndCategoryIdOrderByQuestionId(UUID userId, UUID categoryId);

  Optional<StudyTrackAnswer> findByUserIdAndQuestionId(UUID userId, UUID questionId);
}
