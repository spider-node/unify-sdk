package com.unify.example.source.legacy;

/**
 * 老 CRM 接口的响应结构：金额以分为单位，状态与等级是码值，时间是北京时间字符串。
 *
 * @param custNo   客户号
 * @param custName 客户名称（可能带首尾空格）
 * @param statCd   状态码：1 正常、2 冻结、9 销户
 * @param balFen   余额（分）
 * @param crtTm    开户时间，格式 yyyyMMddHHmmss，北京时间
 * @param grade    客户等级：A、B、C，空表示普通客户
 */
public record LegacyCustomerResponse(String custNo,
                                     String custName,
                                     String statCd,
                                     Long balFen,
                                     String crtTm,
                                     String grade) {
}
