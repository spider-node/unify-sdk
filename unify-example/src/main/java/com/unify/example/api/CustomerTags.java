package com.unify.example.api;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 统一客户标签：多个内部系统的标签汇总后的结果。
 */
public record CustomerTags(String customerId, Set<String> tags) {

    public CustomerTags {
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }

    /** 合并另一份标签（取并集）。 */
    public CustomerTags merge(CustomerTags other) {
        if (other == null) {
            return this;
        }
        Set<String> union = new LinkedHashSet<>(tags);
        union.addAll(other.tags());
        return new CustomerTags(customerId, union);
    }
}
