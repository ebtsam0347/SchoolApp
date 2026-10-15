package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.BelongingIssuance;
import com.forward.hrms.employee.enums.belonging.ActivationStatus;
import com.forward.hrms.employee.enums.belonging.ClearanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BelongingIssuanceRepository extends JpaRepository<BelongingIssuance, Long> {

    /**
     * Belongings that block a termination clearance: still ACTIVE (in the
     * employee's hands) and without an APPROVED clearance sign-off.
     * {@code clearance_status} is nullable, so the null case has to be
     * spelled out — {@code <> APPROVED} alone drops every un-reviewed row,
     * which is the exact set we want to block on.
     *
     * <p>{@code join fetch} is load-bearing: {@code belongingMaster} is a
     * LAZY {@code @ManyToOne} and the response needs {@code belongingName},
     * so without it this is one query plus one per row. With it the whole
     * check is a single indexed query on
     * {@code idx_belonging_issuance_employee}.
     */
    @Query("""
            select i from BelongingIssuance i
              join fetch i.belongingMaster
             where i.employeeId = :employeeId
               and i.activationStatus = :active
               and (i.clearanceStatus is null or i.clearanceStatus <> :approved)
             order by i.issuedDate asc nulls last, i.id asc
            """)
    List<BelongingIssuance> findBlockingClearance(@Param("employeeId") Long employeeId,
                                                  @Param("active") ActivationStatus active,
                                                  @Param("approved") ClearanceStatus approved);

    List<BelongingIssuance> findByEmployeeId(Long employeeId);

    List<BelongingIssuance> findByEmployeeIdAndActivationStatus(Long employeeId,ActivationStatus activationStatus);

    List<BelongingIssuance> findByClearanceStatus(ClearanceStatus clearanceStatus);

    List<BelongingIssuance> findByEmployeeIdAndClearanceStatus(Long employeeId, ClearanceStatus clearanceStatus);

    List<BelongingIssuance> findAllByOrderByEmployeeIdAsc();

    List<BelongingIssuance> findByEmployeeIdOrderByIssuedDateDesc(Long employeeId);

    List<BelongingIssuance> findByActivationStatus(ActivationStatus activationStatus);
}
