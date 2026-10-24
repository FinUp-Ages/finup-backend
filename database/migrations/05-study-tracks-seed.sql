-- Seed: 05-study-tracks-seed.sql
-- Exemplos dos 3 tipos (MULTIPLE_CHOICE, FILL_BLANK, LEARNING)
-- e dos 3 status derivados (PUBLISHED, DRAFT, PUBLISHED_WITH_CHANGES)
-- O conteudo segue o formato gravado pela API de escrita (secao 4 de contracts/study-track-c6.md)

INSERT INTO study_categories (id, name, description, created_at, updated_at)
VALUES
  ('a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d', 'Reserva de Emergência', 'Conceitos fundamentais para construir e gerir sua reserva financeira.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e', 'Investimentos Básicos', 'Primeiros passos em Renda Fixa e planejamento financeiro.', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Pergunta 1: MULTIPLE_CHOICE (Status: PUBLISHED - draft e published idênticos)
INSERT INTO study_questions (id, category_id, type, draft, published, published_at, created_at, updated_at)
VALUES (
  '11111111-2222-3333-4444-555555555555',
  'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
  'MULTIPLE_CHOICE',
  '{
    "type": "MULTIPLE_CHOICE",
    "statement": "Quantos meses de custo de vida é recomendado manter em uma reserva de emergência?",
    "options": [
      {"text": "1 a 2 meses", "correct": false},
      {"text": "3 a 6 meses", "correct": true},
      {"text": "12 a 24 meses", "correct": false},
      {"text": "Não é necessário manter reserva", "correct": false}
    ],
    "explanation": "O consenso financeiro recomenda entre 3 e 6 meses de despesas essenciais para profissionais CLT e até 12 meses para autônomos."
  }'::jsonb,
  '{
    "type": "MULTIPLE_CHOICE",
    "statement": "Quantos meses de custo de vida é recomendado manter em uma reserva de emergência?",
    "options": [
      {"text": "1 a 2 meses", "correct": false},
      {"text": "3 a 6 meses", "correct": true},
      {"text": "12 a 24 meses", "correct": false},
      {"text": "Não é necessário manter reserva", "correct": false}
    ],
    "explanation": "O consenso financeiro recomenda entre 3 e 6 meses de despesas essenciais para profissionais CLT e até 12 meses para autônomos."
  }'::jsonb,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Pergunta 2: FILL_BLANK (Status: DRAFT - published é null)
INSERT INTO study_questions (id, category_id, type, draft, published, published_at, created_at, updated_at)
VALUES (
  '22222222-3333-4444-5555-666666666666',
  'a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d',
  'FILL_BLANK',
  '{
    "type": "FILL_BLANK",
    "title": "Onde aplicar a reserva",
    "statement": "A reserva de emergência deve ser aplicada em investimentos de alta ___.",
    "answers": ["liquidez", "liquidez diária"],
    "explanation": "Alta liquidez garante que você possa resgatar o dinheiro no momento em que precisar."
  }'::jsonb,
  NULL,
  NULL,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Pergunta 3: LEARNING (Status: PUBLISHED_WITH_CHANGES - published existe, mas draft foi alterado)
INSERT INTO study_questions (id, category_id, type, draft, published, published_at, created_at, updated_at)
VALUES (
  '33333333-4444-5555-6666-777777777777',
  'b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e',
  'LEARNING',
  '{
    "type": "LEARNING",
    "title": "O que é CDI e taxa Selic?",
    "body": "## CDI e Selic\n\nA **Selic** é a taxa básica de juros da economia. O **CDI** é a taxa dos empréstimos entre bancos e acompanha de perto a Selic.\n\n- CDBs que rendem 100% do CDI são boas opções para a reserva de emergência."
  }'::jsonb,
  '{
    "type": "LEARNING",
    "title": "O que é CDI e taxa Selic?",
    "body": "## CDI e Selic\n\nO CDI é a taxa média dos empréstimos entre bancos, muito usada como referência da renda fixa."
  }'::jsonb,
  CURRENT_TIMESTAMP - INTERVAL '1 day',
  CURRENT_TIMESTAMP - INTERVAL '2 days',
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
