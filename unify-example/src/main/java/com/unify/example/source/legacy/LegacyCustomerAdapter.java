package com.unify.example.source.legacy;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerProfile;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerStatus;
import com.unify.example.source.SourceIds;
import com.unify.sdk.api.Capability;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.core.convert.CodeMapper;
import com.unify.sdk.core.convert.Conversions;

import java.time.ZoneId;

/**
 * 老 CRM → 统一客户资料。老 CRM 的客户号以 {@code L} 开头。
 */
public class LegacyCustomerAdapter
        implements SourceAdapter<CustomerQuery, CustomerProfile, LegacyCustomerRequest, LegacyCustomerResponse> {

    static final String CHANNEL = "UNIFY-SDK";
    private static final ZoneId CRM_ZONE = ZoneId.of("Asia/Shanghai");

    private static final CodeMapper<String, CustomerStatus> STATUS = CodeMapper.<String, CustomerStatus>builder("legacy.statCd")
            .map("1", CustomerStatus.ACTIVE)
            .map("2", CustomerStatus.FROZEN)
            .map("9", CustomerStatus.CLOSED)
            .defaultValue(CustomerStatus.UNKNOWN)
            .build();

    private static final CodeMapper<String, Integer> GRADE = CodeMapper.<String, Integer>builder("legacy.grade")
            .map("A", 3)
            .map("B", 2)
            .map("C", 1)
            .defaultValue(0)
            .build();

    @Override
    public String sourceId() {
        return SourceIds.LEGACY_CRM;
    }

    @Override
    public Capability<CustomerQuery, CustomerProfile> capability() {
        return CustomerCapabilities.QUERY_CUSTOMER;
    }

    @Override
    public boolean supports(CustomerQuery request, InvocationContext context) {
        return request.customerId() != null && request.customerId().startsWith("L");
    }

    @Override
    public LegacyCustomerRequest toSourceRequest(CustomerQuery request, InvocationContext context) {
        return new LegacyCustomerRequest(request.customerId(), CHANNEL);
    }

    @Override
    public CustomerProfile toUnifiedResponse(LegacyCustomerResponse response, CustomerQuery request,
                                             InvocationContext context) {
        if (response == null) {
            return null;
        }
        return new CustomerProfile(
                response.custNo(),
                Conversions.trimToNull(response.custName()),
                STATUS.map(Conversions.trimToNull(response.statCd())),
                Conversions.centsToYuan(response.balFen()),
                Conversions.parseDateTime(response.crtTm(), "yyyyMMddHHmmss", CRM_ZONE),
                GRADE.map(Conversions.trimToNull(response.grade())));
    }
}
