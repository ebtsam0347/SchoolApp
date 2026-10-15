// Map the wizard's form-shape to the OnboardEmployeeRequest body the
// backend expects. Trims strings, drops empties, and only forwards each
// optional section when it has at least one usable row. Money is rupees
// end-to-end on the wire, so amounts pass through unchanged.

import type {
  OnboardCertificationLine,
  OnboardDisabilityLine,
  OnboardEmergencyContactLine,
  OnboardEmployeeRequest,
  OnboardExperienceLine,
  OnboardQualificationLine,
  OnboardSalaryComponentLine,
  OnboardSalarySetup,
  OnboardSkillLine,
} from '@/people';
import type { WizardForm } from './types';

function trim(s?: string): string | undefined {
  const t = s?.trim();
  return t ? t : undefined;
}

function mapQualifications(rows?: WizardForm['qualifications']): OnboardQualificationLine[] | undefined {
  const out: OnboardQualificationLine[] = [];
  for (const r of rows ?? []) {
    if (!r?.qualificationId || !trim(r.institution)) continue;
    out.push({
      qualificationId: r.qualificationId,
      institution: r.institution!.trim(),
      boardOrUniversity: trim(r.boardOrUniversity),
      yearOfCompletion: r.yearOfCompletion,
      grade: trim(r.grade),
    });
  }
  return out.length ? out : undefined;
}

function mapExperiences(rows?: WizardForm['experiences']): OnboardExperienceLine[] | undefined {
  const out: OnboardExperienceLine[] = [];
  for (const r of rows ?? []) {
    const employer = trim(r?.employerName);
    if (!employer || !r?.range?.[0] || !r?.range?.[1]) continue;
    out.push({
      employerName: employer,
      designation: trim(r.designation),
      startDate: r.range[0].format('YYYY-MM-DD'),
      endDate: r.range[1].format('YYYY-MM-DD'),
      responsibilities: trim(r.responsibilities),
      reasonForLeaving: trim(r.reasonForLeaving),
    });
  }
  return out.length ? out : undefined;
}

function mapSkills(rows?: WizardForm['skills']): OnboardSkillLine[] | undefined {
  const out: OnboardSkillLine[] = [];
  for (const r of rows ?? []) {
    if (!r?.skillId || !r?.proficiency) continue;
    out.push({
      skillId: r.skillId,
      proficiency: r.proficiency,
      years: r.years,
      notes: trim(r.notes),
    });
  }
  return out.length ? out : undefined;
}

function mapCertifications(rows?: WizardForm['certifications']): OnboardCertificationLine[] | undefined {
  const out: OnboardCertificationLine[] = [];
  for (const r of rows ?? []) {
    const name = trim(r?.name);
    if (!name) continue;
    out.push({
      name,
      issuingBody: trim(r?.issuingBody),
      issueDate: r?.issueDate?.format('YYYY-MM-DD'),
      expiryDate: r?.expiryDate?.format('YYYY-MM-DD'),
      credentialId: trim(r?.credentialId),
    });
  }
  return out.length ? out : undefined;
}

function mapEmergencyContacts(
  rows?: WizardForm['emergencyContacts'],
): OnboardEmergencyContactLine[] | undefined {
  const out: OnboardEmergencyContactLine[] = [];
  let primaryClaimed = false;
  for (const r of rows ?? []) {
    const name = trim(r?.name);
    const phone = trim(r?.phone);
    if (!name || !phone) continue;
    // Backend keeps only the first primary=true row; mirror that here.
    const primary = !!r?.primary && !primaryClaimed;
    if (primary) primaryClaimed = true;
    out.push({
      name,
      relation: trim(r?.relation),
      phone,
      primary,
    });
  }
  return out.length ? out : undefined;
}

function mapDisabilities(rows?: WizardForm['disabilities']): OnboardDisabilityLine[] | undefined {
  const out: OnboardDisabilityLine[] = [];
  for (const r of rows ?? []) {
    if (!r?.disabilityId) continue;
    out.push({
      disabilityId: r.disabilityId,
      description: trim(r.description),
      severity: r.severity,
    });
  }
  return out.length ? out : undefined;
}

function mapSalary(v: WizardForm): OnboardSalarySetup | undefined {
  const basic = v.salaryBasic;
  if (basic === undefined || basic === null || Number.isNaN(basic)) return undefined;
  const components: OnboardSalaryComponentLine[] = [];
  for (const r of v.salaryComponents ?? []) {
    if (!r?.componentId) continue;
    components.push({
      componentId: r.componentId,
      amount: r.amount,
      notes: trim(r.notes),
    });
  }
  // Cash mode → never forward bank fields, even if leftover values sit in
  // the form (operator may have toggled BANK_TRANSFER → CASH after typing).
  const bank = v.paymentMode === 'BANK_TRANSFER' ? v.salaryBank : undefined;
  const wantBank = bank && (bank.bankId || bank.iban || bank.accountTitle);
  let bankBlock: OnboardSalarySetup['bank'] | undefined;
  if (wantBank && bank?.bankId && trim(bank.accountTitle) && trim(bank.iban)) {
    bankBlock = {
      bankId: bank.bankId,
      branch: trim(bank.branch),
      accountTitle: bank.accountTitle!.trim(),
      iban: bank.iban!.trim().toUpperCase(),
      accountNumber: trim(bank.accountNumber),
    };
  }
  return {
    basic,
    components: components.length ? components : undefined,
    bank: bankBlock,
  };
}

export function toRequest(v: WizardForm): OnboardEmployeeRequest {
  return {
    fullName: v.fullName.trim(),
    cnic: trim(v.cnic),
    cnicIssueDate: v.cnicDates?.[0]?.format('YYYY-MM-DD'),
    cnicExpiryDate: v.cnicDates?.[1]?.format('YYYY-MM-DD'),
    ntn: trim(v.ntn),
    insuranceNumber: trim(v.insuranceNumber),
    sscNumber: trim(v.sscNumber),
    eobiNumber: trim(v.eobiNumber),
    dateOfBirth: v.dateOfBirth?.format('YYYY-MM-DD'),
    gender: v.gender,
    maritalStatus: v.maritalStatus,
    fatherName: trim(v.fatherName),
    motherName: trim(v.motherName),
    spouseName: trim(v.spouseName),
    religion: trim(v.religion),
    bloodGroup: trim(v.bloodGroup),
    personalEmail: trim(v.personalEmail),
    workEmail: trim(v.workEmail),
    phone: trim(v.phone),
    permanentAddress: trim(v.permanentAddress),
    currentAddress: trim(v.currentAddress),
    cityId: v.cityId,
    joiningDate: v.joiningDate.format('YYYY-MM-DD'),
    employmentType: v.employmentType,
    departmentId: v.departmentId,
    sectionId: v.sectionId,
    designationId: v.designationId,
    gradeId: v.gradeId,
    managerId: v.managerId,
    probationDays: v.probationDays,
    provisionEss: v.provisionEss,
    username: v.provisionEss ? trim(v.username) : undefined,
    email: v.provisionEss ? trim(v.email) : undefined,
    temporaryPassword: v.provisionEss ? v.temporaryPassword : undefined,

    qualifications: mapQualifications(v.qualifications),
    experiences: mapExperiences(v.experiences),
    skills: mapSkills(v.skills),
    certifications: mapCertifications(v.certifications),
    emergencyContacts: mapEmergencyContacts(v.emergencyContacts),
    disabilities: mapDisabilities(v.disabilities),
    salary: mapSalary(v),
    shift: v.shiftId ? { shiftId: v.shiftId } : undefined,
  };
}
