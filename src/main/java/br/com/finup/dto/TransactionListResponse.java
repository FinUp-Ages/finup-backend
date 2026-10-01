package br.com.finup.dto;

import java.math.BigDecimal;
import java.util.List;

public record TransactionListResponse(
    BigDecimal balance, List<TransactionListItemResponse> transactions) {}
