// Barrel for /people/salary-component-transactions subcomponents.

export { TransactionStateMachine } from './TransactionStateMachine';
export type { TransactionStateMachineProps } from './TransactionStateMachine';

export { TransactionActions } from './TransactionActions';
export type { TransactionActionsProps } from './TransactionActions';

export {
  TransactionLinesTable,
  TransactionEmployeeCardGrid,
  toTransactionEmployeeRows,
  transactionEmployeeColumns,
} from './TransactionLinesTable';
export type {
  TransactionLinesTableProps,
  TransactionEmployeeRow,
  TransactionEmployeeCardGridProps,
} from './TransactionLinesTable';

export * from './types';
export * from './api';
export * from './composites';
export * from './format';

// Allowance tabs — each employee's flat allowances (calculate_with_salary = false).
export { AllEmployeesTab } from './AllEmployeesTab';
export type { AllEmployeesTabProps } from './AllEmployeesTab';
export { SetAllowancesTab } from './SetAllowancesTab';
export type { SetAllowancesTabProps } from './SetAllowancesTab';
export { AllowanceHistoryTab } from './AllowanceHistoryTab';
export type { AllowanceHistoryTabProps } from './AllowanceHistoryTab';
export * from './allowances-types';
export { allowancesApi } from './allowances-api';
