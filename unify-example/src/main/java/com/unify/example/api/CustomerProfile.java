package com.unify.example.api;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 统一客户资料。
 *
 * @param customerId   客户号
 * @param name         客户名称
 * @param status       客户状态
 * @param balance      账户余额（元，两位小数）
 * @param registeredAt 注册时间
 * @param vipLevel     会员等级，0 表示普通客户
 */
public record CustomerProfile(String customerId,
                              String name,
                              CustomerStatus status,
                              BigDecimal balance,
                              Instant registeredAt,
                              int vipLevel) {
}
