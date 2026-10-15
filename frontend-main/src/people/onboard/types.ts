// Shared types for the onboarding wizard. The wizard is a single AntD
// Form driving every step + review; this file centralises the form-value
// shape and the step list so the parent and per-step components stay in
// sync.
//
// The optional sections (qualifications/experience/skills/certifications/
// emergency contacts/disabilities/salary/shift) are independent — each
// adds form fields here, the matching toRequest mapper drops empty
// arrays / blocks so the saga skips their step.

import type { ReactElement } from 'react';
import type dayjs from 'dayjs';
import type {
  DisabilitySeverity,
  EmploymentType,
  ProficiencyLevelEnum,
} from '@/people';
import type { EmployeePaymentMode } from '@/payroll';

/* ------------------------ Per-row shapes ----------------------------- */

export type QualificationLineForm = {
  qualificationId?: number;
  institution?: string;
  boardOrUniversity?: string;
  yearOfCompletion?: number;
  grade?: string;
};

export type ExperienceLineForm = {
  employerName?: string;
  designation?: string;
  range?: [dayjs.Dayjs, dayjs.Dayjs];
  responsibilities?: string;
  reasonForLeaving?: string;
};

export type SkillLineForm = {
  skillId?: number;
  proficiency?: ProficiencyLevelEnum;
  years?: number;
  notes?: string;
};

export type CertificationLineForm = {
  name?: string;
  issuingBody?: string;
  issueDate?: dayjs.Dayjs;
  expiryDate?: dayjs.Dayjs;
  credentialId?: string;
};

export type EmergencyContactLineForm = {
  name?: string;
  relation?: string;
  phone?: string;
  primary?: boolean;
};

export type DisabilityLineForm = {
  disabilityId?: number;
  description?: string;
  severity?: DisabilitySeverity;
};

export type SalaryComponentLineForm = {
  componentId?: number;
  /** Whole-rupee amount; passed straight through to the wire. */
  amount?: number;
  notes?: string;
};

export type BankDetailsForm = {
  bankId?: number;
  branch?: string;
  accountTitle?: string;
  iban?: string;
  accountNumber?: string;
};

/* ------------------------------ Form --------------------------------- */

export type WizardForm = {
  // Identity
  fullName: string;
  cnic?: string;
  cnicDates?: [dayjs.Dayjs, dayjs.Dayjs];
  ntn?: string;
  insuranceNumber?: string;
  sscNumber?: string;
  eobiNumber?: string;
  dateOfBirth?: dayjs.Dayjs;
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

  // Employment
  joiningDate: dayjs.Dayjs;
  employmentType?: EmploymentType;
  departmentId?: number;
  sectionId?: number;
  designationId?: number;
  gradeId?: number;
  managerId?: number;
  /** Cached display label for the picked manager. Managers aren't in
   *  /employees/onboarding-lookups, so we stash the label at pick-time so
   *  Review can show "Ahmed Khan · FS-2026-0004" instead of "#123". */
  managerLabel?: string;

  // Contract
  probationDays?: number;

  // Background
  qualifications?: QualificationLineForm[];
  experiences?: ExperienceLineForm[];

  // Skills
  skills?: SkillLineForm[];
  certifications?: CertificationLineForm[];

  // Compliance
  emergencyContacts?: EmergencyContactLineForm[];
  disabilities?: DisabilityLineForm[];

  // Salary (whole rupees, end-to-end). Basic is required at onboarding;
  // components and bank details are still optional.
  salaryBasic?: number;
  salaryComponents?: SalaryComponentLineForm[];
  /** UI-only; defaults to CASH. Pushed via payroll-settings PATCH after
   *  onboarding succeeds — the onboarding wire schema doesn't carry it. */
  paymentMode?: EmployeePaymentMode;
  salaryBank?: BankDetailsForm;

  // Shift
  shiftId?: number;

  // ESS
  provisionEss?: boolean;
  username?: string;
  email?: string;
  temporaryPassword?: string;
};

export type WizardStepKey =
  | 'identity'
  | 'org-placement'
  | 'profile'
  | 'pay-and-login'
  | 'review';

export type WizardStep = {
  key: WizardStepKey;
  title: string;
  icon: ReactElement;
};

export const STEP_FIELDS: Record<WizardStepKey, (keyof WizardForm)[]> = {
  identity: [
    'fullName',
    'cnic',
    'cnicDates',
    'dateOfBirth',
    'gender',
    'phone',
    'workEmail',
    'personalEmail',
    'fatherName',
    'religion',
    'permanentAddress',
    'cityId',
  ],
  'org-placement': [
    'joiningDate',
    'employmentType',
    'departmentId',
    'sectionId',
    'designationId',
    'gradeId',
    'managerId',
    'probationDays',
    'shiftId',
  ],
  profile: [
    'qualifications',
    'experiences',
    'skills',
    'certifications',
    'emergencyContacts',
    'disabilities',
  ],
  'pay-and-login': [
    'salaryBasic',
    'salaryComponents',
    'paymentMode',
    'salaryBank',
    'provisionEss',
    'username',
    'email',
    'temporaryPassword',
  ],
  review: [],
};
