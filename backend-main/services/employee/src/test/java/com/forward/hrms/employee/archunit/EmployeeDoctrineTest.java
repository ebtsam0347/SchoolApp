package com.forward.hrms.employee.archunit;

import com.forward.hrms.commons.testsupport.archunit.DoctrineRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = {"com.forward.hrms.employee", "com.forward.hrms.commons"})
class EmployeeDoctrineTest {

    @ArchTest
    static final ArchRule NO_ZONE_ID = DoctrineRules.noZoneIdReferencesOutsideCommons();

    @ArchTest
    static final ArchRule NO_NEW_OBJECT_MAPPER = DoctrineRules.noNewObjectMapperOutsideCommons();
}
