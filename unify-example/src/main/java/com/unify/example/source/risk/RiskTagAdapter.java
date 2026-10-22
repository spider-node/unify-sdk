package com.unify.example.source.risk;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerTags;
import com.unify.example.source.SourceIds;
import com.unify.sdk.api.Capability;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.core.convert.CodeMapper;
import com.unify.sdk.core.convert.Conversions;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 风控系统 → 统一客户标签：把风险等级和风险标记码翻译为业务可读的标签。
 */
public class RiskTagAdapter implements SourceAdapter<CustomerQuery, CustomerTags, String, RiskProfile> {

    static final String HIGH_RISK = "高风险";
    private static final int HIGH_RISK_LEVEL = 3;

    private static final CodeMapper<String, String> FLAGS = CodeMapper.<String, String>builder("risk.flag")
            .map("OVERDUE", "历史逾期")
            .map("MULTI_LOAN", "多头借贷")
            .build();

    @Override
    public String sourceId() {
        return SourceIds.RISK;
    }

    @Override
    public Capability<CustomerQuery, CustomerTags> capability() {
        return CustomerCapabilities.QUERY_CUSTOMER_TAGS;
    }

    @Override
    public String toSourceRequest(CustomerQuery request, InvocationContext context) {
        return request.customerId();
    }

    @Override
    public CustomerTags toUnifiedResponse(RiskProfile response, CustomerQuery request, InvocationContext context) {
        if (response == null) {
            return null;
        }
        Set<String> tags = new LinkedHashSet<>();
        if (response.riskLevel() >= HIGH_RISK_LEVEL) {
            tags.add(HIGH_RISK);
        }
        String flags = Conversions.trimToNull(response.flags());
        if (flags != null) {
            for (String flag : flags.split(",")) {
                String tag = FLAGS.map(Conversions.trimToNull(flag));
                if (tag != null) {
                    tags.add(tag);
                }
            }
        }
        return new CustomerTags(request.customerId(), tags);
    }
}
