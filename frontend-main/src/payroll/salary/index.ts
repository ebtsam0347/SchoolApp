// Barrel for the /people/$id/salary tab subcomponents. The hook is also
// exported so other consumers (e.g. payroll dashboards) can read the
// same bundle without redefining the query key.

export { BasicSection } from './BasicSection';
export { ComponentsSection } from './ComponentsSection';
export { SettingsSection } from './SettingsSection';
export { BankSection } from './BankSection';
export { GratuitySection } from './GratuitySection';
export { OffCycleSection } from './OffCycleSection';
export { useSalaryTab, salaryTabKey } from './query-keys';
