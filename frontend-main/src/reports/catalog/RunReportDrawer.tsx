// Parameter-capture drawer for one catalog entry. The form is built
// dynamically from `entry.parameters` via <ParameterField>; on submit we
// branch on `entry.mode`:
//   - sync  → PDFs are fetched and handed to `onPdf` (the preview drawer);
//             anything else goes through runSyncReport, which fires the
//             download in-browser.
//   - async → call runAsyncReport, then route the user to /reports/jobs
//             with `?highlight={id}` so they see the freshly-queued job.
//
// Async submits also stash the user's parameter values in localStorage
// (keyed by job id), so the Jobs page can show them in the row's tooltip
// and offer a Retry that re-runs with the same input. The server doesn't
// echo the request body back on the job row.
//
// Errors that don't map onto a field (e.g. a 422 "No employees match…")
// stay in the drawer as an Alert with the server's `detail`, so the user
// can adjust the filters and resubmit.

import { useState } from 'react';

import { Alert, App as AntdApp, Form, Typography } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import dayjs from 'dayjs';

import { DrawerForm, StatusPill } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';

import {
  fetchSyncReport,
  runAsyncReport,
  runSyncReport,
} from '../bff-api';
import type { ReportFile } from '../download';
import type {
  ReportCatalogEntry,
  ReportParameterValues,
} from '../types';
import { rememberSubmittedJob } from '../jobs/job-history-store';
import { ParameterField } from './ParameterField';
import { decodeSmartValues, renderEntryFields } from './SmartFields';
import {
  FORMAT_LABEL,
  FORMAT_TONE,
  MODE_LABEL,
  MODE_TONE,
} from './labels';
import { REPORT_PRESETS } from './presets';

const { Text, Paragraph } = Typography;

export interface RunReportDrawerProps {
  entry: ReportCatalogEntry | null;
  open: boolean;
  onClose: () => void;
  /** A sync PDF rendered — open it in the preview drawer. */
  onPdf: (entry: ReportCatalogEntry, file: ReportFile) => void;
}

/** The server's own wording for business-rule rejections (422). */
function errorText(err: unknown): string {
  if (isApiError(err) && err.status === 422 && err.problem.detail) {
    return err.problem.detail;
  }
  return getErrorMessage(err);
}

/**
 * Convert the raw form values into the typed shape the bff-api expects.
 * Three steps:
 *   1. `decodeSmartValues` splits any composite widgets (period picker
 *      → year/month ints) back into the catalog parameter shape.
 *   2. Date parameters are converted from Dayjs to ISO `YYYY-MM-DD`.
 *   3. Empty values are dropped so optional parameters don't poison the
 *      URL.
 */
function normalizeParameterValues(
  entry: ReportCatalogEntry,
  raw: Record<string, unknown>,
): ReportParameterValues {
  const decoded = decodeSmartValues(entry, raw);
  const out: ReportParameterValues = {};
  for (const p of entry.parameters) {
    const v = decoded[p.name];
    if (v === undefined || v === null || v === '') continue;
    if (p.type === 'date' && dayjs.isDayjs(v)) {
      out[p.name] = v.format('YYYY-MM-DD');
    } else {
      out[p.name] = v;
    }
  }
  return out;
}

export function RunReportDrawer({ entry, open, onClose, onPdf }: RunReportDrawerProps) {
  const [form] = Form.useForm<Record<string, unknown>>();
  const { message } = AntdApp.useApp();
  const [error, setError] = useState<unknown>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const mutation = useMutation({
    mutationFn: async (raw: Record<string, unknown>) => {
      if (!entry) throw new Error('No report selected');
      const values = normalizeParameterValues(entry, raw);
      if (entry.mode === 'sync' && entry.format === 'pdf') {
        const file = await fetchSyncReport(entry, values);
        return { mode: 'pdf' as const, entry, file };
      }
      if (entry.mode === 'sync') {
        await runSyncReport(entry, values);
        return { mode: 'sync' as const };
      }
      const job = await runAsyncReport(entry, values);
      rememberSubmittedJob(job, entry, values);
      return { mode: 'async' as const, jobId: job.id };
    },
    onMutate: () => setError(null),
    onSuccess: (result) => {
      form.resetFields();
      onClose();
      if (result.mode === 'pdf') {
        onPdf(result.entry, result.file);
      } else if (result.mode === 'sync') {
        message.success('Download starting…');
      } else {
        message.success(`Queued · job #${result.jobId}`);
        void queryClient.invalidateQueries({ queryKey: ['reports', 'jobs'] });
        void navigate({
          to: '/reports/jobs',
          search: { highlight: result.jobId },
        });
      }
    },
    onError: (err) => {
      if (applyApiErrorToForm(form, err).applied.length === 0) setError(err);
    },
  });

  if (!entry) return null;

  const hint = REPORT_PRESETS[entry.id]?.hint;
  const isPdf = entry.mode === 'sync' && entry.format === 'pdf';
  // The form instance is shared across reports — drop a cancelled run's
  // values so they don't leak into the next report's shared filters.
  const handleClose = () => {
    setError(null);
    form.resetFields();
    onClose();
  };

  return (
    <DrawerForm<Record<string, unknown>>
      title={entry.title}
      open={open}
      onClose={handleClose}
      form={form}
      submitText={isPdf ? 'Preview' : entry.mode === 'sync' ? 'Download' : 'Run report'}
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <div
        style={{
          display: 'flex',
          gap: 8,
          flexWrap: 'wrap',
          marginBottom: 8,
        }}
      >
        <StatusPill tone={FORMAT_TONE[entry.format]}>
          {FORMAT_LABEL[entry.format]}
        </StatusPill>
        <StatusPill tone={MODE_TONE[entry.mode]}>
          {MODE_LABEL[entry.mode]}
        </StatusPill>
      </div>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        {entry.description}
      </Paragraph>
      {error ? (
        <Alert
          type={isApiError(error) && error.status === 422 ? 'warning' : 'error'}
          showIcon
          style={{ marginBottom: 16 }}
          title={errorText(error)}
        />
      ) : null}
      {hint ? (
        <Alert type="info" showIcon style={{ marginBottom: 16 }} title={hint} />
      ) : null}

      {entry.parameters.length === 0 ? (
        <Text type="secondary">
          This report doesn't take any parameters — submit to {isPdf ? 'preview' : 'download'}.
        </Text>
      ) : (
        renderEntryFields(entry, (p) => (
          <ParameterField parameter={p} />
        )).map((field) => <span key={field.key}>{field.node}</span>)
      )}
    </DrawerForm>
  );
}
