-- Seed: 05-study-tracks-seed.sql
-- Exemplos dos 3 tipos (MULTIPLE_CHOICE, FILL_BLANK, LEARNING)
-- e dos 3 status derivados (PUBLISHED, DRAFT, PUBLISHED_WITH_CHANGES)

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
    "statement": "Quantos meses de custo de vida é recomendado manter em uma reserva de emergência idealmente?",
    "options": [
      {"id": "A", "text": "1 a 2 meses"},
      {"id": "B", "text": "3 a 6 meses"},
      {"id": "C", "text": "12 a 24 meses"},
      {"id": "D", "text": "Não é necessário manter reserva"}
    ],
    "correctOptionId": "B",
    "explanation": "O consenso financeiro recomenda entre 3 a 6 meses de despesas essenciais para profissionais CLT e até 12 meses para autônomos."
  }'::jsonb,
  '{
    "statement": "Quantos meses de custo de vida é recomendado manter em uma reserva de emergência idealmente?",
    "options": [
      {"id": "A", "text": "1 a 2 meses"},
      {"id": "B", "text": "3 a 6 meses"},
      {"id": "C", "text": "12 a 24 meses"},
      {"id": "D", "text": "Não é necessário manter reserva"}
    ],
    "correctOptionId": "B",
    "explanation": "O consenso financeiro recomenda entre 3 a 6 meses de despesas essenciais para profissionais CLT e até 12 meses para autônomos."
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
    "textWithBlanks": "A reserva de emergência deve ser aplicada em investimentos de alta {liquidez} e baixo {risco}.",
    "blanks": [
      {"index": 0, "expectedAnswer": "liquidez", "acceptableAnswers": ["liquidez", "facilidade de resgate"]},
      {"index": 1, "expectedAnswer": "risco", "acceptableAnswers": ["risco", "risco de perda"]}
    ],
    "explanation": "Alta liquidez garante que você possa sacar o dinheiro no momento que precisar, e baixo risco protege o capital."
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
    "title": "O que é CDI e taxa Selic? (Versão Rascunho com Dicas Extras)",
    "content": "O CDI (Certificado de Depósito Interbancário) é a taxa que os bancos emprestam dinheiro entre si diariamente, acompanhando de perto a Selic meta.",
    "keyTakeaways": [
      "Selic é a taxa básica de juros da economia.",
      "CDI rende próximo de 0,10% abaixo da Selic.",
      "CDBs que rendem 100% do CDI são ótimos para reserva."
    ]
  }'::jsonb,
  '{
    "title": "O que é CDI e taxa Selic?",
    "content": "O CDI é a taxa média dos empréstimos entre bancos, muito utilizada como benchmark da renda fixa.",
    "keyTakeaways": [
      "Selic é a taxa básica de juros.",
      "CDI acompanha a Selic."
    ]
  }'::jsonb,
  CURRENT_TIMESTAMP - INTERVAL '1 day',
  CURRENT_TIMESTAMP - INTERVAL '2 days',
  CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
