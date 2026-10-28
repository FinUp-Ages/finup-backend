package br.com.finup.repository;

import br.com.finup.model.StudyQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudyQuestionRepository extends JpaRepository<StudyQuestion, UUID> {

  List<StudyQuestion> findByCategoryId(UUID categoryId);

  List<StudyQuestion> findByCategoryIdOrderByCreatedAtAsc(UUID categoryId);

  List<StudyQuestion> findByCategoryIdAndPublishedIsNotNullOrderByCreatedAtAsc(UUID categoryId);

  @Query(
      """
      SELECT q.category.id AS categoryId, COUNT(q) AS publishedCount
      FROM StudyQuestion q
      WHERE q.published IS NOT NULL
      GROUP BY q.category.id
      """)
  List<PublishedCountProjection> countPublishedGroupedByCategory();

  @Query(
      value =
          """
          SELECT
            COUNT(CASE WHEN q.published IS NOT NULL THEN 1 END) AS publishedCount,
            COUNT(CASE WHEN q.published IS NULL OR q.draft <> q.published THEN 1 END) AS draftCount
          FROM study_questions q
          WHERE q.category_id = :categoryId
          """,
      nativeQuery = true)
  CategoryCountProjection countAggregatedByCategoryId(@Param("categoryId") UUID categoryId);

  public interface CategoryCountProjection {
    long getPublishedCount();

    long getDraftCount();
  }

  public interface PublishedCountProjection {
    UUID getCategoryId();

    long getPublishedCount();
  }
}
