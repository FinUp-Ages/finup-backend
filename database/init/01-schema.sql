-- ============================================
-- FinUp - Estrutura inicial do banco de dados
-- ============================================
CREATE TABLE IF NOT EXISTS users (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255),
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  birth_date DATE,
  monthly_income DECIMAL(12, 2),
  fin_up_score INTEGER,
  financial_profile VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_financial_profiles (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  monthly_expenses_estimate DECIMAL(12, 2),
  income_expense_relation VARCHAR(50),
  spending_habit VARCHAR(50),
  has_investments BOOLEAN,
  approximate_invested_amount DECIMAL(12, 2),
  investment_experience_time VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_financial_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS user_investment_interests (
  id SERIAL PRIMARY KEY,
  user_financial_profile_id INTEGER NOT NULL,
  investment_type VARCHAR(50) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_investment_interests_profile FOREIGN KEY (user_financial_profile_id) REFERENCES user_financial_profiles (id)
);

CREATE TABLE IF NOT EXISTS categories (
  id SERIAL PRIMARY KEY,
  user_id INTEGER,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(50),
  is_default BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS payment_methods (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  type VARCHAR(50),
  name VARCHAR(255),
  institution VARCHAR(255),
  closing_day INTEGER,
  due_day INTEGER,
  credit_limit DECIMAL(12, 2),
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_methods_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS goals (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  title VARCHAR(255),
  description VARCHAR(255),
  target_amount DECIMAL(12, 2) NOT NULL,
  current_amount DECIMAL(12, 2),
  start_date DATE,
  due_date DATE,
  status VARCHAR(50),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS investments (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  type VARCHAR(50),
  institution VARCHAR(255),
  invested_amount DECIMAL(12, 2) NOT NULL,
  current_amount DECIMAL(12, 2),
  return_rate DECIMAL(8, 4),
  application_date DATE,
  redemption_date DATE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_investments_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS transactions (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  category_id INTEGER NOT NULL,
  payment_method_id INTEGER,
  type VARCHAR(50),
  description VARCHAR(255),
  amount DECIMAL(12, 2) NOT NULL,
  transaction_date DATE NOT NULL,
  is_recurring BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_transactions_category FOREIGN KEY (category_id) REFERENCES categories (id),
  CONSTRAINT fk_transactions_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
);

CREATE TABLE IF NOT EXISTS debts (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  payment_method_id INTEGER,
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
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_debts_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_debts_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id)
);

CREATE TABLE IF NOT EXISTS tracks (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  order_index INTEGER,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS lessons (
  id SERIAL PRIMARY KEY,
  track_id INTEGER NOT NULL,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(255),
  order_index INTEGER,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_lessons_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE TABLE IF NOT EXISTS questions (
  id SERIAL PRIMARY KEY,
  lesson_id INTEGER NOT NULL,
  TEXT VARCHAR(255) NOT NULL,
  type VARCHAR(50),
  order_index INTEGER,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_questions_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);

CREATE TABLE IF NOT EXISTS answer_options (
  id SERIAL PRIMARY KEY,
  question_id INTEGER NOT NULL,
  TEXT VARCHAR(255) NOT NULL,
  is_correct BOOLEAN,
  order_index INTEGER,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_answer_options_question FOREIGN KEY (question_id) REFERENCES questions (id)
);

CREATE TABLE IF NOT EXISTS user_track_progress (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  track_id INTEGER NOT NULL,
  status VARCHAR(50),
  completed_lessons_count INTEGER,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_track_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_track_progress_track FOREIGN KEY (track_id) REFERENCES tracks (id)
);

CREATE TABLE IF NOT EXISTS user_lesson_progress (
  id SERIAL PRIMARY KEY,
  user_id INTEGER NOT NULL,
  lesson_id INTEGER NOT NULL,
  status VARCHAR(50),
  current_question_order INTEGER,
  correct_answers_count INTEGER,
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
  CONSTRAINT fk_user_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id)
);