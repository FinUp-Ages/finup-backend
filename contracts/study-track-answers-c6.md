# C6 — Respostas e progresso da Trilha de Estudos

## Contrato

- `POST /api/v1/study-tracks/answers`
- `GET /api/v1/study-tracks/answers?categoryId={uuid}`
- `GET /api/v1/study-tracks/progress`

O POST recebe um conjunto completo:

```json
{
  "categoryId": "uuid",
  "answers": [
    {"questionId": "uuid", "answer": "texto escolhido ou preenchido"}
  ]
}
```

O backend valida a categoria e todas as perguntas publicadas, corrige usando somente
`published` e ignora qualquer acerto/erro enviado pelo cliente. `LEARNING` é salva sem
nota; `MULTIPLE_CHOICE` compara o texto da alternativa correta e `FILL_BLANK` compara
as respostas aceitas, sem diferenciar maiúsculas/minúsculas ou espaços externos.

Estrelas do conjunto: menos de 50% = 1, de 50% a menos de 80% = 2, a partir de 80% =
3. O percentual considera somente perguntas corrigíveis. Sem perguntas corrigíveis,
não há estrelas.

As respostas e o progresso são filtrados pela identidade Cognito autenticada. O app
pode usar `EXPO_PUBLIC_USE_MOCKS` para mockar esses endpoints, mantendo os GETs C6
existentes para categorias e perguntas.
