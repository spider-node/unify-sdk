package com.unify.example.source.marketing;

import java.util.List;

/**
 * 营销系统标签接口的响应结构。
 *
 * @param userId 用户号（与统一客户号一致）
 * @param tags   标签列表
 */
public record MarketingTagResponse(String userId, List<Tag> tags) {

    /**
     * @param tagCode 标签编码
     * @param tagName 标签名称
     */
    public record Tag(String tagCode, String tagName) {
    }
}
