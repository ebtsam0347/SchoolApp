// Drawer for one skipped employee on a payroll run.
//
// Opened from a row in RunSkipsSection. Shows why the employee has no
// payslip, a preview of the payslip a retry would compute (computed and
// rolled back server-side), and their running permanent advance. While the
// run is COMPUTED, HR can waive this month's advance installment: the retry
// deducts 0 and writes a 0 ledger row, but the advance's own installment is
// untouched, so next month deducts as usual. Then retry this one employee.

import {
  Alert,
  App as AntdApp,
  Button,
  Checkbox,
  Descriptions,
  Drawer,
  Space,
  Tag,
  Typography,
} from 'antd';
import { Link } from '@tanstack/react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ExternalLink, RotateCcw } from 'lucide-react';

import { MoneyCell, PersonCell, RequestState } from '@/components';
import { getErrorMessage, useIsDesktop } from '@/lib';
import {
  MANUALLY_UNDONE,
  payrollRunsApi,
  type PayrollRunSkipDto,
  type PayslipLineDto,
} from '@/payroll';

const { Text } = Typography;

export type RunSkipDrawerProps = {
  runId: number;
  skip: PayrollRunSkipDto | null;
  /** Run is COMPUTED — the installment can change and the row can retry. */
  editable: boolean;
  /** Caller's CASL `manage:payroll` resolution. */
  canWrite: boolean;
  fixHref: string | null;
  retrying: boolean;
  onRetry: (employeeId: number) => void;
  onClose: () => void;
};

export function RunSkipDrawer({
  runId,
  skip,
  editable,
  canWrite,
  fixHref,
  retrying,
  onRetry,
  onClose,
}: RunSkipDrawerProps) {
  const isDesktop = useIsDesktop();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const employeeId = skip?.employeeId ?? null;
  const advanceKey = ['payroll', 'run', runId, 'skip-advance', employeeId];
  const previewKey = ['payroll', 'run', runId, 'skip-preview', employeeId];

  const advanceQuery = useQuery({
    queryKey: advanceKey,
    queryFn: ({ signal }) =>
      payrollRunsApi.skipAdvance(runId, employeeId!, signal),
    enabled: employeeId != null,
  });
  const advance = advanceQuery.data;

  const previewQuery = useQuery({
    queryKey: previewKey,
    queryFn: ({ signal }) =>
      payrollRunsApi.previewSkip(runId, employeeId!, signal),
    enabled: employeeId != null,
    // A full compute on every call: refetch only when the waiver changes
    // (invalidated below) or the drawer reopens.
    staleTime: Infinity,
    retry: false,
  });
  const preview = previewQuery.data;

  const waive = useMutation({
    mutationFn: (waived: boolean) =>
      payrollRunsApi.setSkipAdvanceWaiver(runId, employeeId!, { waived }),
    onSuccess: (dto) => {
      queryClient.setQueryData(advanceKey, dto);
      void queryClient.invalidateQueries({ queryKey: previewKey });
      message.success(
        dto.waivedThisMonth
          ? 'No advance deduction this month. Retry to compute with it.'
          : 'Advance deduction restored for this month.',
      );
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  const canEdit = editable && canWrite && advance?.advanceId != null;

  return (
    <Drawer
      open={skip != null}
      onClose={onClose}
      destroyOnHidden
      size={isDesktop ? 'min(560px, 100vw)' : '100%'}
      title="Skipped employee"
      extra={
        skip && (
          <Space size={8}>
            {fixHref && (
              <Link to={fixHref}>
                <Button icon={<ExternalLink size={14} strokeWidth={1.75} />}>
                  Fix data
                </Button>
              </Link>
            )}
            {canWrite && editable && (
              <Button
                type="primary"
                icon={<RotateCcw size={14} strokeWidth={1.75} />}
                loading={retrying}
                onClick={() => onRetry(skip.employeeId)}
              >
                Retry
              </Button>
            )}
          </Space>
        )
      }
    >
      {skip && (
        <Space orientation="vertical" size={20} style={{ width: '100%' }}>
          <PersonCell
            size="large"
            to={`/people/${skip.employeeId}`}
            person={{
              fullName: skip.employeeFullName,
              employeeCode: skip.employeeCode,
            }}
          />

          <Alert
            type={skip.errorCode === MANUALLY_UNDONE ? 'warning' : 'error'}
            showIcon
            title={
              skip.errorCode === MANUALLY_UNDONE ? 'Payslip undone' : skip.errorCode
            }
            description={skip.message}
          />

          <div>
            <Text strong>Salary preview</Text>
            <Text type="secondary" style={{ display: 'block' }}>
              What the retry would compute now. Nothing is saved.
            </Text>
            <RequestState
              loading={previewQuery.isLoading || previewQuery.isFetching}
              error={previewQuery.isError ? previewQuery.error : undefined}
              errorTitle="The retry would fail"
            >
              {preview && (
                <Space orientation="vertical" size={12} style={{ width: '100%', marginTop: 8 }}>
                  <Descriptions size="small" column={1} bordered>
                    <Descriptions.Item label="Paid days">{preview.paidDays}</Descriptions.Item>
                    <Descriptions.Item label="Gross">
                      <MoneyCell amount={preview.gross} />
                    </Descriptions.Item>
                    <Descriptions.Item label="Total deductions">
                      <MoneyCell amount={preview.deductions + preview.tax + preview.eobi} />
                    </Descriptions.Item>
                    <Descriptions.Item label={<Text strong>Net salary</Text>}>
                      <Text strong>
                        <MoneyCell amount={preview.net} />
                      </Text>
                    </Descriptions.Item>
                  </Descriptions>
                  <PreviewLines
                    title="Earnings"
                    lines={(preview.lines ?? []).filter((l) => l.type === 'EARNING')}
                  />
                  <PreviewLines
                    title="Deductions"
                    lines={(preview.lines ?? []).filter((l) => l.type !== 'EARNING')}
                    extra={[
                      { label: 'Income tax', amount: preview.tax },
                      { label: 'EOBI', amount: preview.eobi },
                    ]}
                  />
                </Space>
              )}
            </RequestState>
          </div>

          <div>
            <Text strong>Permanent advance</Text>
            <RequestState
              loading={advanceQuery.isLoading}
              error={advanceQuery.isError ? advanceQuery.error : undefined}
              errorTitle="Couldn't load the permanent advance"
            >
              {advance?.advanceId == null ? (
                <Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
                  No running permanent advance.
                </Text>
              ) : (
                <Space orientation="vertical" size={12} style={{ width: '100%', marginTop: 8 }}>
                  <Descriptions size="small" column={1} bordered>
                    <Descriptions.Item label="Advance">
                      <Tag style={{ marginInlineEnd: 0 }}>{advance.requestCode}</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="Total taken">
                      <MoneyCell amount={advance.taken} />
                    </Descriptions.Item>
                    <Descriptions.Item label="Already deducted">
                      <MoneyCell amount={advance.deducted} />
                    </Descriptions.Item>
                    <Descriptions.Item label="Outstanding">
                      <MoneyCell amount={advance.outstanding} />
                    </Descriptions.Item>
                    <Descriptions.Item label="Monthly installment">
                      <MoneyCell amount={advance.monthlyDeduction ?? 0} />
                    </Descriptions.Item>
                    <Descriptions.Item label="This month">
                      {advance.waivedThisMonth ? (
                        <Tag color="orange" style={{ marginInlineEnd: 0 }}>
                          Not deducted
                        </Tag>
                      ) : (
                        <MoneyCell
                          amount={Math.min(advance.monthlyDeduction ?? 0, advance.outstanding)}
                        />
                      )}
                    </Descriptions.Item>
                  </Descriptions>

                  {canEdit && (
                    <div>
                      <Checkbox
                        checked={advance.waivedThisMonth}
                        disabled={waive.isPending}
                        onChange={(e) => waive.mutate(e.target.checked)}
                      >
                        Don't deduct the advance this month
                      </Checkbox>
                      <Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
                        The retry deducts 0 and records a 0 entry in the advance ledger. The
                        monthly installment stays the same, so next month deducts as usual.
                      </Text>
                    </div>
                  )}
                </Space>
              )}
            </RequestState>
          </div>
        </Space>
      )}
    </Drawer>
  );
}

/** Non-zero lines of one side of the previewed payslip. */
function PreviewLines({
  title,
  lines,
  extra = [],
}: {
  title: string;
  lines: PayslipLineDto[];
  extra?: { label: string; amount: number }[];
}) {
  const rows = [
    ...lines.map((l) => ({ key: `line-${l.code}-${l.displayOrder}`, label: l.name, amount: l.amount })),
    ...extra.map((e) => ({ key: e.label, label: e.label, amount: e.amount })),
  ].filter((r) => r.amount !== 0);
  if (rows.length === 0) return null;
  return (
    <Descriptions size="small" column={1} title={title}>
      {rows.map((r) => (
        <Descriptions.Item key={r.key} label={r.label}>
          <MoneyCell amount={r.amount} />
        </Descriptions.Item>
      ))}
    </Descriptions>
  );
}
