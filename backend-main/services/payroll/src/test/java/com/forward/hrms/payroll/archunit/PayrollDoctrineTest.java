package com.forward.hrms.payroll.archunit;

import com.forward.hrms.commons.testsupport.archunit.DoctrineRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Payroll runs every §5 doctrine rule because it owns money math AND
 * Kafka consumers AND DTOs. ArchUnit scans the payroll package +
 * commons-* (so the helper rules see their own implementation classes
 * and skip them via the package-residence checks they declare).
 */
@AnalyzeClasses(packages = {
        "com.forward.hrms.payroll",
        "com.forward.hrms.commons"
})
class PayrollDoctrineTest {

    @ArchTest
    static final ArchRule MONEY_DIVIDE_THROUGH_MONEY = DoctrineRules.moneyDivideMustGoThroughMoney();

    @ArchTest
    static final ArchRule NO_ZONE_ID = DoctrineRules.noZoneIdReferencesOutsideCommons();

    @ArchTest
    static final ArchRule NO_NEW_OBJECT_MAPPER = DoctrineRules.noNewObjectMapperOutsideCommons();

    @ArchTest
    static final ArchRule NO_LONG_MONEY_FIELDS = DoctrineRules.noLongMoneyFieldsInDtoOrEntity();
}
