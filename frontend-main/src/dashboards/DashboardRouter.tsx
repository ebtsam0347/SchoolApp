/**
 * The `/` landing route is a thin router: pick the persona layout from
 * `useDashboardPersona()` and render it. Unimplemented personas fall
 * through to `<DashboardComingSoon />` until D-5 / D-6 / D-7 land.
 */
import { DashboardAdmin } from './admin';
import { DashboardComingSoon } from './DashboardComingSoon';
import { DashboardEmployee } from './employee';
import { DashboardHR } from './hr';
import { DashboardManager } from './manager';
import { DashboardPayroll } from './payroll';
import { IMPLEMENTED_PERSONAS } from './persona';
import { useDashboardPersona } from './use-dashboard-persona';

export function DashboardRouter() {
  const { persona } = useDashboardPersona();

  if (!IMPLEMENTED_PERSONAS.has(persona)) {
    return <DashboardComingSoon persona={persona} />;
  }

  switch (persona) {
    case 'admin':
      return <DashboardAdmin />;
    case 'payroll':
      return <DashboardPayroll />;
    case 'hr':
      return <DashboardHR />;
    case 'manager':
      return <DashboardManager />;
    case 'employee':
    default:
      return <DashboardEmployee />;
  }
}
