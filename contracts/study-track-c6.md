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
| `category_id` | `UUID` | NOT NULL, FK `study_categories(id)` ON DELETE CASCADE | Categoria vinculada |
| `type` | `VARCHAR(50)` | NOT NULL, CHECK (`FILL_BLANK`, `MULTIPLE_CHOICE`, `LEARNING`) | Tipo de pergunta |
| `draft` | `JSONB` | NOT NULL | Conteúdo em rascunho / edição |
| `published` | `JSONB` | NULL | Conteúdo ativo em produção |
| `published_at` | `TIMESTAMPTZ` | NULL | Timestamp da última publicação |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data de criação |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT `CURRENT_TIMESTAMP` | Data da última alteração |

**Índices:**
- `idx_study_questions_category_id` sobre `category_id`: otimiza buscas e contagens agregadas por categoria.

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

### 4.1 `MULTIPLE_CHOICE` (Múltipla Escolha)
Estrutura para questões de escolha única com alternativas:
```json
{
  "statement": "Quantos meses de custo de vida é recomendado manter na reserva de emergência?",
  "options": [
    { "id": "A", "text": "1 a 2 meses" },
    { "id": "B", "text": "3 a 6 meses" },
    { "id": "C", "text": "12 a 24 meses" }
  ],
  "correctOptionId": "B",
  "explanation": "O consenso financeiro recomenda entre 3 a 6 meses de despesas essenciais."
}
```

### 4.2 `FILL_BLANK` (Preenchimento de Lacunas)
Estrutura para questões interativas onde o usuário preenche lacunas `{termo}`:
```json
{
  "textWithBlanks": "A reserva de emergência deve ser aplicada em investimentos de alta {liquidez} e baixo {risco}.",
  "blanks": [
    {
      "index": 0,
      "expectedAnswer": "liquidez",
      "acceptableAnswers": ["liquidez", "facilidade de resgate"]
    },
    {
      "index": 1,
      "expectedAnswer": "risco",
      "acceptableAnswers": ["risco", "risco de perda"]
    }
  ],
  "explanation": "Alta liquidez permite o resgate imediato e baixo risco protege o valor principal."
}
```

### 4.3 `LEARNING` (Conteúdo Educacional / Teórico)
Estrutura para cards informativos e lições teóricas:
```json
{
  "title": "O que é CDI e taxa Selic?",
  "content": "O CDI é a taxa média praticada nos empréstimos entre instituições financeiras, acompanhando a Selic meta.",
  "keyTakeaways": [
    "A Selic é a taxa básica de juros definida pelo Copom.",
    "O CDI rende historicamente cerca de 0,10% abaixo da Selic.",
    "Títulos a 100% do CDI acompanham o rendimento dessa taxa."
  ],
  "tips": "Para a reserva de emergência, prefira CDBs com liquidez diária a pelo menos 100% do CDI."
}
```
