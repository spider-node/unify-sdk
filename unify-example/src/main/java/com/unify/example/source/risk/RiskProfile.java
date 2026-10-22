package com.unify.example.source.risk;

/**
 * 风控系统接口的响应结构。
 *
 * @param customerId 客户号
 * @param riskLevel  风险等级 1~5，3 及以上为高风险
 * @param flags      风险标记，逗号分隔，例如 {@code OVERDUE,MULTI_LOAN}
 */
public record RiskProfile(String customerId, int riskLevel, String flags) {
}
