package com.forward.hrms.bff.archunit;

import com.forward.hrms.commons.testsupport.archunit.DoctrineRules;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = {"com.forward.hrms.bff", "com.forward.hrms.commons"})
class BffDoctrineTest {

    @ArchTest
    static final ArchRule NO_ZONE_ID = DoctrineRules.noZoneIdReferencesOutsideCommons();

    @ArchTest
    static final ArchRule NO_NEW_OBJECT_MAPPER = DoctrineRules.noNewObjectMapperOutsideCommons();
}
