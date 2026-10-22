package com.unify.example.source.member;

import java.math.BigDecimal;

/**
 * 会员中心接口的响应结构：资产是嵌套对象，时间是毫秒时间戳，状态是英文码值。
 *
 * @param memberId         会员号
 * @param nickName         昵称
 * @param state            状态：ACTIVE、FROZEN、CANCELLED
 * @param asset            资产信息，未请求时为空
 * @param createdAtEpochMs 注册时间（毫秒时间戳）
 * @param level            会员等级，空表示普通会员
 */
public record MemberInfo(String memberId,
                         String nickName,
                         String state,
                         Asset asset,
                         Long createdAtEpochMs,
                         Integer level) {

    /**
     * @param balance 可用余额（元）
     */
    public record Asset(BigDecimal balance) {
    }
}
