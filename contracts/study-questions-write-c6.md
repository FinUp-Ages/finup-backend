# C6 — API administrativa de perguntas

Task: [API de perguntas da Trilha: escrita](https://app.clickup.com/t/86e3m95y3).
Base provisória autorizada: PR #51, commit `0d236ba`. A modelagem, migrations e seeds
desse PR são consumidos sem alterações nesta task.

Todas as rotas abaixo usam o prefixo `/api/v1/admin/study-tracks` e exigem access
token do Cognito com grupo `admin`. O profile de desenvolvimento `mock-auth`
mantém o comportamento existente de liberar as rotas; não serve para validar 401/403.

| Método | Caminho | Sucesso |
| --- | --- | --- |
| POST | `/categories/{categoryId}/questions` | 201 com a pergunta e header Location |
| GET | `/categories/{categoryId}/questions` | 200 com array, por ordem de criação |
| PUT | `/questions/{id}` | 200 com a pergunta atualizada |
| DELETE | `/questions/{id}` | 204 sem corpo |

Categoria existente sem perguntas retorna `[]`. Categoria ou pergunta inexistente
retorna 404. UUID, JSON, tipo ou conteúdo inválido retorna 400. Erros seguem o
handler central em `application/problem+json`. Token ausente/inválido retorna
401; usuário autenticado sem grupo `admin`, 403.

## Conteúdo de POST e PUT

```json
{"type":"FILL_BLANK","title":"CDB","statement":"O CDB rende ___ do CDI","answers":["100%"],"explanation":"Rendimento do CDB."}
```

```json
{"type":"MULTIPLE_CHOICE","statement":"Qual opção?","options":[{"text":"A","correct":true},{"text":"B","correct":false}],"explanation":"Explicação."}
```

```json
{"type":"LEARNING","title":"Reserva","body":"## Reserva de emergência"}
```

- Lacuna exige título, enunciado e lista não vazia de respostas não vazias.
- Objetiva exige enunciado, alternativas com texto e indicação booleana `correct`,
  com pelo menos uma correta. Não se impõe exatamente uma correta.
- Conteúdo educativo exige título e corpo; o Markdown é preservado.
- `explanation` é opcional. Os campos específicos dos outros tipos não são gravados.
- Não se adicionam limites de tamanho, deduplicação ou regra de marcador `___`.
- O PUT mantém o tipo original: tentativa de troca retorna 400. Decisão confirmada
  pelo solicitante, pois a leitura publicada usa o tipo da entidade.

O formato desta API segue os exemplos da task de escrita. Os exemplos e seeds do
PR #51 usam alguns campos diferentes (`textWithBlanks`, `correctOptionId`, `content`);
nesta entrega eles não são migrados nem convertidos automaticamente. Conteúdo
publicado existente é sempre devolvido e preservado como foi armazenado.

## Resposta e publicação

```json
{
  "id":"20c7206d-6496-4577-a423-faa8a7039d28",
  "status":"DRAFT",
  "draft":{"type":"LEARNING","title":"Reserva","body":"## Reserva de emergência"},
  "published":null,
  "updatedAt":"2026-10-21T12:00:00Z",
  "publishedAt":null
}
```

O POST nunca publica. O PUT substitui somente o conteúdo de `draft`, preservando
`published`, `publishedAt`, categoria e tipo. Metadados enviados no corpo não
controlam a publicação. O flush ocorre antes da resposta para refletir `updatedAt`.
O status usa a regra da entidade do PR #51: `DRAFT` quando não há publicação,
`PUBLISHED` quando os mapas são iguais e `PUBLISHED_WITH_CHANGES` quando diferem.
O DELETE remove a linha inteira, inclusive conteúdo publicado. Esta task não cria
rotas de publicação, categorias ou leitura para o app.

## Validação

```sh
./mvnw -Dtest=StudyQuestionValidatorTest,StudyQuestionServiceTest,StudyQuestionIntegrationTests test
./mvnw -B clean verify
```

Os testes integrados usam H2 em modo PostgreSQL por padrão, com segurança,
controller, service e repositories reais; apenas o decoder JWT é simulado.
Para repetir em PostgreSQL, use **um banco exclusivo e descartável**: o teste
configura `ddl-auto=create-drop`. As propriedades de teste são
`study-question-test-db-url`, `study-question-test-db-driver`,
`study-question-test-db-user` e `study-question-test-db-password`.
As migrations são responsabilidade do PR #51 e não são executadas por esses testes.
