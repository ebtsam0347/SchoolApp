// "Print Statement" for a final settlement — the one-page dues statement
// PDF from GET /reports/payroll/settlements/{id}/statement. Used on the
// /people/termination list rows and in SettlementReviewDrawer.
//
// Hidden without `payroll.reports.run`. The PDF opens in a new tab so HR
// can print straight away; if the popup is blocked it downloads instead.
// Every status prints — the PDF itself stamps DRAFT / REJECTED / CANCELLED.

import { useContext } from 'react';
import { useMutation } from '@tanstack/react-query';
import { App as AntdApp, Button, Tooltip, type ButtonProps } from 'antd';
import { Printer } from 'lucide-react';

import { AbilityContext } from '@/auth';
import { getErrorMessage, isApiError } from '@/lib';
import { payrollReportsApi, type FinalSettlementStatus } from '@/payroll';
import { saveBlob } from '@/reports';

/** Keep the blob URL alive long enough for the new tab to load it. */
const REVOKE_AFTER_MS = 60_000;

/** User-facing copy for a failed statement request. */
function settlementStatementErrorMessage(e: unknown): string {
  if (isApiError(e)) {
    if (e.status === 404) return 'Settlement not found.';
    if (e.status === 403) return "You don't have permission to print settlement statements.";
    if (e.status === 502) return "Couldn't load employee details, please try again.";
  }
  return getErrorMessage(e);
}

export function SettlementStatementButton({
  settlementId,
  status,
  iconOnly = false,
  size,
}: {
  settlementId: number;
  /** Drives the DRAFT hint; unknown while the details are loading. */
  status?: FinalSettlementStatus;
  /** Compact icon button for table rows. */
  iconOnly?: boolean;
  size?: ButtonProps['size'];
}) {
  const ability = useContext(AbilityContext);
  const { message } = AntdApp.useApp();

  const mutation = useMutation({
    mutationFn: async (tab: Window | null) => {
      try {
        return { tab, file: await payrollReportsApi.settlementStatement(settlementId) };
      } catch (e) {
        tab?.close();
        throw e;
      }
    },
    onSuccess: ({ tab, file }) => {
      if (tab && !tab.closed) {
        const url = URL.createObjectURL(file.blob);
        tab.location.href = url;
        window.setTimeout(() => URL.revokeObjectURL(url), REVOKE_AFTER_MS);
      } else {
        saveBlob(file.blob, file.filename);
      }
    },
    onError: (e) => {
      if (isApiError(e)) {
        console.error('Settlement statement failed', { correlationId: e.correlationId });
      }
      message.error(settlementStatementErrorMessage(e));
    },
  });

  if (!ability.can('run', 'payroll.reports')) return null;

  const onClick = () => {
    if (mutation.isPending) return;
    // Open the tab inside the click so the popup blocker allows it; the PDF
    // is loaded into it once fetched. `null` means blocked → download.
    const tab = window.open('', '_blank');
    if (tab) {
      tab.opener = null;
      tab.document.title = 'Settlement statement';
      tab.document.body.textContent = 'Loading settlement statement…';
    }
    mutation.mutate(tab);
  };

  const hint =
    status === 'DRAFT'
      ? 'Draft — prints as NOT FOR PAYMENT.'
      : iconOnly
        ? 'Print Statement'
        : undefined;

  return (
    <Tooltip title={hint}>
      <Button
        size={size}
        icon={<Printer size={iconOnly ? 14 : 16} strokeWidth={1.75} />}
        loading={mutation.isPending}
        disabled={mutation.isPending}
        aria-label="Print Statement"
        onClick={onClick}
      >
        {iconOnly ? null : 'Print Statement'}
      </Button>
    </Tooltip>
  );
}
