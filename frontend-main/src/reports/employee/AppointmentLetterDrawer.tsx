// Appointment letter: override form on the left, live PDF preview on the
// right. The PDF is rendered by employee-service (POST with only the
// fields that are set, so everything else takes the server default); the
// preview is an <iframe> on a blob URL that is revoked whenever it's
// replaced and when the drawer closes.
//
// The first preview renders when the drawer opens; after that it only
// refreshes on "Preview" (or implicitly on "Download PDF" when the form has
// changed since), never per keystroke.

import { useContext, useRef, useState, useEffect } from 'react';
import { Link } from '@tanstack/react-router';
import {
  Alert,
  Button,
  DatePicker,
  Drawer,
  Empty,
  Form,
  Input,
  Select,
  Space,
  Spin,
  Switch,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import { Download, Eye, FileSignature, Printer } from 'lucide-react';
import dayjs, { type Dayjs } from 'dayjs';

import { AbilityContext } from '@/auth';
import {
  applyApiErrorToForm,
  getErrorMessage,
  hasCode,
  isApiError,
  isForbidden,
  isNotFound,
  useIsDesktop,
} from '@/lib';
import type { EmployeeDto } from '@/people';

import { saveBlob, type ReportFile } from '../download';
import {
  employeeReportsApi,
  type AppointmentLetterMissingField,
  type AppointmentLetterOverrides,
} from './api';

const { Text } = Typography;
const { useToken } = antdTheme;

type FormValues = {
  letterDate?: Dayjs | null;
  reportingDate?: Dayjs | null;
  probationMonths?: number | null;
  includeSalary?: boolean;
  refNo?: string;
};

type Preview = ReportFile & { url: string };

const PROBATION_OPTIONS = Array.from({ length: 25 }, (_, n) => ({
  value: n,
  label: n === 0 ? 'No probation' : `${n} month${n === 1 ? '' : 's'}`,
}));

/** Where each missing field is fixed, and how to name it. */
const MISSING_FIELD: Record<
  AppointmentLetterMissingField,
  { label: string; tab: '/people/$id/employment' | '/people/$id/salary' }
> = {
  fullName: { label: 'Full name', tab: '/people/$id/employment' },
  designation: { label: 'Designation', tab: '/people/$id/employment' },
  department: { label: 'Department', tab: '/people/$id/employment' },
  joiningDate: { label: 'Joining date', tab: '/people/$id/employment' },
  basicSalary: { label: 'Basic salary (Payroll → Salary)', tab: '/people/$id/salary' },
};

function missingFieldsOf(err: unknown): string[] {
  if (!isApiError(err)) return [];
  const raw = (err.problem as { missingFields?: unknown }).missingFields;
  return Array.isArray(raw) ? raw.filter((f): f is string => typeof f === 'string') : [];
}

/** Only the fields that are set — the server defaults the rest. */
function toOverrides(v: FormValues, canSalary: boolean): AppointmentLetterOverrides {
  const out: AppointmentLetterOverrides = {
    includeSalary: canSalary && v.includeSalary !== false,
  };
  if (v.letterDate) out.letterDate = v.letterDate.format('YYYY-MM-DD');
  if (v.reportingDate) out.reportingDate = v.reportingDate.format('YYYY-MM-DD');
  if (typeof v.probationMonths === 'number') out.probationMonths = v.probationMonths;
  const ref = v.refNo?.trim();
  if (ref) out.refNo = ref;
  return out;
}

export interface AppointmentLetterDrawerProps {
  open: boolean;
  employee: EmployeeDto;
  onClose: () => void;
}

export function AppointmentLetterDrawer({
  open,
  employee,
  onClose,
}: AppointmentLetterDrawerProps) {
  const { token } = useToken();
  const isDesktop = useIsDesktop();
  const ability = useContext(AbilityContext);
  // Salary lines are only served to `payroll.read` (admin implies it).
  const canSalary = ability.can('read', 'payroll');
  const [form] = Form.useForm<FormValues>();
  const iframeRef = useRef<HTMLIFrameElement>(null);

  const [preview, setPreview] = useState<Preview | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);
  // Form edited since the preview on screen was rendered.
  const [stale, setStale] = useState(false);

  // Revoke each blob URL once it's replaced, and the last one on unmount.
  const previewUrl = preview?.url;
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const employeeId = employee.id!;

  async function render(): Promise<Preview | null> {
    let values: FormValues;
    try {
      values = await form.validateFields();
    } catch {
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const file = await employeeReportsApi.appointmentLetter(
        employeeId,
        toOverrides(values, canSalary),
      );
      const next = { ...file, url: URL.createObjectURL(file.blob) };
      setPreview(next);
      setStale(false);
      return next;
    } catch (e) {
      if (isApiError(e) && e.status === 400) applyApiErrorToForm(form, e);
      setError(e);
      return null;
    } finally {
      setLoading(false);
    }
  }

  async function downloadPdf() {
    const file = preview && !stale ? preview : await render();
    if (file) saveBlob(file.blob, file.filename);
  }

  function handleClose() {
    setPreview(null);
    setError(null);
    setStale(false);
    onClose();
  }

  const initialValues: FormValues = {
    letterDate: dayjs(),
    reportingDate: employee.joiningDate ? dayjs(employee.joiningDate) : null,
    includeSalary: canSalary,
  };

  const formPanel = (
    <Form<FormValues>
      form={form}
      layout="vertical"
      initialValues={initialValues}
      onValuesChange={() => setStale(true)}
      requiredMark={false}
    >
      <Form.Item name="letterDate" label="Letter date">
        <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
      </Form.Item>
      <Form.Item name="reportingDate" label="Reporting date" extra="Date of joining.">
        <DatePicker
          style={{ width: '100%' }}
          format="DD MMM YYYY"
          placeholder="Joining date"
        />
      </Form.Item>
      <Form.Item name="probationMonths" label="Probation">
        <Select
          allowClear
          placeholder="Default"
          options={PROBATION_OPTIONS}
        />
      </Form.Item>
      <Form.Item
        name="includeSalary"
        label="Include salary"
        valuePropName="checked"
        extra={
          canSalary
            ? 'Prints basic salary and allowances.'
            : 'Salary needs payroll access'
        }
      >
        <Switch disabled={!canSalary} />
      </Form.Item>
      <Form.Item
        name="refNo"
        label="Reference no."
        rules={[{ max: 64, message: 'At most 64 characters.' }]}
      >
        <Input placeholder="Auto" allowClear />
      </Form.Item>
    </Form>
  );

  const missing = missingFieldsOf(error);
  const errorAlert = error ? (
    <Alert
      type={hasCode(error, 'APPOINTMENT_LETTER_DATA_MISSING') ? 'warning' : 'error'}
      showIcon
      style={{ marginBottom: 12 }}
      title={
        hasCode(error, 'APPOINTMENT_LETTER_DATA_MISSING')
          ? 'Some details are missing for this letter'
          : isForbidden(error)
            ? "You need payroll access to print salary. Turn off 'Include salary'."
            : isApiError(error) &&
                (error.status === 503 || error.code === 'DEPENDENCY_UNAVAILABLE')
              ? 'Payroll is unavailable right now. Try again or print without salary.'
              : isNotFound(error)
                ? 'Employee not found or not in your team.'
                : "Couldn't render the letter"
      }
      description={
        missing.length > 0 ? (
          <ul style={{ margin: 0, paddingLeft: 18 }}>
            {missing.map((f) => {
              const meta = MISSING_FIELD[f as AppointmentLetterMissingField];
              return (
                <li key={f}>
                  {meta ? (
                    <Link
                      to={meta.tab}
                      params={{ id: String(employeeId) }}
                      onClick={handleClose}
                    >
                      {meta.label}
                    </Link>
                  ) : (
                    f
                  )}
                </li>
              );
            })}
          </ul>
        ) : hasCode(error, 'APPOINTMENT_LETTER_DATA_MISSING') ||
          isForbidden(error) ||
          isNotFound(error) ? undefined : (
          getErrorMessage(error)
        )
      }
    />
  ) : null;

  const previewPanel = (
    <div
      style={{
        flex: 1,
        minWidth: 0,
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {errorAlert}
      {stale && preview ? (
        <Text type="secondary" style={{ fontSize: 12, marginBottom: 8 }}>
          You've changed the details — press Preview to update the letter.
        </Text>
      ) : null}
      <div
        style={{
          position: 'relative',
          flex: 1,
          minHeight: isDesktop ? 0 : '70vh',
          border: `1px solid ${token.colorBorderSecondary}`,
          borderRadius: token.borderRadiusLG,
          background: token.colorFillQuaternary,
          overflow: 'hidden',
        }}
      >
        {preview ? (
          <iframe
            ref={iframeRef}
            title="Appointment letter preview"
            src={preview.url}
            style={{ width: '100%', height: '100%', border: 0, display: 'block' }}
          />
        ) : !loading ? (
          <div style={{ height: '100%', display: 'grid', placeItems: 'center' }}>
            <Empty description="Press Preview to render the letter" />
          </div>
        ) : null}
        {loading ? (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              display: 'grid',
              placeItems: 'center',
              background: `color-mix(in srgb, ${token.colorBgContainer} 60%, transparent)`,
            }}
          >
            <Spin />
          </div>
        ) : null}
      </div>
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={handleClose}
      destroyOnHidden
      size={isDesktop ? 900 : '100%'}
      afterOpenChange={(visible) => {
        if (visible && !preview) void render();
      }}
      title={
        <Space size={10}>
          <FileSignature size={16} strokeWidth={1.75} />
          {`Appointment Letter — ${employee.fullName ?? 'Employee'}${
            employee.employeeCode ? ` (${employee.employeeCode})` : ''
          }`}
        </Space>
      }
      styles={{ body: { display: 'flex', flexDirection: 'column' } }}
      footer={
        <Space style={{ width: '100%', justifyContent: 'flex-end' }} wrap>
          <Button
            icon={<Eye size={14} strokeWidth={1.75} />}
            loading={loading}
            onClick={() => void render()}
          >
            Preview
          </Button>
          <Tooltip title={stale ? 'Preview your changes first' : undefined}>
            <Button
              icon={<Printer size={14} strokeWidth={1.75} />}
              disabled={!preview || stale || loading}
              onClick={() => iframeRef.current?.contentWindow?.print()}
            >
              Print
            </Button>
          </Tooltip>
          <Button
            type="primary"
            icon={<Download size={14} strokeWidth={1.75} />}
            disabled={loading}
            onClick={() => void downloadPdf()}
          >
            Download PDF
          </Button>
        </Space>
      }
    >
      <div
        style={{
          flex: 1,
          minHeight: 0,
          display: 'flex',
          flexDirection: isDesktop ? 'row' : 'column',
          gap: 20,
        }}
      >
        <div style={{ width: isDesktop ? 260 : '100%', flexShrink: 0 }}>
          {formPanel}
        </div>
        {previewPanel}
      </div>
    </Drawer>
  );
}
