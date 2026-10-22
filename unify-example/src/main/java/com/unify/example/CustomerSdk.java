package com.unify.example;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerTags;
import com.unify.example.source.legacy.LegacyCustomerAdapter;
import com.unify.example.source.legacy.LegacyCustomerRequest;
import com.unify.example.source.legacy.LegacyCustomerResponse;
import com.unify.example.source.marketing.MarketingTagAdapter;
import com.unify.example.source.marketing.MarketingTagResponse;
import com.unify.example.source.member.MemberCustomerAdapter;
import com.unify.example.source.member.MemberInfo;
import com.unify.example.source.member.MemberQueryRequest;
import com.unify.example.source.risk.RiskProfile;
import com.unify.example.source.risk.RiskTagAdapter;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.core.CapabilityConfig;
import com.unify.sdk.core.Mergers;
import com.unify.sdk.core.UnifiedClients;
import com.unify.sdk.core.interceptor.LoggingInterceptor;
import com.unify.sdk.spi.DataSource;

import java.time.Duration;

/**
 * 客户能力 SDK 的装配入口：适配器与能力配置由 SDK 维护方内置，
 * 数据源由各提供方实现后传入，调用方拿到的只是一个 {@link UnifiedClient}。
 */
public final class CustomerSdk {

    private CustomerSdk() {
    }

    public static UnifiedClient create(DataSource<LegacyCustomerRequest, LegacyCustomerResponse> legacyCrm,
                                       DataSource<MemberQueryRequest, MemberInfo> memberCenter,
                                       DataSource<String, MarketingTagResponse> marketing,
                                       DataSource<String, RiskProfile> risk) {
        return UnifiedClients.builder()
                .bind(legacyCrm, new LegacyCustomerAdapter())
                .bind(memberCenter, new MemberCustomerAdapter())
                .bind(marketing, new MarketingTagAdapter())
                .bind(risk, new RiskTagAdapter())
                .capability(tagsConfig())
                .interceptor(new LoggingInterceptor())
                .build();
    }

    /** 标签能力：并行查询营销与风控系统，标签取并集，单个系统失败时降级返回其余结果。 */
    static CapabilityConfig<CustomerQuery, CustomerTags> tagsConfig() {
        return CapabilityConfig.builder(CustomerCapabilities.QUERY_CUSTOMER_TAGS)
                .fanOutMerge(Mergers.reduce(CustomerTags::merge))
                .timeout(Duration.ofMillis(800))
                .build();
    }
}
