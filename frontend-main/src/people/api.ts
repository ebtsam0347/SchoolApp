// Typed wrappers for the employee-service surface (gateway-mounted under /
// no prefix). The BFF-side composites live in `./bff-api.ts`. All requests
// flow through `src/lib/http.ts` so they inherit bearer + idempotency +
// 401-refresh.

import { http } from '@/lib/http';
import { qs } from '@/lib/qs';
import type {
  OnboardEmployeeResponse,
  PageResponse,
  SagaHealthSnapshot,
  SagaResponse,
  SagaStatus,
  AddCertificationRequest,
  AddEmployeeQualificationRequest,
  AddEmployeeSkillRequest,
  AddPriorExperienceRequest,
  AssignmentChangeRequest,
  CityDto,
  ConfirmEmployeeRequest,
  CreateEmergencyContactRequest,
  DecideDisciplinaryRequest,
  DepartmentDto,
  DesignationDto,
  DisabilityDto,
  DisciplinaryActionDto,
  DisciplinaryActionListResponse,
  DisciplinaryCategory,
  DisciplinaryLevel,
  DisciplinaryStatus,
  EmergencyContactDto,
  EmployeeCertificationDto,
  EmployeeDocumentDto,
  EmployeeDto,
  EmployeeHistoryDto,
  EmployeePriorExperienceDto,
  EmployeeQualificationDto,
  EmployeeSkillDto,
  EmploymentContractDto,
  EmploymentStatus,
  EmploymentType,
  ExitDto,
  ExtendProbationRequest,
  GradeDto,
  GradeType,
  GrantEssRequest,
  IssueDisciplinaryRequest,
  LiftSuspensionRequest,
  OnboardEmployeeRequest,
  ProfilePictureFilenameRequest,
  ProvinceDto,
  QualificationDto,
  RecordDocumentRequest,
  ResignRequest,
  SectionDto,
  SetProfilePictureRequest,
  SkillDto,
  SuspendRequest,
  SuspensionDto,
  TerminateRequest,
  UpdateCertificationRequest,
  UpdateEmergencyContactRequest,
  UpdateEmployeeQualificationRequest,
  UpdateEmployeeRequest,
  UpdateEmployeeSkillRequest,
  UpdatePriorExperienceRequest,
  UploadUrlRequest,
  UploadUrlResponse,
  WithdrawExitRequest,
  ReligionDto,
  BelongingMasterDto,
  offCyclePaymentDto,
} from './types';

export type ListEmployeesParams = {
  page?: number;
  size?: number;
  sort?: string;
  q?: string;
  status?: EmploymentStatus | null;
  includeTerminated?: boolean | null;
  employmentType?: EmploymentType | null;
  departmentId?: number | null;
  sectionId?: number | null;
  managerId?: number | null;
  gradeId?: number | null;
  designationId?: number | null;
  hasEss?: boolean | null;
  probationDueWithin?: number | null;
  probationOverdue?: boolean | null;
};

export const employeesApi = {
  list: (params: ListEmployeesParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<EmployeeDto>>(
      `/employees${qs({
        page: params.page,
        size: params.size,
        sort: params.sort,
        q: params.q,
        status: params.status ?? undefined,
        includeTerminated: params.includeTerminated ?? undefined,
        employmentType: params.employmentType ?? undefined,
        departmentId: params.departmentId ?? undefined,
        sectionId: params.sectionId ?? undefined,
        managerId: params.managerId ?? undefined,
        gradeId: params.gradeId ?? undefined,
        designationId: params.designationId ?? undefined,
        hasEss: params.hasEss ?? undefined,
        probationDueWithin: params.probationDueWithin ?? undefined,
        probationOverdue: params.probationOverdue ?? undefined,
      })}`,
      { signal },
    ),
  get: (id: number, signal?: AbortSignal) =>
    http.get<EmployeeDto>(`/employees/${id}`, { signal }),
  me: (signal?: AbortSignal) => http.get<EmployeeDto>('/employees/me', { signal }),
  /** Batch lookup (≤200 ids, missing dropped). Login only — no
   *  `employee.read` — so operator screens can show photo / section ids. */
  byIds: (ids: number[], signal?: AbortSignal) =>
    http.get<EmployeeDto[]>(`/employees/by-ids${qs({ ids })}`, { signal }),
  /**
   * Everyone in the caller's own department. Auth-only — no `employee.read`
   * — which is what makes it usable by a plain EMPLOYEE holding
   * TEAM_REQUEST_GENERATION, unlike `/employees` and `/employees/directory`.
   *
   * Returns full `EmployeeDto` rows (catalog ids, not labels) as a Spring
   * page, default sort `employeeCode,asc`. `q` is a server-side substring
   * match on name / employee code.
   */
  myDepartmentEmployees: (
    params: { q?: string; page?: number; size?: number } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<EmployeeDto>>(
      `/employees/me/department/employees${qs({
        q: params.q,
        page: params.page,
        size: params.size,
      })}`,
      { signal },
    ),
  myTeam: (signal?: AbortSignal) =>
    http
      .get<PageResponse<EmployeeDto>>('/employees/me/team', { signal })
      .then((r) => r.content),
  team: (
    id: number,
    params: { page?: number; size?: number; sort?: string; q?: string } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<EmployeeDto>>(
      `/employees/${id}/team${qs({
        page: params.page,
        size: params.size,
        sort: params.sort,
        q: params.q,
      })}`,
      { signal },
    ),
  /**
   * @deprecated Use `peopleBff.history(id)` — the upstream endpoint only
   * returns FK ids. Always go through the BFF for resolved labels.
   */
  history: (id: number, signal?: AbortSignal) =>
    http.get<PageResponse<EmployeeHistoryDto>>(`/employees/${id}/history`, {
      signal,
    }),
  contracts: (id: number, signal?: AbortSignal) =>
    http.get<EmploymentContractDto[]>(`/employees/${id}/contracts`, { signal }),

  // Mutations
  create: (body: OnboardEmployeeRequest) =>
    http.post<OnboardEmployeeResponse>('/employees', body),
  update: (id: number, body: UpdateEmployeeRequest) =>
    http.patch<EmployeeDto>(`/employees/${id}`, body),
  changeManager: (id: number, body: AssignmentChangeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/manager`, body),
  changeDepartment: (id: number, body: AssignmentChangeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/department`, body),
  changeSection: (id: number, body: AssignmentChangeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/section`, body),
  changeDesignation: (id: number, body: AssignmentChangeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/designation`, body),
  changeGrade: (id: number, body: AssignmentChangeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/grade`, body),
  confirm: (id: number, body: ConfirmEmployeeRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/confirm`, body),
  extendProbation: (id: number, body: ExtendProbationRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/extend-probation`, body),

  // Exits
  exit: (id: number, signal?: AbortSignal) =>
    http.get<ExitDto>(`/employees/${id}/exit`, { signal }),
  resign: (id: number, body: ResignRequest) =>
    http.post<ExitDto>(`/employees/${id}/resign`, body),
  terminate: (id: number, body: TerminateRequest) =>
    http.post<ExitDto>(`/employees/${id}/terminate`, body),
  withdrawExit: (id: number, body: WithdrawExitRequest) =>
    http.post<ExitDto>(`/employees/${id}/withdraw-exit`, body),

  // Suspensions
  suspensions: (id: number, signal?: AbortSignal) =>
    http.get<SuspensionDto[]>(`/employees/${id}/suspensions`, { signal }),
  suspend: (id: number, body: SuspendRequest) =>
    http.post<SuspensionDto>(`/employees/${id}/suspend`, body),
  liftSuspension: (id: number, body: LiftSuspensionRequest) =>
    http.post<SuspensionDto>(`/employees/${id}/lift-suspension`, body),

  // ESS
  grantEss: (id: number, body: GrantEssRequest) =>
    http.post<EmployeeDto>(`/employees/${id}/grant-ess`, body),
  revokeEss: (id: number) =>
    http.post<EmployeeDto>(`/employees/${id}/revoke-ess`),
};

/* ----------- Sagas --------------------------------------------- */

export type ListSagasParams = {
  status?: SagaStatus;
  name?: string;
  page?: number;
  size?: number;
  sort?: string;
};

export const sagasApi = {
  get: (id: number, signal?: AbortSignal) =>
    http.get<SagaResponse>(`/sagas/${id}`, { signal }),
  byKey: (name: string, correlationKey: string, signal?: AbortSignal) =>
    http.get<SagaResponse>(
      `/sagas/by-key${qs({ name, correlationKey })}`,
      { signal },
    ),
  list: (params: ListSagasParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<SagaResponse>>(`/sagas${qs(params)}`, { signal }),
  retry: (id: number) => http.post<SagaResponse>(`/sagas/${id}/retry`),
  health: (signal?: AbortSignal) =>
    http.get<SagaHealthSnapshot>('/sagas/health', { signal }),
};

/* ----------- Disciplinary --------------------------------------- */

export type ListDisciplinaryParams = {
  level?: DisciplinaryLevel;
  category?: DisciplinaryCategory;
  status?: DisciplinaryStatus;
  fyCode?: string;
  from?: string;
  to?: string;
  employeeId?: number;
  q?: string;
  page?: number;
  size?: number;
};

export const disciplinaryApi = {
  perEmployee: (
    employeeId: number,
    params: { fyCode?: string; level?: DisciplinaryLevel; status?: DisciplinaryStatus } = {},
    signal?: AbortSignal,
  ) =>
    http.get<DisciplinaryActionListResponse>(
      `/employees/${employeeId}/disciplinary-actions${qs({ ...params })}`,
      { signal },
    ),
  meSelf: (signal?: AbortSignal) =>
    http.get<DisciplinaryActionListResponse>(
      '/employees/me/disciplinary-actions',
      { signal },
    ),
  global: (params: ListDisciplinaryParams = {}, signal?: AbortSignal) =>
    http.get<PageResponse<DisciplinaryActionDto>>(
      `/disciplinary-actions${qs({ ...params })}`,
      { signal },
    ),
  issue: (employeeId: number, body: IssueDisciplinaryRequest) =>
    http.post<DisciplinaryActionDto>(
      `/employees/${employeeId}/disciplinary-actions`,
      body,
    ),
  decide: (id: number, body: DecideDisciplinaryRequest) =>
    http.post<DisciplinaryActionDto>(`/disciplinary-actions/${id}/decide`, body),
};

/* ----------- Documents ----------------------------------------- */

export const documentsApi = {
  list: (employeeId: number, signal?: AbortSignal) =>
    http
      .get<PageResponse<EmployeeDocumentDto>>(
        `/employees/${employeeId}/documents`,
        { signal },
      )
      .then((r) => r.content),
  uploadUrl: (employeeId: number, body: UploadUrlRequest) =>
    http.post<UploadUrlResponse>(
      `/employees/${employeeId}/documents/upload-url`,
      body,
    ),
  record: (employeeId: number, body: RecordDocumentRequest) =>
    http.post<EmployeeDocumentDto>(`/employees/${employeeId}/documents`, body),
  delete: (employeeId: number, docId: number) =>
    http.del<void>(`/employees/${employeeId}/documents/${docId}`),
};

/* ----------- Profile picture ------------------------------------ */

/**
 * Avatar upload for the signed-in employee. Same two-step flow as
 * documents (pre-sign → PUT to object storage → record the key), but the
 * key lands on `employees.profile_picture_key` rather than the documents
 * table.
 *
 * The `set` response comes off the write-side mapper, so its
 * `profilePictureUrl` is null — the fresh signed GET URL only appears on
 * a read (`/bff/account/profile`). Callers must refetch, not trust the
 * response for display.
 */
export const myProfilePictureApi = {
  uploadUrl: (body: ProfilePictureFilenameRequest) =>
    http.post<UploadUrlResponse>('/employees/me/profile-picture/upload-url', body),
  set: (body: SetProfilePictureRequest) =>
    http.post<EmployeeDto>('/employees/me/profile-picture', body),
};

/**
 * Same flow on someone else's record, for HR/admin working a directory
 * profile. Server requires `employee.write` on both calls — gate the UI on
 * the same ability so we don't offer a button the API will reject.
 */
export const employeeProfilePictureApi = {
  uploadUrl: (employeeId: number, body: ProfilePictureFilenameRequest) =>
    http.post<UploadUrlResponse>(
      `/employees/${employeeId}/profile-picture/upload-url`,
      body,
    ),
  set: (employeeId: number, body: SetProfilePictureRequest) =>
    http.post<EmployeeDto>(`/employees/${employeeId}/profile-picture`, body),
};

/* ----------- Skills / Quals / Certs / Experience --------------- */

export const skillsApi = {
  catalog: (signal?: AbortSignal) =>
    http.get<SkillDto[]>('/skills?active=true', { signal }),
  perEmployee: (id: number, signal?: AbortSignal) =>
    http.get<EmployeeSkillDto[]>(`/employees/${id}/skills`, { signal }),
  add: (id: number, body: AddEmployeeSkillRequest) =>
    http.post<EmployeeSkillDto>(`/employees/${id}/skills`, body),
  update: (id: number, employeeSkillId: number, body: UpdateEmployeeSkillRequest) =>
    http.patch<EmployeeSkillDto>(
      `/employees/${id}/skills/${employeeSkillId}`,
      body,
    ),
  remove: (id: number, employeeSkillId: number) =>
    http.del<void>(`/employees/${id}/skills/${employeeSkillId}`),
};

export const qualificationsApi = {
  catalog: (signal?: AbortSignal) =>
    http.get<QualificationDto[]>('/qualifications?active=true', { signal }),
  perEmployee: (id: number, signal?: AbortSignal) =>
    http.get<EmployeeQualificationDto[]>(`/employees/${id}/qualifications`, { signal }),
  add: (id: number, body: AddEmployeeQualificationRequest) =>
    http.post<EmployeeQualificationDto>(`/employees/${id}/qualifications`, body),
  update: (
    id: number,
    empQualId: number,
    body: UpdateEmployeeQualificationRequest,
  ) =>
    http.patch<EmployeeQualificationDto>(
      `/employees/${id}/qualifications/${empQualId}`,
      body,
    ),
  remove: (id: number, empQualId: number) =>
    http.del<void>(`/employees/${id}/qualifications/${empQualId}`),
};

export const certificationsApi = {
  expiringQueue: (
    params: {
      expiringWithin?: number;
      departmentId?: number;
      sectionId?: number;
      expired?: boolean;
      page?: number;
      size?: number;
    } = {},
    signal?: AbortSignal,
  ) =>
    http.get<PageResponse<EmployeeCertificationDto>>(
      `/certifications${qs({ ...params })}`,
      { signal },
    ),
  perEmployee: (id: number, signal?: AbortSignal) =>
    http.get<EmployeeCertificationDto[]>(
      `/employees/${id}/certifications`,
      { signal },
    ),
  add: (id: number, body: AddCertificationRequest) =>
    http.post<EmployeeCertificationDto>(
      `/employees/${id}/certifications`,
      body,
    ),
  update: (id: number, certId: number, body: UpdateCertificationRequest) =>
    http.patch<EmployeeCertificationDto>(
      `/employees/${id}/certifications/${certId}`,
      body,
    ),
  remove: (id: number, certId: number) =>
    http.del<void>(`/employees/${id}/certifications/${certId}`),
};

export const experienceApi = {
  perEmployee: (id: number, signal?: AbortSignal) =>
    http.get<EmployeePriorExperienceDto[]>(
      `/employees/${id}/prior-experience`,
      { signal },
    ),
  add: (id: number, body: AddPriorExperienceRequest) =>
    http.post<EmployeePriorExperienceDto>(
      `/employees/${id}/prior-experience`,
      body,
    ),
  update: (id: number, expId: number, body: UpdatePriorExperienceRequest) =>
    http.patch<EmployeePriorExperienceDto>(
      `/employees/${id}/prior-experience/${expId}`,
      body,
    ),
  remove: (id: number, expId: number) =>
    http.del<void>(`/employees/${id}/prior-experience/${expId}`),
};

export const disabilitiesApi = {
  catalog: (params: { includeInactive?: boolean } = {}, signal?: AbortSignal) =>
    http.get<DisabilityDto[]>(`/disabilities${qs({ ...params })}`, { signal }),
};

export const emergencyContactsApi = {
  perEmployee: (id: number, signal?: AbortSignal) =>
    http.get<EmergencyContactDto[]>(
      `/employees/${id}/emergency-contacts`,
      { signal },
    ),
  add: (id: number, body: CreateEmergencyContactRequest) =>
    http.post<EmergencyContactDto>(
      `/employees/${id}/emergency-contacts`,
      body,
    ),
  update: (id: number, contactId: number, body: UpdateEmergencyContactRequest) =>
    http.patch<EmergencyContactDto>(
      `/employees/${id}/emergency-contacts/${contactId}`,
      body,
    ),
  remove: (id: number, contactId: number) =>
    http.del<void>(`/employees/${id}/emergency-contacts/${contactId}`),
};

/* ----------- Catalogs ------------------------------------------ */

export type DepartmentBody = {
  code: string;
  name: string;
  active?: boolean;
  headId?: number;
  isProduction?: boolean;
};
export type ReligionBody = {
  name: string;
  active?: boolean;
};
export type OffCyclePaymentBody={
  name:string,
  description:string,
  active?:boolean
}
export type SectionBody = {
  code: string;
  name: string;
  departmentId: number;
  active?: boolean;
  headId?: number;
};
export type DesignationBody = { code: string; title: string; active?: boolean };
export type GradeBody = {
  code: string;
  name: string;
  level: number;
  description?: string;
  active?: boolean;
  /** Role bucket. Defaults to LABOUR server-side when omitted on create. */
  gradeType?: GradeType;
};
export type ProvinceBody = { code: string; name: string; active?: boolean };
export type CityBody = { code: string; name: string; provinceId: number; active?: boolean };
export type SkillBody = { code: string; name: string; category?: string; active?: boolean };
export type QualificationBody = { code: string; name: string; level: string; active?: boolean };
export type BelongingMasterBody = {
  belongingName: string;
  authorizedEmployeeId: number;
  active?: boolean;
};

/** Reorder payload — one entry per row whose order changed. */
export type ReorderEntry = { id: number; displayOrder: number };

export const catalogsApi = {
  // departments
  departments: (signal?: AbortSignal) =>
    http.get<DepartmentDto[]>('/departments', { signal }),
  createDepartment: (body: DepartmentBody) =>
    http.post<DepartmentDto>('/departments', body),
  updateDepartment: (id: number, body: Partial<DepartmentBody>) =>
    http.patch<DepartmentDto>(`/departments/${id}`, body),

  // sections
  sections: (departmentId?: number, signal?: AbortSignal) =>
    http.get<SectionDto[]>(`/sections${qs({ departmentId })}`, { signal }),
  createSection: (body: SectionBody) => http.post<SectionDto>('/sections', body),
  updateSection: (id: number, body: Partial<SectionBody>) =>
    http.patch<SectionDto>(`/sections/${id}`, body),

  // designations
  designations: (signal?: AbortSignal) =>
    http.get<DesignationDto[]>('/designations', { signal }),
  createDesignation: (body: DesignationBody) =>
    http.post<DesignationDto>('/designations', body),
  updateDesignation: (id: number, body: Partial<DesignationBody>) =>
    http.patch<DesignationDto>(`/designations/${id}`, body),

  // grades
  grades: (signal?: AbortSignal) => http.get<GradeDto[]>('/grades', { signal }),
  createGrade: (body: GradeBody) => http.post<GradeDto>('/grades', body),
  updateGrade: (id: number, body: Partial<GradeBody>) =>
    http.patch<GradeDto>(`/grades/${id}`, body),
  reorderGrades: (entries: ReorderEntry[]) =>
    http.patch<GradeDto[]>('/grades/reorder', entries),

  // provinces
  provinces: (signal?: AbortSignal) =>
    http.get<ProvinceDto[]>('/provinces', { signal }),
  createProvince: (body: ProvinceBody) => http.post<ProvinceDto>('/provinces', body),
  updateProvince: (id: number, body: Partial<ProvinceBody>) =>
    http.patch<ProvinceDto>(`/provinces/${id}`, body),

  // cities
  cities: (provinceId?: number, signal?: AbortSignal) =>
    http.get<CityDto[]>(`/cities${qs({ provinceId })}`, { signal }),
  createCity: (body: CityBody) => http.post<CityDto>('/cities', body),
  updateCity: (id: number, body: Partial<CityBody>) =>
    http.patch<CityDto>(`/cities/${id}`, body),
  reorderCities: (entries: ReorderEntry[]) =>
    http.patch<CityDto[]>('/cities/reorder', entries),

  // skills
  skillsAll: (signal?: AbortSignal) =>
    http.get<SkillDto[]>('/skills', { signal }),
  createSkill: (body: SkillBody) => http.post<SkillDto>('/skills', body),
  updateSkill: (id: number, body: Partial<SkillBody>) =>
    http.patch<SkillDto>(`/skills/${id}`, body),

  // qualifications
  qualificationsAll: (signal?: AbortSignal) =>
    http.get<QualificationDto[]>('/qualifications', { signal }),
  createQualification: (body: QualificationBody) =>
    http.post<QualificationDto>('/qualifications', body),
  updateQualification: (id: number, body: Partial<QualificationBody>) =>
    http.patch<QualificationDto>(`/qualifications/${id}`, body),
  reorderQualifications: (entries: ReorderEntry[]) =>
    http.patch<QualificationDto[]>('/qualifications/reorder', entries),

  // religions
  religions: (signal?: AbortSignal) =>
    http.get<ReligionDto[]>('/religions', { signal }),
  createReligion: (body: ReligionBody) =>
    http.post<ReligionDto>('/religions', body),
  updateReligion: (id: number, body: Partial<ReligionBody>) =>
    http.patch<ReligionDto>(`/religions/${id}`, body),

  // belonging master
  belongingMasters: (signal?: AbortSignal) =>
    http.get<BelongingMasterDto[]>('/belonging-master', { signal }),
  activeBelongingMasters: (signal?: AbortSignal) =>
    http.get<BelongingMasterDto[]>('/belonging-master/active', { signal }),
  belongingsAuthorizedToMe: (signal?: AbortSignal) =>
    http.get<BelongingMasterDto[]>(
      '/belonging-master/belongings-authorized-to-me',
      { signal },
    ),
  createBelongingMaster: (body: BelongingMasterBody) =>
    http.post<BelongingMasterDto>('/belonging-master', body),
  // PUT replaces the whole record (unlike the PATCH-based catalogs above) —
  // callers must send the full body, not a partial patch.
  updateBelongingMaster: (id: number, body: BelongingMasterBody) =>
    http.put<BelongingMasterDto>(`/belonging-master/${id}`, body),
    // off-cycle payment types
  offCyclePayments: (
    params: { active?: boolean } = {},
    signal?: AbortSignal,
  ) =>
    http.get<offCyclePaymentDto[]>(
      `/employees/off-cycle-payment-types${qs({ active: params.active })}`,
      { signal },
    ),
  createOffCyclePayment: (body: OffCyclePaymentBody) =>
    http.post<offCyclePaymentDto>('/employees/off-cycle-payment-types', body),
  updateOffCyclePayment: (id: number, body: Partial<OffCyclePaymentBody>) =>
    http.patch<offCyclePaymentDto>(`/employees/off-cycle-payment-types/${id}`, body),
};
