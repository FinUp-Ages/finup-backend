-- Rollback: 04-study-tracks-schema-rollback.sql
-- Desfaz a modelagem da Trilha de Estudos (Contrato C6)

DROP INDEX IF EXISTS idx_study_questions_category_id;
DROP TABLE IF EXISTS study_questions;
DROP TABLE IF EXISTS study_categories;
