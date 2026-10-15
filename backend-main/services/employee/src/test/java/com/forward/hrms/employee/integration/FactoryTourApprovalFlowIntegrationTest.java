package com.forward.hrms.employee.integration;

import com.forward.hrms.commons.approval.entity.ApprovalStep;
import com.forward.hrms.commons.approval.enums.ResolverKind;
import com.forward.hrms.commons.approval.enums.StepStatus;
import com.forward.hrms.commons.approval.repository.ApprovalStepRepository;
import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.core.error.ForbiddenError;
import com.forward.hrms.commons.testsupport.containers.WithTestcontainers;
import com.forward.hrms.employee.approval.RequisitionManagerResolver;
import com.forward.hrms.employee.dto.request.CreateTourRequest;
import com.forward.hrms.employee.dto.request.RejectTourRequest;
import com.forward.hrms.employee.dto.response.FactoryTourDto;
import com.forward.hrms.employee.dto.response.FactoryToursInbox;
import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.enums.EmploymentStatus;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;
import com.forward.hrms.employee.enums.VehicleSource;
import com.forward.hrms.employee.repository.EmployeeRepository;

import com.forward.hrms.employee.service.FactoryTourServiceOld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Traces the FACTORY_TOUR_LOCAL request all the way through
 * {@code commons-approval}: submit opens the chain configured in
 * V0014__factory_tours.sql, approve/reject drives {@link FactoryTourServiceOld}
 * via {@code ApprovalService}, and the outcome handler writes the tour's
 * final status. Also proves that {@link RequisitionManagerResolver} — the
 * only {@code ManagerResolver} bean in employee-service — is currently dead
 * code for this request type, because the seeded stage is PERMISSION-kind,
 * not MANAGER-kind.
 */
@SpringBootTest
@WithTestcontainers
class FactoryTourApprovalFlowIntegrationTest {

    // DB persists across test classes (Testcontainers reuse) and
    // employees.user_id is unique — never hardcode a userId literal.
    private static final AtomicLong USER_ID_SEQ = new AtomicLong(System.nanoTime() & 0x7fffffffL);

    @Autowired
    FactoryTourServiceOld tours;
    @Autowired EmployeeRepository employees;
    @Autowired ApprovalStepRepository steps;
    @Autowired RequisitionManagerResolver managerResolver;

    @AfterEach
    void clearCaller() {
        CurrentUserHolder.clear();
    }

    @Test
    void submit_opensASinglePermissionStage_notAssignedToAnyone() {
        long requesterUserId = nextUserId();
        persistEmployee("Requester One", requesterUserId, null);
        CurrentUserHolder.set(requester(requesterUserId));

        FactoryTourDto created = tours.create(localTourRequest());
        tours.submit(created.id());

        List<ApprovalStep> chain = steps.findAllByRequestTypeAndRequestIdOrderByStageNoAsc(
                "FACTORY_TOUR_LOCAL", created.id());

        assertThat(chain).hasSize(1);
        ApprovalStep stage1 = chain.get(0);
        assertThat(stage1.getResolverKind()).isEqualTo(ResolverKind.PERMISSION);
        assertThat(stage1.getApprovePermission()).isEqualTo("employee.tours.local.approve");
        // PERMISSION stages are never assigned to a specific user — anyone
        // holding the permission can act. resolveAssignee() returns null
        // for this kind, so RequisitionManagerResolver never runs here.
        assertThat(stage1.getAssignedTo()).isNull();
        assertThat(stage1.getStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    void approverWithPermission_canApprove_tourFlipsToApproved() {
        long requesterUserId = nextUserId();
        persistEmployee("Requester Two", requesterUserId, null);
        CurrentUserHolder.set(requester(requesterUserId));
        FactoryTourDto created = tours.create(localTourRequest());
        tours.submit(created.id());

        CurrentUserHolder.set(approverWithPermission(nextUserId(), "employee.tours.local.approve"));
        FactoryTourDto approved = tours.approve(created.id());

        assertThat(approved.status()).isEqualTo(TourStatus.APPROVED);
    }

    @Test
    void callerWithoutPermission_cannotApprove() {
        long requesterUserId = nextUserId();
        persistEmployee("Requester Three", requesterUserId, null);
        CurrentUserHolder.set(requester(requesterUserId));
        FactoryTourDto created = tours.create(localTourRequest());
        tours.submit(created.id());

        CurrentUserHolder.set(approverWithPermission(nextUserId(), "some.other.permission"));

        assertThatThrownBy(() -> tours.approve(created.id()))
                .isInstanceOf(ForbiddenError.class);
    }

    @Test
    void rejection_skipsTheChain_tourFlipsToRejected() {
        long requesterUserId = nextUserId();
        persistEmployee("Requester Four", requesterUserId, null);
        CurrentUserHolder.set(requester(requesterUserId));
        FactoryTourDto created = tours.create(localTourRequest());
        tours.submit(created.id());

        CurrentUserHolder.set(approverWithPermission(nextUserId(), "employee.tours.local.approve"));
        FactoryTourDto rejected = tours.reject(created.id(), new RejectTourRequest("not needed"));

        assertThat(rejected.status()).isEqualTo(TourStatus.REJECTED);
    }

    @Test
    void pendingApprovals_showsTheTourToAnApproverHoldingThePermission_withRequesterSummary() {
        long requesterUserId = nextUserId();
        long requesterEmployeeId = persistEmployee("Requester Five", requesterUserId, null);
        CurrentUserHolder.set(requester(requesterUserId));
        FactoryTourDto created = tours.create(localTourRequest());
        tours.submit(created.id());

        CurrentUserHolder.set(approverWithPermission(nextUserId(), "employee.tours.local.approve"));
        FactoryToursInbox inbox = tours.pendingApprovals();

        assertThat(inbox.counts().total()).isGreaterThanOrEqualTo(1);
        FactoryToursInbox.Row row = inbox.factoryTours().stream()
                .filter(r -> r.request().id() == created.id())
                .findFirst().orElseThrow();
        assertThat(row.request().status()).isEqualTo(TourStatus.PENDING_APPROVAL);
        assertThat(row.request().actions()).contains("APPROVE", "REJECT");
        assertThat(row.employee()).isNotNull();
        assertThat(row.employee().id()).isEqualTo(requesterEmployeeId);
        assertThat(row.employee().fullName()).isEqualTo("Requester Five");
    }

    /**
     * FACTORY_TOUR_LOCAL never routes through MANAGER-kind stages today,
     * but the resolver it would use is exercised directly here so its
     * manager-chain math (employee -> managerId -> manager's userId) is
     * covered by something.
     */
    @Test
    void managerResolver_walksEmployeeToManagerUserId_ifAnyoneEverWiresAMANAGERStage() {
        long managerUserId = nextUserId();
        long managerEmployeeId = persistEmployee("Manager Person", managerUserId, null);
        long staffEmployeeId = persistEmployee("Staff Person", nextUserId(), managerEmployeeId);

        Long resolved = managerResolver.resolveManagerUserId(staffEmployeeId);

        assertThat(resolved).isEqualTo(managerUserId);
    }

    @Test
    void managerResolver_returnsNull_whenEmployeeHasNoManager() {
        long staffEmployeeId = persistEmployee("Orphan Staff", nextUserId(), null);

        assertThat(managerResolver.resolveManagerUserId(staffEmployeeId)).isNull();
    }

    // ---------------------------------------------------------------- helpers

    private static long nextUserId() {
        return USER_ID_SEQ.incrementAndGet();
    }

    private long persistEmployee(String fullName, Long userId, Long managerId) {
        Employee e = new Employee();
        e.setEmployeeCode("TOUR-" + System.nanoTime());
        e.setFullName(fullName);
        e.setUserId(userId);
        e.setManagerId(managerId);
        e.setJoiningDate(LocalDate.now().minusYears(1));
        e.setEmploymentStatus(EmploymentStatus.ACTIVE);
        return employees.save(e).getId();
    }

    private CreateTourRequest localTourRequest() {
        LocalDateTime start = LocalDateTime.now().plusDays(7);
        return new CreateTourRequest(
                TourType.LOCAL, start, start.plusHours(4), "Head Office", "Sialkot Plant",
                "Sialkot", null, null, VehicleSource.COMPANY_VEHICLE,
                "Car", null, "Vendor audit", null, null);
    }

    private CurrentUser requester(long userId) {
        return new CurrentUser(userId, "requester" + userId, Set.of(), Set.of());
    }

    private CurrentUser approverWithPermission(long userId, String permission) {
        return new CurrentUser(userId, "approver" + userId, Set.of(), Set.of(permission));
    }
}
