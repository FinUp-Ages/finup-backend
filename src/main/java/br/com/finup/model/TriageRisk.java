package br.com.finup.model;

/** Um risco identificado pelas regras da triagem, como devolvido ao app e gravado na avaliação. */
public record TriageRisk(
    String code, RiskSeverity severity, String title, String description, String suggestion) {}
