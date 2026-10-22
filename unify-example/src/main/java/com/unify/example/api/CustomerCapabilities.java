package com.unify.example.api;

import com.unify.sdk.api.Capability;

/**
 * 对外提供的统一能力清单。调用方只依赖这里的能力定义和统一模型。
 */
public final class CustomerCapabilities {

    /** 查询客户资料：按客户归属路由到老 CRM 或会员中心。 */
    public static final Capability<CustomerQuery, CustomerProfile> QUERY_CUSTOMER =
            Capability.of("customer.query", CustomerQuery.class, CustomerProfile.class);

    /** 查询客户标签：并行查询营销系统与风控系统，合并标签。 */
    public static final Capability<CustomerQuery, CustomerTags> QUERY_CUSTOMER_TAGS =
            Capability.of("customer.tags", CustomerQuery.class, CustomerTags.class);

    private CustomerCapabilities() {
    }
}
