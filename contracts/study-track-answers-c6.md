# C6 — Respostas e progresso da Trilha de Estudos

## Contrato

- `POST /api/v1/study-tracks/answers`
- `GET /api/v1/study-tracks/answers?categoryId={uuid}`
- `GET /api/v1/study-tracks/progress`

As respostas e o progresso são filtrados pela identidade Cognito autenticada. O app
pode usar `EXPO_PUBLIC_USE_MOCKS` para mockar esses endpoints, mantendo os GETs C6
existentes para categorias e perguntas.

## Níveis da trilha

Cada pergunta publicada de uma categoria é um **nível**, na ordem de
`GET /api/v1/study-tracks/categories/{id}/questions`: `created_at` crescente e, no
empate, `id` crescente. O nível N é a N-ésima pergunta publicada.

- **Nível concluído:** o usuário tem resposta gravada para aquela pergunta publicada,
  certa ou errada. `LEARNING` conta como concluída quando foi lida (resposta `null`).
- **Nível atual (`currentLevel`):** o primeiro nível não concluído, na ordem. Com todos
  concluídos, é o total de níveis; sem perguntas publicadas, é `0`. Sem nenhuma
  resposta, é `1`: o primeiro nível nunca fica bloqueado.
- **Nível alcançado:** `level <= currentLevel`. Os demais aparecem bloqueados.
- Só contam respostas de perguntas publicadas hoje. Resposta de pergunta despublicada
  continua gravada, mas sai do progresso, dos acertos e das estrelas. Pergunta
  publicada depois entra no fim da ordem e passa a ser o próximo nível pendente.

## POST `/answers`

Recebe uma ou mais perguntas publicadas da categoria; não precisa ser o conjunto
completo (o conjunto completo continua aceito):

```json
{
  "categoryId": "uuid",
  "answers": [
    {"questionId": "uuid", "answer": "texto escolhido ou preenchido"}
  ]
}
```

O backend corrige usando somente `published` e ignora qualquer acerto/erro enviado
pelo cliente. `LEARNING` é salva sem nota; `MULTIPLE_CHOICE` compara o texto da
alternativa correta e `FILL_BLANK` compara as respostas aceitas, sem diferenciar
maiúsculas/minúsculas ou espaços externos. Responder de novo uma pergunta substitui a
resposta anterior (vale a última).

Erros: `400` para lista vazia, pergunta repetida no lote, ou pergunta que não é da
categoria ou não está publicada; `404` para categoria inexistente. Nada é gravado
quando o lote é recusado.

Resposta (`200`): `stars`, `correctCount` e `gradableCount` valem para **todas** as
respostas atuais da categoria, não só para o lote; `answers` lista essas respostas.

```json
{
  "categoryId": "uuid",
  "stars": 2,
  "correctCount": 1,
  "gradableCount": 2,
  "answers": [
    {"questionId": "uuid", "answer": "3 a 6 meses", "correct": true, "submittedAt": "2026-11-12T12:00:00Z"}
  ]
}
```

## GET `/progress`

Uma entrada por categoria (ordem por nome) e o total. Os campos novos são aditivos.

```json
{
  "categories": [
    {
      "categoryId": "uuid",
      "totalQuestions": 3,
      "answeredQuestions": 2,
      "correctAnswers": 1,
      "gradableQuestions": 1,
      "stars": 3,
      "percentage": 100.0,
      "completionPercentage": 66.66666666666667,
      "currentLevel": 3,
      "levels": [
        {"level": 1, "questionId": "uuid", "completed": true, "correct": null, "stars": null},
        {"level": 2, "questionId": "uuid", "completed": true, "correct": true, "stars": 3},
        {"level": 3, "questionId": "uuid", "completed": false, "correct": null, "stars": null}
      ]
    }
  ],
  "total": {
    "totalQuestions": 3,
    "answeredQuestions": 2,
    "correctAnswers": 1,
    "gradableQuestions": 1,
    "stars": 3,
    "percentage": 100.0,
    "completionPercentage": 66.66666666666667
  }
}
```

| Campo | Significado |
| :--- | :--- |
| `totalQuestions` | Perguntas publicadas (níveis) |
| `answeredQuestions` | Níveis concluídos |
| `correctAnswers` / `gradableQuestions` | Acertos / respostas corrigíveis (sem `LEARNING`) |
| `percentage` | Percentual de **acertos**: `correctAnswers / gradableQuestions` (0 sem nota) |
| `completionPercentage` | Percentual de **avanço**: `answeredQuestions / totalQuestions` (0 sem níveis) |
| `stars` | Estrelas do conjunto, pelo `percentage`; `null` sem nota |
| `currentLevel` | Nível atual (ver "Níveis da trilha") |
| `levels[].stars` | Estrelas do nível: `3` no acerto, `1` no erro, `null` sem nota (`LEARNING` ou não respondido) |

Os percentuais não são arredondados; o app arredonda para exibir.

Estrelas do conjunto: menos de 50% = 1, de 50% a menos de 80% = 2, a partir de 80% =
3. O percentual considera somente perguntas corrigíveis. Sem perguntas corrigíveis,
não há estrelas.

A regra de estrelas por nível (3/1) é provisória até a confirmação do design (Figma,
nó 185-20958).

Nulos: no `GET /progress` os campos nulos (`stars`, `levels[].correct`,
`levels[].stars`) vêm explícitos como `null`. Nas respostas do `POST /answers` e do
`GET /answers` vale a configuração global da API (`non_null`): `stars` e `correct`
nulos são omitidos, e o cliente deve tratar campo ausente como `null`.

## GET `/answers`

Lista as respostas gravadas do usuário, agrupadas por categoria, com `stars`,
`correctCount` e `gradableCount` do grupo. É o histórico gravado: pode incluir
respostas de perguntas que foram despublicadas depois. Para o progresso da trilha, use
`GET /progress`.
