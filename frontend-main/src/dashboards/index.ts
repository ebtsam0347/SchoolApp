export * from './manager';
export * from './employee';
export * from './hr';
export * from './payroll';
export * from './admin';
export { DashboardRouter } from './DashboardRouter';
export { DashboardComingSoon } from './DashboardComingSoon';
export { RoleSwitcher, RoleSwitcherCaption } from './RoleSwitcher';
export {
  availablePersonas,
  resolveDashboardPersona,
  IMPLEMENTED_PERSONAS,
  PERSONA_LABEL,
  PERSONA_ORDER,
} from './persona';
export type { DashboardPersona } from './persona';
export { useDashboardPersona } from './use-dashboard-persona';
export type { UseDashboardPersonaResult } from './use-dashboard-persona';
export type * from './types';
