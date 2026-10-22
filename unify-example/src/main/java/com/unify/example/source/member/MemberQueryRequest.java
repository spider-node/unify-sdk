package com.unify.example.source.member;

/**
 * 会员中心接口的请求结构。
 *
 * @param memberId     会员号
 * @param includeAsset 是否返回资产信息
 */
public record MemberQueryRequest(String memberId, boolean includeAsset) {
}
