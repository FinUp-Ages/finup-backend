# Contrato C6 - Modelagem da Trilha de Estudos

Este contrato define a modelagem de dados, status derivados, contagens agregadas e os schemas JSON para as perguntas e categorias da **Trilha de Estudos**.

---

## 1. Modelo Relacional e Tabelas

### 1.1 Tabela `study_categories`
Representa uma categoria de estudos que agrupa perguntas temáticas.

| Coluna | Tipo | Restrições | Descrição |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | PK, DEFAULT `gen_random_uuid()` | Identificador único |
| `name` | `VARCHAR(100)` | NOT NULL | Nome da categoria |
| `description` | `VARCHAR(255)` | NULL | Descrição da categoria |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data de criação |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data da última atualização |

### 1.2 Tabela `study_questions`
Representa um item da trilha de estudos (pergunta ou conteúdo educativo).

| Coluna | Tipo | Restrições | Descrição |
| :--- | :--- | :--- | :--- |
| `id` | `UUID` | PK, DEFAULT `gen_random_uuid()` | Identificador único |
| `category_id` | `UUID` | NOT NULL, FK `study_categories(id)` | Categoria vinculada |
| `type` | `VARCHAR(50)` | NOT NULL, CHECK (`FILL_BLANK`, `MULTIPLE_CHOICE`, `LEARNING`) | Tipo de pergunta |
| `draft` | `JSONB` | NOT NULL | Conteúdo em rascunho / edição |
| `published` | `JSONB` | NULL | Conteúdo ativo em produção |
| `published_at` | `TIMESTAMPTZ` | NULL | Timestamp da última publicação |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data de criação |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data da última alteração |

**Índices:**
- `idx_study_questions_category_id` sobre `category_id`: otimiza buscas e contagens agregadas por categoria.

A FK não tem `ON DELETE`, como as demais do schema: o banco recusa remover uma categoria que ainda tem perguntas, o que reforça a regra da API de categorias (409 ao remover categoria com perguntas).

---

## 2. Status Derivado da Pergunta (`QuestionStatus`)

O status de uma pergunta **nunca é persistido em coluna física**, sendo estritamente calculado a partir do estado de `published` e `draft`:

| Status | Regra de Cálculo | Significado |
| :--- | :--- | :--- |
| **`DRAFT`** | `published IS NULL` | Pergunta recém-criada ou nunca publicada no ar. |
| **`PUBLISHED`** | `published IS NOT NULL && Objects.equals(draft, published)` | Pergunta em produção sem nenhuma alteração pendente. |
| **`PUBLISHED_WITH_CHANGES`** | `published IS NOT NULL && !Objects.equals(draft, published)` | Pergunta em produção, mas que possui edição no rascunho ainda não republicada. |

---

## 3. Contrato de Categoria com Contagens Agregadas

O DTO `StudyCategoryResponse` expõe:
```json
{
  "id": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "name": "Reserva de Emergência",
  "description": "Conceitos fundamentais para gerir sua reserva financeira.",
  "draftCount": 1,
  "publishedCount": 1,
  "createdAt": "2026-10-20T19:00:00Z",
  "updatedAt": "2026-10-20T19:00:00Z"
}
```

### Critério de Contagem para `PUBLISHED_WITH_CHANGES`:
- **`publishedCount`**: Quantidade de perguntas que possuem versão no ar disponível aos alunos (`published IS NOT NULL`). Inclui perguntas em **`PUBLISHED`** e **`PUBLISHED_WITH_CHANGES`**.
- **`draftCount`**: Quantidade de perguntas que possuem rascunho necessitando revisão/publicação (`published IS NULL || draft <> published`). Inclui perguntas em **`DRAFT`** e **`PUBLISHED_WITH_CHANGES`**.

---

## 4. Schemas JSON por Tipo de Pergunta

`draft` e `published` guardam o mesmo formato: o conteúdo enviado no POST/PUT da API de escrita (task 86e3m95y3), com o `type` repetido dentro do JSON. Só os campos do tipo são gravados; os campos dos outros tipos são descartados. A validação por tipo é responsabilidade da API de escrita.

### 4.1 `MULTIPLE_CHOICE` (Objetiva)
Enunciado com alternativas; pelo menos uma alternativa tem `correct: true`. `explanation` é opcional.
```json
{
  "type": "MULTIPLE_CHOICE",
  "statement": "Quantos meses de custo de vida é recomendado manter em uma reserva de emergência?",
  "options": [
    { "text": "1 a 2 meses", "correct": false },
    { "text": "3 a 6 meses", "correct": true },
    { "text": "12 a 24 meses", "correct": false }
  ],
  "explanation": "O consenso financeiro recomenda entre 3 e 6 meses de despesas essenciais."
}
```

### 4.2 `FILL_BLANK` (Preencher lacuna)
O `statement` marca a lacuna com `___`; `answers` lista as respostas aceitas (pelo menos uma). `explanation` é opcional.
```json
{
  "type": "FILL_BLANK",
  "title": "Onde aplicar a reserva",
  "statement": "A reserva de emergência deve ser aplicada em investimentos de alta ___.",
  "answers": ["liquidez", "liquidez diária"],
  "explanation": "Alta liquidez garante que você possa resgatar o dinheiro no momento em que precisar."
}
```

### 4.3 `LEARNING` (Conteúdo explicativo)
Título e corpo em Markdown, preservado como foi enviado.
```json
{
  "type": "LEARNING",
  "title": "O que é CDI e taxa Selic?",
  "body": "## CDI e Selic\n\nO CDI é a taxa média dos empréstimos entre bancos, muito usada como referência da renda fixa."
}
```
