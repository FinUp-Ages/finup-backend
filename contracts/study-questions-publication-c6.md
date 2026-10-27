# C6 — Publicação de perguntas da trilha

Task: Backend — Publicar, despublicar e descartar alterações da Trilha (ClickUp 86e3m95k1).
Base provisória: commit `799474c` (PR #54, API administrativa de perguntas), sobre a `develop`
com o PR #51. O PR desta task passa a ser contra a `develop` quando o #54 for mesclado.

Todas as rotas usam o prefixo `/api/v1/admin/study-tracks` e exigem access token do Cognito com
grupo `admin` (regra única em `/api/v1/admin/**` no `SecurityConfig`). O profile `mock-auth` não
verifica grupo e não serve para validar 401/403.

| Método | Caminho | Sucesso | O que faz |
| --- | --- | --- | --- |
| POST | `/questions/{id}/publish` | 200 com a pergunta | valida o `draft` e o copia para `published` |
| POST | `/questions/{id}/unpublish` | 200 com a pergunta | zera `published` e `publishedAt` |
| DELETE | `/questions/{id}/draft` | 200 com a pergunta | restaura o `draft` a partir de `published` |

As três respondem com o mesmo corpo do `StudyQuestionResponse` do #54 (`id`, `status`, `draft`,
`published`, `updatedAt`, `publishedAt`), com o status já recalculado. O descarte devolve a pergunta
restaurada (200), e não 204, para o painel atualizar a tela sem outra chamada.

## Status depois de cada ação

O status continua derivado (nunca gravado): `DRAFT` sem `published`, `PUBLISHED` quando `draft` e
`published` são iguais, `PUBLISHED_WITH_CHANGES` quando diferem.

| Ação | Estado de partida | Estado final |
| --- | --- | --- |
| publish | `DRAFT` ou `PUBLISHED_WITH_CHANGES` | `PUBLISHED`, `publishedAt` = agora |
| unpublish | `PUBLISHED` ou `PUBLISHED_WITH_CHANGES` | `DRAFT`, `published` e `publishedAt` nulos; o `draft` é mantido, inclusive com edição pendente |
| discard | `PUBLISHED_WITH_CHANGES` | `PUBLISHED`, `draft` = `published`; `publishedAt` não muda |

As contagens da categoria (`draftCount` e `publishedCount`) saem do status das perguntas, então
acompanham cada ação sem código a mais: publicar move `draftCount` -1 e `publishedCount` +1 para uma
pergunta nova; despublicar faz o inverso; descartar uma edição tira a pergunta de `draftCount`.

## Erros

| Situação | Resposta |
| --- | --- |
| `draft` que não passa na validação do tipo | **400**, nada é publicado (nem `publishedAt`) |
| `draft` em formato antigo ou com `type` diferente do da pergunta | **400** |
| descartar pergunta que **nunca foi publicada** | **409** (não há versão para restaurar) |
| pergunta inexistente | **404** |
| `id` que não é UUID | **400** |
| sem token ou token inválido | **401** |
| autenticado fora do grupo `admin` | **403** |

O 409 do descarte foi uma escolha desta task: o enunciado deixava "409 ou 400" para o contrato.
Adotou-se 409 porque a requisição é válida e só conflita com o estado atual da pergunta (o mesmo
critério da remoção de categoria com perguntas).

## Validação do rascunho

Ao publicar, o `draft` gravado é lido como `StudyQuestionRequest` e passa pelo mesmo
`StudyQuestionValidator` do POST e do PUT, então as regras por tipo não são duplicadas: lacuna exige
título, enunciado e respostas não vazias; objetiva exige enunciado e alternativas com texto e
`correct`, pelo menos uma correta; conteúdo educativo exige título e corpo. A mensagem do 400 é
`Rascunho inválido, não foi publicado: <motivo>`.

## Sem efeito (idempotência)

Quando não há o que fazer, a resposta é 200 com a pergunta como está e **nada é gravado** (nem
`updatedAt` nem `publishedAt`):

- publicar o que já está `PUBLISHED`, sem edição pendente (também não valida o conteúdo);
- despublicar o que está `DRAFT`;
- descartar o que está `PUBLISHED`, sem edição pendente.

## Concorrência

Cada ação trava a linha da pergunta (`SELECT ... FOR UPDATE`), então duas ações simultâneas na mesma
pergunta rodam uma depois da outra. O `PUT` do #54 não usa esse lock (ver "Pontos em aberto").

## Pontos em aberto

- `PUT /questions/{id}` (#54) lê e regrava a linha inteira sem lock. Se ele rodar ao mesmo tempo que
  uma publicação, o último a gravar pode desfazer o outro (por exemplo, voltar `published` a nulo).
  A correção natural é `@DynamicUpdate` em `StudyQuestion` ou o mesmo lock no `PUT`; fica fora desta
  task por tocar código do #51 e do #54.

## Validação

```sh
./mvnw -Dtest=StudyQuestionPublicationServiceTest,StudyQuestionPublicationIntegrationTests test
./mvnw -B clean verify
```

Os testes integrados usam H2 em modo PostgreSQL por padrão, com segurança, controller, service e
repositories reais; só o decoder JWT é simulado. Para repetir em PostgreSQL, use um banco exclusivo
e descartável (`create-drop`), com as propriedades `study-question-test-db-url`,
`study-question-test-db-driver`, `study-question-test-db-user` e `study-question-test-db-password`.
