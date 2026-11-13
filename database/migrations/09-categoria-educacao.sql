-- Disponibiliza Educacao em bancos ja inicializados, sem alterar categorias pessoais.
-- Idempotente: pode ser reaplicada sem duplicar a categoria padrao.
INSERT INTO categories (user_id, name, type, is_default)
SELECT NULL, 'Educação', 'EXPENSE', TRUE
WHERE NOT EXISTS (
  SELECT 1 FROM categories
  WHERE name = 'Educação' AND type = 'EXPENSE' AND is_default = TRUE
);
