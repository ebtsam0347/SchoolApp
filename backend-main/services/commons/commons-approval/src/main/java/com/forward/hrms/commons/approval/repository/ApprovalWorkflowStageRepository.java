package com.forward.hrms.commons.approval.repository;

import com.forward.hrms.commons.approval.entity.ApprovalWorkflowStage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ApprovalWorkflowStageRepository extends JpaRepository<ApprovalWorkflowStage, Long> {

    /** The live chain for a request type, in stage order — what {@code openChain} snapshots. */
    List<ApprovalWorkflowStage> findByRequestTypeAndActiveTrueOrderByStageNoAsc(String requestType);

    /** Every active stage across all types, for the admin overview. */
    List<ApprovalWorkflowStage> findByActiveTrueOrderByRequestTypeAscStageNoAsc();

    /**
     * Hard-replace path: drop the existing chain before inserting the new
     * ordered set. Bulk delete (executes immediately) so the following
     * inserts can't collide with the old rows on {@code uq_workflow_stage_active}
     * — a per-entity deleteBy… would queue the deletes behind the inserts
     * at flush and trip the unique index.
     */
    @Modifying
    @Query("delete from ApprovalWorkflowStage s where s.requestType = :requestType")
    void deleteByRequestType(String requestType);
}
