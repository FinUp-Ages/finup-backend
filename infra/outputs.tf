output "guardrail_id" {
  description = "Valor de BEDROCK_GUARDRAIL_ID no backend."
  value       = aws_bedrock_guardrail.assistant.guardrail_id
}

output "guardrail_version" {
  description = "Valor de BEDROCK_GUARDRAIL_VERSION no backend."
  value       = aws_bedrock_guardrail_version.assistant.version
}
