// Typed mirror of the employee + bff openapi specs. Field optionality
// matches the wire shape — all DTO properties are nullable in the JSON, so
// every field is `?` here. Components (PersonCell, StatusPill, etc.) are
// the place to deal with absent values, not the network type.
//
// Sources:
//   docs/services/employee.openapi.json (live :8080/v3/api-docs/employee)
//   docs/services/bff.openapi.json      (live :8080/v3/api-docs/bff)

import type { StatusPillTone } from '@/components';

/* ------------------------------------------------------------------ */
/* Enums                                                              */
/* ------------------------------------------------------------------ */

export type EmploymentStatus =
  | 'ACTIVE'
  | 'ON_NOTICE'
  | 'ON_LEAVE'
  | 'SUSPENDED'
  | 'TERMINATED';

export type EmploymentType = 'PERMANENT' | 'CONTRACT' | 'INTERN';

export type ContractType = 'PROBATION' | 'PERMANENT' | 'FIXED_TERM';

export type ContractStatus = 'ACTIVE' | 'EXPIRED' | 'TERMINATED';

export type SuspensionStatus = 'ACTIVE' | 'LIFTED';

export type ExitType = 'RESIGNATION' | 'TERMINATION' | 'RETIREMENT';

export type ExitStatus = 'PENDING' | 'COMPLETED' | 'WITHDRAWN';

export type DocumentType = 'CNIC' | 'DEGREE' | 'OFFER_LETTER' | 'OTHER';

export type SkillCategory = 'TECH' | 'LANGUAGE' | 'TRADE' | 'SPORT' | 'OTHER';

export type QualificationLevel =
  | 'MATRIC'
  | 'INTERMEDIATE'
  | 'DIPLOMA'
  | 'BACHELORS'
  | 'MASTERS'
  | 'MPHIL'
  | 'PHD'
  | 'OTHER';

export type DisciplinaryLevel =
  | 'FIRST_WARNING'
  | 'SECOND_WARNING'
  | 'FINAL_WARNING';

export type DisciplinaryCategory =
  | 'ATTENDANCE'
  | 'MISCONDUCT'
  | 'PERFORMANCE'
  | 'INSUBORDINATION'
  | 'POLICY_VIOLATION'
  | 'OTHER';

export type DisciplinaryStatus = 'ISSUED' | 'CLOSED';
export type DisciplinaryDecision = 'UPHELD' | 'WITHDRAWN';

export type ProficiencyLevelEnum =
  | 'BEGINNER'
  | 'INTERMEDIATE'
  | 'ADVANCED'
  | 'EXPERT';

export type DisabilitySeverity = 'MILD' | 'MODERATE' | 'SEVERE';

export type EssProvisioningStatus =
  | 'NOT_REQUESTED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED';

export type SagaStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export type HistoryChangeReason =
  | 'ONBOARD'
  | 'TRANSFER'
  | 'PROMOTION'
  | 'DEMOTION'
  | 'DEPARTMENT_CHANGE'
  | 'MANAGER_CHANGE'
  | 'SECTION_CHANGE'
  | 'EXIT';

/* ------------------------------------------------------------------ */
/* Tone maps — kept here so every consumer (pills, hero accents, row   */
/* highlights) reads the same colour for the same status.              */
/* ------------------------------------------------------------------ */

export const EMPLOYMENT_STATUS_TONE: Record<EmploymentStatus, StatusPillTone> = {
  ACTIVE: 'success',
  ON_NOTICE: 'warning',
  ON_LEAVE: 'info',
  SUSPENDED: 'error',
  TERMINATED: 'neutral',
};

export const EMPLOYMENT_STATUS_LABEL: Record<EmploymentStatus, string> = {
  ACTIVE: 'Active',
  ON_NOTICE: 'On notice',
  ON_LEAVE: 'On leave',
  SUSPENDED: 'Suspended',
  TERMINATED: 'Terminated',
};

export const EMPLOYMENT_TYPE_LABEL: Record<EmploymentType, string> = {
  PERMANENT: 'Permanent',
  CONTRACT: 'Contract',
  INTERN: 'Intern',
};

export const CONTRACT_TYPE_LABEL: Record<ContractType, string> = {
  PROBATION: 'Probation',
  PERMANENT: 'Permanent',
  FIXED_TERM: 'Fixed term',
};

export const EXIT_TYPE_LABEL: Record<ExitType, string> = {
  RESIGNATION: 'Resignation',
  TERMINATION: 'Termination',
  RETIREMENT: 'Retirement',
};

export const EXIT_STATUS_TONE: Record<ExitStatus, StatusPillTone> = {
  PENDING: 'warning',
  COMPLETED: 'neutral',
  WITHDRAWN: 'neutral',
};

export const SUSPENSION_STATUS_TONE: Record<SuspensionStatus, StatusPillTone> = {
  ACTIVE: 'error',
  LIFTED: 'neutral',
};

export const DISCIPLINARY_CATEGORY_LABEL: Record<DisciplinaryCategory, string> = {
  ATTENDANCE: 'Attendance',
  MISCONDUCT: 'Misconduct',
  PERFORMANCE: 'Performance',
  INSUBORDINATION: 'Insubordination',
  POLICY_VIOLATION: 'Policy violation',
  OTHER: 'Other',
};

/* ------------------------------------------------------------------ */
/* Catalog DTOs                                                       */
/* ------------------------------------------------------------------ */

export type DepartmentDto = {
  id?: number;
  code?: string;
  name?: string;
  headId?: number;
  active?: boolean;
  /** True for departments on the production floor (drives canteen rules
   *  and other production-specific policy switches). */
  isProduction?: boolean;
};

export type ReligionDto = {
  id?: number;
  name?: string;
  active?: boolean;
};

export type BelongingMasterDto = {
  id?: number;
  belongingName?: string;
  authorizedEmployeeId?: number;
  active?: boolean;
}

export type offCyclePaymentDto = {
  id?: number;
  name?: string;
  description?: string;
  active?: boolean;
};

export type SectionDto = {
  id?: number;
  code?: string;
  name?: string;
  departmentId?: number;
  departmentCode?: string;
  headId?: number;
  active?: boolean;
};

export type DesignationDto = {
  id?: number;
  code?: string;
  title?: string;
  /** DIRECT (production-line) vs INDIRECT (office / admin). Null when the distinction doesn't apply. */
  category?: 'DIRECT' | 'INDIRECT' | null;
  active?: boolean;
};

export type GradeType = 'MANAGEMENT' | 'SUPERVISOR' | 'LABOUR';

export type GradeDto = {
  id?: number;
  code?: string;
  name?: string;
  level?: number;
  description?: string;
  displayOrder?: number;
  active?: boolean;
  /** Role bucket. Drives payroll's canteen rule
   *  (management → weekly, supervisor/labour → per-day) and other
   *  grade-type policy switches. */
  gradeType?: GradeType;
};

export type ProvinceDto = {
  id?: number;
  code?: string;
  name?: string;
  active?: boolean;
};

export type CityDto = {
  id?: number;
  code?: string;
  name?: string;
  provinceId?: number;
  provinceCode?: string;
  displayOrder?: number;
  active?: boolean;
};

export type SkillDto = {
  id?: number;
  code?: string;
  name?: string;
  category?: SkillCategory;
  active?: boolean;
};

export type QualificationDto = {
  id?: number;
  code?: string;
  name?: string;
  level?: QualificationLevel;
  displayOrder?: number;
  active?: boolean;
};

export type DisabilityDto = {
  id: number;
  code: string;
  name: string;
  active: boolean;
};

/* ------------------------------------------------------------------ */
/* Core DTOs                                                          */
/* ------------------------------------------------------------------ */

export type ActiveProbation = {
  contractId?: number;
  endDate?: string;
  daysRemaining?: number;
  isExtended?: boolean;
};

export type EmployeeDto = {
  id?: number;
  userId?: number;
  employeeCode?: string;
  fullName?: string;
  cnic?: string;
  cnicIssueDate?: string;
  cnicExpiryDate?: string;
  ntn?: string;
  insuranceNumber?: string;
  sscNumber?: string;
  eobiNumber?: string;
  dateOfBirth?: string;
  gender?: string;
  maritalStatus?: string;
  fatherName?: string;
  motherName?: string;
  spouseName?: string;
  religion?: string;
  bloodGroup?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  permanentAddress?: string;
  currentAddress?: string;
  cityId?: number;
  country?: string;
  joiningDate?: string;
  confirmationDate?: string;
  employmentStatus?: EmploymentStatus;
  employmentType?: EmploymentType;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  managerId?: number;
  profilePictureKey?: string;
  profilePictureUrl?: string;
  hasEss?: boolean;
  /**
   * ESS provisioning state. On mutation responses reflects the linked-user
   * check (COMPLETED / NOT_REQUESTED). On read paths the assembler looks up
   * the onboarding saga row to surface RUNNING / FAILED.
   */
  essProvisioningStatus?: EssProvisioningStatus;
  directReportsCount?: number;
  activeProbation?: ActiveProbation;
  createdAt?: string;
  updatedAt?: string;
};

/**
 * Response from POST /employees. `sagaId` is set when ESS provisioning was
 * requested — the FE polls GET /sagas/{id} for live RUNNING / COMPLETED /
 * FAILED status to render the SagaProgress screen on the wizard.
 */
export type OnboardEmployeeResponse = {
  employee?: EmployeeDto;
  sagaId?: number;
};

/** Status snapshot of a durable saga row from GET /sagas/{id}. */
export type SagaResponse = {
  id?: number;
  sagaName?: string;
  correlationKey?: string;
  status?: SagaStatus;
  currentStep?: string;
  stepIndex?: number;
  attemptCount?: number;
  nextAttemptAt?: string;
  lastError?: string;
  createdAt?: string;
  updatedAt?: string;
  createdBy?: string;
};

/**
 * Per-saga-name rollup row from GET /sagas/health. `failureRate24h` is 24
 * hourly buckets (newest last). `status` is the rollup tone — the backend
 * returns one of "OK" | "DEGRADED" | "INCIDENT"; treat anything else as
 * UNKNOWN at the call site.
 */
export type SagaHealthPerName = {
  name: string;
  status: string;
  failureRate24h: number[];
};

/** Response from GET /sagas/health — rollup counters + per-saga breakdown. */
export type SagaHealthSnapshot = {
  running: number;
  failed24h: number;
  perName: SagaHealthPerName[];
};

export type EmploymentContractDto = {
  id?: number;
  employeeId?: number;
  contractType?: ContractType;
  startDate?: string;
  endDate?: string;
  status?: ContractStatus;
  probationDays?: number;
  extended?: boolean;
  documentKey?: string;
  documentUrl?: string;
};

export type SuspensionDto = {
  id?: number;
  employeeId?: number;
  startDate?: string;
  endDate?: string;
  reason?: string;
  withPay?: boolean;
  status?: SuspensionStatus;
  liftedOn?: string;
  liftedReason?: string;
};

export type ExitDto = {
  id?: number;
  employeeId?: number;
  exitType?: ExitType;
  noticeStartDate?: string;
  lastWorkingDay?: string;
  reason?: string;
  notes?: string;
  status?: ExitStatus;
  completedAt?: string;
  withdrawnAt?: string;
  withdrawnReason?: string;
};

export type EmployeeDocumentDto = {
  id?: number;
  employeeId?: number;
  docType?: DocumentType;
  title?: string;
  objectKey?: string;
  contentType?: string;
  sizeBytes?: number;
  downloadUrl?: string;
};

export type EmployeeSkillDto = {
  id?: number;
  employeeId?: number;
  skillId?: number;
  skillCode?: string;
  skillName?: string;
  proficiency?: ProficiencyLevelEnum;
  years?: number;
  notes?: string;
};

export type EmployeeQualificationDto = {
  id?: number;
  employeeId?: number;
  qualificationId?: number;
  qualificationCode?: string;
  qualificationName?: string;
  institution?: string;
  boardOrUniversity?: string;
  yearOfCompletion?: number;
  grade?: string;
  documentKey?: string;
  documentUrl?: string;
};

export type EmployeeCertificationDto = {
  id?: number;
  employeeId?: number;
  employeeCode?: string;
  fullName?: string;
  name?: string;
  issuingBody?: string;
  issueDate?: string;
  expiryDate?: string;
  credentialId?: string;
  documentKey?: string;
  documentUrl?: string;
};

export type EmployeePriorExperienceDto = {
  id?: number;
  employeeId?: number;
  employerName?: string;
  designation?: string;
  startDate?: string;
  endDate?: string;
  responsibilities?: string;
  reasonForLeaving?: string;
};

export type EmergencyContactDto = {
  id?: number;
  employeeId?: number;
  name?: string;
  relation?: string;
  phone?: string;
  primary?: boolean;
};

export type DisciplinaryActionDto = {
  id?: number;
  employeeId?: number;
  level?: DisciplinaryLevel;
  category?: DisciplinaryCategory;
  incidentDate?: string;
  issuedOn?: string;
  reason?: string;
  decision?: DisciplinaryDecision;
  decisionNotes?: string;
  closedOn?: string;
  status?: DisciplinaryStatus;
};

export type DisciplinaryActionListResponse = {
  actions?: DisciplinaryActionDto[];
  fyCode?: string;
  warningCounts?: Partial<Record<DisciplinaryLevel, number>>;
};

export type EmployeeHistoryDto = {
  id?: number;
  employeeId?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  managerId?: number;
  changeReason?: HistoryChangeReason;
  notes?: string;
};

/**
 * Point-in-time assignment row + server-resolved catalog labels and manager
 * summary. Returned by `GET /employees/{id}/history/resolved` and embedded as
 * `recentHistory` on `EmployeeDetail`. Always prefer this shape over the
 * raw upstream `EmployeeHistoryDto` (which only carries FK ids).
 */
export type EmployeeHistoryRow = {
  id?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  changeReason?: HistoryChangeReason;
  notes?: string;
  department?: DepartmentDto;
  section?: SectionDto;
  designation?: DesignationDto;
  grade?: GradeDto;
  manager?: {
    id?: number;
    fullName?: string;
    employeeCode?: string;
    profilePictureUrl?: string;
  };
  /** Raw upstream row, in case the FE needs the unresolved ids. */
  raw?: EmployeeHistoryDto;
};

export type PageResponse<T> = {
  content?: T[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  sort?: string;
};

/* ------------------------------------------------------------------ */
/* BFF composites                                                     */
/* ------------------------------------------------------------------ */

export type EmployeeResolved = {
  department?: DepartmentDto;
  section?: SectionDto;
  designation?: DesignationDto;
  grade?: GradeDto;
  city?: CityDto;
  manager?: {
    id?: number;
    fullName?: string;
    employeeCode?: string;
    profilePictureUrl?: string;
  };
};

export type ReportSummary = {
  id?: number;
  fullName?: string;
  employeeCode?: string;
  profilePictureUrl?: string;
  directReportsCount?: number;
};

export type DirectReportsPreview = {
  items?: ReportSummary[];
  total?: number;
};

export type TimelineEntryView = {
  id?: number;
  action?: string;
  actorUsername?: string;
  actorLabel?: string;
  message?: string;
  occurredAt?: string;
};

export type TimelinePageView = {
  content?: TimelineEntryView[];
  nextCursor?: string;
};

export type EmployeeDetail = {
  employee?: EmployeeDto;
  resolved?: EmployeeResolved;
  activeContract?: EmploymentContractDto;
  activeSuspension?: SuspensionDto;
  exit?: ExitDto;
  upheldWarningsThisFy?: Record<string, number>;
  openWarningsThisFy?: number;
  directReportsPreview?: DirectReportsPreview;
  recentTimeline?: TimelineEntryView[];
  /**
   * Last 10 assignment-history rows (newest first), labels pre-resolved.
   * Use `peopleBff.history(id)` for the paginated full feed.
   */
  recentHistory?: EmployeeHistoryRow[];
  tenureDays?: number;
};

export type EmployeeDirectoryRow = {
  employee?: EmployeeDto;
  resolved?: EmployeeResolved;
};

export type PageResponseEmployeeDirectoryRow = {
  content?: EmployeeDirectoryRow[];
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  sort?: string;
};

export type EmployeeStats = {
  total?: number;
  byStatus?: Partial<Record<EmploymentStatus, number>>;
  byEmploymentType?: Partial<Record<EmploymentType, number>>;
  probation?: { active?: number; dueWithin14?: number; overdue?: number };
  certifications?: { expiringWithin30?: number; expired?: number };
  ess?: { granted?: number; ungranted?: number; coveragePct?: number };
};

export type OnboardingLookups = {
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
  provinces?: ProvinceDto[];
  cities?: CityDto[];
  qualifications?: QualificationDto[];
  skills?: SkillDto[];
};

export type OrgTreeNode = {
  id?: number;
  fullName?: string;
  employeeCode?: string;
  designationTitle?: string;
  profilePictureUrl?: string;
  directReportsCount?: number;
  children?: OrgTreeNode[];
};

export type OrgTree = {
  root?: OrgTreeNode;
  depth?: number;
};

export type AccountProfileBff = {
  employee?: EmployeeDto;
  resolved?: EmployeeResolved;
  activeContract?: EmploymentContractDto;
  team?: ReportSummary[];
  upcomingProbationDeadline?: string;
};

/* ------------------------------------------------------------------ */
/* Request bodies                                                     */
/* ------------------------------------------------------------------ */

/**
 * One row in `employee_qualifications` to seed during onboarding.
 * Persisted in the kickoff transaction (no saga step).
 */
export type OnboardQualificationLine = {
  qualificationId: number;
  institution: string;
  boardOrUniversity?: string;
  yearOfCompletion?: number;
  grade?: string;
  documentKey?: string;
};

/** Prior employment row. Persisted in the kickoff transaction. */
export type OnboardExperienceLine = {
  employerName: string;
  designation?: string;
  startDate: string;
  endDate: string;
  responsibilities?: string;
  reasonForLeaving?: string;
};

/** Skill + proficiency. Persisted in the kickoff transaction. */
export type OnboardSkillLine = {
  skillId: number;
  proficiency: ProficiencyLevelEnum;
  /** Years of experience — backend takes BigDecimal so a small float is fine. */
  years?: number;
  notes?: string;
};

export type OnboardCertificationLine = {
  name: string;
  issuingBody?: string;
  issueDate?: string;
  expiryDate?: string;
  credentialId?: string;
  documentKey?: string;
};

export type OnboardEmergencyContactLine = {
  name: string;
  relation?: string;
  phone: string;
  /** First row with primary=true wins — others are stored non-primary. */
  primary: boolean;
};

export type OnboardDisabilityLine = {
  disabilityId: number;
  description?: string;
  severity?: DisabilitySeverity;
  documentKey?: string;
};

/**
 * One row in employee_salary_components to apply via the saga.
 * `amount` in rupees; null falls back to the catalog default.
 */
export type OnboardSalaryComponentLine = {
  componentId: number;
  amount?: number;
  notes?: string;
};

/** Bank disbursement details, applied by the SET_BANK_DETAILS saga step. */
export type OnboardBankDetails = {
  bankId: number;
  branch?: string;
  accountTitle: string;
  iban: string;
  accountNumber?: string;
};

/**
 * Salary block applied by SET_BASIC_SALARY → ASSIGN_SALARY_COMPONENTS →
 * SET_BANK_DETAILS saga steps. All amounts are rupees (BigDecimal scale 2
 * on the wire).
 */
export type OnboardSalarySetup = {
  basic: number;
  components?: OnboardSalaryComponentLine[];
  bank?: OnboardBankDetails;
};

/** Initial shift, applied by ASSIGN_SHIFT. */
export type OnboardShiftAssignment = {
  shiftId: number;
};

export type OnboardEmployeeRequest = {
  fullName: string;
  cnic?: string;
  cnicIssueDate?: string;
  cnicExpiryDate?: string;
  ntn?: string;
  insuranceNumber?: string;
  sscNumber?: string;
  eobiNumber?: string;
  dateOfBirth?: string;
  gender?: string;
  maritalStatus?: string;
  fatherName?: string;
  motherName?: string;
  spouseName?: string;
  religion?: string;
  bloodGroup?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  permanentAddress?: string;
  currentAddress?: string;
  cityId?: number;
  joiningDate: string;
  employmentType?: EmploymentType;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  managerId?: number;
  probationDays?: number;
  provisionEss?: boolean;
  username?: string;
  email?: string;
  temporaryPassword?: string;

  // Complete-form sections — all optional. Per-row arrays are persisted in
  // the kickoff tx. salary + shift sit in the saga payload.
  qualifications?: OnboardQualificationLine[];
  experiences?: OnboardExperienceLine[];
  skills?: OnboardSkillLine[];
  certifications?: OnboardCertificationLine[];
  emergencyContacts?: OnboardEmergencyContactLine[];
  disabilities?: OnboardDisabilityLine[];
  salary?: OnboardSalarySetup;
  shift?: OnboardShiftAssignment;
};

export type UpdateEmployeeRequest = {
  fullName?: string;
  cnic?: string;
  cnicIssueDate?: string;
  cnicExpiryDate?: string;
  ntn?: string;
  insuranceNumber?: string;
  sscNumber?: string;
  eobiNumber?: string;
  dateOfBirth?: string;
  gender?: string;
  maritalStatus?: string;
  fatherName?: string;
  motherName?: string;
  spouseName?: string;
  religion?: string;
  bloodGroup?: string;
  personalEmail?: string;
  workEmail?: string;
  phone?: string;
  permanentAddress?: string;
  currentAddress?: string;
  cityId?: number;
  employmentType?: EmploymentType;
  profilePictureKey?: string;
};

export type AssignmentChangeRequest = {
  /** Catalog id (department / section / designation / grade) or new manager employee id. */
  targetId?: number;
  notes?: string;
};

export type ConfirmEmployeeRequest = { notes?: string };

export type ExtendProbationRequest = {
  newProbationDays: number;
  reason: string;
};

export type ResignRequest = {
  lastWorkingDay: string;
  reason?: string;
  notes?: string;
  reassignReportsTo?: number;
};

export type TerminateRequest = {
  reason: string;
  notes?: string;
  reassignReportsTo?: number;
};

export type WithdrawExitRequest = { reason: string };

export type SuspendRequest = {
  startDate: string;
  endDate?: string;
  reason: string;
  withPay: boolean;
};

export type LiftSuspensionRequest = {
  liftedOn?: string;
  reason: string;
};

export type GrantEssRequest = {
  username?: string;
  email?: string;
  temporaryPassword: string;
};

export type IssueDisciplinaryRequest = {
  level: DisciplinaryLevel;
  category: DisciplinaryCategory;
  incidentDate?: string;
  issuedOn: string;
  reason: string;
};

export type DecideDisciplinaryRequest = {
  decision: DisciplinaryDecision;
  decisionNotes?: string;
  closedOn?: string;
};

export type RecordDocumentRequest = {
  docType: DocumentType;
  title: string;
  objectKey: string;
  contentType?: string;
};

export type UploadUrlRequest = {
  docType: DocumentType;
  filename: string;
  contentType?: string;
  /** Declared object size. Server rejects pre-sign requests above the configured cap. */
  bytes?: number;
};

export type UploadUrlResponse = {
  uploadUrl: string;
  objectKey: string;
  expiresAt?: string;
  /** Bucket the URL targets. */
  bucket?: string;
};

/**
 * Pre-sign request for a profile picture. Unlike documents there is no
 * docType — the server derives the key prefix (`employees/{id}/profile-picture/`)
 * itself, and rejects `bytes` above the avatar cap (2 MB by default).
 */
export type ProfilePictureFilenameRequest = {
  filename: string;
  bytes?: number;
};

/** Posted after the PUT to object storage completes. */
export type SetProfilePictureRequest = {
  objectKey: string;
};

export type AddEmployeeSkillRequest = {
  skillId: number;
  proficiency: ProficiencyLevelEnum;
  years?: number;
  notes?: string;
};

export type UpdateEmployeeSkillRequest = {
  proficiency?: ProficiencyLevelEnum;
  years?: number;
  notes?: string;
};

export type AddEmployeeQualificationRequest = {
  qualificationId: number;
  institution: string;
  boardOrUniversity?: string;
  yearOfCompletion?: number;
  grade?: string;
  documentKey?: string;
};

export type UpdateEmployeeQualificationRequest = {
  institution?: string;
  boardOrUniversity?: string;
  yearOfCompletion?: number;
  grade?: string;
  documentKey?: string;
};

export type AddCertificationRequest = {
  name: string;
  issuingBody?: string;
  issueDate?: string;
  expiryDate?: string;
  credentialId?: string;
  documentKey?: string;
};

export type UpdateCertificationRequest = {
  name?: string;
  issuingBody?: string;
  issueDate?: string;
  expiryDate?: string;
  credentialId?: string;
  documentKey?: string;
};

export type AddPriorExperienceRequest = {
  employerName: string;
  designation?: string;
  startDate: string;
  endDate: string;
  responsibilities?: string;
  reasonForLeaving?: string;
};

export type UpdatePriorExperienceRequest = {
  employerName?: string;
  designation?: string;
  startDate?: string;
  endDate?: string;
  responsibilities?: string;
  reasonForLeaving?: string;
};

export type CreateEmergencyContactRequest = {
  name: string;
  relation?: string;
  phone: string;
  primary?: boolean;
};

export type UpdateEmergencyContactRequest = {
  name?: string;
  relation?: string;
  phone?: string;
  primary?: boolean;
};
