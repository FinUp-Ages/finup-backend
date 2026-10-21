# C4 rascunho - uso de IA por usuario

## Objetivo

Definir o contrato final do endpoint administrativo de uso de IA e a tabela minima para guardar, por usuario, as chamadas feitas ao Amazon Bedrock.

## C4 final proposto

### Endpoint

GET /api/v1/admin/ai-usage?from=yyyy-MM-dd&to=yyyy-MM-dd

### Resposta

```json
{
  "totals": {
    "requests": 0,
    "inputTokens": 0,
    "outputTokens": 0,
    "estimatedCostUsd": 0.0
  },
  "users": [
    {
      "userId": "uuid",
      "name": "...",
      "requests": 0,
      "inputTokens": 0,
      "outputTokens": 0
    }
  ]
}
```

### Regras de contrato

- `from` e `to` sao obrigatorios e representam um intervalo inclusivo.
- `totals.requests` conta as chamadas da aplicacao que tiveram uso contabilizavel da Bedrock no periodo.
- `totals.inputTokens` e `totals.outputTokens` somam os tokens observados em todas as chamadas do periodo.
- `totals.estimatedCostUsd` e um valor estimado, nao um valor oficial retornado pela AWS.
- `users` deve agrupar por usuario autenticado e trazer apenas os numeros que interessam ao painel no primeiro corte.
- Se nao houver uso no periodo, a resposta deve voltar com totais zerados e lista vazia.

## O que a Bedrock devolve

A chamada ao Bedrock pela Converse API devolve o texto gerado e a contagem de tokens de entrada e saida do uso contabilizado. Isso e suficiente para alimentar o painel de uso.

O que a Bedrock nao devolve de forma confiavel e pronta para o produto:

- custo em USD da chamada
- agrupamento por usuario do sistema
- consolidacao por periodo do nosso dominio

Conclusao pratica: o app precisa persistir os tokens por chamada e calcular o custo estimado com base no modelo usado e na tabela de preco correspondente.

## O que vale guardar por usuario

Guardar por usuario significa guardar o fato de uso de cada chamada, com os campos minimos abaixo:

- `user_id`
- `model_id`
- `request_id` da AWS, quando disponivel
- `input_tokens`
- `output_tokens`
- `estimated_cost_usd`
- `created_at`

Nao vale guardar aqui o prompt bruto nem a resposta bruta. Esses textos aumentam risco de privacidade e nao sao necessarios para o painel administrativo.

## Números da AWS que valem expor

### No total

- `requests`
- `inputTokens`
- `outputTokens`
- `estimatedCostUsd`

### Por usuario

- `requests`
- `inputTokens`
- `outputTokens`

O custo por usuario pode ficar para uma segunda versao. Ele e derivavel da mesma tabela, mas nao e necessario para o primeiro contrato.

## Proposta de tabela

Uma tabela unica de eventos e suficiente para o primeiro corte:

```sql
CREATE TABLE ai_usage_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  model_id VARCHAR(120) NOT NULL,
  request_id VARCHAR(255),
  input_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ai_usage_events_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_ai_usage_events_created_at ON ai_usage_events (created_at);
CREATE INDEX idx_ai_usage_events_user_created_at ON ai_usage_events (user_id, created_at DESC);
```

### Por que esse formato

- Uma linha por chamada simplifica auditoria e recalculo.
- A agregacao do endpoint vira consulta por periodo e por usuario.
- O custo estimado pode ser recalculado depois se a tabela de precos da AWS mudar.
- O modelo usado fica preservado para explicar divergencias de custo entre chamadas.

## Observacoes de implementacao futura

- O registro do uso deve acontecer logo depois do retorno util da Bedrock, antes de qualquer agregacao do endpoint.
- Chamadas que falham antes de haver resposta util da Bedrock nao entram na tabela de uso.
- Se no futuro for necessario detalhar custos por modelo ou versao de preco, isso pode sair da mesma tabela sem alterar o contrato do endpoint.
