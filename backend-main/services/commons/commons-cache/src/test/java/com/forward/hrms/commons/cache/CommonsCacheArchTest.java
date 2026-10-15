package com.forward.hrms.commons.cache;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * commons-cache must stay reverse-dep-clean: it depends on
 * commons-* and the shared {@code api} DTO module, never on any
 * service's internals (entity / repository / service / controller /
 * client / config / events). If this rule starts failing, the cache is
 * reaching into a service it shouldn't know about — promote whatever
 * shape it needs into the api module instead.
 */
@AnalyzeClasses(packages = "com.forward.hrms.commons.cache")
class CommonsCacheArchTest {

    private static final String[] FORBIDDEN_SERVICE_INTERNALS = {
            "com.forward.hrms.auth.entity..",
            "com.forward.hrms.auth.repository..",
            "com.forward.hrms.auth.service..",
            "com.forward.hrms.auth.controller..",
            "com.forward.hrms.auth.config..",
            "com.forward.hrms.auth.events..",
            "com.forward.hrms.employee.entity..",
            "com.forward.hrms.employee.repository..",
            "com.forward.hrms.employee.service..",
            "com.forward.hrms.employee.controller..",
            "com.forward.hrms.employee.config..",
            "com.forward.hrms.employee.events..",
            "com.forward.hrms.attendance..",
            "com.forward.hrms.payroll..",
            "com.forward.hrms.audit..",
            "com.forward.hrms.notification..",
            "com.forward.hrms.chat..",
            "com.forward.hrms.gateway..",
            "com.forward.hrms.bff..",
    };

    @ArchTest
    static final ArchRule NO_SERVICE_INTERNALS = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(FORBIDDEN_SERVICE_INTERNALS);
}
