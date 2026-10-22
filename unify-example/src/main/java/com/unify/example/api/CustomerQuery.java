package com.unify.example.api;

/**
 * 统一查询条件：调用方只需提供客户号，无需关心客户归属哪个内部系统。
 */
public record CustomerQuery(String customerId) {
}
