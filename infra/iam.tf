# Permissao para a API aplicar o guardrail no Converse. Com o tier STANDARD a avaliacao pode
# rodar em outra regiao do perfil us.guardrail.v1:0, por isso o perfil entra no Resource.
data "aws_iam_policy_document" "apply_guardrail" {
  statement {
    actions = ["bedrock:ApplyGuardrail"]
    resources = [
      aws_bedrock_guardrail.assistant.guardrail_arn,
      "arn:aws:bedrock:*:${data.aws_caller_identity.current.account_id}:guardrail-profile/us.guardrail.v1:0",
    ]
  }
}

resource "aws_iam_policy" "apply_guardrail" {
  name        = "finup-apply-assistant-guardrail"
  description = "Permite a API do FinUp aplicar o guardrail do assistente de IA"
  policy      = data.aws_iam_policy_document.apply_guardrail.json
}

resource "aws_iam_user_policy_attachment" "app_apply_guardrail" {
  count      = var.app_iam_user_name == null ? 0 : 1
  user       = var.app_iam_user_name
  policy_arn = aws_iam_policy.apply_guardrail.arn
}

resource "aws_iam_role_policy_attachment" "app_apply_guardrail" {
  count      = var.app_iam_role_name == null ? 0 : 1
  role       = var.app_iam_role_name
  policy_arn = aws_iam_policy.apply_guardrail.arn
}
