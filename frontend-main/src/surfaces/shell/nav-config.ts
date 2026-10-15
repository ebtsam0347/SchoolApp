import {
  Activity,
  Banknote,
  BarChart3,
  Bell,
  BookOpen,
  Bot,
  Building2,
  CalendarCheck,
  CalendarClock,
  CalendarRange,
  ClipboardCheck,
  ClipboardList,
  Clock,
  Coins,
  Database,
  FileBarChart,
  FileScan,
  FileSearch,
  FileText,
  Fingerprint,
  Gauge,
  Gift,
  Hand,
  Heart,
  IdCard,
  Inbox,
  Landmark,
  LayoutDashboard,
  LineChart,
  ListChecks,
  Lock,
  Mailbox,
  MessageSquare,
  MonitorSmartphone,
  Network,
  Package,
  Percent,
  Plane,
  Plus,
  Printer,
  Receipt,
  ReceiptText,
  RotateCcw,
  ScanSearch,
  ScrollText,
  Search,
  ShieldAlert,
  ShieldCheck,
  ShieldX,
  Sliders,
  Sparkles,
  Tags,
  Timer,
  TrendingUp,
  Truck,
  UserCheck,
  UserPlus,
  Users,
  // UtensilsCrossed,
  Wallet,
  UserMinus,
  Workflow,
  Wrench,
  type LucideIcon,
} from 'lucide-react';

import type { NavBadge } from './Sidebar/NavBadge';
import type { Workspace } from './workspace-store';

export interface NavItem {
  key: string;
  label: string;
  icon?: LucideIcon;
  to?: string;
  children?: NavItem[];
  /**
   * Optional flag chip rendered alongside the row — small "NEW" pill
   * in the expanded sidebar, tiny dot in the collapsed rail. Used to
   * draw attention to freshly-launched surfaces; remove once the
   * surface stops being "new" (no specific cadence — judgement call).
   */
  badge?: NavBadge;
  /**
   * Live count shown as a solid accent-gradient pill after the label
   * (expanded sidebar) or a small corner badge on the icon (collapsed
   * rail) — e.g. "Approvals Inbox" with a "27" pill. For numbers that
   * change per-request and so can't live in the static `badge` pill
   * above. Omit/0 renders nothing. See NavBadge.tsx's NavCountBadge /
   * NavCountDot.
   */
  countSuffix?: number;
  /**
   * CASL subject the caller must be able to `read`. Undefined = always
   * visible. The Sidebar reads the ability from context and hides items the
   * caller cannot read — "permissions hide, not disable".
   */
  subject?: string;
  /**
   * Override the verb the Sidebar checks against `subject`. Defaults to
   * `read`. Set this when the catalog permission isn't a read/write/admin
   * triple — e.g. the `attendance.operator` grant checks verb `operator`
   * on subject `attendance`.
   */
  verb?: string;
  /**
   * "Visible if any of these checks pass" — used for admin landing pages
   * that any admin-tier user should reach (e.g. /admin overview shows for
   * auth-admin OR audit-admin OR session-admin). When set together with
   * `subject`, both are evaluated and the item shows if either passes.
   */
  anyOf?: Array<{ subject: string; verb?: string }>;
  /**
   * Hide when the caller's user account isn't linked to an employee record.
   * Use for self-service surfaces (/me, /time/*, /payroll/payslips, …) whose
   * data lookups key off the caller's employeeId — leaving them visible
   * just bounces the user with a 403/404 or an empty state.
   */
  requiresEmployee?: boolean;
  /**
   * Hide when the caller has no direct reports. Use for manager-only
   * self-service surfaces (Team Attendance, etc.) whose data lookups
   * fan out over the caller's reports — an ordinary employee would
   * just see an empty page. Permission-gated team surfaces for HR/admin
   * (`subject`/`anyOf`) are unaffected and still pass through those checks.
   */
  requiresDirectReports?: boolean;
  /**
   * Hide unless the caller holds at least one of these auth role codes
   * (bare, no `ROLE_` prefix — matching /auth/me).
   *
   * Deliberately NOT subject to the ROLE_ADMIN bypass that
   * `requiresDirectReports` grants: this is for tabs that are scoped to a
   * specific delegated capability, where "admin can see everything" is the
   * wrong default. Use `subject`/`anyOf` for ordinary permission gating —
   * this is the escape hatch for when the grant is a role, not a
   * permission triple.
   */
  requiresRole?: string[];
}

/**
 * Subjects that mark a user as "admin-tier" — any one of these grants
 * visibility on the cross-cutting admin entries (overview, integrations,
 * settings) that don't map to a single service permission.
 */
const ADMIN_TIER: Array<{ subject: string; verb?: string }> = [
  { subject: 'auth.users' },
  { subject: 'auth.roles' },
  { subject: 'auth.sessions', verb: 'manage' },
  { subject: 'audit' },
  { subject: 'employee', verb: 'write' },
  { subject: 'payroll', verb: 'write' },
  { subject: 'notification', verb: 'announce' },
  { subject: 'notification.template' },
];

export interface NavGroup {
  key: string;
  label: string;
  items: NavItem[];
  /** Hide the entire group when none of its items are visible. */
  subject?: string;
}

export const hrNavGroups: NavGroup[] = [
  {
    key: 'work',
    label: 'Work',
    items: [
      { key: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, to: '/' },
      { key: 'me', label: 'Me', icon: IdCard, to: '/me', requiresEmployee: true },
      { key: 'inbox', label: 'Approvals Inbox', icon: Inbox, to: '/inbox' },
      { key: 'chat', label: 'Chat', icon: MessageSquare, to: '/chat' },
    ],
  },
  {
    key: 'people',
    label: 'People',
    subject: 'employee',
    items: [
      { key: 'people-overview', label: 'Overview', icon: LayoutDashboard, to: '/people', subject: 'employee' },
      { key: 'directory', label: 'Directory', icon: Users, to: '/people/directory', subject: 'employee' },
      { key: 'org-chart', label: 'Reporting Chart', icon: Network, to: '/people/org', subject: 'employee' },
      { key: 'onboard', label: 'Onboard employee', icon: UserPlus, to: '/people/onboard', subject: 'employee' },
      {
        key: 'people-queues',
        label: 'HR queues',
        icon: ClipboardList,
        subject: 'employee',
        children: [
          { key: 'queue-probation', label: 'Probation', icon: CalendarClock, to: '/people/queues/probation', subject: 'employee' },
          { key: 'queue-disciplinary', label: 'Disciplinary', icon: ShieldAlert, to: '/people/queues/disciplinary', subject: 'employee' },
          { key: 'queue-suspensions', label: 'Suspensions', icon: ShieldX, to: '/people/queues/suspensions', subject: 'employee' },
          { key: 'queue-certifications', label: 'Certifications', icon: Sparkles, to: '/people/queues/certifications', subject: 'employee' },
          { key: 'queue-hr-requisition-approval', label: 'HR Requisition Assign', icon: ClipboardCheck, to: '/people/queues/hr-requisition-approval', subject: 'employee' },
          // `payroll.advance.hr` → can('hr', 'payroll.advance') — the endpoint's own gate.
          { key: 'queue-hr-lta-print', label: 'HR LTA Print', icon: Printer, to: '/people/queues/hr-lta-print', subject: 'payroll.advance', verb: 'hr' },
        ],
      },
      {
        key: 'lookups',
        label: 'Lookups',
        icon: Tags,
        to: '/admin/lookups',
        subject: 'employee',
        verb: 'write',
      },
      { key: 'components', label: 'Salary components', icon: Sliders, to: '/payroll/components', subject: 'payroll' },
      { key: 'salary-component-transactions', label: 'Employee Allowances', icon: ScrollText, to: '/people/salary-component-transactions', subject: 'employee' },
      { key: 'annual-leave-transaction', label: 'Annual Leave Transaction', icon: CalendarRange, to: '/people/annual-leave-transaction', subject: 'employee' },
      { key: 'termination', label: 'Termination Stages', icon: UserMinus, to: '/people/termination', subject: 'employee' },
    ],
  },
  {
    key: 'time',
    label: 'Time',
    items: [
      { key: 'today', label: 'Today', icon: Clock, to: '/time/today', requiresEmployee: true },
      { key: 'attendance', label: 'My Attendance', icon: CalendarClock, to: '/time/attendance', requiresEmployee: true },
      { key: 'team-attendance', label: 'Team Attendance', icon: Users, to: '/time/team', requiresDirectReports: true },
      { key: 'leaves', label: 'Leaves', icon: Plane, to: '/time/leaves', requiresEmployee: true },
      { key: 'short-leave', label: 'Short Leave', icon: Timer, to: '/time/short-leave', requiresEmployee: true },
      { key: 'overtime', label: 'Overtime', icon: Sparkles, to: '/time/overtime', requiresEmployee: true },
      {
        // Not requiresDirectReports: someone holding a SUBMIT delegation may
        // manage nobody and still need this page, and the sidebar can't see
        // delegations. The page itself explains when there's nobody to file for.
        //
        // Gated to the TEAM_REQUEST_GENERATION role only — managers and
        // admins do not get this tab. `requiresRole` has no admin bypass,
        // which is the point.
        key: 'team-requests',
        label: 'Team requests',
        icon: UserCheck,
        to: '/time/team-requests',
        requiresEmployee: true,
        requiresRole: ['TEAM_REQUEST_GENERATION'],
      },
      {
        // Read-only tracker for what the caller filed from Team requests —
        // same TEAM_REQUEST_GENERATION gate (no admin bypass), and the page's
        // `beforeLoad` runs the same check.
        key: 'generated-team-requests',
        label: 'Generated team requests',
        icon: ListChecks,
        to: '/time/generated-requests',
        requiresEmployee: true,
        requiresRole: ['TEAM_REQUEST_GENERATION'],
      },
      {
        // Manager-level surface, same gate as Hiring Requisitions below:
        // raising a travel request is a manager's call, so this is gated on
        // the MANAGER role rather than on merely being a linked employee.
        // `requiresRole` has no admin bypass, so an ADMIN / HR account
        // without MANAGER does not get the row either. The route's
        // `beforeLoad` runs the same check.
        //
        // Approving a travel request lands in /inbox, so approvers don't
        // need this row.
        key: 'factory-tour',
        label: 'Travel Request',
        icon: Truck,
        to: '/self-service/travel-request',
        requiresEmployee: true,
        requiresRole: ['MANAGER'],
      },
      {
        // Manager-level surface: raising a headcount request is a manager's
        // call, so this is gated on the MANAGER role rather than on merely
        // being a linked employee. `requiresRole` has no admin bypass, so an
        // ADMIN / HR account without MANAGER does not get the row either —
        // add the codes here if they should.
        //
        // Not `requiresDirectReports`: the role is the grant, and that gate
        // carries a ROLE_ADMIN bypass we don't want here.
        //
        // Approving a requisition is a different surface — it lands in
        // /inbox, so approvers don't need this row.
        key: 'employee-requisition',
        label: 'Hiring Requisitions',
        icon: ClipboardCheck,
        to: '/admin/employee-requisition',
        requiresEmployee: true,
        requiresRole: ['MANAGER'],
      },
      { key: 'holidays', label: 'Holidays', icon: CalendarCheck, to: '/time/holidays' },
    ],
  },
  {
    key: 'attendance',
    label: 'Attendance',
    items: [
      {
        key: 'attendance-dashboard',
        label: 'Operator dashboard',
        icon: LayoutDashboard,
        to: '/attendance',
        anyOf: [
          { subject: 'attendance', verb: 'manage' },
          { subject: 'attendance', verb: 'operator' },
        ],
      },
      // {
      //   key: 'attendance-live',
      //   label: 'Live monitoring',
      //   icon: Activity,
      //   to: '/attendance/live',
      //   anyOf: [
      //     { subject: 'attendance', verb: 'manage' },
      //     { subject: 'attendance', verb: 'operator' },
      //   ],
      // },
      {
        key: 'attendance-records',
        label: 'Daily records',
        icon: ClipboardList,
        to: '/attendance/records',
        anyOf: [
          { subject: 'attendance', verb: 'manage' },
          { subject: 'attendance', verb: 'operator' },
        ],
      },
      {
        key: 'attendance-punches',
        label: 'Punch log',
        icon: Fingerprint,
        to: '/attendance/punches',
        anyOf: [
          { subject: 'attendance', verb: 'manage' },
          { subject: 'attendance', verb: 'operator' },
        ],
      },
      {
        key: 'attendance-operator-punch',
        label: 'Operator-add punch',
        icon: Plus,
        to: '/attendance/operator-punch',
        subject: 'attendance',
        verb: 'operator',
      },
      {
        key: 'attendance-shift-overrides',
        label: 'Shift overrides',
        icon: CalendarRange,
        to: '/attendance/shift-overrides',
        subject: 'attendance',
        verb: 'operator',
      },
      {
        key: 'attendance-periods',
        label: 'Period locks',
        icon: Lock,
        to: '/attendance/periods',
        subject: 'attendance',
        verb: 'manage',
      },
    ],
  },
  // {
  //   key: 'canteen',
  //   label: 'Canteen',
  //   items: [
  //     {
  //       key: 'canteen-attendance',
  //       label: 'Attendance',
  //       icon: ClipboardList,
  //       to: '/canteen/attendance',
  //       anyOf: [
  //         { subject: 'canteen.attendance' },
  //         { subject: 'canteen' },
  //         { subject: 'canteen', verb: 'manage' },
  //         { subject: 'attendance', verb: 'manage' },
  //       ],
  //     },
  //     {
  //       key: 'canteen-devices',
  //       label: 'Canteen devices',
  //       icon: MonitorSmartphone,
  //       to: '/canteen/devices',
  //       anyOf: [
  //         { subject: 'canteen.devices' },
  //         { subject: 'canteen' },
  //         { subject: 'canteen', verb: 'manage' },
  //         { subject: 'attendance', verb: 'manage' },
  //       ],
  //     },
  //     {
  //       key: 'canteen-employees',
  //       label: 'Employees',
  //       icon: UtensilsCrossed,
  //       to: '/canteen/employees',
  //       anyOf: [
  //         { subject: 'canteen.employees' },
  //         { subject: 'canteen' },
  //         { subject: 'canteen', verb: 'manage' },
  //         { subject: 'attendance', verb: 'manage' },
  //       ],
  //     },
  //   ],
  // },
  {
    key: 'money',
    label: 'Money',
    items: [
      { key: 'payslips', label: 'My Payslips', icon: Receipt, to: '/payroll/payslips', requiresEmployee: true },
      { key: 'team-payslips', label: 'Team payslips', icon: Users, to: '/payroll/team', requiresEmployee: true, requiresDirectReports: true },
      { key: 'queue', label: 'Disbursement queue', icon: Hand, to: '/payroll/queue', subject: 'payroll' },
      { key: 'runs', label: 'Payroll runs', icon: Wallet, to: '/payroll/runs', subject: 'payroll' },
      // Payroll-admin only (`payroll.admin` → CASL `manage:payroll`); the route guards the same check.
      { key: 'bonuses', label: 'Bonus runs', icon: Gift, to: '/payroll/bonuses', subject: 'payroll', verb: 'manage' },
      { key: 'off-cycle', label: 'Off-cycle payments', icon: Receipt, to: '/payroll/off-cycle', subject: 'payroll' },
      // { key: 'employee-benefits', label: 'Employee benefits', icon: Gift, to: '/payroll/employee-benefits', subject: 'payroll' },
      // { key: 'increments', label: 'Increments', icon: TrendingUp, to: '/payroll/increments', subject: 'payroll' },
      { key: 'advances', label: 'Salary advances', icon: Banknote, to: '/payroll/advances', requiresEmployee: true },
      { key: 'short-term-advances', label: 'Short-term advances', icon: Coins, to: '/payroll/short-term-advances', requiresEmployee: true },
      { key: 'advance-ledger', label: 'Advance recovery', icon: BookOpen, to: '/payroll/advance-ledger', subject: 'payroll' },
      { key: 'statutory', label: 'Payroll config & FY', icon: Landmark, to: '/payroll/statutory', subject: 'payroll' },
    ],
  },
  {
    key: 'insights',
    label: 'Insights',
    items: [
      {
        key: 'reports',
        label: 'Reports',
        icon: FileBarChart,
        to: '/reports',
        anyOf: [
          { subject: 'employee.reports', verb: 'run' },
          { subject: 'attendance.reports', verb: 'run' },
          { subject: 'attendance.leaves.reports', verb: 'run' },
          { subject: 'payroll.reports', verb: 'run' },
        ],
      },
      // {
      //   key: 'reports-jobs',
      //   label: 'Job history',
      //   icon: History,
      //   to: '/reports/jobs',
      //   anyOf: [
      //     { subject: 'employee.reports', verb: 'run' },
      //     { subject: 'attendance.reports', verb: 'run' },
      //     { subject: 'attendance.leaves.reports', verb: 'run' },
      //     { subject: 'payroll.reports', verb: 'run' },
      //   ],
      // },
      {
        key: 'analytics',
        label: 'Analytics',
        icon: BarChart3,
        children: [
          {
            key: 'analytics-overview',
            label: 'Overview',
            to: '/analytics',
            anyOf: [
              { subject: 'employee.analytics', verb: 'read' },
              { subject: 'attendance.analytics', verb: 'read' },
              { subject: 'leaves.analytics', verb: 'read' },
              { subject: 'payroll.analytics', verb: 'read' },
            ],
          },
          {
            key: 'analytics-people',
            label: 'People',
            to: '/analytics/people',
            subject: 'employee.analytics',
          },
          {
            key: 'analytics-attendance',
            label: 'Attendance',
            to: '/analytics/attendance',
            subject: 'attendance.analytics',
          },
          {
            key: 'analytics-leaves',
            label: 'Leaves',
            to: '/analytics/leaves',
            subject: 'leaves.analytics',
          },
          {
            key: 'analytics-payroll',
            label: 'Payroll',
            to: '/analytics/payroll',
            subject: 'payroll.analytics',
          },
        ],
      },
    ],
  },
  {
    key: 'admin',
    label: 'Admin',
    items: [
      {
        key: 'admin-overview',
        label: 'Control plane',
        icon: LayoutDashboard,
        to: '/admin',
        anyOf: ADMIN_TIER,
      },
      {
        key: 'iam',
        label: 'Users & Roles',
        icon: ShieldCheck,
        children: [
          { key: 'admin-users', label: 'Users', to: '/admin/users', subject: 'auth.users' },
          { key: 'admin-roles', label: 'Roles & Permissions', to: '/admin/roles', subject: 'auth.roles' },
        ],
      },
      {
        key: 'audit-pipeline',
        label: 'Audit',
        icon: Activity,
        children: [
          { key: 'audit-explorer', label: 'Explorer', icon: Search, to: '/admin/audit', subject: 'audit' },
          { key: 'audit-health', label: 'Health', icon: Heart, to: '/admin/audit/health', subject: 'audit' },
          { key: 'audit-dead-letters', label: 'Dead-letters', icon: Mailbox, to: '/admin/audit/dead-letters', subject: 'audit' },
          { key: 'audit-replay', label: 'Replay', icon: RotateCcw, to: '/admin/audit/replay', subject: 'audit' },
        ],
      },
      {
        key: 'admin-sagas',
        label: 'Sagas',
        icon: Workflow,
        to: '/admin/sagas',
        anyOf: [
          { subject: 'employee', verb: 'write' },
          { subject: 'payroll', verb: 'write' },
        ],
      },
      {
        key: 'admin-devices',
        label: 'Devices',
        icon: MonitorSmartphone,
        to: '/admin/devices',
        subject: 'attendance',
        verb: 'manage',
      },
      {
        key: 'notifications',
        label: 'Notifications',
        icon: Bell,
        children: [
          { key: 'admin-announce', label: 'Announce', to: '/admin/notifications', subject: 'notification', verb: 'announce' },
          { key: 'admin-templates', label: 'Templates', to: '/admin/notifications/templates', subject: 'notification.template' },
        ],
      },
    ],
  },
];

/**
 * Accounts (Preview) workspace nav. Mock surface for the standard
 * manufacturing-company finance workflow — GL, AP/AR, bank, cost,
 * tax, period close, reports. Pages render fixture data; no permission
 * gates yet (workspace toggle controls visibility instead).
 */
export const accountsNavGroups: NavGroup[] = [
  {
    key: 'a-overview',
    label: 'Overview',
    items: [
      { key: 'a-dashboard', label: 'Executive dashboard', icon: Gauge, to: '/accounts' },
      { key: 'a-close', label: 'Period close', icon: CalendarCheck, to: '/accounts/close' },
    ],
  },
  {
    key: 'a-ledger',
    label: 'General ledger',
    items: [
      { key: 'a-journal', label: 'Journal entries', icon: BookOpen, to: '/accounts/journal' },
      { key: 'a-coa', label: 'Chart of accounts', icon: Network, to: '/accounts/coa' },
      { key: 'a-trial-balance', label: 'Trial balance', icon: ScrollText, to: '/accounts/trial-balance' },
    ],
  },
  {
    key: 'a-payables',
    label: 'Payables & receivables',
    items: [
      { key: 'a-ap', label: 'Vendor bills', icon: ReceiptText, to: '/accounts/payables' },
      { key: 'a-ar', label: 'Customer invoices', icon: FileText, to: '/accounts/receivables' },
      { key: 'a-vendors', label: 'Vendors', icon: Building2, to: '/accounts/vendors' },
      { key: 'a-customers', label: 'Customers', icon: Users, to: '/accounts/customers' },
    ],
  },
  {
    key: 'a-banking',
    label: 'Banking & cash',
    items: [
      { key: 'a-bank', label: 'Bank book', icon: Banknote, to: '/accounts/bank' },
      { key: 'a-cash', label: 'Cash position', icon: Coins, to: '/accounts/cash' },
    ],
  },
  {
    key: 'a-mfg',
    label: 'Manufacturing',
    items: [
      { key: 'a-inventory', label: 'Inventory & WIP', icon: Package, to: '/accounts/inventory' },
      { key: 'a-cogs', label: 'Cost of production', icon: Wrench, to: '/accounts/cogs' },
      { key: 'a-assets', label: 'Fixed assets', icon: Landmark, to: '/accounts/assets' },
    ],
  },
  {
    key: 'a-compliance',
    label: 'Tax & compliance',
    items: [
      { key: 'a-sales-tax', label: 'Sales tax (FBR)', icon: Percent, to: '/accounts/tax/sales' },
      { key: 'a-withholding', label: 'Withholding', icon: Percent, to: '/accounts/tax/withholding' },
    ],
  },
  {
    key: 'a-reports',
    label: 'Reports',
    items: [
      { key: 'a-pl', label: 'Profit & loss', icon: LineChart, to: '/accounts/reports/profit-loss' },
      { key: 'a-bs', label: 'Balance sheet', icon: BarChart3, to: '/accounts/reports/balance-sheet' },
      { key: 'a-cashflow', label: 'Cash flow', icon: TrendingUp, to: '/accounts/reports/cash-flow' },
      { key: 'a-builder', label: 'Custom reports', icon: FileBarChart, to: '/accounts/reports' },
    ],
  },
];

/**
 * Assistant (Preview) workspace nav. Mock surface for in-house AI features —
 * chatbot, policy Q&A, semantic employee search, summarisation, and the
 * compliance/ops corners (transcripts review, ingest runs). All pages
 * render fixture data; no permission gates yet (workspace toggle controls
 * visibility instead).
 */
export const aiNavGroups: NavGroup[] = [
  {
    key: 'ai-conversations',
    label: 'Conversations',
    items: [
      { key: 'ai-chat', label: 'Chat', icon: Bot, to: '/ai/chat' },
      { key: 'ai-policy', label: 'Policy Q&A', icon: BookOpen, to: '/ai/policy' },
    ],
  },
  {
    key: 'ai-discover',
    label: 'Discover',
    items: [
      { key: 'ai-search', label: 'Semantic search', icon: ScanSearch, to: '/ai/search' },
      { key: 'ai-summaries', label: 'Summaries gallery', icon: FileSearch, to: '/ai/summaries' },
    ],
  },
  {
    key: 'ai-admin',
    label: 'Admin (preview)',
    items: [
      { key: 'ai-admin-conversations', label: 'All transcripts', icon: FileScan, to: '/ai/admin/conversations' },
      { key: 'ai-admin-ingest', label: 'Ingest runs', icon: Database, to: '/ai/admin/ingest' },
    ],
  },
];

/**
 * Backwards-compatible alias — older callers (palette search, breadcrumb)
 * still import `navGroups` and want the HR tree.
 */
export const navGroups = hrNavGroups;

/**
 * Selector used by the Sidebar to render the active workspace's tree.
 *
 * HR is the only workspace now. `accountsNavGroups` / `aiNavGroups` above are
 * deliberately kept: the /accounts and /ai routes still exist and stay
 * reachable by direct URL, so restoring either preview is a branch here plus
 * a WORKSPACES entry in the Topbar switcher.
 */
export function navGroupsForWorkspace(_workspace: Workspace): NavGroup[] {
  return hrNavGroups;
}
