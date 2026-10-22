package com.unify.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;

/**
 * 运行示例：把两套来源的客户数据转换为统一客户 JSON，并演示临时 DSL 与错误信息。
 */
public final class ExampleApplication {

    private static final String AD_HOC_DSL = """
            {
              "mapping": {
                "id": "$.CUST_ID",
                "orderNos": "$.ORDERS[*].ORDER_NO",
                "paidAmount": "=centsToYuan(first($.ORDERS[*].AMT_FEN))"
              }
            }
            """;

    private static final String BROKEN_DSL = """
            { "mapping": { "phone": "=maskPhone($.MOBILE, 4)" } }
            """;

    private ExampleApplication() {
    }

    public static void main(String[] args) {
        JsonTransformer transformer = CustomerMappings.createTransformer();
        String legacySource = CustomerMappings.readResource("samples/legacy-customer.json");
        String memberSource = CustomerMappings.readResource("samples/member-customer.json");

        print("老 CRM → 统一客户", transformer.transformByName(CustomerMappings.LEGACY_CUSTOMER,
                readTree(transformer, legacySource)));
        print("会员中心 → 统一客户", transformer.transformByName(CustomerMappings.MEMBER_CUSTOMER,
                readTree(transformer, memberSource)));
        print("临时 DSL", readTree(transformer, transformer.transform(legacySource, AD_HOC_DSL)));

        try {
            transformer.transform(legacySource, BROKEN_DSL);
        } catch (TransformException e) {
            System.out.println("== 错误示例 ==");
            System.out.println(e.getMessage());
        }
    }

    private static JsonNode readTree(JsonTransformer transformer, String json) {
        try {
            return transformer.objectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void print(String title, JsonNode output) {
        System.out.println("== " + title + " ==");
        System.out.println(output.toPrettyString());
    }
}
