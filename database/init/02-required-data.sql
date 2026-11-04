-- ============================================
-- FinUp - Dados obrigatórios da aplicação
-- ============================================
INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Alimentação',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Alimentação'
      AND is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Transporte',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Transporte'
      AND is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Moradia',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Moradia'
      AND is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Saúde',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Saúde'
      AND is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Lazer',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Lazer'
      AND is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Salário',
  'INCOME',
  TRUE
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories
    WHERE
      name = 'Salário'
      AND is_default = TRUE
  );
-- ============================================
-- Perguntas de perfil financeiro (onboarding)
-- ============================================
-- Pesos batem com docs/finup-score.md (pilares 1 e 5). HAS_INVESTMENTS nao tem peso ainda: e um
-- gate para approximate_invested_amount no pilar 3, nao um valor direto na formula.
-- INVESTMENT_EXPERIENCE_TIME fica de fora por enquanto: a coluna existe (user_financial_profiles),
-- mas nao ha um conjunto de opcoes definido no codigo para popular com seguranca.
WITH new_question AS (
  INSERT INTO
    financial_profile_questions (code, text, order_index)
  SELECT
    'INCOME_EXPENSE_RELATION',
    'Como você descreveria a relação entre sua renda e seus gastos?',
    1
  WHERE
    NOT EXISTS (
      SELECT
        1
      FROM
        financial_profile_questions
      WHERE
        code = 'INCOME_EXPENSE_RELATION'
    )
  RETURNING
    id
)
INSERT INTO
  financial_profile_answer_options (question_id, code, text, weight, order_index)
SELECT
  id,
  'SPENDS_LESS',
  'Gasto menos do que ganho',
  0.25,
  1
FROM
  new_question
UNION ALL
SELECT
  id,
  'BALANCED',
  'Gasto o que ganho',
  0.0,
  2
FROM
  new_question
UNION ALL
SELECT
  id,
  'SPENDS_MORE',
  'Gasto mais do que ganho',
  -0.25,
  3
FROM
  new_question;

WITH new_question AS (
  INSERT INTO
    financial_profile_questions (code, text, order_index)
  SELECT
    'SPENDING_HABIT',
    'Como você avalia o seu hábito de gastar dinheiro?',
    2
  WHERE
    NOT EXISTS (
      SELECT
        1
      FROM
        financial_profile_questions
      WHERE
        code = 'SPENDING_HABIT'
    )
  RETURNING
    id
)
INSERT INTO
  financial_profile_answer_options (question_id, code, text, weight, order_index)
SELECT
  id,
  'CONTROLLED',
  'Controlado: planejo antes de gastar',
  100,
  1
FROM
  new_question
UNION ALL
SELECT
  id,
  'MODERATE',
  'Moderado: às vezes gasto por impulso',
  60,
  2
FROM
  new_question
UNION ALL
SELECT
  id,
  'IMPULSIVE',
  'Impulsivo: gasto sem pensar muito',
  20,
  3
FROM
  new_question;

WITH new_question AS (
  INSERT INTO
    financial_profile_questions (code, text, order_index)
  SELECT
    'HAS_INVESTMENTS',
    'Você já possui investimentos hoje?',
    3
  WHERE
    NOT EXISTS (
      SELECT
        1
      FROM
        financial_profile_questions
      WHERE
        code = 'HAS_INVESTMENTS'
    )
  RETURNING
    id
)
INSERT INTO
  financial_profile_answer_options (question_id, code, text, order_index)
SELECT
  id,
  'YES',
  'Sim',
  1
FROM
  new_question
UNION ALL
SELECT
  id,
  'NO',
  'Não',
  2
FROM
  new_question;
