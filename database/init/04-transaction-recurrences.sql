-- ============================================
-- FinUp - Recorrencia de transacoes
-- ============================================
-- Script novo (nao altera 01-schema.sql) porque os scripts de database/init/ so rodam
-- na criacao do volume: quem ja tem o banco local precisa de
-- `docker compose down -v && docker compose up -d` para pegar esta tabela.
CREATE TABLE IF NOT EXISTS transaction_recurrences (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  category_id UUID NOT NULL,
  payment_method_id UUID,
  type VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  amount DECIMAL(12, 2) NOT NULL,
  frequency VARCHAR(50) NOT NULL,
  day_of_month INTEGER NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transaction_recurrences_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transaction_recurrences_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_transaction_recurrences_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
  CONSTRAINT chk_transaction_recurrences_day_of_month CHECK (day_of_month BETWEEN 1 AND 31)
);
