// Account Reports as /reports catalog entries. The accounts registry
// (payroll-service) isn't part of the BFF catalog, so each definition is
// adapted to a `ReportCatalogEntry` — the catalog page then renders it
// with the same card, grouping and search as every other report, and
// opens <AccountsRunDrawer> on Run.

import { useQuery } from '@tanstack/react-query';

import type { ReportCatalogEntry } from '../types';
import { accountsReportsApi } from './api';
import { isDocument, visibleParams } from './filters';
import type { AccountsReportDefinition, AccountsReportOutput } from './types';

export type AccountsCatalogEntry = ReportCatalogEntry & { accounts: AccountsReportDefinition };

export const isAccountsEntry = (e: ReportCatalogEntry): e is AccountsCatalogEntry =>
  'accounts' in e;

const DESCRIPTION: Record<AccountsReportOutput, string> = {
  LIST: 'Employee-wise list with grand totals. Download as Excel or CSV.',
  GROUPED_LIST: 'Employee list grouped by bank, with a subtotal per bank. Download as Excel or CSV.',
  SUMMARY: 'Department-wise totals. Download as Excel or CSV.',
  SLIP: 'One slip per employee — preview and print the PDF, or download the list as Excel or CSV.',
  LETTER: 'Transfer letter for the selected bank — preview and print the PDF, or download the list.',
  LEDGER: "One employee's advances and recoveries with a running balance — PDF, Excel or CSV.",
};

const PRINTABLE_LIST =
  'Employee-wise list with grand totals — preview and print the PDF, or download as Excel or CSV.';

export function toCatalogEntry(def: AccountsReportDefinition): AccountsCatalogEntry {
  return {
    id: `accounts.${def.id}`,
    service: 'accounts',
    title: `${def.id} — ${def.title}`,
    description:
      def.output === 'LIST' && isDocument(def) ? PRINTABLE_LIST : (DESCRIPTION[def.output] ?? ''),
    format: isDocument(def) ? 'pdf' : 'xlsx',
    mode: 'sync',
    method: 'POST',
    path: `/reports/payroll/accounts/${encodeURIComponent(def.id)}/export`,
    permission: 'payroll.reports.run',
    userVisibleAudit: true,
    parameters: visibleParams(def).map((p) => ({
      name: p.param,
      location: 'body',
      type: 'string',
      required: p.required,
      values: null,
      help: null,
    })),
    accounts: def,
  };
}

/** Registry group label ("Salary", "Permanent Advance" …) — the catalog's sub-heading. */
export const accountsGroupLabel = (e: ReportCatalogEntry) =>
  isAccountsEntry(e) ? e.accounts.groupLabel : null;

/**
 * Every implemented account report as catalog entries, in registry order.
 * Only fetched for callers with `payroll.reports.run`.
 */
export function useAccountsCatalog(enabled: boolean) {
  return useQuery({
    queryKey: ['reports', 'accounts', 'registry'],
    queryFn: ({ signal }) => accountsReportsApi.registry(signal),
    select: (defs) => defs.map(toCatalogEntry),
    enabled,
    // The registry changes with deployments, not data.
    staleTime: 30 * 60 * 1000,
  });
}
