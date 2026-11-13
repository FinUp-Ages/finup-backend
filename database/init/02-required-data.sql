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

-- --------------------------------------------
-- Categorias padrao de receita
-- --------------------------------------------
-- Nomes sem ambiguidade com gasto ("Presentes recebidos", "Rendimentos"): o assistente de IA
-- recusa uma categoria cujo nome so existe com o outro tipo.
INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  padrao.name,
  'INCOME',
  TRUE
FROM
  (
    VALUES
      ('Freelance'),
      ('Rendimentos'),
      ('Vendas'),
      ('Reembolso'),
      ('Presentes recebidos'),
      ('Outras receitas')
  ) AS padrao (name)
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      categories c
    WHERE
      c.name = padrao.name
      AND c.is_default = TRUE
  );

-- --------------------------------------------
-- Formas de pagamento padrao do sistema
-- --------------------------------------------
-- Valem para todo usuario (user_id NULL), como as categorias padrao. O created_at escalonado
-- fixa a ordem em que a API lista as formas padrao.
INSERT INTO
  payment_methods (
    user_id,
    type,
    name,
    is_default,
    is_active,
    created_at,
    updated_at
  )
SELECT
  NULL,
  padrao.type,
  padrao.name,
  TRUE,
  TRUE,
  CURRENT_TIMESTAMP + padrao.ordem * INTERVAL '1 millisecond',
  CURRENT_TIMESTAMP + padrao.ordem * INTERVAL '1 millisecond'
FROM
  (
    VALUES
      (1, 'PIX', 'Pix'),
      (2, 'CREDIT_CARD', 'Cartão de crédito'),
      (3, 'DEBIT_CARD', 'Cartão de débito'),
      (4, 'CASH', 'Dinheiro'),
      (5, 'BOLETO', 'Boleto'),
      (6, 'BANK_TRANSFER', 'Transferência bancária'),
      (7, 'DIGITAL_WALLET', 'Apple Pay'),
      (8, 'DIGITAL_WALLET', 'Google Pay')
  ) AS padrao (ordem, type, name)
WHERE
  NOT EXISTS (
    SELECT
      1
    FROM
      payment_methods pm
    WHERE
      pm.name = padrao.name
      AND pm.is_default = TRUE
  );

INSERT INTO
  categories (user_id, name, type, is_default)
SELECT
  NULL,
  'Educação',
  'EXPENSE',
  TRUE
WHERE
  NOT EXISTS (
    SELECT 1 FROM categories
    WHERE name = 'Educação' AND type = 'EXPENSE' AND is_default = TRUE
  );
