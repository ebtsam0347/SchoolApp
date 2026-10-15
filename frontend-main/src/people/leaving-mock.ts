// Mock data for the /people/leaving design preview (see the route file for
// context). Deterministic per card no — the same employee always shows the
// same numbers, without any backend involved. Delete this file once a real
// full-and-final-settlement API exists and the route is wired to it.

export type MockLeavingEmployee = {
  cardNo: string;
  name: string;
  fatherName: string;
  cnic: string;
  department: string;
  section: string;
  designation: string;
  jobType: string;
  grade: string;
  joiningDate: string;
  confirmationDate: string;
};

export const MOCK_EMPLOYEES: MockLeavingEmployee[] = [
  {
    cardNo: '8502',
    name: 'Moosa Butt',
    fatherName: 'Muhammad Shoaib',
    cnic: '34603-5874040-5',
    department: 'Information Technology',
    section: 'System Development',
    designation: 'Assistant Manager',
    jobType: 'Permanent',
    grade: 'M-4',
    joiningDate: '2021-11-04',
    confirmationDate: '2022-02-04',
  },
  {
    cardNo: '4210',
    name: 'Ayesha Raza',
    fatherName: 'Raza Ahmed',
    cnic: '35202-1234567-1',
    department: 'Finance',
    section: 'Accounts Payable',
    designation: 'Senior Executive',
    jobType: 'Permanent',
    grade: 'M-3',
    joiningDate: '2020-06-15',
    confirmationDate: '2020-12-15',
  },
  {
    cardNo: '6117',
    name: 'Bilal Ahmed',
    fatherName: 'Ahmed Khan',
    cnic: '35201-9876543-2',
    department: 'Production',
    section: 'Stitching',
    designation: 'Line Supervisor',
    jobType: 'Contract',
    grade: 'M-2',
    joiningDate: '2019-03-10',
    confirmationDate: '2019-09-10',
  },
  {
    cardNo: '3009',
    name: 'Sana Tariq',
    fatherName: 'Tariq Mehmood',
    cnic: '35202-4567891-3',
    department: 'Human Resources',
    section: 'Talent Acquisition',
    designation: 'HR Officer',
    jobType: 'Permanent',
    grade: 'M-3',
    joiningDate: '2022-01-20',
    confirmationDate: '2022-07-20',
  },
];

export type MockSettlement = {
  basicSalary: number;
  gratuityYears: number;
  gratuityAmount: number;
  bonusMultiplier: 0 | 1 | 1.5 | 2;
  pAdvAmount: number;
  pAdvPayBack: number;
  fullDays: number;
  sundays: number;
  totalAbsenties: number;
  canteenDedDue: number;
  bonusAmount: number;
  dueLeaves: number;
};

/** Simple deterministic hash so the same card no always seeds the same numbers. */
function seedFrom(cardNo: string): number {
  let hash = 0;
  for (let i = 0; i < cardNo.length; i++) {
    hash = (hash * 31 + cardNo.charCodeAt(i)) >>> 0;
  }
  return hash;
}

export function mockSettlementFor(cardNo: string): MockSettlement {
  const seed = seedFrom(cardNo);
  const basicSalary = 60_000 + (seed % 12) * 5_000;
  const gratuityYears = 1 + (seed % 6);
  const gratuityAmount = Math.round(basicSalary * gratuityYears * 1.15);
  const bonusMultiplier = ([0, 1, 1.5, 2] as const)[seed % 4];
  const pAdvAmount = (seed % 5) * 15_000;
  const pAdvPayBack = pAdvAmount;
  const fullDays = 2 + (seed % 6);
  const sundays = 2 + (seed % 3);
  const totalAbsenties = 15 + (seed % 10);
  const canteenDedDue = (seed % 8) * 75;
  const bonusAmount = Math.round((basicSalary / 26) * bonusMultiplier * 1.9);
  const dueLeaves = (seed % 4) + (seed % 2) * 0.5;

  return {
    basicSalary,
    gratuityYears,
    gratuityAmount,
    bonusMultiplier,
    pAdvAmount,
    pAdvPayBack,
    fullDays,
    sundays,
    totalAbsenties,
    canteenDedDue,
    bonusAmount,
    dueLeaves,
  };
}
