// Bulk short-term advance — one batch, N employees, single target month
// deduction. Pick employees via the typeahead, then set per-row amounts.
// "Apply to all" stamps the default amount on every line that's still
// empty (or every line, if the row is hovered). Submit hits POST
// /short-term-advance-batches with one line per row.

import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Radio,
  Space,
  Table,
  Typography,
  Upload,
  theme as antdTheme,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useEffect, useMemo, useState } from 'react';
import { Boxes, Download, Trash2, UploadCloud } from 'lucide-react';
import * as XLSX from 'xlsx';

import { DrawerForm, MoneyCell, PersonCell } from '@/components';
import { handleFormError } from '@/lib';
import { EmployeePicker } from '@/people/EmployeePicker';
import type { EmployeePickerCandidate } from '@/people/employee-picker';
import { catalogsApi, employeesApi } from '@/people/api';
import { useDepartmentRoster } from '@/people/department-roster';
import type { EmployeeDto } from '@/people/types';
import { shortTermAdvancesApi } from '../api';
import type { CreateShortTermAdvanceBatchRequest } from '../types';

const EXCEL_TEMPLATE_HEADERS = ['CardNo', 'Amount'] as const;

type SkippedRow = { code: string; reason: string };

export type BulkApplyShortTermAdvanceDrawerProps = {
  open: boolean;
  onClose: () => void;
  /**
   * Which roster the employee picker and the Excel importer may draw from.
   *
   * - `department` — everyone in the caller's own department. Payroll / HR
   *   raise department-wide batches, so this stays their default.
   * - `directReports` — only the caller's own direct reports, via
   *   /employees/me/team. A manager's department can contain people who
   *   don't report to them, and those aren't theirs to advance.
   * - `ownDepartment` — the caller's department again, but via the
   *   permission-free /employees/me/department/employees. For Team requests,
   *   whose filers hold no `employee.read` (which `department` needs).
   */
  scope?: 'department' | 'directReports' | 'ownDepartment';
  /**
   * Employees to pre-add as lines each time the drawer opens — Team requests
   * opens it from a colleague's row with that colleague already in.
   */
  initialEmployeeIds?: ReadonlyArray<number>;
};

type Values = {
  targetMonth: dayjs.Dayjs;
  defaultAmount?: number;
  notes?: string;
};

type Line = {
  employeeId: number;
  amount: number | null;
};

export function BulkApplyShortTermAdvanceDrawer({
  open,
  onClose,
  scope = 'department',
  initialEmployeeIds,
}: BulkApplyShortTermAdvanceDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const { token } = antdTheme.useToken();

  const [lines, setLines] = useState<Line[]>([]);
  const [addMode, setAddMode] = useState<'manual' | 'excel'>('manual');
  const [excelSkipped, setExcelSkipped] = useState<SkippedRow[]>([]);

  useEffect(() => {
    if (!open) {
      setLines([]);
      setAddMode('manual');
      setExcelSkipped([]);
      form.resetFields();
    }
  }, [open, form]);

  // Seed on open only — keyed by the ids' contents, not the array identity,
  // so a parent re-render doesn't wipe lines the user has since edited.
  const initialKey = initialEmployeeIds?.join(',') ?? '';
  useEffect(() => {
    if (open && initialKey) {
      setLines(
        initialKey.split(',').map((id) => ({ employeeId: Number(id), amount: null })),
      );
    }
  }, [open, initialKey]);

  // Who's logged in, and which department they belong to. Drives the
  // `department` scope below, and labels picker candidates when a specific
  // employee's own department can't be resolved.
  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }: { signal: AbortSignal }) => employeesApi.me(signal),
    enabled: open,
    staleTime: 5 * 60_000,
  });
  const myDepartmentId = meQuery.data?.departmentId;

  const departmentsQuery = useQuery({
    queryKey: ['catalogs', 'departments'],
    queryFn: ({ signal }: { signal: AbortSignal }) => catalogsApi.departments(signal),
    enabled: open,
    staleTime: 5 * 60_000,
  });
  const myDepartmentName = useMemo(
    () => departmentsQuery.data?.find((d) => d.id === myDepartmentId)?.name,
    [departmentsQuery.data, myDepartmentId],
  );

  const byDepartment = scope !== 'directReports';

  // The roster this batch may draw from — fetched whenever the drawer is open.
  // Used both to pre-populate the manual picker (so it isn't empty until a
  // search happens to surface someone in scope) and to resolve employee codes
  // from an uploaded Excel sheet.
  const departmentRosterQuery = useQuery({
    queryKey: ['employees', 'department-roster', myDepartmentId],
    queryFn: ({ signal }: { signal: AbortSignal }) =>
      employeesApi.list({ departmentId: myDepartmentId, size: 2000 }, signal),
    enabled: open && scope === 'department' && myDepartmentId != null,
    staleTime: 60_000,
  });
  // Team-requests scope: same department, no `employee.read` needed.
  const ownDepartmentRoster = useDepartmentRoster({
    enabled: open && scope === 'ownDepartment',
  });
  // Manager scope. Same key + options as the other /employees/me/team callers
  // (team payslips, employee pickers) so they share one cache entry.
  const teamQuery = useQuery({
    queryKey: ['employees', 'me', 'team'],
    queryFn: ({ signal }: { signal: AbortSignal }) => employeesApi.myTeam(signal),
    enabled: open && !byDepartment,
    staleTime: 5 * 60_000,
    retry: false,
  });

  const roster = useMemo<EmployeeDto[]>(
    () =>
      scope === 'ownDepartment'
        ? ownDepartmentRoster.rows
        : scope === 'department'
          ? (departmentRosterQuery.data?.content ?? [])
          : (teamQuery.data ?? []),
    [scope, ownDepartmentRoster.rows, departmentRosterQuery.data, teamQuery.data],
  );
  const rosterLoading =
    scope === 'ownDepartment'
      ? ownDepartmentRoster.isLoading
      : scope === 'department'
        ? departmentRosterQuery.isLoading
        : teamQuery.isLoading;
  /** Human label for the active scope — used in the hint and Excel copy. */
  const scopeLabel = byDepartment
    ? (myDepartmentName ?? 'your department')
    : 'your team';

  const rosterByCode = useMemo(() => {
    const m = new Map<string, EmployeeDto>();
    for (const e of roster) {
      if (e.employeeCode) m.set(e.employeeCode.trim().toUpperCase(), e);
    }
    return m;
  }, [roster]);

  // Same roster keyed by id — the table below renders from this, so a row
  // shows a real name + code the moment it's added.
  const rosterById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const e of roster) {
      if (e.id != null) m.set(e.id, e);
    }
    return m;
  }, [roster]);

  const departmentById = useMemo(() => {
    const m = new Map<number, string>();
    for (const d of departmentsQuery.data ?? []) {
      if (d.id != null && d.name) m.set(d.id, d.name);
    }
    return m;
  }, [departmentsQuery.data]);

  const departmentCandidates = useMemo<EmployeePickerCandidate[]>(
    () =>
      roster
        .filter((e): e is EmployeeDto & { id: number } => e.id != null)
        .map((e) => ({
          id: e.id,
          fullName: e.fullName ?? `#${e.id}`,
          employeeCode: e.employeeCode ?? null,
          departmentName:
            (e.departmentId != null
              ? departmentById.get(e.departmentId)
              : null) ??
            myDepartmentName ??
            null,
          designationName: null,
        })),
    [roster, departmentById, myDepartmentName],
  );

  // Ids the picker may offer. Matching on id rather than department *name*
  // keeps direct-report scope exact when a report sits in another department
  // — a name comparison would silently drop them.
  const allowedIds = useMemo(
    () => new Set(departmentCandidates.map((c) => c.id)),
    [departmentCandidates],
  );

  // Fallback name resolution, for a row that somehow isn't in the roster.
  // Deliberately skipped for roster members: GET /employees/{id} requires
  // `employee.read`, which a manager doesn't hold — firing it per row 403s
  // every time and leaves the table rendering raw `#<id>` placeholders. The
  // roster already carries fullName + employeeCode for everyone the picker
  // can offer, so it answers this without a request.
  const nameQueries = useQueries({
    queries: lines.map((l) => ({
      queryKey: ['employees', 'detail', l.employeeId],
      queryFn: ({ signal }: { signal: AbortSignal }) =>
        employeesApi.get(l.employeeId, signal),
      staleTime: 5 * 60_000,
      enabled: open && !rosterById.has(l.employeeId),
    })),
  });

  const employeeById = useMemo(() => {
    const m = new Map<number, EmployeeDto>();
    for (const q of nameQueries) {
      const e = q.data;
      if (e?.id != null) m.set(e.id, e);
    }
    return m;
  }, [nameQueries]);

  const selectedIds = useMemo(() => lines.map((l) => l.employeeId), [lines]);

  const totalAmount = lines.reduce((s, l) => s + (l.amount ?? 0), 0);
  const incompleteCount = lines.filter(
    (l) => l.amount == null || l.amount <= 0,
  ).length;

  // Why the submit button is disabled, shown next to it in the footer.
  // Without this the button just sits there greyed out with no explanation —
  // the per-row amount is easy to miss once several employees are added, and
  // a batch line with no amount is rejected server-side anyway.
  const blockReason =
    lines.length === 0
      ? 'Add at least one employee to submit.'
      : incompleteCount > 0
        ? `${incompleteCount} ${incompleteCount === 1 ? 'row needs' : 'rows need'} an amount — type one per row, or set a default and press "Apply to all".`
        : null;

  const mutation = useMutation({
    mutationFn: (body: CreateShortTermAdvanceBatchRequest) =>
      shortTermAdvancesApi.create(body),
    onSuccess: () => {
      message.success(`Submitted batch for ${lines.length} employees`);
      queryClient.invalidateQueries({
        queryKey: ['payroll', 'short-term-advance-batches'],
      });
      form.resetFields();
      setLines([]);
      onClose();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const setAmount = (employeeId: number, amount: number | null) => {
    setLines((prev) =>
      prev.map((l) => (l.employeeId === employeeId ? { ...l, amount } : l)),
    );
  };

  const removeRow = (employeeId: number) => {
    setLines((prev) => prev.filter((l) => l.employeeId !== employeeId));
  };

  const onAddEmployees = (ids: number[]) => {
    setLines((prev) => {
      const have = new Set(prev.map((l) => l.employeeId));
      const additions = ids
        .filter((id) => !have.has(id))
        .map((id) => ({ employeeId: id, amount: null }));
      return [...prev, ...additions];
    });
  };

  const downloadExcelTemplate = () => {
    const ws = XLSX.utils.aoa_to_sheet([
      [...EXCEL_TEMPLATE_HEADERS],
      ['1001', 5000],
    ]);
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Advances');
    XLSX.writeFile(wb, 'short-term-advance-template.xlsx');
  };

  const handleExcelFile = async (file: File) => {
    if (roster.length === 0) {
      message.error(
        byDepartment
          ? "Couldn't resolve your department — try again in a moment."
          : "Couldn't resolve your team — try again in a moment.",
      );
      return false;
    }
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: 'array' });
    const sheet = wb.Sheets[wb.SheetNames[0]];
    const rows = XLSX.utils.sheet_to_json<Record<string, unknown>>(sheet, {
      defval: '',
    });

    const skipped: SkippedRow[] = [];
    const additions: Line[] = [];
    const have = new Set(lines.map((l) => l.employeeId));

    for (const row of rows) {
      const code = String(
        row['CardNo'] ??
          row['cardno'] ??
          row['Card No'] ??
          row['card no'] ??
          row['Card Number'] ??
          row['Employee Code'] ??
          row['Code'] ??
          row['code'] ??
          '',
      ).trim();
      if (!code) continue;

      const emp = rosterByCode.get(code.toUpperCase());
      if (!emp?.id) {
        skipped.push({
          code,
          reason: byDepartment ? 'Not in your department' : 'Not in your team',
        });
        continue;
      }
      if (have.has(emp.id)) continue;

      const amountRaw = Number(row['Amount'] ?? row['amount']);
      additions.push({
        employeeId: emp.id,
        amount: Number.isFinite(amountRaw) && amountRaw > 0 ? amountRaw : null,
      });
      have.add(emp.id);
    }

    setLines((prev) => [...prev, ...additions]);
    setExcelSkipped(skipped);
    if (additions.length === 0 && skipped.length === 0) {
      message.warning('No rows found — check the "CardNo" / "Amount" columns.');
    } else {
      message.success(
        `Added ${additions.length} employee${additions.length === 1 ? '' : 's'}` +
          (skipped.length > 0 ? ` · skipped ${skipped.length}` : ''),
      );
    }
    return false;
  };

  const applyDefaultToAll = () => {
    const def = form.getFieldValue('defaultAmount') as number | undefined;
    if (!def || def <= 0) {
      message.warning('Enter a default amount first');
      return;
    }
    setLines((prev) => prev.map((l) => ({ ...l, amount: def })));
  };

  const columns: ColumnsType<Line> = [
    {
      title: 'Employee',
      key: 'employee',
      render: (_v, row) => {
        // Roster first — it's the data the caller is actually permitted to
        // read. The per-id detail query is only a fallback.
        const e =
          rosterById.get(row.employeeId) ?? employeeById.get(row.employeeId);
        return (
          <PersonCell
            size="compact"
            person={{
              fullName: e?.fullName ?? `#${row.employeeId}`,
              employeeCode: e?.employeeCode,
              profilePictureUrl: e?.profilePictureUrl,
            }}
          />
        );
      },
    },
    {
      title: 'Amount (Rs)',
      key: 'amount',
      width: 180,
      render: (_v, row) => (
        <InputNumber
          size="small"
          style={{ width: '100%' }}
          min={0}
          step={500}
          value={row.amount ?? undefined}
          onChange={(v) => setAmount(row.employeeId, typeof v === 'number' ? v : null)}
          placeholder="0"
        />
      ),
    },
    {
      title: '',
      key: 'remove',
      width: 36,
      render: (_v, row) => (
        <Button
          type="text"
          size="small"
          aria-label="Remove"
          icon={<Trash2 size={14} />}
          onClick={() => removeRow(row.employeeId)}
        />
      ),
    },
  ];

  return (
    <DrawerForm<Values>
      title="Bulk salary advance"
      open={open}
      onClose={onClose}
      form={form}
      size={680}
      submitText={
        lines.length > 0 ? `Submit batch · ${lines.length}` : 'Submit batch'
      }
      submitting={mutation.isPending}
      submitDisabled={lines.length === 0 || incompleteCount > 0}
      footerExtra={
        blockReason ? (
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {blockReason}
          </Typography.Text>
        ) : null
      }
      initialValues={{ targetMonth: dayjs().add(1, 'month').startOf('month') }}
      onFinish={(v) => {
        if (lines.length === 0) {
          message.warning('Add at least one employee');
          return;
        }
        const period = v.targetMonth;
        mutation.mutate({
          targetYear: period.year(),
          targetMonth: period.month() + 1,
          notes: v.notes?.trim() || undefined,
          lines: lines.map((l) => ({
            employeeId: l.employeeId,
            amount: l.amount ?? 0,
          })),
        });
      }}
    >
      <Alert
        type="info"
        showIcon
        icon={<Boxes size={14} />}
        title="One batch, one deduction month"
        description="Every employee on this batch gets the listed amount deducted in full from the selected payroll month. HR reviews first, then Accounts approves it."
        style={{ marginBottom: 16 }}
      />

      <Form.Item
        label="Deduct in"
        name="targetMonth"
        rules={[{ required: true, message: 'Pick the payroll month' }]}
      >
        <DatePicker
          picker="month"
          style={{ width: '100%' }}
          format="MMM YYYY"
          allowClear={false}
        />
      </Form.Item>

      <div
        style={{
          display: addMode === 'excel' ? 'none' : 'flex',
          gap: 8,
          alignItems: 'flex-end',
          marginBottom: 12,
        }}
      >
        <Form.Item
          label="Default amount"
          name="defaultAmount"
          tooltip='Stamp the same amount on every row with "Apply to all".'
          style={{ flex: 1, marginBottom: 0 }}
        >
          <InputNumber style={{ width: '100%' }} min={0} addonBefore="Rs" />
        </Form.Item>
        <Button
          onClick={applyDefaultToAll}
          disabled={lines.length === 0}
        >
          Apply to all
        </Button>
      </div>

      <Radio.Group
        value={addMode}
        onChange={(e) => setAddMode(e.target.value)}
        style={{
          display: 'flex',
          border: `1px solid ${token.colorBorder}`,
          borderRadius: token.borderRadius,
          marginBottom: 12,
        }}
      >
        <Radio value="manual" style={{ flex: 1, padding: '8px 12px' }}>
          Manual
        </Radio>
        <Radio value="excel" style={{ flex: 1, padding: '8px 12px' }}>
          Excel
        </Radio>
      </Radio.Group>

      {addMode === 'manual' ? (
        <Form.Item label="Add employees" style={{ marginBottom: 12 }}>
          <EmployeePicker
            multiple
            placeholder="Search by name or code"
            value={selectedIds}
            onChange={onAddEmployees}
            staticCandidates={departmentCandidates}
            filterCandidate={(c) => allowedIds.has(c.id)}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {rosterLoading
              ? 'Loading who you can add…'
              : byDepartment
                ? `Showing ${scopeLabel} employees only.`
                : `Showing your direct reports only (${roster.length}).`}
          </Typography.Text>
        </Form.Item>
      ) : (
        <div style={{ marginBottom: 12 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 8,
            }}
          >
            <Typography.Text strong style={{ fontSize: 13 }}>
              Upload file
            </Typography.Text>
            <Button
              size="small"
              icon={<Download size={14} />}
              onClick={downloadExcelTemplate}
            >
              Download Excel format
            </Button>
          </div>
          <Upload.Dragger
            accept=".xlsx,.xls"
            showUploadList={false}
            beforeUpload={handleExcelFile}
            disabled={roster.length === 0}
          >
            <p style={{ marginBottom: 8 }}>
              <UploadCloud size={20} />
            </p>
            <p style={{ margin: 0 }}>Click or drag an Excel file here</p>
            <p style={{ margin: 0, fontSize: 12, opacity: 0.65 }}>
              Columns: CardNo, Amount. Employees outside {scopeLabel} are
              skipped.
            </p>
          </Upload.Dragger>
          {excelSkipped.length > 0 ? (
            <Alert
              type="warning"
              showIcon
              style={{ marginTop: 8 }}
              title={`${excelSkipped.length} row${excelSkipped.length === 1 ? '' : 's'} skipped`}
              description={excelSkipped
                .slice(0, 10)
                .map((s) => `${s.code} — ${s.reason}`)
                .join('; ')}
            />
          ) : null}
        </div>
      )}

      {lines.length > 0 ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 8,
            fontSize: 12,
            color: token.colorTextSecondary,
          }}
        >
          <span>
            {lines.length} {lines.length === 1 ? 'line' : 'lines'}
            {incompleteCount > 0
              ? ` · ${incompleteCount} missing amount`
              : ''}
          </span>
          <Space size={6}>
            <span>Total</span>
            <Typography.Text strong>
              <MoneyCell amount={totalAmount} />
            </Typography.Text>
          </Space>
        </div>
      ) : null}

      <Table<Line>
        rowKey={(r) => r.employeeId}
        dataSource={lines}
        columns={columns}
        size="small"
        pagination={false}
        scroll={{ y: 320 }}
        locale={{ emptyText: 'Search above to add employees to this batch.' }}
      />

      <Form.Item label="Notes" name="notes" style={{ marginTop: 16 }}>
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Festival advance — packing section."
          maxLength={1024}
          showCount
        />
      </Form.Item>
    </DrawerForm>
  );
}
