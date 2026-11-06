package br.com.finup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "study_track_answers")
public class StudyTrackAnswer {

  @Id
  @UuidGenerator
  @Column(updatable = false, nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "category_id", nullable = false, updatable = false)
  private UUID categoryId;

  @Column(name = "question_id", nullable = false, updatable = false)
  private UUID questionId;

  @Column(columnDefinition = "TEXT")
  private String answer;

  private Boolean correct;
  private Integer stars;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

  protected StudyTrackAnswer() {}

  public StudyTrackAnswer(
      UUID userId,
      UUID categoryId,
      UUID questionId,
      String answer,
      Boolean correct,
      Integer stars,
      Instant submittedAt) {
    this.userId = userId;
    this.categoryId = categoryId;
    this.questionId = questionId;
    this.answer = answer;
    this.correct = correct;
    this.stars = stars;
    this.submittedAt = submittedAt;
  }

  public void replace(String answer, Boolean correct, Integer stars, Instant submittedAt) {
    this.answer = answer;
    this.correct = correct;
    this.stars = stars;
    this.submittedAt = submittedAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getQuestionId() {
    return questionId;
  }

  public String getAnswer() {
    return answer;
  }

  public Boolean getCorrect() {
    return correct;
  }

  public Integer getStars() {
    return stars;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }
}
