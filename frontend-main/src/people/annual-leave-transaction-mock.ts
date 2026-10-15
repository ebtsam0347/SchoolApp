// Mock data for the /people/annual-leave-transaction design preview (see the
// route file for context). Deterministic per card no + month — the same
// employee always shows the same numbers for a given month, without any
// backend involved. Delete this file once a real Annual Leave Transaction /
// LTA calculation API exists and the route is wired to it.

import { MOCK_EMPLOYEES, mockSettlementFor } from './leaving-mock';

export type AnnualLeaveRecord = {
  cardNo: string;
  name: string;
  department: string;
  section: string;
  designation: string;
  dateOfJoining: string;
  dateOfPermanent: string;
  basicSalary: number;
  payDays: number;
  dueAmount: number;
  ltaDeduction: number;
  paid: boolean;
};

/** Simple deterministic hash so the same card no + month always seeds the same numbers. */
function seedFrom(key: string): number {
  let hash = 0;
  for (let i = 0; i < key.length; i++) {
    hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
  }
  return hash;
}

export function buildAnnualLeaveRecords(monthKey: string): AnnualLeaveRecord[] {
  return MOCK_EMPLOYEES.map((e) => {
    const seed = seedFrom(`${e.cardNo}-${monthKey}`);
    const basicSalary = mockSettlementFor(e.cardNo).basicSalary;
    const payDays = 18 + (seed % 9);
    const dueAmount = Math.round((basicSalary / 26) * payDays);
    const ltaDeduction = Math.round(basicSalary * 0.05);

    return {
      cardNo: e.cardNo,
      name: e.name,
      department: e.department,
      section: e.section,
      designation: e.designation,
      dateOfJoining: e.joiningDate,
      dateOfPermanent: e.confirmationDate,
      basicSalary,
      payDays,
      dueAmount,
      ltaDeduction,
      paid: seed % 3 === 0,
    };
  });
}
