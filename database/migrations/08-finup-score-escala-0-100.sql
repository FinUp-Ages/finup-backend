-- Migration: 08-finup-score-escala-0-100.sql
-- O FinUp Score passou da escala 0-1000 para 0-100 (docs/finup-score.md, secao 1).
-- Converte os scores ja gravados na escala antiga. Os novos recalculos ja gravam 0-100.
--
-- Pode ser reaplicada: so converte valores acima de 100, que nao existem na escala nova.
-- Limite conhecido: um score antigo entre 20 e 100 (o minimo da formula antiga era 20) fica
-- como esta; ele e corrigido no proximo recalculo (ex.: reenviar a triagem).

UPDATE users
SET
  fin_up_score = ROUND(fin_up_score / 10.0)
WHERE
  fin_up_score > 100;
