package com.unify.example.source.marketing;

import com.unify.example.source.SourceIds;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.SourceContext;

import java.util.List;
import java.util.Map;

/**
 * 营销系统数据源的内存实现，模拟数据提供方。
 */
public class MarketingDataSource implements DataSource<String, MarketingTagResponse> {

    private final Map<String, List<MarketingTagResponse.Tag>> tags = Map.of(
            "L1001", List.of(new MarketingTagResponse.Tag("T01", "高净值"), new MarketingTagResponse.Tag("T07", "理财偏好")),
            "M2001", List.of(new MarketingTagResponse.Tag("T03", "新客")));

    @Override
    public String sourceId() {
        return SourceIds.MARKETING;
    }

    @Override
    public MarketingTagResponse fetch(String customerId, SourceContext context) {
        return new MarketingTagResponse(customerId, tags.getOrDefault(customerId, List.of()));
    }
}
