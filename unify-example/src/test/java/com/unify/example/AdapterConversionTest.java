package com.unify.example;

import com.unify.example.api.CustomerProfile;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerStatus;
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
import com.unify.sdk.testkit.AdapterVerifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

/**
 * 逐个校验适配器的路由判断与双向转换，不依赖引擎和真实接口。
 */
class AdapterConversionTest {

    @Test
    void legacyCrmAdapter() {
        CustomerQuery query = new CustomerQuery("L1001");
        LegacyCustomerResponse response =
                new LegacyCustomerResponse("L1001", " 张三 ", "1", 1234567L, "20190315093000", "A");
        CustomerProfile expected = new CustomerProfile("L1001", "张三", CustomerStatus.ACTIVE,
                new BigDecimal("12345.67"), Instant.parse("2019-03-15T01:30:00Z"), 3);

        AdapterVerifier.of(new LegacyCustomerAdapter())
                .assertSupports(query)
                .assertNotSupports(new CustomerQuery("M2001"))
                .assertNotSupports(new CustomerQuery(null))
                .assertSourceRequest(query, new LegacyCustomerRequest("L1001", "UNIFY-SDK"))
                .assertUnifiedResponse(query, response, expected)
                .assertHandlesNullResponse(query);
    }

    @Test
    void legacyCrmAdapterToleratesUnknownCodesAndMissingFields() {
        CustomerQuery query = new CustomerQuery("L1003");
        LegacyCustomerResponse response = new LegacyCustomerResponse("L1003", "  ", "5", null, " ", null);
        CustomerProfile expected = new CustomerProfile("L1003", null, CustomerStatus.UNKNOWN, null, null, 0);

        AdapterVerifier.of(new LegacyCustomerAdapter()).assertUnifiedResponse(query, response, expected);
    }

    @Test
    void memberCenterAdapter() {
        CustomerQuery query = new CustomerQuery("M2001");
        MemberInfo response = new MemberInfo("M2001", "王五", "CANCELLED",
                new MemberInfo.Asset(new BigDecimal("88.5")), 1700000000000L, null);
        CustomerProfile expected = new CustomerProfile("M2001", "王五", CustomerStatus.CLOSED,
                new BigDecimal("88.50"), Instant.ofEpochMilli(1700000000000L), 0);

        AdapterVerifier.of(new MemberCustomerAdapter())
                .assertSupports(query)
                .assertNotSupports(new CustomerQuery("L1001"))
                .assertSourceRequest(query, new MemberQueryRequest("M2001", true))
                .assertUnifiedResponse(query, response, expected)
                .assertHandlesNullResponse(query);
    }

    @Test
    void marketingTagAdapter() {
        CustomerQuery query = new CustomerQuery("L1001");
        MarketingTagResponse response = new MarketingTagResponse("L1001", Arrays.asList(
                new MarketingTagResponse.Tag("T01", "高净值"),
                new MarketingTagResponse.Tag("T02", " "),
                null));

        AdapterVerifier.of(new MarketingTagAdapter())
                .assertSupports(query)
                .assertSourceRequest(query, "L1001")
                .assertUnifiedResponse(query, response, new CustomerTags("L1001", Set.of("高净值")))
                .assertHandlesNullResponse(query);
    }

    @Test
    void riskTagAdapter() {
        CustomerQuery query = new CustomerQuery("M2001");

        AdapterVerifier.of(new RiskTagAdapter())
                .assertSourceRequest(query, "M2001")
                .assertUnifiedResponse(query, new RiskProfile("M2001", 4, "OVERDUE, MULTI_LOAN,UNKNOWN_FLAG"),
                        new CustomerTags("M2001", Set.of("高风险", "历史逾期", "多头借贷")))
                .assertUnifiedResponse(query, new RiskProfile("M2001", 1, null),
                        new CustomerTags("M2001", Set.of()))
                .assertHandlesNullResponse(query);
    }
}
