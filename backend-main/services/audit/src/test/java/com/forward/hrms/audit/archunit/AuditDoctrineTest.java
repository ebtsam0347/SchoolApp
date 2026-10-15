package com.forward.hrms.audit.archunit;

import com.forward.hrms.commons.testsupport.archunit.DoctrineRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = {"com.forward.hrms.audit", "com.forward.hrms.commons"})
class AuditDoctrineTest {

    @ArchTest
    static final ArchRule NO_ZONE_ID = DoctrineRules.noZoneIdReferencesOutsideCommons();

    @ArchTest
    static final ArchRule NO_NEW_OBJECT_MAPPER = DoctrineRules.noNewObjectMapperOutsideCommons();
}
