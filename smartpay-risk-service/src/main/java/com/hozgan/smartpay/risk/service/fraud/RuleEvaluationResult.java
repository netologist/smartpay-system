package com.hozgan.smartpay.risk.service.fraud;

public record RuleEvaluationResult(
        String ruleName,
        boolean triggered,
        int scoreImpact,
        boolean immediateReject,
        String reasoning
) {
    public static RuleEvaluationResult passed(String ruleName) {
        return new RuleEvaluationResult(ruleName, false, 0, false, null);
    }

    public static RuleEvaluationResult triggered(String ruleName, int scoreImpact, String reasoning) {
        return new RuleEvaluationResult(ruleName, true, scoreImpact, false, reasoning);
    }

    public static RuleEvaluationResult immediateReject(String ruleName, String reasoning) {
        return new RuleEvaluationResult(ruleName, true, 100, true, reasoning);
    }
}
