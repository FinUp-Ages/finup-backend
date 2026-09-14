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