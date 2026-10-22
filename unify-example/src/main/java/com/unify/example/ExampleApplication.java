package com.unify.example;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerProfile;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerTags;
import com.unify.example.source.legacy.LegacyCrmDataSource;
import com.unify.example.source.marketing.MarketingDataSource;
import com.unify.example.source.member.MemberCenterDataSource;
import com.unify.example.source.risk.RiskDataSource;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.api.UnifiedException;

/**
 * 调用方视角的示例：只认识 {@link CustomerCapabilities} 和统一模型，
 * 不感知客户来自老 CRM 还是会员中心，也不感知标签来自几个系统。
 */
public final class ExampleApplication {

    private ExampleApplication() {
    }

    public static void main(String[] args) {
        try (UnifiedClient client = CustomerSdk.create(new LegacyCrmDataSource(), new MemberCenterDataSource(),
                new MarketingDataSource(), new RiskDataSource())) {

            for (String customerId : new String[]{"L1001", "M2001"}) {
                CustomerQuery query = new CustomerQuery(customerId);
                CustomerProfile profile = client.execute(CustomerCapabilities.QUERY_CUSTOMER, query);
                CustomerTags tags = client.execute(CustomerCapabilities.QUERY_CUSTOMER_TAGS, query);
                System.out.println(profile);
                System.out.println(tags);
            }

            try {
                client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("X9999"));
            } catch (UnifiedException e) {
                System.out.println("Expected failure: " + e.code() + " - " + e.getMessage());
            }
        }
    }
}
