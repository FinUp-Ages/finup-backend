-- Migration: 04-study-tracks-schema.sql
-- Modelagem da Trilha de Estudos (Contrato C6)
-- Cria tabelas study_categories e study_questions

CREATE TABLE IF NOT EXISTS study_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS study_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    draft JSONB NOT NULL,
    published JSONB,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_study_questions_category FOREIGN KEY (category_id) REFERENCES study_categories (id),
    CONSTRAINT chk_study_questions_type CHECK (type IN ('FILL_BLANK', 'MULTIPLE_CHOICE', 'LEARNING'))
);

-- Índice na FK de categoria para otimização de consultas e contagens agregadas
CREATE INDEX IF NOT EXISTS idx_study_questions_category_id ON study_questions (category_id);
