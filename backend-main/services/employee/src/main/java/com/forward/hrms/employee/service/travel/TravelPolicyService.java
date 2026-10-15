package com.forward.hrms.employee.service.travel;

import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyGradeLimitRequest;
import com.forward.hrms.employee.dto.request.travel.request.CreateTravelPolicyVersionRequest;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyGradeLimitResponse;
import com.forward.hrms.employee.dto.request.travel.response.TravelPolicyVersionResponse;
import com.forward.hrms.employee.entity.Grade;
import com.forward.hrms.employee.entity.travel.TravelPolicyGradeLimit;
import com.forward.hrms.employee.entity.travel.TravelPolicyVersion;
import com.forward.hrms.employee.enums.EmployeeErrorCode;
import com.forward.hrms.employee.enums.travel.TravelErrorCode;
import com.forward.hrms.employee.enums.travel.TravelPolicyVersionStatus;
import com.forward.hrms.employee.enums.travel.TravelType;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.travel.TravelPolicyGradeLimitRepository;
import com.forward.hrms.employee.repository.travel.TravelPolicyVersionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Travel policy administration: versioned policies plus their
 * grade-specific entitlement limits.
 *
 * <p>Lifecycle: {@code DRAFT -> ACTIVE -> INACTIVE -> RETIRED}. A policy
 * version and its grade limits are editable only while {@code DRAFT};
 * once activated the configuration is frozen so historical Travel
 * Requests keep resolving against the exact rules that applied to them.
 *
 * <p>Every mutation emits an audit row (catalog-admin doctrine).
 */
@Service
@RequiredArgsConstructor
public class TravelPolicyService {

    private final TravelPolicyVersionRepository policyVersions;
    private final TravelPolicyGradeLimitRepository gradeLimits;
    private final GradeRepository grades;
    private final AuditEmitter audit;

    // ── Policy versions ──────────────────────────────────────────────────────

    /**
     * Creates a {@code DRAFT} version of {@code req.policyCode()}.
     *
     * <p>{@code versionNo} is allocated as {@code max(versionNo) + 1} within
     * the policy code. That read-then-write is not serialized, so two
     * concurrent creates for the same code can compute the same number;
     * {@code uq_travel_policy_versions_code_version} rejects the loser and
     * it is reported as {@link TravelErrorCode#TRAVEL_POLICY_VERSION_DUPLICATE}
     * (409) rather than escaping as an uncontrolled 500. The insert is
     * flushed inside this method so the violation surfaces here and not at
     * commit, after the audit row has already been staged.
     */
    @Transactional
    public TravelPolicyVersionResponse createPolicyVersion(CreateTravelPolicyVersionRequest req) {
        TravelType.requireSupportedPolicyCode(req.policyCode());

        int nextVersionNo = policyVersions.findByPolicyCodeOrderByVersionNoDesc(req.policyCode()).stream()
                .findFirst()
                .map(v -> v.getVersionNo() + 1)
                .orElse(1);

        TravelPolicyVersion v = new TravelPolicyVersion();
        v.setPolicyCode(req.policyCode());
        v.setPolicyName(req.policyName());
        v.setVersionNo(nextVersionNo);
        v.setEffectiveFrom(req.effectiveFrom());
        v.setEffectiveTo(req.effectiveTo());
        v.setStatus(TravelPolicyVersionStatus.DRAFT);
        v.setDescription(req.description());
        try {
            policyVersions.saveAndFlush(v);
        } catch (DataIntegrityViolationException ex) {
            // uq_travel_policy_versions_code_version is the only unique key an
            // inserted DRAFT row can violate: uq_travel_policy_versions_one_active
            // (V0624) is scoped to status = 'ACTIVE', and every check constraint on
            // the table is already covered by bean validation on the request record.
            throw new ConflictError(TravelErrorCode.TRAVEL_POLICY_VERSION_DUPLICATE,
                    "version " + nextVersionNo + " of policy code '" + req.policyCode()
                            + "' already exists; retry the create");
        }

        audit.emit("travel_policy_version", String.valueOf(v.getId()), "created",
                null, snapshot(v), false);
        return TravelPolicyVersionResponse.of(v);
    }

    @Transactional(readOnly = true)
    public TravelPolicyVersionResponse getPolicyVersion(Long id) {
        return TravelPolicyVersionResponse.of(requireVersion(id));
    }

    @Transactional(readOnly = true)
    public List<TravelPolicyVersionResponse> listVersionsOfPolicy(String policyCode) {
        return policyVersions.findByPolicyCodeOrderByVersionNoDesc(policyCode).stream()
                .map(TravelPolicyVersionResponse::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<TravelPolicyVersionResponse> searchPolicyVersions(String policyCode,
                                                                  TravelPolicyVersionStatus status,
                                                                  Pageable pageable) {
        return policyVersions.search(policyCode, status, pageable).map(TravelPolicyVersionResponse::of);
    }

    /**
     * Editable only while DRAFT. {@code policyCode} is immutable — it
     * anchors the version sequence, so any value the payload carries for it
     * is ignored rather than applied; only {@code policyName},
     * {@code effectiveFrom}, {@code effectiveTo} and {@code description}
     * move. Unchanged by the create-time policy-code guard: an existing
     * version's code is never re-validated, so historical families keep
     * loading and editing normally.
     */
    @Transactional
    public TravelPolicyVersionResponse updatePolicyVersion(Long id, CreateTravelPolicyVersionRequest req) {
        TravelPolicyVersion v = requireVersion(id);
        requireDraft(v);
        Map<String, Object> before = snapshot(v);

        v.setPolicyName(req.policyName());
        v.setEffectiveFrom(req.effectiveFrom());
        v.setEffectiveTo(req.effectiveTo());
        v.setDescription(req.description());

        audit.emit("travel_policy_version", String.valueOf(v.getId()), "updated",
                before, snapshot(v), false);
        return TravelPolicyVersionResponse.of(v);
    }

    /**
     * DRAFT -&gt; ACTIVE. Refused when another version of the same
     * {@code policyCode} is already ACTIVE: a policy code carries at most
     * one ACTIVE version, and the replaced version must be deactivated or
     * retired explicitly first. The previous ACTIVE version is never
     * auto-mutated, and no "activated" audit row is emitted on refusal.
     */
    @Transactional
    public TravelPolicyVersionResponse activatePolicyVersion(Long id) {
        TravelPolicyVersion v = requireVersion(id);
        requireStatus(v, TravelPolicyVersionStatus.DRAFT);
        requireNoOtherActiveVersion(v);
        return applyTransition(v, TravelPolicyVersionStatus.ACTIVE, "activated");
    }

    @Transactional
    public TravelPolicyVersionResponse deactivatePolicyVersion(Long id) {
        return transition(id, TravelPolicyVersionStatus.ACTIVE, TravelPolicyVersionStatus.INACTIVE, "deactivated");
    }

    @Transactional
    public TravelPolicyVersionResponse retirePolicyVersion(Long id) {
        TravelPolicyVersion v = requireVersion(id);
        if (v.getStatus() == TravelPolicyVersionStatus.DRAFT
                || v.getStatus() == TravelPolicyVersionStatus.RETIRED) {
            throw new ConflictError(TravelErrorCode.TRAVEL_POLICY_VERSION_INVALID_STATE,
                    "cannot retire a policy version in state " + v.getStatus());
        }
        Map<String, Object> before = snapshot(v);
        v.setStatus(TravelPolicyVersionStatus.RETIRED);
        audit.emit("travel_policy_version", String.valueOf(v.getId()), "retired",
                before, snapshot(v), false);
        return TravelPolicyVersionResponse.of(v);
    }

    // ── Grade limits ─────────────────────────────────────────────────────────
    @Transactional
    public TravelPolicyGradeLimitResponse addGradeLimit(Long policyVersionId,
                                                        CreateTravelPolicyGradeLimitRequest req) {
        TravelPolicyVersion version = requireVersion(policyVersionId);
        requireDraft(version);
        Grade grade = requireGrade(req.gradeCode());

        if (gradeLimits.existsByPolicyVersionIdAndGradeIdAndExpenseTypeCode(
                policyVersionId, grade.getId(), req.expenseTypeCode())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_GRADE_LIMIT_DUPLICATE,
                    "a limit for this grade and expense type already exists in this policy version");
        }

        TravelPolicyGradeLimit gl = new TravelPolicyGradeLimit();
        gl.setPolicyVersionId(policyVersionId);
        gl.setGradeId(grade.getId());
        gl.setGradeCode(grade.getCode());
        gl.setGradeName(grade.getName());
        gl.setExpenseTypeCode(req.expenseTypeCode());
        gl.setLimitType(req.limitType());
        gl.setLimitAmount(req.limitAmount());
        gl.setCurrencyCode(req.currencyCode());
        gl.setIsActive(req.isActive());
        gl.setRemarks(req.remarks());
        gradeLimits.save(gl);

        audit.emit("travel_grade_limit", String.valueOf(gl.getId()), "created",
                null, snapshot(gl), false);
        return TravelPolicyGradeLimitResponse.of(gl, version);
    }

    @Transactional
    public TravelPolicyGradeLimitResponse updateGradeLimit(Long id,
                                                           CreateTravelPolicyGradeLimitRequest req) {
        TravelPolicyGradeLimit gl = requireGradeLimit(id);
        TravelPolicyVersion version = requireVersion(gl.getPolicyVersionId());
        requireDraft(version);
        Grade grade = requireGrade(req.gradeCode());
        Map<String, Object> before = snapshot(gl);

        boolean keyChanged = !grade.getId().equals(gl.getGradeId())
                || !req.expenseTypeCode().equals(gl.getExpenseTypeCode());
        if (keyChanged && gradeLimits.existsByPolicyVersionIdAndGradeIdAndExpenseTypeCode(
                version.getId(), grade.getId(), req.expenseTypeCode())) {
            throw new ConflictError(TravelErrorCode.TRAVEL_GRADE_LIMIT_DUPLICATE,
                    "a limit for this grade and expense type already exists in this policy version");
        }

        gl.setGradeId(grade.getId());
        gl.setGradeCode(grade.getCode());
        gl.setGradeName(grade.getName());
        gl.setExpenseTypeCode(req.expenseTypeCode());
        gl.setLimitType(req.limitType());
        gl.setLimitAmount(req.limitAmount());
        gl.setCurrencyCode(req.currencyCode());
        gl.setIsActive(req.isActive());
        gl.setRemarks(req.remarks());

        audit.emit("travel_grade_limit", String.valueOf(gl.getId()), "updated",
                before, snapshot(gl), false);
        return TravelPolicyGradeLimitResponse.of(gl, version);
    }

    @Transactional
    public void deleteGradeLimit(Long id) {
        TravelPolicyGradeLimit gl = requireGradeLimit(id);
        requireDraft(requireVersion(gl.getPolicyVersionId()));
        Map<String, Object> before = snapshot(gl);
        gradeLimits.delete(gl);
        audit.emit("travel_grade_limit", String.valueOf(id), "deleted", before, null, false);
    }

    @Transactional(readOnly = true)
    public List<TravelPolicyGradeLimitResponse> listGradeLimits(Long policyVersionId) {
        TravelPolicyVersion version = requireVersion(policyVersionId);
        return gradeLimits.findByPolicyVersionId(policyVersionId).stream()
                .map(gl -> TravelPolicyGradeLimitResponse.of(gl, version))
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TravelPolicyVersionResponse transition(Long id,
                                                   TravelPolicyVersionStatus from,
                                                   TravelPolicyVersionStatus to,
                                                   String action) {
        TravelPolicyVersion v = requireVersion(id);
        requireStatus(v, from);
        return applyTransition(v, to, action);
    }

    private static void requireStatus(TravelPolicyVersion v, TravelPolicyVersionStatus expected) {
        if (v.getStatus() != expected) {
            throw new ConflictError(TravelErrorCode.TRAVEL_POLICY_VERSION_INVALID_STATE,
                    "policy version is " + v.getStatus() + ", expected " + expected);
        }
    }

    /** Flips the status and audits. Every guard must already have passed. */
    private TravelPolicyVersionResponse applyTransition(TravelPolicyVersion v,
                                                        TravelPolicyVersionStatus to,
                                                        String action) {
        Map<String, Object> before = snapshot(v);
        v.setStatus(to);
        audit.emit("travel_policy_version", String.valueOf(v.getId()), action,
                before, snapshot(v), false);
        return TravelPolicyVersionResponse.of(v);
    }

    /**
     * At most one ACTIVE version per {@code policyCode}. DRAFT, INACTIVE and
     * RETIRED siblings are unconstrained, and the rule is per policy code —
     * {@code LOCAL_FACTORY_TRAVEL} and {@code INTERNATIONAL_FACTORY_TRAVEL} may
     * each hold their own ACTIVE version.
     */
    private void requireNoOtherActiveVersion(TravelPolicyVersion v) {
        List<TravelPolicyVersion> active = policyVersions
                .findByPolicyCodeAndStatus(v.getPolicyCode(), TravelPolicyVersionStatus.ACTIVE).stream()
                .filter(other -> !other.getId().equals(v.getId()))
                .toList();
        if (!active.isEmpty()) {
            String versions = active.stream().map(o -> "v" + o.getVersionNo()).collect(Collectors.joining(", "));
            throw new ConflictError(TravelErrorCode.TRAVEL_POLICY_VERSION_ALREADY_ACTIVE,
                    "an ACTIVE version already exists for policy code '" + v.getPolicyCode()
                            + "' (" + versions + "); deactivate or retire it before activating another");
        }
    }

    private TravelPolicyVersion requireVersion(Long id) {
        return policyVersions.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_POLICY_VERSION_NOT_FOUND, "travel policy version not found"));
    }

    private TravelPolicyGradeLimit requireGradeLimit(Long id) {
        return gradeLimits.findById(id)
                .orElseThrow(() -> new NotFoundError(
                        TravelErrorCode.TRAVEL_GRADE_LIMIT_NOT_FOUND, "travel policy grade limit not found"));
    }

    private Grade requireGrade(String code) {
        return grades.findByCode(code)
                .orElseThrow(() -> new NotFoundError(
                        EmployeeErrorCode.GRADE_NOT_FOUND, "grade not found: " + code));
    }

    private void requireDraft(TravelPolicyVersion v) {
        if (v.getStatus() != TravelPolicyVersionStatus.DRAFT) {
            throw new ConflictError(TravelErrorCode.TRAVEL_POLICY_VERSION_NOT_DRAFT,
                    "policy version is " + v.getStatus() + "; only DRAFT versions can be edited");
        }
    }

    private static Map<String, Object> snapshot(TravelPolicyVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyCode", v.getPolicyCode());
        m.put("policyName", v.getPolicyName());
        m.put("versionNo", v.getVersionNo());
        m.put("effectiveFrom", String.valueOf(v.getEffectiveFrom()));
        m.put("effectiveTo", String.valueOf(v.getEffectiveTo()));
        m.put("status", String.valueOf(v.getStatus()));
        m.put("description", v.getDescription());
        return m;
    }

    private static Map<String, Object> snapshot(TravelPolicyGradeLimit gl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("policyVersionId", gl.getPolicyVersionId());
        m.put("gradeCode", gl.getGradeCode());
        m.put("expenseTypeCode", gl.getExpenseTypeCode());
        m.put("limitType", String.valueOf(gl.getLimitType()));
        m.put("limitAmount", String.valueOf(gl.getLimitAmount()));
        m.put("currencyCode", gl.getCurrencyCode());
        m.put("isActive", gl.getIsActive());
        m.put("remarks", gl.getRemarks());
        return m;
    }
}
