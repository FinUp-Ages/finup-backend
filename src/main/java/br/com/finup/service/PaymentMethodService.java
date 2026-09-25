package br.com.finup.service;

import br.com.finup.dto.PaymentMethodResponse;
import br.com.finup.model.User;
import br.com.finup.repository.PaymentMethodRepository;
import br.com.finup.security.AuthenticatedIdentity;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PaymentMethodService {

  private final PaymentMethodRepository paymentMethodRepository;
  private final UserService userService;

  public PaymentMethodService(
      PaymentMethodRepository paymentMethodRepository, UserService userService) {
    this.paymentMethodRepository = paymentMethodRepository;
    this.userService = userService;
  }

  public List<PaymentMethodResponse> findActiveByUser(AuthenticatedIdentity identity) {
    User user = userService.findByAuthenticatedIdentity(identity);

    return paymentMethodRepository.findByUserIdAndIsActiveTrue(user.getId()).stream()
        .map(pm -> new PaymentMethodResponse(pm.getId(), pm.getName()))
        .toList();
  }
}
