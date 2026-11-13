-- Migration: 10-categorias-receita-padrao.sql
-- Categorias padrao de receita alem de Salario, visiveis para todo usuario.
-- Para bancos criados antes delas; bancos novos ja recebem tudo pelo database/init.
--
-- Pode ser reaplicada: so cria a categoria padrao que ainda nao existir.

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
