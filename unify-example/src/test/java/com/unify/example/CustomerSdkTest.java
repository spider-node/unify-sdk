package com.unify.example;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerProfile;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerStatus;
import com.unify.example.api.CustomerTags;
import com.unify.example.source.SourceIds;
import com.unify.example.source.legacy.LegacyCrmDataSource;
import com.unify.example.source.legacy.LegacyCustomerRequest;
import com.unify.example.source.legacy.LegacyCustomerResponse;
import com.unify.example.source.marketing.MarketingDataSource;
import com.unify.example.source.member.MemberCenterDataSource;
import com.unify.example.source.member.MemberInfo;
import com.unify.example.source.member.MemberQueryRequest;
import com.unify.example.source.risk.RiskDataSource;
import com.unify.example.source.risk.RiskProfile;
import com.unify.sdk.api.ErrorCode;
import com.unify.sdk.api.SourceStatus;
import com.unify.sdk.api.UnifiedClient;
import com.unify.sdk.api.UnifiedException;
import com.unify.sdk.api.UnifiedResult;
import com.unify.sdk.spi.DataSourceException;
import com.unify.sdk.testkit.StubDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从调用方视角验证：同一套能力与模型，屏蔽了背后的多个内部接口。
 */
class CustomerSdkTest {

    private static UnifiedClient defaultClient() {
        return CustomerSdk.create(new LegacyCrmDataSource(), new MemberCenterDataSource(),
                new MarketingDataSource(), new RiskDataSource());
    }

    @Test
    void sameCapabilityServesCustomersFromDifferentSystems() {
        try (UnifiedClient client = defaultClient()) {
            CustomerProfile legacy = client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("L1001"));
            CustomerProfile member = client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("M2001"));

            assertEquals("张三", legacy.name());
            assertEquals(CustomerStatus.ACTIVE, legacy.status());
            assertEquals(new BigDecimal("12345.67"), legacy.balance());
            assertEquals(3, legacy.vipLevel());

            assertEquals("王五", member.name());
            assertEquals(CustomerStatus.ACTIVE, member.status());
            assertEquals(new BigDecimal("88.50"), member.balance());
            assertEquals(2, member.vipLevel());
        }
    }

    @Test
    void onlyTheOwningSystemIsCalled() {
        StubDataSource<LegacyCustomerRequest, LegacyCustomerResponse> legacy =
                StubDataSource.returning(SourceIds.LEGACY_CRM, null);
        StubDataSource<MemberQueryRequest, MemberInfo> member = StubDataSource.of(SourceIds.MEMBER_CENTER,
                request -> new MemberInfo(request.memberId(), "测试", "FROZEN", null, null, null));

        try (UnifiedClient client = CustomerSdk.create(legacy, member, new MarketingDataSource(), new RiskDataSource())) {
            UnifiedResult<CustomerProfile> result =
                    client.executeWithMeta(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("M3001"));

            assertEquals(CustomerStatus.FROZEN, result.data().status());
            assertEquals(List.of(SourceIds.MEMBER_CENTER), result.successfulSources());
            assertEquals(new MemberQueryRequest("M3001", true), member.lastRequest());
            assertEquals(0, legacy.invocationCount());
        }
    }

    @Test
    void unknownCustomerReturnsNull() {
        try (UnifiedClient client = defaultClient()) {
            assertNull(client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("L9999")));
        }
    }

    @Test
    void unsupportedCustomerIdIsRejected() {
        try (UnifiedClient client = defaultClient()) {
            UnifiedException error = assertThrows(UnifiedException.class,
                    () -> client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("X9999")));
            assertEquals(ErrorCode.NO_AVAILABLE_SOURCE, error.code());
        }
    }

    @Test
    void providerFailureIsReportedWithUnifiedError() {
        StubDataSource<LegacyCustomerRequest, LegacyCustomerResponse> legacy = StubDataSource.failing(
                SourceIds.LEGACY_CRM, new DataSourceException("gateway timeout", "CRM-504", true, null));

        try (UnifiedClient client = CustomerSdk.create(legacy, new MemberCenterDataSource(),
                new MarketingDataSource(), new RiskDataSource())) {
            UnifiedException error = assertThrows(UnifiedException.class,
                    () -> client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("L1001")));
            assertEquals(ErrorCode.SOURCE_FAILED, error.code());
            assertTrue(error.getMessage().contains("CRM-504"));
            assertEquals(1, error.traces().size());
        }
    }

    @Test
    void tagsAreMergedFromAllSystems() {
        try (UnifiedClient client = defaultClient()) {
            UnifiedResult<CustomerTags> result =
                    client.executeWithMeta(CustomerCapabilities.QUERY_CUSTOMER_TAGS, new CustomerQuery("M2001"));

            assertEquals(Set.of("新客", "高风险", "历史逾期", "多头借贷"), result.data().tags());
            assertEquals(2, result.traces().size());
            assertFalse(result.degraded());
        }
    }

    @Test
    void tagsDegradeWhenOneSystemFails() {
        StubDataSource<String, RiskProfile> risk =
                StubDataSource.failing(SourceIds.RISK, new DataSourceException("risk service down"));

        try (UnifiedClient client = CustomerSdk.create(new LegacyCrmDataSource(), new MemberCenterDataSource(),
                new MarketingDataSource(), risk)) {
            UnifiedResult<CustomerTags> result =
                    client.executeWithMeta(CustomerCapabilities.QUERY_CUSTOMER_TAGS, new CustomerQuery("L1001"));

            assertEquals(Set.of("高净值", "理财偏好"), result.data().tags());
            assertTrue(result.degraded());
            assertEquals(List.of(SourceIds.MARKETING), result.successfulSources());
            assertTrue(result.traces().stream()
                    .anyMatch(trace -> trace.sourceId().equals(SourceIds.RISK) && trace.status() == SourceStatus.FAILED));
        }
    }

    @Test
    void slowTagSystemIsCutOffByCapabilityTimeout() {
        StubDataSource<String, RiskProfile> risk = StubDataSource.<String, RiskProfile>returning(SourceIds.RISK,
                new RiskProfile("L1001", 5, null)).withDelay(Duration.ofSeconds(3));

        try (UnifiedClient client = CustomerSdk.create(new LegacyCrmDataSource(), new MemberCenterDataSource(),
                new MarketingDataSource(), risk)) {
            long start = System.nanoTime();
            UnifiedResult<CustomerTags> result =
                    client.executeWithMeta(CustomerCapabilities.QUERY_CUSTOMER_TAGS, new CustomerQuery("L1001"));
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - start).toMillis();

            assertTrue(elapsedMillis < 2000, "should return around the 800ms capability timeout");
            assertTrue(result.degraded());
            assertEquals(Set.of("高净值", "理财偏好"), result.data().tags());
            assertTrue(result.traces().stream()
                    .anyMatch(trace -> trace.sourceId().equals(SourceIds.RISK) && trace.status() == SourceStatus.TIMEOUT));
        }
    }
}
