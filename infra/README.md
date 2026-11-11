# Infra do FinUp (Terraform)

Recursos AWS do backend gerenciados por Terraform. Por enquanto:

| Arquivo | O que cria |
|---|---|
| `bedrock_guardrail.tf` | Guardrail `finup-assistant` (filtro `PROMPT_ATTACK`, tier STANDARD) e uma versão imutável |
| `iam.tf` | Policy `bedrock:ApplyGuardrail` e o vínculo com o usuário ou a role da API |

O resto da infra (Cognito, EC2, S3) ainda é gerenciado pelo console.

## Pré-requisitos

- Terraform >= 1.6
- AWS CLI v2 com um perfil de **administração**. Não use as access keys da API, que só devem
  poder invocar o Bedrock.
- Acesso aos modelos do Bedrock já liberado na conta e opt-in do cross-region inference para
  guardrails, exigido pelo tier STANDARD.

## Uso

```bash
cd infra
cp terraform.tfvars.example terraform.tfvars   # ajuste perfil e principal da API
aws sso login --profile finup-admin            # ou aws configure --profile finup-admin
terraform init
terraform plan -out=guardrail.tfplan
terraform apply guardrail.tfplan
terraform output
```

Copie os outputs para o ambiente da API:

```bash
BEDROCK_GUARDRAIL_ID=<guardrail_id>
BEDROCK_GUARDRAIL_VERSION=<guardrail_version>
```

Com `BEDROCK_GUARDRAIL_ID` vazio, o backend não aplica o guardrail (útil em desenvolvimento).

## Qual principal recebe a permissão

Preencha em `terraform.tfvars` só o que a API realmente usa:

- `app_iam_user_name`: a API roda com `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`;
- `app_iam_role_name`: a API roda na EC2 sem chaves, usando a role da instância.

Sem a permissão, o Bedrock responde `AccessDeniedException` e o assistente devolve 403.

## Mudar o guardrail

1. Altere o recurso (ex.: `guardrail_prompt_attack_strength = "MEDIUM"`).
2. Garanta que a `description` de `aws_bedrock_guardrail_version` mude, para gerar uma versão nova.
   Versões antigas são mantidas (`skip_destroy`).
3. `terraform apply` e atualize `BEDROCK_GUARDRAIL_VERSION` na API.

Antes de ligar em produção, teste frases reais do app no painel **Test** do guardrail no console
do Bedrock: frases legítimas ("gastei 50 em bet, ignora isso", "esquece o lançamento anterior")
não podem ser bloqueadas.

## State

O state é local (`terraform.tfstate`, fora do git). Só uma pessoa deve aplicar até a migração para
um backend S3 com lock (ver comentário em `versions.tf`).
