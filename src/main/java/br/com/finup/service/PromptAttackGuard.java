package br.com.finup.service;

import br.com.finup.config.BedrockProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ApplyGuardrailRequest;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailAction;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailContentSource;
import software.amazon.awssdk.services.bedrockruntime.model.GuardrailTextBlock;

/**
 * Avalia, com o guardrail do Bedrock, texto do usuario que depois vai parar dentro de um prompt
 * (ex.: nome de categoria). Diferente da mensagem do assistente, esse texto e gravado e reaparece
 * em todas as conversas, entao o melhor momento de barrar e na gravacao.
 *
 * <p>Falha aberta: se o Bedrock nao responder, o texto passa e o erro fica no log. Gravar uma
 * categoria nao pode depender da IA, e os prompts ja tratam esses textos como dado ({@link
 * UntrustedText}).
 */
@Component
public class PromptAttackGuard {

  private static final Logger log = LoggerFactory.getLogger(PromptAttackGuard.class);

  private final BedrockRuntimeClient client;
  private final BedrockProperties properties;

  public PromptAttackGuard(BedrockRuntimeClient client, BedrockProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  /**
   * {@code true} se o guardrail configurado barrar o texto. Sem guardrail, sempre {@code false}.
   */
  public boolean isAttack(String text) {
    BedrockProperties.Guardrail guardrail = properties.guardrail();
    if (guardrail == null || !guardrail.enabled() || text == null || text.isBlank()) {
      return false;
    }
    ApplyGuardrailRequest request =
        ApplyGuardrailRequest.builder()
            .guardrailIdentifier(guardrail.id())
            .guardrailVersion(guardrail.version())
            .source(GuardrailContentSource.INPUT)
            .content(
                GuardrailContentBlock.fromText(GuardrailTextBlock.builder().text(text).build()))
            .build();
    try {
      return client.applyGuardrail(request).action() == GuardrailAction.GUARDRAIL_INTERVENED;
    } catch (SdkException e) {
      // Sem o texto no log: ele e do usuario.
      log.warn("Falha ao avaliar texto no guardrail; seguindo sem bloqueio", e);
      return false;
    }
  }
}
