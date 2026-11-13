-- ============================================
-- FinUp - Estrutura inicial do banco de dados
-- ============================================
CREATE TABLE IF NOT EXISTS users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cognito_id VARCHAR(255) NOT NULL UNIQUE,
  name VARCHAR(255),
  email VARCHAR(255) NOT NULL UNIQUE,
  birth_date DATE,
  monthly_income DECIMAL(12, 2),
  fin_up_score INTEGER,
  financial_profile VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Instante do ultimo GET /api/v1/users/me, gravado no maximo uma vez por hora (ver
-- UserRepository.touchLastSeen). Usado para a regra de status ACTIVE/INACTIVE (ultimos 7 dias) no
-- GET /admin/users e na tabela de analise de usuarios do painel.
-- Este script so roda quando o volume do Postgres e criado: quem ja tem o volume precisa
-- recria-lo (docker compose down -v) ou aplicar este ALTER a mao.
ALTER TABLE users
ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS user_financial_profiles (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  monthly_expenses_estimate DECIMAL(12, 2),
  income_expense_relation VARCHAR(50),
  spending_habit VARCHAR(50),
  has_investments BOOLEAN,
  approximate_invested_amount DECIMAL(12, 2),
  investment_experience_time VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_financial_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Informações complementares do cadastro (Etapas 2 e 3), gravadas por
-- PATCH /api/v1/users/me/additional-info; um perfil por usuário.
-- Estes scripts só rodam quando o volume do Postgres é criado: quem já tem o volume precisa
-- recriá-lo (docker compose down -v) ou aplicar este ALTER e o índice abaixo à mão.
ALTER TABLE user_financial_profiles
ADD COLUMN IF NOT EXISTS phone VARCHAR(20),
ADD COLUMN IF NOT EXISTS profession VARCHAR(255),
ADD COLUMN IF NOT EXISTS birth_date DATE,
ADD COLUMN IF NOT EXISTS monthly_income DECIMAL(12, 2);

CREATE UNIQUE INDEX IF NOT EXISTS uq_user_financial_profiles_user ON user_financial_profiles (user_id);

CREATE TABLE IF NOT EXISTS user_investment_interests (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_financial_profile_id UUID NOT NULL,
  investment_type VARCHAR(50) NOT NULL,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_investment_interests_profile FOREIGN KEY (user_financial_profile_id) REFERENCES user_financial_profiles (id)
);

CREATE TABLE IF NOT EXISTS categories (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50),
  is_default BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Forma de pagamento padrao do sistema (is_default TRUE) nao tem dono e vale para todo usuario,
-- como as categorias padrao. Forma criada para um usuario sempre tem dono.
CREATE TABLE IF NOT EXISTS payment_methods (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID,
  type VARCHAR(50),
  name VARCHAR(255),
  institution VARCHAR(255),
  closing_day INTEGER,
  due_day INTEGER,
  credit_limit DECIMAL(12, 2),
  is_active BOOLEAN DEFAULT TRUE,
  is_default BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT chk_payment_methods_owner CHECK (
    (
      is_default = TRUE
      AND user_id IS NULL
    )
    OR (
      is_default = FALSE
      AND user_id IS NOT NULL
    )
  )
);

CREATE TABLE IF NOT EXISTS goals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  title VARCHAR(255),
  description VARCHAR(255),
  target_amount DECIMAL(12, 2) NOT NULL,
  current_amount DECIMAL(12, 2),
  start_date DATE,
  due_date DATE,
  status VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS investments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  type VARCHAR(50),
  institution VARCHAR(255),
  invested_amount DECIMAL(12, 2) NOT NULL,
  current_amount DECIMAL(12, 2),
  return_rate DECIMAL(8, 4),
  application_date DATE,
  redemption_date DATE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_investments_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  category_id UUID NOT NULL,
  payment_method_id UUID,
  type VARCHAR(50),
  description VARCHAR(255),
  amount DECIMAL(12, 2) NOT NULL,
  transaction_date DATE NOT NULL,
  is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
  recurrence_frequency VARCHAR(50),
  last_occurrence_date DATE,
  recurrence_origin_id UUID,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_transactions_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
  CONSTRAINT fk_transactions_recurrence_origin FOREIGN KEY (recurrence_origin_id) REFERENCES transactions (id),
  CONSTRAINT uq_transactions_recurrence_occurrence UNIQUE (recurrence_origin_id, transaction_date),
  CONSTRAINT chk_transactions_recurrence CHECK (
    (
      is_recurring = TRUE
      AND recurrence_frequency IS NOT NULL
      AND last_occurrence_date IS NOT NULL
    )
    OR (
      is_recurring = FALSE
      AND recurrence_frequency IS NULL
      AND last_occurrence_date IS NULL
    )
  ),
  CONSTRAINT chk_transactions_recurrence_origin CHECK (
    recurrence_origin_id IS NULL
    OR (is_recurring = FALSE AND recurrence_origin_id <> id)
  )
);

CREATE TABLE IF NOT EXISTS conversations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  title VARCHAR(255),
  -- O corpo das mensagens fica no S3 (um objeto JSON por conversa); aqui so o metadado.
  message_count INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_conversations_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- Estado do popup de alerta de gastos: um registro por usuário e categoria dispensada.
-- ON DELETE CASCADE na categoria para que apagar uma categoria não seja bloqueado por dispensas.
CREATE TABLE IF NOT EXISTS spending_alert_dismissals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  category_id UUID NOT NULL,
  dismissed_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_spending_alert_dismissals_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_spending_alert_dismissals_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE CASCADE,
  CONSTRAINT uq_spending_alert_dismissals_user_category UNIQUE (user_id, category_id)
);

-- Triagem financeira: cada envio do questionário é uma linha (o mais recente vale) e gera uma
-- avaliação de riscos. Respostas e riscos em JSONB; nunca são logados. Sem volume novo, aplique
-- estes dois CREATE TABLE e o índice à mão. ON DELETE CASCADE: remover o usuário remove a triagem.
CREATE TABLE IF NOT EXISTS triage_responses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  questionnaire_version INTEGER NOT NULL,
  answers JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_triage_responses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_triage_responses_user_created ON triage_responses (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS risk_assessments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  triage_response_id UUID NOT NULL UNIQUE,
  profile VARCHAR(50) NOT NULL,
  summary TEXT NOT NULL,
  risks JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_risk_assessments_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_risk_assessments_response FOREIGN KEY (triage_response_id) REFERENCES triage_responses (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_risk_assessments_user_created ON risk_assessments (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS debts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  payment_method_id UUID,
  description VARCHAR(255),
  type VARCHAR(50),
  total_amount DECIMAL(12, 2) NOT NULL,
  paid_amount DECIMAL(12, 2),
  interest_rate DECIMAL(8, 4),
  installment_count INTEGER,
  current_installment INTEGER,
  start_date DATE,
  due_date DATE,
  status VARCHAR(50),
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_debts_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_debts_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
);

CREATE TABLE IF NOT EXISTS tracks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  order_index INTEGER,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lessons (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  track_id UUID NOT NULL,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  order_index INTEGER,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_lessons_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE TABLE IF NOT EXISTS questions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  lesson_id UUID NOT NULL,
  TEXT VARCHAR(255) NOT NULL,
  type VARCHAR(50),
  order_index INTEGER,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_questions_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

CREATE TABLE IF NOT EXISTS answer_options (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  question_id UUID NOT NULL,
  TEXT VARCHAR(255) NOT NULL,
  is_correct BOOLEAN,
  order_index INTEGER,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_answer_options_question FOREIGN KEY (question_id) REFERENCES questions (id)
);

CREATE TABLE IF NOT EXISTS user_track_progress (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  track_id UUID NOT NULL,
  status VARCHAR(50),
  completed_lessons_count INTEGER,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_track_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_track_progress_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE TABLE IF NOT EXISTS user_lesson_progress (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  lesson_id UUID NOT NULL,
  status VARCHAR(50),
  current_question_order INTEGER,
  correct_answers_count INTEGER,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

-- Configuração dos widgets do dashboard por admin, gravada por
-- PUT /api/v1/admin/dashboard/widgets (substitui a lista inteira do usuário).
-- Quem já tem o volume do Postgres precisa recriá-lo (docker compose down -v) ou aplicar à mão.
CREATE TABLE IF NOT EXISTS dashboard_widget_settings (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  widget_id VARCHAR(100) NOT NULL,
  enabled BOOLEAN NOT NULL,
  position INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_dashboard_widget_settings_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT uq_dashboard_widget_settings_user_widget UNIQUE (user_id, widget_id)
);

-- Trilha de Estudos (Contrato C6)
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

CREATE INDEX IF NOT EXISTS idx_study_questions_category_id ON study_questions (category_id);

-- Respostas da Trilha de Estudos, uma versão atual por usuário e pergunta (C6).
-- Mesmo conteúdo de database/migrations/06-study-track-answers.sql: quem já tem o volume
-- do Postgres precisa aplicar essa migration à mão (ou recriar o volume com docker compose down -v).
CREATE TABLE IF NOT EXISTS study_track_answers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  category_id UUID NOT NULL,
  question_id UUID NOT NULL,
  answer TEXT,
  correct BOOLEAN,
  stars INTEGER,
  submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_study_track_answers_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_study_track_answers_category
    FOREIGN KEY (category_id) REFERENCES study_categories (id),
  CONSTRAINT fk_study_track_answers_question
    FOREIGN KEY (question_id) REFERENCES study_questions (id),
  CONSTRAINT uq_study_track_answers_user_question UNIQUE (user_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_study_track_answers_user_category
  ON study_track_answers (user_id, category_id);
