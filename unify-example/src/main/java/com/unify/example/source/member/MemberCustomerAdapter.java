package com.unify.example.source.member;

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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 会员中心 → 统一客户资料。会员中心的客户号以 {@code M} 开头。
 */
public class MemberCustomerAdapter
        implements SourceAdapter<CustomerQuery, CustomerProfile, MemberQueryRequest, MemberInfo> {

    private static final CodeMapper<String, CustomerStatus> STATUS = CodeMapper.<String, CustomerStatus>builder("member.state")
            .map("ACTIVE", CustomerStatus.ACTIVE)
            .map("FROZEN", CustomerStatus.FROZEN)
            .map("CANCELLED", CustomerStatus.CLOSED)
            .defaultValue(CustomerStatus.UNKNOWN)
            .build();

    @Override
    public String sourceId() {
        return SourceIds.MEMBER_CENTER;
    }

    @Override
    public Capability<CustomerQuery, CustomerProfile> capability() {
        return CustomerCapabilities.QUERY_CUSTOMER;
    }

    @Override
    public boolean supports(CustomerQuery request, InvocationContext context) {
        return request.customerId() != null && request.customerId().startsWith("M");
    }

    @Override
    public MemberQueryRequest toSourceRequest(CustomerQuery request, InvocationContext context) {
        return new MemberQueryRequest(request.customerId(), true);
    }

    @Override
    public CustomerProfile toUnifiedResponse(MemberInfo response, CustomerQuery request, InvocationContext context) {
        if (response == null) {
            return null;
        }
        return new CustomerProfile(
                response.memberId(),
                Conversions.trimToNull(response.nickName()),
                STATUS.map(response.state()),
                balanceOf(response.asset()),
                Conversions.epochMillis(response.createdAtEpochMs()),
                response.level() == null ? 0 : response.level());
    }

    private static BigDecimal balanceOf(MemberInfo.Asset asset) {
        if (asset == null || asset.balance() == null) {
            return null;
        }
        return asset.balance().setScale(2, RoundingMode.HALF_UP);
    }
}
