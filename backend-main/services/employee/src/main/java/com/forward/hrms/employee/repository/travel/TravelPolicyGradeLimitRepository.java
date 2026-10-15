package com.forward.hrms.employee.repository.travel;

import com.forward.hrms.employee.entity.travel.TravelPolicyGradeLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TravelPolicyGradeLimitRepository extends JpaRepository<TravelPolicyGradeLimit, Long> {

    List<TravelPolicyGradeLimit> findByPolicyVersionId(Long policyVersionId);

    @Query("""
            select l from TravelPolicyGradeLimit l
            where l.policyVersionId = :policyVersionId
              and l.isActive = true
            """)
    List<TravelPolicyGradeLimit> findActiveByPolicyVersionId(Long policyVersionId);

    /** Matches the {@code uq_travel_policy_grade_limits_rule} unique key. */
    Optional<TravelPolicyGradeLimit> findByPolicyVersionIdAndGradeIdAndExpenseTypeCode(
            Long policyVersionId, Long gradeId, String expenseTypeCode);

    boolean existsByPolicyVersionIdAndGradeIdAndExpenseTypeCode(
            Long policyVersionId, Long gradeId, String expenseTypeCode);

    List<TravelPolicyGradeLimit> findByPolicyVersionIdAndGradeId(Long policyVersionId, Long gradeId);
}
