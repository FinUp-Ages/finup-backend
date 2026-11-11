# Guardrail do assistente de IA: barra prompt injection e jailbreak na mensagem do usuario antes
# de ela chegar ao modelo. O backend envia so o texto do usuario como guardContent, entao o prompt
# de sistema nao e avaliado.
resource "aws_bedrock_guardrail" "assistant" {
  name        = "finup-assistant"
  description = "Bloqueia prompt injection e jailbreak na mensagem do usuario do assistente FinUp"

  # O backend troca o bloqueio por uma mensagem propria; estes textos so aparecem em chamadas
  # diretas ao Bedrock.
  blocked_input_messaging   = "Nao posso processar essa mensagem. Tente algo como \"gastei 20 reais no mercado\"."
  blocked_outputs_messaging = "Nao consegui gerar uma resposta segura. Tente reformular."

  # O tier STANDARD e o unico multilingue (inclui portugues) e exige o perfil cross-region.
  cross_region_config {
    guardrail_profile_identifier = local.guardrail_profile_arn
  }

  content_policy_config {
    tier_config = [{ tier_name = "STANDARD" }]

    filters_config {
      type            = "PROMPT_ATTACK"
      input_strength  = var.guardrail_prompt_attack_strength
      output_strength = "NONE" # a AWS so aplica PROMPT_ATTACK a entrada
    }
  }
}

# Versao imutavel: o backend aponta para ela (BEDROCK_GUARDRAIL_VERSION), nunca para o DRAFT.
# Mudou o guardrail? Altere a description para gerar uma versao nova e atualize o backend.
resource "aws_bedrock_guardrail_version" "assistant" {
  guardrail_arn = aws_bedrock_guardrail.assistant.guardrail_arn
  description   = "PROMPT_ATTACK ${var.guardrail_prompt_attack_strength} na entrada"
  skip_destroy  = true
}

locals {
  guardrail_profile_arn = "arn:aws:bedrock:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:guardrail-profile/us.guardrail.v1:0"
}
