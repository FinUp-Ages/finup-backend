variable "aws_region" {
  description = "Regiao do Bedrock usada pelo backend (finup.ai.bedrock.region)."
  type        = string
  default     = "us-east-2"
}

variable "aws_profile" {
  description = "Perfil do AWS CLI com permissao de administrar a infra. Nulo usa a cadeia padrao."
  type        = string
  default     = null
}

variable "guardrail_prompt_attack_strength" {
  description = "Forca do filtro PROMPT_ATTACK na entrada: LOW, MEDIUM ou HIGH."
  type        = string
  default     = "HIGH"

  validation {
    condition     = contains(["LOW", "MEDIUM", "HIGH"], var.guardrail_prompt_attack_strength)
    error_message = "Use LOW, MEDIUM ou HIGH."
  }
}

variable "app_iam_user_name" {
  description = "Usuario IAM cujas access keys a API usa. Nulo = nao anexa a policy a um usuario."
  type        = string
  default     = null
}

variable "app_iam_role_name" {
  description = "Role da instancia EC2 que roda a API. Nulo = nao anexa a policy a uma role."
  type        = string
  default     = null
}
