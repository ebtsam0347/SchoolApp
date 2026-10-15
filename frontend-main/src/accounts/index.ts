// Accounts (Preview) module barrel — mock-only surface for the demo. No
// real api.ts / bff-api.ts here yet; all data flows from src/accounts/mock.

export * from './mock/data';
export { ExecutiveDashboard } from './dashboard/ExecutiveDashboard';
export { JournalEntries } from './ledger/JournalEntries';
export { ChartOfAccounts } from './ledger/ChartOfAccounts';
export { TrialBalance } from './ledger/TrialBalance';
export { VendorBills } from './payables/VendorBills';
export { CustomerInvoices } from './receivables/CustomerInvoices';
export { Vendors } from './payables/Vendors';
export { Customers } from './receivables/Customers';
export { BankBook } from './banking/BankBook';
export { CashPosition } from './banking/CashPosition';
export { Inventory } from './manufacturing/Inventory';
export { Cogs } from './manufacturing/Cogs';
export { FixedAssets } from './manufacturing/FixedAssets';
export { SalesTax } from './compliance/SalesTax';
export { Withholding } from './compliance/Withholding';
export { ProfitLoss } from './reports/ProfitLoss';
export { BalanceSheet } from './reports/BalanceSheet';
export { CashFlow } from './reports/CashFlow';
export { ReportsLanding } from './reports/ReportsLanding';
export { PeriodClose } from './close/PeriodClose';
