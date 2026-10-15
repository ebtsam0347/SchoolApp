// Run drawer for one Account Report — the accounts counterpart of the
// catalog's <RunReportDrawer>, with the same layout: format / mode pills,
// description, error alert, hint, then the report's filters.
//
// On submit the chosen format decides the call:
//   pdf        /document  → handed to `onPdf` (the catalog's preview drawer)
//   xlsx, csv  /export    → downloaded in the browser
//
// Filter violations (400) land under their fields; anything else (nothing
// to print, bank account missing, employee service down) stays in the
// drawer as an alert so the user can adjust and resubmit.

import { useMemo, useState } from 'react';
import { Alert, App as AntdApp, Form, Typography } from 'antd';
import { useMutation } from '@tanstack/react-query';

import { DrawerForm, StatusPill } from '@/components';
import { applyApiErrorToForm } from '@/lib';

import { saveBlob, type ReportFile } from '../download';
import { saveSlipLayout } from '../slip-layout';
import { FORMAT_LABEL, FORMAT_TONE, MODE_LABEL, MODE_TONE } from '../catalog/labels';
import { AccountsFields } from './AccountsFields';
import { accountsReportsApi } from './api';
import type { AccountsCatalogEntry } from './catalog';
import { describeAccountsError } from './errors';
import {
  defaultFormValues,
  formToBody,
  hasParam,
  hasSlipLayout,
  violationFieldMap,
  type FilterFormValues,
} from './filters';
import { FILTER_HINTS, reportKey } from './notes';

const { Paragraph } = Typography;

export interface AccountsRunDrawerProps {
  entry: AccountsCatalogEntry | null;
  open: boolean;
  onClose: () => void;
  /** A PDF rendered — open it in the preview drawer. */
  onPdf: (entry: AccountsCatalogEntry, file: ReportFile) => void;
}

export function AccountsRunDrawer({ entry, ...rest }: AccountsRunDrawerProps) {
  if (!entry) return null;
  // One form per report: each report has its own fields and defaults, and
  // a form instance only takes `initialValues` when it is first created.
  return <RunDrawer key={entry.accounts.id} entry={entry} {...rest} />;
}

function RunDrawer({
  entry,
  open,
  onClose,
  onPdf,
}: Omit<AccountsRunDrawerProps, 'entry'> & { entry: AccountsCatalogEntry }) {
  const def = entry.accounts;
  const [form] = Form.useForm<FilterFormValues>();
  const { message } = AntdApp.useApp();
  const [error, setError] = useState<unknown>(null);
  const initialValues = useMemo(() => defaultFormValues(def), [def]);
  const format = Form.useWatch('format', form) ?? initialValues.format;

  const mutation = useMutation({
    mutationFn: async (values: FilterFormValues) => {
      const body = formToBody(def, values);
      if (values.format === 'pdf') {
        const language = hasParam(def, 'SLIP_LANGUAGE') ? values.language : undefined;
        const layout = hasSlipLayout(def) ? values.layout : undefined;
        if (layout) saveSlipLayout(layout);
        return { pdf: await accountsReportsApi.document(def.id, body, language, layout) };
      }
      const file = await accountsReportsApi.export(def.id, values.format === 'csv' ? 'csv' : 'xlsx', body);
      saveBlob(file.blob, file.filename);
      return { pdf: null };
    },
    onMutate: () => setError(null),
    onSuccess: ({ pdf }) => {
      onClose();
      if (pdf) onPdf(entry, pdf);
      else message.success('Download starting…');
    },
    onError: (err) => {
      const { applied, unmapped } = applyApiErrorToForm(form, err, {
        fieldMap: violationFieldMap(def),
      });
      if (applied.length === 0 || unmapped.length > 0) setError(err);
    },
  });

  const hint = FILTER_HINTS[reportKey(def.id)];
  const copy = error ? describeAccountsError(error) : null;

  return (
    <DrawerForm<FilterFormValues>
      title={entry.title}
      open={open}
      onClose={onClose}
      form={form}
      initialValues={initialValues}
      submitText={format === 'pdf' ? 'Preview' : 'Download'}
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 8 }}>
        <StatusPill tone={FORMAT_TONE[entry.format]}>{FORMAT_LABEL[entry.format]}</StatusPill>
        <StatusPill tone={MODE_TONE[entry.mode]}>{MODE_LABEL[entry.mode]}</StatusPill>
      </div>
      <Paragraph type="secondary" style={{ marginBottom: 16 }}>
        {entry.description}
      </Paragraph>
      {copy ? (
        <Alert
          type={copy.retryable ? 'error' : 'warning'}
          showIcon
          style={{ marginBottom: 16 }}
          title={copy.title}
          description={copy.detail}
        />
      ) : null}
      {hint ? <Alert type="info" showIcon style={{ marginBottom: 16 }} title={hint} /> : null}

      <AccountsFields def={def} />
    </DrawerForm>
  );
}
