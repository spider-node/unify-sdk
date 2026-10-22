package com.unify.sdk.core;

/**
 * SDK 入口。
 *
 * <pre>{@code
 * UnifiedClient client = UnifiedClients.builder()
 *         .bind(new LegacyCrmDataSource(), new LegacyCrmAdapter())
 *         .bind(new MemberCenterDataSource(), new MemberCenterAdapter())
 *         .build();
 *
 * CustomerProfile profile = client.execute(CustomerCapabilities.QUERY_CUSTOMER, new CustomerQuery("M1001"));
 * }</pre>
 */
public final class UnifiedClients {

    private UnifiedClients() {
    }

    public static UnifiedClientBuilder builder() {
        return new UnifiedClientBuilder();
    }
}
