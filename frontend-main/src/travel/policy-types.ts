// Travel Policy Administration — versioned travel policies, exposed at
// gateway path /travel/policies. Reads require travel.read; all mutations
// require travel.admin. Lifecycle: DRAFT -> ACTIVE -> INACTIVE -> RETIRED;
// a version is editable only while DRAFT.

export type PolicyVersionStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'RETIRED' | (string & {});

export type PolicyVersionResponse = {
  id: number;
  policyCode: string;
  policyName?: string | null;
  versionNo?: number | null;
  status?: PolicyVersionStatus | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  description?: string | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

/** Both filters optional — GET /travel/policies. */
export type PolicyVersionSearchParams = {
  policyCode?: string;
  status?: PolicyVersionStatus;
};

/** POST /travel/policies — starts the new version in DRAFT; version number is assigned automatically per policy code. */
export type CreatePolicyVersionRequest = {
  policyCode: string;
  policyName: string;
  effectiveFrom: string;
  /** Omitted / null = open-ended: the version applies until further notice. */
  effectiveTo?: string | null;
  description?: string;
};

/** Grade-specific entitlement limit on a policy version, e.g. a daily PER_DIEM cap. */
export type GradeLimitResponse = {
  id: number;
  policyVersionId: number;
  policyCode?: string | null;
  policyVersionNo?: number | null;
  gradeId: number;
  gradeCode?: string | null;
  gradeName?: string | null;
  expenseTypeCode: string;
  limitType: string;
  limitAmount: number;
  currencyCode: string;
  isActive?: boolean | null;
  remarks?: string | null;
  createdAt?: string;
  updatedAt?: string;
  version?: number;
};

/**
 * POST /travel/policies/{policyVersionId}/grade-limits — one limit per
 * grade + expense type per version. Only allowed while the version is DRAFT.
 */
export type CreateGradeLimitRequest = {
  gradeCode: string;
  expenseTypeCode: string;
  limitType: string;
  limitAmount: number;
  currencyCode: string;
  isActive?: boolean;
  remarks?: string;
};

/**
 * PATCH /travel/policies/grade-limits/{id} — update a grade limit. Only
 * allowed while the parent version is DRAFT. Addresses the grade by `gradeCode`.
 */
export type UpdateGradeLimitRequest = {
  gradeCode: string;
  expenseTypeCode: string;
  limitType: string;
  limitAmount: number;
  currencyCode: string;
  isActive?: boolean;
  remarks?: string;
};
