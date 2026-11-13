# Edição de categoria de gastos

Task: [Permitir editar a categoria dos gastos](https://app.clickup.com/t/1dfcuabck3w).

`PATCH /api/v1/transactions/{id}/category` recebe `{"categoryId":"<UUID>"}` e responde
`200` com o `TransactionResponse` existente. Exige access token; o usuário é resolvido
pelo token, sem aceitar identidade enviada pelo cliente.

O gasto deve pertencer ao usuário e ser `EXPENSE`. A categoria deve ser uma despesa
disponível para ele (pessoal ou padrão). Transação/categoria inexistente ou de outro
usuário responde `404`; receita/categoria incompatível responde `422`; corpo/UUID
inválido responde `400`; sem autenticação responde `401`.

A alteração afeta apenas `categoryId` e `updatedAt` da transação selecionada. Valor,
descrição, pagamento, data, proprietário, criação e dados de recorrência são preservados.
Outros campos enviados no JSON não são usados. Repetir a categoria já salva é uma
operação sem alteração. Gastos da IA usam a mesma entidade e podem ser corrigidos
pelo mesmo endpoint, sem consultar o modelo novamente.

## Categorias disponíveis

A troca aceita qualquer categoria de despesa disponível para o usuário, pessoal ou
padrão existente, independentemente do nome. Não cria categorias automaticamente.
Educação, por exemplo, só estará disponível se for cadastrada pelo usuário.

Esta funcionalidade não altera o schema nem o seed e não exige migration.

## Validação

`TransactionCategoryIntegrationTests` usa HTTP, segurança e JPA reais com H2 e decoder
JWT simulado. Cobre cadastro manual, registro pela ação da IA sem Bedrock, persistência
após recarga, categorias padrão/pessoais de nomes livres, dados preservados, retry,
validação e isolamento entre usuários. O contrato PATCH também é verificado no OpenAPI.

Executar `./mvnw clean verify` com Java 21. A conferência no PostgreSQL e no Expo deve
usar o mesmo gasto antes/depois e verificar que a categoria continua após recarregar.
