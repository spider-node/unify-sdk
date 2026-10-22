package com.unify.example.source.legacy;

/**
 * 老 CRM 接口的请求结构。
 *
 * @param custNo  客户号
 * @param channel 调用渠道
 */
public record LegacyCustomerRequest(String custNo, String channel) {
}
