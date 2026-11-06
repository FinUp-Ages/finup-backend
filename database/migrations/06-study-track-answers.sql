-- Migration: 06-study-track-answers.sql
-- Respostas da Trilha de Estudos, uma versão atual por usuário e pergunta.

CREATE TABLE IF NOT EXISTS study_track_answers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  category_id UUID NOT NULL,
  question_id UUID NOT NULL,
  answer TEXT,
  correct BOOLEAN,
  stars INTEGER,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_study_track_answers_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_study_track_answers_category
    FOREIGN KEY (category_id) REFERENCES study_categories (id),
  CONSTRAINT fk_study_track_answers_question
    FOREIGN KEY (question_id) REFERENCES study_questions (id),
  CONSTRAINT uq_study_track_answers_user_question UNIQUE (user_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_study_track_answers_user_category
  ON study_track_answers (user_id, category_id);
