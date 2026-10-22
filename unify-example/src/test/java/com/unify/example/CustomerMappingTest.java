package com.unify.example;

import com.unify.sdk.ErrorCode;
import com.unify.sdk.JsonTransformer;
import com.unify.sdk.TransformException;
import com.unify.sdk.testkit.MappingTester;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerMappingTest {

    private final JsonTransformer transformer = CustomerMappings.createTransformer();
    private final MappingTester tester = MappingTester.of(transformer);

    @Test
    void registersAllMappingFiles() {
        assertEquals(Set.of(CustomerMappings.LEGACY_CUSTOMER, CustomerMappings.MEMBER_CUSTOMER),
                transformer.mappingNames());
        assertTrue(transformer.functionNames().contains("maskPhone"));
    }

    @Test
    void legacyCustomer() {
        tester.verifyByName(CustomerMappings.LEGACY_CUSTOMER,
                "samples/legacy-customer.json", "expected/legacy-customer.json");
    }

    @Test
    void legacyCustomerWithMissingFields() {
        tester.verifyByName(CustomerMappings.LEGACY_CUSTOMER,
                "samples/legacy-customer-sparse.json", "expected/legacy-customer-sparse.json");
    }

    @Test
    void memberCustomer() {
        tester.verifyByName(CustomerMappings.MEMBER_CUSTOMER,
                "samples/member-customer.json", "expected/member-customer.json");
    }

    @Test
    void memberCustomerOmitsNullFields() {
        tester.verifyByName(CustomerMappings.MEMBER_CUSTOMER,
                "samples/member-customer-sparse.json", "expected/member-customer-sparse.json");
    }

    @Test
    void mappingFilesCanAlsoBeUsedAsAdHocDsl() {
        tester.verify("mappings/legacy-customer.json",
                "samples/legacy-customer.json", "expected/legacy-customer.json");
    }

    @Test
    void reportsWhereSourceDataIsUnexpected() {
        TransformException e = assertThrows(TransformException.class, () -> transformer.transformByName(
                CustomerMappings.LEGACY_CUSTOMER, "{\"CUST_ID\": 1, \"REG_TIME\": \"2023/05/06\"}"));

        assertEquals(ErrorCode.FUNCTION_FAILED, e.errorCode());
        assertEquals(CustomerMappings.LEGACY_CUSTOMER, e.mappingName());
        assertEquals("/mapping/registeredAt", e.location());
    }
}
