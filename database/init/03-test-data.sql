-- ============================================
-- FinUp - Dados fictícios para desenvolvimento
-- ============================================
-- --------------------------------------------
-- Usuário de teste
-- --------------------------------------------
INSERT INTO
  users (
    cognito_id,
    name,
    email,
    birth_date,
    monthly_income,
    fin_up_score,
    financial_profile
  )
VALUES
  (
    'mock-sub-usuario-teste',
    'Usuário Teste',
    'teste@finup.local',
    '2000-01-01',
    5000.00,
    650,
    'MODERATE'
  )
ON CONFLICT DO NOTHING;

-- --------------------------------------------
-- Usuários de demonstração do FinUp Score
-- (docs/finup-score.md, Exemplos A/C/D — ver InMemoryFinUpScoreDataProvider).
-- Testar via Swagger com o header X-Mock-Cognito-Sub igual ao cognito_id abaixo,
-- chamando POST /api/v1/users/me/finup-score/recalculate.
-- --------------------------------------------
INSERT INTO
  users (cognito_id, name, email, monthly_income)
VALUES
  (
    'mock-sub-exemplo-a',
    'Exemplo A',
    'exemplo.a.fallback@finup.local',
    5000.00
  ),
  (
    'mock-sub-exemplo-c',
    'Exemplo C',
    'exemplo.c.saudavel@finup.local',
    8000.00
  ),
  (
    'mock-sub-exemplo-d',
    'Exemplo D',
    'exemplo.d.dificuldade@finup.local',
    3000.00
  )
-- Sem alvo de propósito: users tem UNIQUE em email E em cognito_id, e
-- ON CONFLICT (email) ignoraria só o primeiro, deixando o segundo abortar a carga.
ON CONFLICT DO NOTHING;

-- --------------------------------------------
-- Perfil financeiro do usuário de teste
-- --------------------------------------------
INSERT INTO
  user_financial_profiles (
    user_id,
    monthly_expenses_estimate,
    income_expense_relation,
    spending_habit,
    has_investments,
    approximate_invested_amount,
    investment_experience_time
  )
SELECT
  u.id,
  3000.00,
  'BALANCED',
  'MODERATE',
  TRUE,
  2000.00,
  'BEGINNER'
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      user_financial_profiles ufp
    WHERE
      ufp.user_id = u.id
  );

-- --------------------------------------------
-- Interesse de investimento do usuário
-- --------------------------------------------
INSERT INTO
  user_investment_interests (user_financial_profile_id, investment_type)
SELECT
  ufp.id,
  'CDB'
FROM
  user_financial_profiles ufp
  JOIN users u ON u.id = ufp.user_id
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      user_investment_interests uii
    WHERE
      uii.user_financial_profile_id = ufp.id
      AND uii.investment_type = 'CDB'
  );

-- --------------------------------------------
-- Método de pagamento
-- --------------------------------------------
INSERT INTO
  payment_methods (
    user_id,
    type,
    name,
    institution,
    closing_day,
    due_day,
    credit_limit,
    is_active
  )
SELECT
  u.id,
  'CREDIT_CARD',
  'Cartão Teste',
  'Banco Teste',
  10,
  17,
  5000.00,
  TRUE
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      payment_methods pm
    WHERE
      pm.user_id = u.id
      AND pm.name = 'Cartão Teste'
  );

-- --------------------------------------------
-- Meta financeira
-- --------------------------------------------
INSERT INTO
  goals (
    user_id,
    title,
    description,
    target_amount,
    current_amount,
    start_date,
    due_date,
    status
  )
SELECT
  u.id,
  'Viagem de férias',
  'Economizar dinheiro para uma viagem',
  5000.00,
  1500.00,
  CURRENT_DATE,
  CURRENT_DATE + INTERVAL '6 months',
  'IN_PROGRESS'
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      goals g
    WHERE
      g.user_id = u.id
      AND g.title = 'Viagem de férias'
  );

-- --------------------------------------------
-- Investimento
-- --------------------------------------------
INSERT INTO
  investments (
    user_id,
    type,
    institution,
    invested_amount,
    current_amount,
    return_rate,
    application_date
  )
SELECT
  u.id,
  'CDB',
  'Banco Teste',
  2000.00,
  2150.00,
  7.50,
  CURRENT_DATE - INTERVAL '6 months'
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      investments i
    WHERE
      i.user_id = u.id
      AND i.institution = 'Banco Teste'
      AND i.invested_amount = 2000.00
  );

-- --------------------------------------------
-- Dívida
-- --------------------------------------------
INSERT INTO
  debts (
    user_id,
    payment_method_id,
    description,
    type,
    total_amount,
    paid_amount,
    interest_rate,
    installment_count,
    current_installment,
    start_date,
    due_date,
    status
  )
SELECT
  u.id,
  pm.id,
  'Notebook',
  'INSTALLMENT',
  3000.00,
  1000.00,
  0.00,
  6,
  2,
  CURRENT_DATE - INTERVAL '2 months',
  CURRENT_DATE + INTERVAL '4 months',
  'ACTIVE'
FROM
  users u
  JOIN payment_methods pm ON pm.user_id = u.id
  AND pm.name = 'Cartão Teste'
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      debts d
    WHERE
      d.user_id = u.id
      AND d.description = 'Notebook'
  );

-- --------------------------------------------
-- Transação: supermercado
-- --------------------------------------------
INSERT INTO
  transactions (
    user_id,
    category_id,
    payment_method_id,
    type,
    description,
    amount,
    transaction_date,
    is_recurring
  )
SELECT
  u.id,
  c.id,
  pm.id,
  'EXPENSE',
  'Supermercado',
  320.00,
  CURRENT_DATE,
  FALSE
FROM
  users u
  JOIN categories c ON c.name = 'Alimentação'
  AND c.is_default = TRUE
  JOIN payment_methods pm ON pm.user_id = u.id
  AND pm.name = 'Cartão Teste'
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      transactions t
    WHERE
      t.user_id = u.id
      AND t.description = 'Supermercado'
      AND t.transaction_date = CURRENT_DATE
  );

-- --------------------------------------------
-- Transação: transporte
-- --------------------------------------------
INSERT INTO
  transactions (
    user_id,
    category_id,
    payment_method_id,
    type,
    description,
    amount,
    transaction_date,
    is_recurring
  )
SELECT
  u.id,
  c.id,
  pm.id,
  'EXPENSE',
  'Transporte por aplicativo',
  35.00,
  CURRENT_DATE,
  FALSE
FROM
  users u
  JOIN categories c ON c.name = 'Transporte'
  AND c.is_default = TRUE
  JOIN payment_methods pm ON pm.user_id = u.id
  AND pm.name = 'Cartão Teste'
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      transactions t
    WHERE
      t.user_id = u.id
      AND t.description = 'Transporte por aplicativo'
      AND t.transaction_date = CURRENT_DATE
  );

-- --------------------------------------------
-- Transação: salário
-- --------------------------------------------
INSERT INTO
  transactions (
    user_id,
    category_id,
    payment_method_id,
    type,
    description,
    amount,
    transaction_date,
    is_recurring,
    recurrence_frequency,
    last_occurrence_date
  )
SELECT
  u.id,
  c.id,
  NULL,
  'INCOME',
  'Salário mensal',
  5000.00,
  CURRENT_DATE,
  TRUE,
  'MONTHLY',
  CURRENT_DATE
FROM
  users u
  JOIN categories c ON c.name = 'Salário'
  AND c.is_default = TRUE
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      transactions t
    WHERE
      t.user_id = u.id
      AND t.description = 'Salário mensal'
      AND t.transaction_date = CURRENT_DATE
  );

-- --------------------------------------------
-- Conversas do assistente
-- --------------------------------------------
INSERT INTO
  conversations (user_id, title, created_at, updated_at)
SELECT
  u.id,
  'Como economizar na viagem de férias?',
  CURRENT_TIMESTAMP - INTERVAL '2 days',
  CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      conversations c
    WHERE
      c.user_id = u.id
      AND c.title = 'Como economizar na viagem de férias?'
  );

INSERT INTO
  conversations (user_id, title, created_at, updated_at)
SELECT
  u.id,
  'Vale a pena investir em CDB agora?',
  CURRENT_TIMESTAMP - INTERVAL '5 days',
  CURRENT_TIMESTAMP - INTERVAL '5 days'
FROM
  users u
WHERE
  u.email = 'teste@finup.local'
  AND NOT EXISTS (
    SELECT
      1
    FROM
      conversations c
    WHERE
      c.user_id = u.id
      AND c.title = 'Vale a pena investir em CDB agora?'
  );

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
