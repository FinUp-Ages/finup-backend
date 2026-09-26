package br.com.finup.controller;

import br.com.finup.dto.PaymentMethodResponse;
import br.com.finup.security.AuthenticatedIdentity;
import br.com.finup.security.AuthenticatedIdentityResolver;
import br.com.finup.service.PaymentMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-methods")
@Tag(name = "Payment Methods", description = "Métodos de pagamento do usuário autenticado")
public class PaymentMethodController {

  private final PaymentMethodService paymentMethodService;
  private final AuthenticatedIdentityResolver authenticatedIdentityResolver;

  public PaymentMethodController(
      PaymentMethodService paymentMethodService,
      AuthenticatedIdentityResolver authenticatedIdentityResolver) {
    this.paymentMethodService = paymentMethodService;
    this.authenticatedIdentityResolver = authenticatedIdentityResolver;
  }

  @GetMapping
  @Operation(
      summary = "Lista métodos de pagamento ativos",
      description = "Retorna os métodos de pagamento ativos do usuário autenticado.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(responseCode = "404", description = "Usuário não encontrado")
      })
  public ResponseEntity<List<PaymentMethodResponse>> listPaymentMethods() {
    AuthenticatedIdentity identity = authenticatedIdentityResolver.resolveCurrent();
    List<PaymentMethodResponse> methods = paymentMethodService.findActiveByUser(identity);
    return ResponseEntity.ok(methods);
  }
}
