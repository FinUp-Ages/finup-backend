package br.com.finup.repository;

import br.com.finup.model.StudyCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudyCategoryRepository extends JpaRepository<StudyCategory, UUID> {

  List<StudyCategory> findAllByOrderByNameAsc();
}
