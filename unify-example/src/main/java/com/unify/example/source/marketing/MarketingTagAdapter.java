package com.unify.example.source.marketing;

import com.unify.example.api.CustomerCapabilities;
import com.unify.example.api.CustomerQuery;
import com.unify.example.api.CustomerTags;
import com.unify.example.source.SourceIds;
import com.unify.sdk.api.Capability;
import com.unify.sdk.core.InvocationContext;
import com.unify.sdk.core.SourceAdapter;
import com.unify.sdk.core.convert.Conversions;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 营销系统 → 统一客户标签。营销系统接口直接以客户号字符串作为请求。
 */
public class MarketingTagAdapter
        implements SourceAdapter<CustomerQuery, CustomerTags, String, MarketingTagResponse> {

    @Override
    public String sourceId() {
        return SourceIds.MARKETING;
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
    public CustomerTags toUnifiedResponse(MarketingTagResponse response, CustomerQuery request,
                                          InvocationContext context) {
        if (response == null || response.tags() == null) {
            return null;
        }
        Set<String> tags = response.tags().stream()
                .filter(Objects::nonNull)
                .map(tag -> Conversions.trimToNull(tag.tagName()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new CustomerTags(request.customerId(), tags);
    }
}
