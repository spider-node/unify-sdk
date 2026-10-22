package com.unify.example.source.risk;

import com.unify.example.source.SourceIds;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.SourceContext;

import java.util.Map;

/**
 * 风控系统数据源的内存实现，模拟数据提供方。
 */
public class RiskDataSource implements DataSource<String, RiskProfile> {

    private final Map<String, RiskProfile> profiles = Map.of(
            "L1001", new RiskProfile("L1001", 1, ""),
            "M2001", new RiskProfile("M2001", 4, "OVERDUE, MULTI_LOAN"));

    @Override
    public String sourceId() {
        return SourceIds.RISK;
    }

    @Override
    public RiskProfile fetch(String customerId, SourceContext context) {
        return profiles.get(customerId);
    }
}
