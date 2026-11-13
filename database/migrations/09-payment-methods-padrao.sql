-- Migration: 09-payment-methods-padrao.sql
-- Formas de pagamento padrao do sistema, visiveis para todo usuario (como as categorias padrao).
-- Para bancos criados antes da coluna is_default; bancos novos ja recebem tudo pelo database/init.
--
-- Pode ser reaplicada: a coluna, o CHECK e o seed so sao criados se ainda nao existirem.

ALTER TABLE payment_methods
ADD COLUMN IF NOT EXISTS is_default BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE payment_methods
ALTER COLUMN user_id DROP NOT NULL;

ALTER TABLE payment_methods
DROP CONSTRAINT IF EXISTS chk_payment_methods_owner;

ALTER TABLE payment_methods
ADD CONSTRAINT chk_payment_methods_owner CHECK (
  (
    is_default = TRUE
    AND user_id IS NULL
  )
  OR (
    is_default = FALSE
    AND user_id IS NOT NULL
  )
);

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
