// Salary Component Transaction types — maps to the payroll.allowance_run
// table. The frontend structure and state machine mirror Payroll Runs so
// operators get a consistent UX across both surfaces.

import type { PageResponse } from '@/attendance';

/* ------------------------------ Enums --------------------------------- */

export type SalaryComponentTransactionStatus =
  | 'DRAFT'
  | 'LOCKED'
  | 'COMPUTING'
  | 'COMPUTED'
  | 'APPROVED'
  | 'POSTING'
  | 'POSTED'
  | 'FAILED'
  | 'CANCELLED';

export type TransactionLineStatus =
  | 'DRAFT'
  | 'PROCESSED'
  | 'HELD'
  | 'RELEASE_PENDING'
  | 'RELEASED';

/* ------------------------------ DTOs ---------------------------------- */

export interface SalaryComponentTransactionDto {
  id: number;
  fiscalYearCode: string;
  periodYear: number;
  periodMonth: number;
  status: SalaryComponentTransactionStatus;
  computedAt: string | null;
  approvedAt: string | null;
  postedAt: string | null;
  approvedBy: number | null;
  notes: string | null;
  skippedCount: number;
  progressCount: number;
  totalCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSalaryComponentTransactionRequest {
  year: number;
  month: number;
}

/* ----------------------- Transactions endpoint ------------------------ */

export interface TransactionComponentDto {
  componentId: number;
  code: string;
  name: string;
  amount: number;
  status: TransactionLineStatus;
}

/** Denormalized employee snapshot embedded on a TransactionEmployeeDto row. */
export interface TransactionEmployeeSnapshot {
  id: number;
  fullName: string;
  employeeCode: string;
  departmentId: number | null;
  departmentName: string | null;
  sectionId: number | null;
  sectionName: string | null;
  designationId: number | null;
  designationName: string | null;
  profilePictureUrl: string | null;
}

export interface TransactionEmployeeDto {
  employeeId: number;
  employeeCode: string;
  grade: string;
  fiscalYearCode: string;
  allowanceYear: number;
  allowanceMonth: number;
  employee: TransactionEmployeeSnapshot | null;
  components: TransactionComponentDto[];
}

/* ----------------------------- Composites ----------------------------- */

export interface TransactionSummaryDto {
  id: number;
  fiscalYearCode: string;
  periodYear: number;
  periodMonth: number;
  status: SalaryComponentTransactionStatus;
  totalCount: number;
  progressCount: number;
  skippedCount: number;
  successCount: number;
  computedAt: string | null;
  postedAt: string | null;
  totals: {
    runId: number;
    employeeCount: number;
    allowance: number;
  };
}

export { type PageResponse };
