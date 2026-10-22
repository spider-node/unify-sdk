package com.unify.example.source.legacy;

import com.unify.example.source.SourceIds;
import com.unify.sdk.spi.DataSource;
import com.unify.sdk.spi.SourceContext;

import java.util.Map;

/**
 * 老 CRM 数据源的内存实现，模拟数据提供方。真实场景中由提供方调用 HTTP / RPC 接口实现。
 */
public class LegacyCrmDataSource implements DataSource<LegacyCustomerRequest, LegacyCustomerResponse> {

    private final Map<String, LegacyCustomerResponse> customers = Map.of(
            "L1001", new LegacyCustomerResponse("L1001", " 张三 ", "1", 1234567L, "20190315093000", "A"),
            "L1002", new LegacyCustomerResponse("L1002", "李四", "2", 0L, "20200101000000", null));

    @Override
    public String sourceId() {
        return SourceIds.LEGACY_CRM;
    }

    @Override
    public LegacyCustomerResponse fetch(LegacyCustomerRequest request, SourceContext context) {
        return customers.get(request.custNo());
    }
}
