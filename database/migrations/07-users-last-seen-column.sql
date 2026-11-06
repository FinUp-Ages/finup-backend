-- Migration: 07-users-last-seen-column.sql
-- Compatibilidade para bancos criados antes da coluna usada pelo modelo User.

ALTER TABLE users
ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
