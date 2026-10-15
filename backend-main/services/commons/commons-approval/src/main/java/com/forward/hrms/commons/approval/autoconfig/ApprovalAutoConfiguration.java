package com.forward.hrms.commons.approval.autoconfig;

import com.forward.hrms.commons.approval.controller.ApprovalDelegationController;
import com.forward.hrms.commons.approval.controller.ApprovalWorkflowController;
import com.forward.hrms.commons.approval.enums.ApprovalErrorCode;
import com.forward.hrms.commons.approval.repository.ApprovalDelegationRepository;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.approval.repository.ApprovalWorkflowStageRepository;
import com.forward.hrms.commons.approval.service.ApprovalDelegationService;
import com.forward.hrms.commons.approval.service.ApprovalInboxService;
import com.forward.hrms.commons.approval.service.ApprovalService;
import com.forward.hrms.commons.approval.service.ApprovalWorkflowService;
import com.forward.hrms.commons.approval.spi.ApprovalOutcomeHandler;
import com.forward.hrms.commons.approval.spi.DepartmentHeadResolver;
import com.forward.hrms.commons.approval.spi.GradeResolver;
import com.forward.hrms.commons.approval.spi.ManagerResolver;
import com.forward.hrms.commons.approval.spi.RequestTypeRegistration;
import com.forward.hrms.commons.core.error.ErrorCode;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.Collections;
import java.util.List;

/**
 * Wires the approval engine. Activates when JPA is on the classpath
 * and the consuming service has a {@link ManagerResolver} bean.
 *
 * <p>Services must provide:
 * <ul>
 *   <li>A {@link ManagerResolver} bean — resolves MANAGER-kind stages</li>
 *   <li>One or more {@link ApprovalOutcomeHandler} beans — handle post-decision effects</li>
 *   <li>A {@link RequestTypeRegistration} bean — declares available types</li>
 * </ul>
 *
 * <p>{@link DepartmentHeadResolver} and {@link GradeResolver} beans are
 * optional — services that never configure a {@code DEPARTMENT_HEAD} or
 * {@code GRADE} stage get a no-op default and don't need to implement them.
 *
 * <p>Error codes default to {@link ApprovalErrorCode}. Services can override
 * individual codes by providing named {@code @Bean ErrorCode} instances.
 *
 * <p>Permission strings are bound from {@code hrms.approval.permissions.*} so
 * each service supplies its own namespace rather than being pinned to attendance.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.jpa.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
})
@ConditionalOnClass(jakarta.persistence.Entity.class)
public class ApprovalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApprovalPermissions approvalPermissions(
            @Value("${hrms.approval.permissions.read:}") String read,
            @Value("${hrms.approval.permissions.write:}") String write,
            @Value("${hrms.approval.permissions.admin:}") String admin,
            @Value("${hrms.approval.permissions.override:}") String override) {
        return new ApprovalPermissions(read, write, admin, override);
    }

    @Bean
    @ConditionalOnMissingBean
    public DepartmentHeadResolver departmentHeadResolver() {
        return departmentCode -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public GradeResolver gradeResolver() {
        return userId -> null;
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalService approvalService(ApprovalStepRepository steps,
                                           ApprovalWorkflowStageRepository workflowStages,
                                           ApprovalDelegationRepository delegationRepository,
                                           ManagerResolver managerResolver,
                                           DepartmentHeadResolver departmentHeadResolver,
                                           GradeResolver gradeResolver,
                                           AuditEmitter audit,
                                           ObjectProvider<List<ApprovalOutcomeHandler>> outcomeHandlers,
                                           ApprovalPermissions permissions) {
        return new ApprovalService(
                steps, workflowStages, delegationRepository,
                managerResolver, departmentHeadResolver, gradeResolver, audit, outcomeHandlers,
                ApprovalErrorCode.WORKFLOW_NOT_CONFIGURED,
                ApprovalErrorCode.NO_PENDING_STAGE,
                ApprovalErrorCode.NOT_AUTHORIZED,
                permissions.override());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalWorkflowService approvalWorkflowService(
            ApprovalWorkflowStageRepository workflowStages,
            AuditEmitter audit,
            ObjectProvider<List<RequestTypeRegistration>> registrations) {
        List<RequestTypeRegistration> regs = registrations.getIfAvailable();
        return new ApprovalWorkflowService(
                workflowStages, audit,
                regs != null ? regs : Collections.emptyList(),
                ApprovalErrorCode.WORKFLOW_STAGE_INVALID);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalDelegationService approvalDelegationService(
            ApprovalDelegationRepository delegationRepository,
            AuditEmitter audit,
            ApprovalPermissions permissions) {
        return new ApprovalDelegationService(
                delegationRepository, audit,
                ApprovalErrorCode.DELEGATION_NOT_FOUND,
                ApprovalErrorCode.DELEGATION_OVERLAP,
                ApprovalErrorCode.DELEGATION_INVALID,
                ApprovalErrorCode.NOT_AUTHORIZED,
                permissions.override());
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalInboxService approvalInboxService(ApprovalStepRepository steps,
                                                     ApprovalDelegationRepository delegationRepository,
                                                     GradeResolver gradeResolver) {
        return new ApprovalInboxService(steps, delegationRepository, gradeResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalWorkflowController approvalWorkflowController(ApprovalWorkflowService workflows,
                                                                  ApprovalPermissions permissions) {
        return new ApprovalWorkflowController(workflows, permissions);
    }

    @Bean
    @ConditionalOnMissingBean
    public ApprovalDelegationController approvalDelegationController(ApprovalDelegationService delegations,
                                                                      ApprovalPermissions permissions) {
        return new ApprovalDelegationController(delegations, permissions);
    }
}
