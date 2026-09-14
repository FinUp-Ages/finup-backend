-- ============================================
-- FinUp - Dados fictícios para desenvolvimento
-- ============================================
-- --------------------------------------------
-- Usuário de teste
-- --------------------------------------------
INSERT INTO
  users (
    name,
    email,
    password_hash,
    birth_date,
    monthly_income,
    fin_up_score,
    financial_profile
  )
VALUES
  (
    'Usuário Teste',
    'teste@finup.local',
    'test-only',
    '2000-01-01',
    5000.00,
    650,
    'MODERATE'
  )
ON CONFLICT (email) DO NOTHING;

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
    is_recurring
  )
SELECT
  u.id,
  c.id,
  NULL,
  'INCOME',
  'Salário mensal',
  5000.00,
  CURRENT_DATE,
  TRUE
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