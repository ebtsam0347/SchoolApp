// Review drawer for one submitted final settlement on /people/termination.
//
// Loads the full breakdown from GET /settlement/employees/submitted-settlements/{id}
// and lays it out near full-screen so everything reads at a glance without
// expanding sections: identity strip → additions | deductions | net + audit →
// attendance / overtime / leave cards → advance & bonus tables. The stage
// action (with its note/reason) is pinned in the footer:
// - DRAFT:       Approve → HR_APPROVED, or Reject → REJECTED (final).
// - HR_APPROVED: Cancel approval (POST /cancel); reason required.

import { useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App as AntdApp,
  Avatar,
  Button,
  Drawer,
  Form,
  Input,
  Modal,
  Skeleton,
  Table,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import {
  Calculator,
  CalendarClock,
  CircleCheck,
  CircleX,
  Eye,
  ClipboardList,
  Gift,
  History,
  Landmark,
  Minus,
  Package,
  PackageX,
  Plus,
  Timer,
  TrendingDown,
  TrendingUp,
  Undo2,
  UserRound,
  Wallet,
} from 'lucide-react';

import {
  ACTIVATION_STATUS_LABEL,
  AssetDetailModal,
  belongingIssuanceApi,
  type BelongingIssuanceDto,
} from '@/belongings';
import { KvList, MoneyCell } from '@/components';
import { getErrorMessage } from '@/lib';
import { formatDateShort, formatDateTime } from '@/lib/dates';
import { settlementApi, type FinalSettlementDetailsDto } from '@/payroll';
import { InfoCard, LedgerCard, type LedgerRowItem } from './action-modals';
import { SettlementStatementButton } from './SettlementStatementButton';

const { Text } = Typography;

export type SettlementReviewMode = 'review' | 'cancel';

type Action = 'approve' | 'reject' | 'cancel';

/** An asset blocks HR approval until its clearance has been approved. */
function isCleared(asset: BelongingIssuanceDto) {
  return asset.clearanceStatus === 'APPROVED';
}

function clearanceTag(asset: BelongingIssuanceDto) {
  if (isCleared(asset)) return <Tag color="green" style={{ marginInlineEnd: 0 }}>Cleared</Tag>;
  if (asset.clearanceStatus === 'REJECTED') {
    return <Tag color="red" style={{ marginInlineEnd: 0 }}>Clearance rejected</Tag>;
  }
  return <Tag color="orange" style={{ marginInlineEnd: 0 }}>Not cleared</Tag>;
}

const CONDITION_LABEL: Record<NonNullable<BelongingIssuanceDto['condition']>, string> = {
  NEW: 'New',
  USED: 'Used',
  OLD: 'Old',
};

/** Every field the issuance API returns, for the expanded asset row. */
function AssetDetails({ asset }: { asset: BelongingIssuanceDto }) {
  const pct = (v: number | undefined) => (v != null ? `${v}%` : '—');
  return (
    <KvList
      minColumnWidth={150}
      items={[
        { label: 'Asset', value: asset.belongingName ?? '—' },
        { label: 'Model', value: asset.assetModel ?? '—' },
        { label: 'Year', value: asset.assetYear ?? '—' },
        {
          label: 'Price',
          value: asset.assetPrice != null ? <MoneyCell amount={asset.assetPrice} /> : '—',
        },
        { label: 'Condition', value: asset.condition ? CONDITION_LABEL[asset.condition] : '—' },
        { label: 'Issued on', value: formatDateShort(asset.issuedDate) || '—' },
        { label: 'Expires on', value: formatDateShort(asset.expiryDate) || '—' },
        {
          label: 'Activation',
          value: asset.activationStatus
            ? `${ACTIVATION_STATUS_LABEL[asset.activationStatus]}${
                asset.activationDate ? ` · ${formatDateShort(asset.activationDate)}` : ''
              }`
            : '—',
        },
        { label: 'Buy-back (employee)', value: pct(asset.buyBackPercEmployee) },
        { label: 'Buy-back (employer)', value: pct(asset.buyBackPercEmployer) },
        { label: 'Clearance', value: clearanceTag(asset) },
        { label: 'Clearance ID', value: asset.clearanceId || '—' },
        { label: 'Cleared on', value: formatDateShort(asset.clearanceDate) || '—' },
        ...(asset.description
          ? [{ label: 'Description', value: asset.description, span: 2 }]
          : []),
      ]}
    />
  );
}

/**
 * One line per asset: name/model on the left, clearance + view on the right.
 * `expandable` adds a +/− toggle that opens the full issuance record inline.
 */
function AssetRows({
  assets,
  onView,
  expandable = false,
}: {
  assets: BelongingIssuanceDto[];
  onView?: (asset: BelongingIssuanceDto) => void;
  expandable?: boolean;
}) {
  const { token } = antdTheme.useToken();
  const [open, setOpen] = useState<Set<number>>(() => new Set());
  const toggle = (i: number) =>
    setOpen((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      return next;
    });

  return (
    <>
      {assets.map((asset, i) => {
        const isOpen = expandable && open.has(i);
        return (
          <div
            key={asset.id ?? i}
            style={{ borderTop: i > 0 ? `1px solid ${token.colorBorderSecondary}` : undefined }}
          >
            <div
              role={expandable ? 'button' : undefined}
              tabIndex={expandable ? 0 : undefined}
              aria-expanded={expandable ? isOpen : undefined}
              onClick={expandable ? () => toggle(i) : undefined}
              onKeyDown={
                expandable
                  ? (e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        toggle(i);
                      }
                    }
                  : undefined
              }
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 8,
                padding: expandable ? '10px 12px' : '8px 0',
                cursor: expandable ? 'pointer' : undefined,
                background: isOpen ? token.colorFillAlter : undefined,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
                {expandable ? (
                  <span
                    aria-hidden
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      width: 20,
                      height: 20,
                      flexShrink: 0,
                      borderRadius: token.borderRadiusSM,
                      border: `1px solid ${token.colorBorder}`,
                      color: token.colorPrimary,
                      background: token.colorBgContainer,
                    }}
                  >
                    {isOpen ? <Minus size={12} /> : <Plus size={12} />}
                  </span>
                ) : null}
                <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                  <Text style={{ fontSize: 13 }} ellipsis>
                    {asset.belongingName ?? '—'}
                  </Text>
                  {asset.assetModel ? (
                    <Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                      {asset.assetModel}
                    </Text>
                  ) : null}
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>
                {clearanceTag(asset)}
                {onView ? (
                  <Button
                    size="small"
                    icon={<Eye size={14} />}
                    onClick={(e) => {
                      e.stopPropagation();
                      onView(asset);
                    }}
                    aria-label="View asset details"
                  />
                ) : null}
              </div>
            </div>
            {isOpen ? (
              <div
                style={{
                  padding: '12px 12px 14px 42px',
                  background: token.colorFillAlter,
                  borderTop: `1px dashed ${token.colorBorderSecondary}`,
                }}
              >
                <AssetDetails asset={asset} />
              </div>
            ) : null}
          </div>
        );
      })}
    </>
  );
}

function minutesToHours(minutes: number | null | undefined) {
  const m = minutes ?? 0;
  return `${Math.floor(m / 60)}h ${m % 60}m`;
}

export function SettlementReviewDrawer({
  settlementId,
  mode,
  statusPill,
  onClose,
}: {
  settlementId: number;
  mode: SettlementReviewMode;
  /** Rendered status badge — the list page owns the status colours. */
  statusPill: (status: FinalSettlementDetailsDto['status']) => ReactNode;
  onClose: () => void;
}) {
  const { token } = antdTheme.useToken();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<{ text?: string }>();
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['settlements', 'details', settlementId],
    queryFn: ({ signal }) => settlementApi.details(settlementId, signal),
  });
  const s = query.data;

  // Same source as the termination form's asset card. HR can't approve
  // while any of these is still awaiting clearance.
  const employeeId = s?.employee.employeeId;
  const assetsQuery = useQuery({
    queryKey: ['belonging-issuance', 'employee', employeeId, 'active'],
    queryFn: ({ signal }) => belongingIssuanceApi.employeeActive(employeeId!, signal),
    enabled: employeeId != null,
  });
  const assets = assetsQuery.data ?? [];
  const unclearedAssets = assets.filter((a) => !isCleared(a));
  const [blockedOpen, setBlockedOpen] = useState(false);

  const mutation = useMutation({
    mutationFn: ({ action, text }: { action: Action; text?: string }) =>
      action === 'approve'
        ? settlementApi.approve(settlementId, text)
        : action === 'reject'
          ? settlementApi.reject(settlementId, text)
          : settlementApi.cancel(settlementId, text ?? ''),
    onSuccess: (_result, { action }) => {
      void queryClient.invalidateQueries({ queryKey: ['settlements'] });
      message.success(
        action === 'approve'
          ? 'Settlement approved.'
          : action === 'reject'
            ? 'Settlement rejected.'
            : 'Approval cancelled.',
      );
      onClose();
    },
    onError: (e) => setError(getErrorMessage(e)),
  });

  const run = async (action: Action) => {
    setError(null);
    if (action === 'approve' && unclearedAssets.length > 0) {
      setBlockedOpen(true);
      return;
    }
    const { text } = await form.validateFields();
    mutation.mutate({ action, text: text?.trim() || undefined });
  };
  const pendingAction = mutation.isPending ? mutation.variables?.action : undefined;

  // Note/reason sits beside the buttons in the pinned footer, so the
  // decision is always on screen no matter how long the breakdown is.
  const footer = (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {error ? <Alert type="error" showIcon title={error} /> : null}
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <Form form={form} style={{ flex: '1 1 360px', minWidth: 0 }}>
          <Form.Item
            name="text"
            style={{ marginBottom: 0 }}
            rules={
              mode === 'cancel'
                ? [{ required: true, whitespace: true, message: 'Reason is required to cancel' }]
                : [{ max: 500, message: 'Max 500 characters' }]
            }
          >
            <Input.TextArea
              autoSize={{ minRows: 1, maxRows: 3 }}
              maxLength={500}
              placeholder={
                mode === 'review'
                  ? 'Note (optional) — rejection is final and can\'t be undone'
                  : 'Reason for cancelling (required) — reverses the settlement and the termination'
              }
            />
          </Form.Item>
        </Form>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
          <SettlementStatementButton size="large" settlementId={settlementId} status={s?.status} />
          <Button size="large" onClick={onClose} disabled={mutation.isPending}>
            Close
          </Button>
          {mode === 'review' ? (
            <>
              <Button
                size="large"
                danger
                icon={<CircleX size={16} />}
                loading={pendingAction === 'reject'}
                disabled={!s || mutation.isPending}
                onClick={() => void run('reject')}
              >
                Reject
              </Button>
              <Button
                size="large"
                type="primary"
                icon={<CircleCheck size={16} />}
                loading={pendingAction === 'approve'}
                // Wait for the asset check so approval can't slip past it.
                disabled={!s || mutation.isPending || assetsQuery.isLoading}
                onClick={() => void run('approve')}
              >
                Approve
              </Button>
            </>
          ) : (
            <Button
              size="large"
              type="primary"
              danger
              icon={<Undo2 size={16} />}
              loading={pendingAction === 'cancel'}
              disabled={!s}
              onClick={() => void run('cancel')}
            >
              Cancel approval
            </Button>
          )}
        </div>
      </div>
    </div>
  );

  return (
    <Drawer
      open
      // Leaves room for the expanded sidebar (256px) so the nav stays visible.
      size="calc(100vw - 256px)"
      destroyOnHidden
      onClose={onClose}
      styles={{ body: { padding: 16 } }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
          <Wallet size={18} color={token.colorPrimary} />
          <span style={{ fontWeight: 600 }}>Settlement #{settlementId}</span>
          {s ? statusPill(s.status) : null}
        </div>
      }
      footer={footer}
    >
      {query.isLoading ? <Skeleton active paragraph={{ rows: 12 }} /> : null}
      {query.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load the settlement"
          description={getErrorMessage(query.error)}
        />
      ) : null}
      {s ? (
        <SettlementBody
          s={s}
          assets={assets}
          assetsLoading={assetsQuery.isLoading}
          assetsError={assetsQuery.isError ? getErrorMessage(assetsQuery.error) : null}
        />
      ) : null}
      <Modal
        open={blockedOpen}
        onCancel={() => setBlockedOpen(false)}
        width={640}
        title={
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            <PackageX size={18} color={token.colorError} />
            Assets not cleared
          </span>
        }
        footer={
          <Button type="primary" onClick={() => setBlockedOpen(false)}>
            OK
          </Button>
        }
      >
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 12 }}
          title={`${s?.employee.employeeName ?? 'This employee'} still has ${unclearedAssets.length} asset${unclearedAssets.length === 1 ? '' : 's'} / belonging${unclearedAssets.length === 1 ? '' : 's'} pending clearance.`}
          description="Get these cleared first — HR approval stays blocked until every allocated asset is cleared."
        />
        <div
          style={{
            border: `1px solid ${token.colorBorderSecondary}`,
            borderRadius: token.borderRadiusLG,
            overflow: 'hidden',
          }}
        >
          <AssetRows assets={unclearedAssets} expandable />
        </div>
      </Modal>
    </Drawer>
  );
}

function SettlementBody({
  s,
  assets,
  assetsLoading,
  assetsError,
}: {
  s: FinalSettlementDetailsDto;
  assets: BelongingIssuanceDto[];
  assetsLoading: boolean;
  assetsError: string | null;
}) {
  const { token } = antdTheme.useToken();
  const [selectedAsset, setSelectedAsset] = useState<BelongingIssuanceDto | null>(null);
  const unclearedCount = assets.filter((a) => !isCleared(a)).length;
  const e = s.employee;

  const additionRows: LedgerRowItem[] = [
    {
      label: 'Basic salary (earned)',
      hint: (
        <>
          Of monthly basic <MoneyCell amount={s.earnedSalary.monthlyBasicSalary} compact />
        </>
      ),
      amount: s.earnedSalary.earnedBasicSalary,
    },
    { label: 'Allowances', amount: s.earnedSalary.allowances },
    {
      label: 'Gratuity',
      hint: s.gratuity.eligible
        ? `${s.gratuity.completedYears} completed years of service`
        : 'Not eligible',
      amount: s.gratuity.amount,
    },
    ...(s.annualLeave
      ? [
          {
            label: 'Annual leave encashment',
            hint: `${s.annualLeave.leaveName} · ${s.annualLeave.payableDays} payable days`,
            amount: s.annualLeave.encashmentAmount,
          },
        ]
      : []),
    ...(s.medicalAndCasualLeave && s.medicalAndCasualLeave.encashmentAmount > 0
      ? [
          {
            label: 'Medical & casual encashment',
            hint: `${s.medicalAndCasualLeave.payableDays} payable days`,
            amount: s.medicalAndCasualLeave.encashmentAmount,
          },
        ]
      : []),
    {
      label: 'Bonus',
      hint: s.bonus.events.length > 0 ? `${s.bonus.events.length} outstanding event(s)` : undefined,
      amount: s.bonus.outstanding,
    },
  ];

  const deductionRows: LedgerRowItem[] = [
    {
      label: 'Advances',
      hint: (
        <>
          Permanent <MoneyCell amount={s.advances.permanent} compact /> · Short term{' '}
          <MoneyCell amount={s.advances.shortTerm} compact /> · RFD{' '}
          <MoneyCell amount={s.advances.rfd} compact />
        </>
      ),
      amount: s.advances.total,
    },
    ...(s.annualExcessiveLeave && s.annualExcessiveLeave.excessiveAmount > 0
      ? [
          {
            label: 'Annual leave — excessive',
            hint: `${s.annualExcessiveLeave.excessiveDays} excessive days`,
            amount: s.annualExcessiveLeave.excessiveAmount,
          },
        ]
      : []),
    ...(s.medicalAndCasualExcessiveLeave && s.medicalAndCasualExcessiveLeave.excessiveAmount > 0
      ? [
          {
            label: 'Medical & casual leave — excessive',
            hint: `${s.medicalAndCasualExcessiveLeave.excessiveDays} excessive days`,
            amount: s.medicalAndCasualExcessiveLeave.excessiveAmount,
          },
        ]
      : []),
    { label: 'Fair price shop', amount: s.fairPriceShop?.outstandingAmount ?? 0 },
    { label: 'Canteen', amount: s.canteen?.amount ?? 0 },
    ...(s.outstandingDeductions ?? []).map((d) => ({
      label: d.deductionName || d.deductionCode,
      hint: d.deductionName ? d.deductionCode : undefined,
      amount: d.amount,
    })),
    { label: 'Tax withheld', amount: s.totals.tax },
  ];

  const att = s.attendanceDetail;
  const ot = s.normalOvertimeDetail;
  const al = s.annualLeave;
  const mc = s.medicalAndCasualLeave;

  const auditLine = (at: string | null, by: number | null) =>
    at ? `${formatDateTime(at)}${by ? ` · user #${by}` : ''}` : '—';

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* Identity strip */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          flexWrap: 'wrap',
          gap: 16,
          padding: '12px 16px',
          borderRadius: token.borderRadiusLG,
          background: token.colorFillAlter,
          border: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 0 }}>
          <Avatar
            size={52}
            icon={<UserRound size={26} />}
            style={{ background: token.colorPrimary, flexShrink: 0 }}
          />
          <div style={{ minWidth: 0 }}>
            <Text strong style={{ fontSize: token.fontSizeLG, display: 'block', lineHeight: 1.25 }}>
              {e.employeeName}
            </Text>
            <Text type="secondary">
              {[e.designation, e.department, e.section].filter(Boolean).join(' · ')}
            </Text>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Tag>Code {e.employeeCode}</Tag>
          {e.grade ? (
            <Tag>
              Grade {e.grade}
              {e.gradeType ? ` · ${e.gradeType}` : ''}
            </Tag>
          ) : null}
          {e.hireDate ? <Tag>Hired {formatDateShort(e.hireDate)}</Tag> : null}
          {e.gratuityStartDate ? <Tag>Gratuity from {formatDateShort(e.gratuityStartDate)}</Tag> : null}
          <Tag color="red">Last working day {formatDateShort(s.lastWorkingDay)}</Tag>
        </div>
      </div>

      {/* Money: additions | deductions | net payable + remarks */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
          gap: 12,
          alignItems: 'stretch',
        }}
      >
        <LedgerCard
          title="Additions"
          icon={<TrendingUp size={18} />}
          tone="addition"
          rows={additionRows}
          totalLabel="Gross settlement"
          totalAmount={s.totals.grossSettlement}
        />
        <LedgerCard
          title="Deductions"
          icon={<TrendingDown size={18} />}
          tone="deduction"
          rows={deductionRows}
          totalLabel="Total deductions"
          totalAmount={s.totals.totalDeductions}
        />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minWidth: 0 }}>
          <div
            style={{
              padding: '18px 20px',
              borderRadius: token.borderRadiusLG,
              background: token.colorPrimaryBg,
              border: `1px solid ${token.colorPrimaryBorder}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
              <Calculator size={18} color={token.colorPrimary} />
              <Text strong style={{ fontSize: token.fontSizeLG }}>
                Net payable
              </Text>
            </div>
            <MoneyCell
              amount={s.totals.netSettlement}
              showSign
              coloredSign
              style={{ fontWeight: 700, fontSize: token.fontSizeHeading2, display: 'block' }}
            />
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'auto 1fr',
                gap: '4px 12px',
                marginTop: 10,
                fontSize: 13,
              }}
            >
              <Text type="secondary">Gross</Text>
              <MoneyCell amount={s.totals.grossSettlement} style={{ textAlign: 'right' }} />
              <Text type="secondary">Deductions</Text>
              <MoneyCell amount={s.totals.totalDeductions} style={{ textAlign: 'right' }} />
              <Text type="secondary">Tax</Text>
              <MoneyCell amount={s.totals.tax} style={{ textAlign: 'right' }} />
            </div>
          </div>
          {s.remarks ? (
            <Alert type="info" showIcon title="Remarks" description={s.remarks} />
          ) : null}
        </div>
      </div>

      {/* Audit + assets: kept out of the money row so the ledger columns
          size to their own rows instead of stretching to a tall sidebar. */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
          gap: 12,
          alignItems: 'stretch',
        }}
      >
        <InfoCard icon={<History size={16} />} title="Audit trail">
          <KvList
            minColumnWidth={180}
            items={[
              { label: 'Created', value: auditLine(s.audit.createdAt, s.audit.createdBy) },
              { label: 'HR approved', value: auditLine(s.audit.approvedAt, s.audit.approvedBy) },
              { label: 'Paid', value: auditLine(s.audit.paidAt, s.audit.paidBy) },
              { label: 'Last updated', value: formatDateTime(s.audit.updatedAt) },
            ]}
          />
        </InfoCard>
        <InfoCard
          icon={<Package size={16} />}
          title="Allocated assets"
          extra={
            assets.length > 0 ? (
              <span style={{ color: unclearedCount > 0 ? token.colorError : token.colorSuccess }}>
                {unclearedCount > 0 ? `${unclearedCount} not cleared` : 'All cleared'}
              </span>
            ) : undefined
          }
        >
          {assetsLoading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
          {assetsError ? (
            <Alert type="error" showIcon title="Couldn't load allocated assets" description={assetsError} />
          ) : null}
          {!assetsLoading && !assetsError && assets.length === 0 ? (
            <Text type="secondary">No allocated assets</Text>
          ) : null}
          <AssetRows assets={assets} onView={setSelectedAsset} />
        </InfoCard>
      </div>

      {/* Attendance / overtime / leave — always open, side by side */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 12,
          alignItems: 'stretch',
        }}
      >
        {att ? (
          <InfoCard
            icon={<CalendarClock size={16} />}
            title="Attendance"
            extra={`${formatDateShort(att.periodFrom)} → ${formatDateShort(att.periodTo)}`}
          >
            <KvList
              minColumnWidth={110}
              items={[
                { label: 'Present', value: att.presentDays },
                { label: 'Half days', value: att.halfDays },
                { label: 'Absent', value: att.absentDays },
                { label: 'Leave', value: att.leaveDays },
                { label: 'Paid leave', value: att.paidLeaveDays },
                { label: 'Unpaid leave', value: att.unpaidLeaveDays },
                { label: 'Weekends', value: att.weekendDays },
                { label: 'Holidays', value: att.holidayDays },
              ]}
            />
          </InfoCard>
        ) : null}
        {ot ? (
          <InfoCard icon={<Timer size={16} />} title="Normal overtime">
            <KvList
              minColumnWidth={110}
              items={[
                {
                  label: 'Approved',
                  value: (
                    <>
                      {minutesToHours(ot.approvedMinutes)} · <MoneyCell amount={ot.approvedAmount} compact />
                    </>
                  ),
                },
                {
                  label: 'Pending',
                  value: (
                    <>
                      {minutesToHours(ot.pendingMinutes)} · <MoneyCell amount={ot.pendingAmount} compact />
                    </>
                  ),
                },
                {
                  label: 'Unapplied',
                  value: (
                    <>
                      {minutesToHours(ot.unappliedMinutes)} · <MoneyCell amount={ot.unappliedAmount} compact />
                    </>
                  ),
                },
                {
                  label: 'Total',
                  value: (
                    <strong>
                      {minutesToHours(ot.totalMinutes)} · <MoneyCell amount={ot.totalAmount} compact />
                    </strong>
                  ),
                },
              ]}
            />
          </InfoCard>
        ) : null}
        {al ? (
          <InfoCard
            icon={<ClipboardList size={16} />}
            title={al.leaveName || 'Annual leave'}
            extra={`${formatDateShort(al.cycleStart)} → ${formatDateShort(al.cycleEnd)}`}
          >
            <KvList
              minColumnWidth={110}
              items={[
                { label: 'Allocated', value: al.allocated },
                { label: 'Accrued', value: al.accrued },
                { label: 'Used', value: al.used },
                { label: 'Pending', value: al.pending },
                { label: 'Payable days', value: al.payableDays },
                { label: 'Excessive days', value: al.excessiveDays },
              ]}
            />
          </InfoCard>
        ) : null}
        {mc ? (
          <InfoCard icon={<ClipboardList size={16} />} title="Medical & casual leave">
            <KvList
              minColumnWidth={110}
              items={[
                { label: 'Allocated', value: mc.allocated },
                { label: 'Earned', value: mc.earned },
                { label: 'Used', value: mc.used },
                { label: 'Pending', value: mc.pending },
                { label: 'Payable days', value: mc.payableDays },
                { label: 'Excessive days', value: mc.excessiveDays },
              ]}
            />
          </InfoCard>
        ) : null}
      </div>

      {/* Breakdown tables — only when there's something to list */}
      {s.permanentAdvances.advances.length > 0 || s.bonus.events.length > 0 ? (
        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(520px, 1fr))',
            gap: 12,
            alignItems: 'start',
          }}
        >
          {s.permanentAdvances.advances.length > 0 ? (
            <InfoCard
              icon={<Landmark size={16} />}
              title={`Permanent advances (${s.permanentAdvances.advances.length})`}
              extra={
                <>
                  Outstanding <MoneyCell amount={s.permanentAdvances.totalOutstanding} compact />
                </>
              }
            >
              <Table
                size="small"
                pagination={false}
                scroll={{ x: 'max-content' }}
                rowKey="advanceId"
                dataSource={s.permanentAdvances.advances}
                columns={[
                  { title: 'Request', dataIndex: 'requestCode' },
                  {
                    title: 'Received',
                    dataIndex: 'receivingDate',
                    render: (v: string) => formatDateShort(v),
                  },
                  { title: 'Status', dataIndex: 'advanceStatus', render: (v: string) => <Tag>{v}</Tag> },
                  { title: 'Original', dataIndex: 'originalAmount', render: (v: number) => <MoneyCell amount={v} /> },
                  { title: 'Recovered', dataIndex: 'recoveredAmount', render: (v: number) => <MoneyCell amount={v} /> },
                  {
                    title: 'From settlement',
                    dataIndex: 'settlementRecovered',
                    render: (v: number) => <MoneyCell amount={v} />,
                  },
                  {
                    title: 'Outstanding',
                    dataIndex: 'outstandingAmount',
                    render: (v: number) => <MoneyCell amount={v} />,
                  },
                ]}
              />
            </InfoCard>
          ) : null}
          {s.bonus.events.length > 0 ? (
            <InfoCard icon={<Gift size={16} />} title={`Bonus events (${s.bonus.events.length})`}>
              <Table
                size="small"
                pagination={false}
                scroll={{ x: 'max-content' }}
                rowKey="bonusEventId"
                dataSource={s.bonus.events}
                columns={[
                  { title: 'Event', dataIndex: 'eventName' },
                  { title: 'Type', dataIndex: 'eventType' },
                  {
                    title: 'Reference date',
                    dataIndex: 'referenceDate',
                    render: (v: string) => formatDateShort(v),
                  },
                  { title: 'Eligible', dataIndex: 'eligibleAmount', render: (v: number) => <MoneyCell amount={v} /> },
                  { title: 'Paid', dataIndex: 'paidAmount', render: (v: number) => <MoneyCell amount={v} /> },
                  {
                    title: 'Outstanding',
                    dataIndex: 'outstandingAmount',
                    render: (v: number) => <MoneyCell amount={v} />,
                  },
                ]}
              />
            </InfoCard>
          ) : null}
        </div>
      ) : null}

      <AssetDetailModal
        asset={selectedAsset}
        open={!!selectedAsset}
        onClose={() => setSelectedAsset(null)}
      />
    </div>
  );
}
