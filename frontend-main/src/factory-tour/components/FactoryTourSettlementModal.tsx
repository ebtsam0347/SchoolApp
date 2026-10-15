// Modal that lists a trip's settlements
// (GET /travel/requests/{id}/settlements) and prepares one for a chosen
// traveller
// (POST /travel/requests/{id}/participants/{participantId}/settlement/prepare).
//
// Preparing is idempotent and server-driven: the caller supplies nothing but
// who to settle, and the service assembles the DRAFT FINAL settlement from
// the participant's own approved/paid expenses and paid advances. Calling it
// again just returns the same DRAFT/FINAL settlement, which is why the
// button is simply labelled "Prepare settlement" whether or not one exists
// yet — the fresh/returned row lands straight in the list below.

import { useState } from 'react';
import { App as AntdApp, Button, Modal, Select, Table, Typography, type TableProps } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FileText, HandCoins, ReceiptText, SendHorizonal } from 'lucide-react';

import { EmptyState, StatusPill, type StatusPillTone } from '@/components';
import { formatDateTime, getErrorMessage } from '@/lib';
import type { DepartmentDto, DesignationDto, GradeDto } from '@/people';
import {
  travelExpenseApi,
  travelSettlementApi,
  type TravelExpenseResponse,
  type TravelSettlementResponse,
} from '@/travel';
import { printTravelExpenditureSheet } from '../pdf/travelExpenditurePdf';
import type { FactoryTourDto } from '../types';

const { Text } = Typography;

const STATUS_TONE: Record<string, StatusPillTone> = {
  DRAFT: 'neutral',
  PENDING_APPROVAL: 'info',
  APPROVED: 'success',
  REJECTED: 'error',
  PAID: 'success',
};

const EXPENSE_STATUS_TONE: Record<string, StatusPillTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  APPROVED: 'success',
  PARTIALLY_APPROVED: 'warning',
  REJECTED: 'error',
  PAID: 'success',
  CANCELLED: 'neutral',
};

/** Expanded-row content — the settled traveller's own expense claims that fed
 * this settlement's totals. */
function SettlementExpenses({
  participantId,
  currency,
}: {
  participantId: number;
  currency: string;
}) {
  const query = useQuery({
    queryKey: ['travel-participant-expenses', participantId],
    queryFn: ({ signal }) => travelExpenseApi.listForParticipant(participantId, signal),
  });

  const rows = (query.data ?? []).filter((r) => r.status !== 'CANCELLED');
  const money = (amount?: number | null, ccy?: string | null) =>
    amount == null ? '—' : `${amount.toLocaleString('en-PK')} ${ccy ?? currency}`;

  const cols: TableProps<TravelExpenseResponse>['columns'] = [
    {
      title: 'Category',
      key: 'category',
      render: (_v, row) => (
        <Text style={{ fontSize: 12 }}>
          {[row.expenseCategoryCode, row.expenseSubcategoryCode].filter(Boolean).join(' · ')}
        </Text>
      ),
    },
    {
      title: 'Description',
      key: 'description',
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {row.description || '—'}
        </Text>
      ),
    },
    {
      title: 'Date',
      key: 'expenseAt',
      width: 150,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
          {formatDateTime(row.expenseAt)}
        </Text>
      ),
    },
    {
      title: 'Claimed',
      key: 'claimed',
      align: 'right',
      width: 110,
      render: (_v, row) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {money(row.claimedAmount)}
        </Text>
      ),
    },
    {
      title: 'Approved',
      key: 'approved',
      align: 'right',
      width: 110,
      render: (_v, row) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {money(row.approvedAmount ?? row.eligibleAmount)}
        </Text>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_v, row) => (
        <StatusPill tone={EXPENSE_STATUS_TONE[row.status ?? ''] ?? 'neutral'}>
          {row.status ?? '—'}
        </StatusPill>
      ),
    },
  ];

  return (
    <Table<TravelExpenseResponse>
      rowKey={(r) => r.id}
      columns={cols}
      dataSource={rows}
      loading={query.isFetching}
      size="small"
      pagination={false}
      showHeader={rows.length > 0}
      locale={{
        emptyText: (
          <EmptyState size="sm" icon={ReceiptText} title="No expense claims from this traveller" />
        ),
      }}
    />
  );
}

export type FactoryTourSettlementModalProps = {
  /** The trip being settled; null keeps the modal closed. */
  tour: FactoryTourDto | null;
  onClose: () => void;
  departments?: DepartmentDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
};

export function FactoryTourSettlementModal({
  tour,
  onClose,
  departments,
  designations,
  grades,
}: FactoryTourSettlementModalProps) {
  const [participantId, setParticipantId] = useState<number | undefined>(undefined);
  const { message } = AntdApp.useApp();
  const qc = useQueryClient();

  const participants = tour?.participants ?? [];
  const currency = tour?.currency ?? 'PKR';

  const listQuery = useQuery({
    queryKey: ['travel-request-settlements', tour?.id],
    queryFn: ({ signal }) => travelSettlementApi.listForRequest(tour?.id ?? 0, signal),
    enabled: tour != null,
  });

  const prepareMutation = useMutation({
    mutationFn: (id: number) => travelSettlementApi.prepare(tour?.id ?? 0, id),
    onSuccess: () => {
      message.success('Settlement prepared');
      void qc.invalidateQueries({ queryKey: ['travel-request-settlements', tour?.id] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const submitMutation = useMutation({
    mutationFn: (settlement: TravelSettlementResponse) => travelSettlementApi.submit(settlement.id),
    onSuccess: () => {
      message.success('Settlement submitted for approval');
      void qc.invalidateQueries({ queryKey: ['travel-request-settlements', tour?.id] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const pdfMutation = useMutation({
    // Every settled traveller lands on the same sheet: one request, one PDF.
    // The sheet prints from a hidden iframe on this page, so unlike the old
    // new-tab flow it can be kicked off after the expenses fetch resolves.
    mutationFn: async ({ settlements }: { settlements: TravelSettlementResponse[] }) => {
      const travellers = await Promise.all(
        settlements.map(async (settlement) => {
          const participantExpenses =
            settlement.participantId != null
              ? await travelExpenseApi.listForParticipant(settlement.participantId)
              : [];
          return {
            employeeCode: settlement.employeeCode ?? employeeName(settlement),
            employeeName: employeeName(settlement),
            settlement,
            expenses: participantExpenses.filter((e) => e.status !== 'CANCELLED'),
          };
        }),
      );
      printTravelExpenditureSheet({ tour: tour!, travellers, departments, designations, grades });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const rows = listQuery.data ?? [];

  // One settlement per participant — the service itself is idempotent (a
  // second prepare just hands back the same DRAFT/FINAL row), but the picker
  // hides anyone already settled so it reads as "done", not "add again".
  const settledParticipantIds = new Set(
    rows.filter((r) => r.status !== 'REJECTED').map((r) => r.participantId).filter((id) => id != null),
  );

  const money = (amount?: number | null, ccy?: string | null) =>
    amount == null ? '—' : `${amount.toLocaleString('en-PK')} ${ccy ?? currency}`;

  const employeeName = (row: TravelSettlementResponse) =>
    row.employeeName ??
    participants.find((p) => p.cardNo === row.employeeCode)?.name ??
    row.employeeCode ??
    '—';

  const cols: TableProps<TravelSettlementResponse>['columns'] = [
    {
      title: 'Traveller',
      key: 'employee',
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <Text style={{ fontSize: 13 }}>{employeeName(row)}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.settlementNumber ?? `#${row.id}`}
          </Text>
        </div>
      ),
    },
    {
      title: 'Type',
      key: 'type',
      width: 90,
      render: (_v, row) => <Text style={{ fontSize: 13 }}>{row.settlementType ?? '—'}</Text>,
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_v, row) => (
        <StatusPill tone={STATUS_TONE[row.status ?? ''] ?? 'neutral'}>
          {row.status ?? '—'}
        </StatusPill>
      ),
    },
    {
      title: 'Direction',
      key: 'direction',
      width: 140,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {row.settlementDirection === 'EMPLOYEE_PAYABLE'
            ? 'Employee payable'
            : row.settlementDirection === 'COMPANY_PAYABLE'
              ? 'Company payable'
              : (row.settlementDirection ?? '—')}
        </Text>
      ),
    },
    {
      title: 'Final amount',
      key: 'amount',
      align: 'right',
      width: 130,
      render: (_v, row) => (
        <Text strong style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
          {money(row.finalSettlementAmount, row.settlementCurrencyCode)}
        </Text>
      ),
    },
    {
      title: 'Prepared',
      key: 'createdAt',
      width: 150,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
          {formatDateTime(row.createdAt)}
        </Text>
      ),
    },
    {
      title: '',
      key: 'rowActions',
      width: 100,
      align: 'right',
      render: (_v, row) =>
        row.status === 'DRAFT' ? (
          <Button
            size="small"
            type="primary"
            icon={<SendHorizonal size={12} />}
            loading={submitMutation.isPending && submitMutation.variables?.id === row.id}
            onClick={(e) => {
              e.stopPropagation();
              submitMutation.mutate(row);
            }}
          >
            Submit
          </Button>
        ) : null,
    },
  ];

  // One sheet for the whole request — every settlement that's out of DRAFT
  // becomes a row on it, so there's a single PDF button, not one per row.
  const settledRows = rows.filter((r) => r.status !== 'DRAFT');

  return (
    <Modal
      title={tour ? `Settlements · ${tour.requestId}` : 'Settlements'}
      open={tour != null}
      onCancel={onClose}
      afterClose={() => setParticipantId(undefined)}
      footer={<Button onClick={onClose}>Close</Button>}
      width={940}
      destroyOnHidden
    >
      {tour ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'flex-start',
              gap: 8,
            }}
          >
            <Text type="secondary" style={{ fontSize: 12 }}>
              {tour.origin} → {tour.destination} · {formatDateTime(tour.tourStart)} to{' '}
              {formatDateTime(tour.tourEnd)}
            </Text>
            {settledRows.length > 0 ? (
              <Button
                size="small"
                icon={<FileText size={12} />}
                loading={pdfMutation.isPending}
                onClick={() => pdfMutation.mutate({ settlements: settledRows })}
              >
                Expenditure sheet
              </Button>
            ) : null}
          </div>

          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <div style={{ flex: 1, minWidth: 200 }}>
              <Text style={{ display: 'block', marginBottom: 4, fontSize: 12 }}>
                Prepare for traveller
              </Text>
              <Select
                style={{ width: '100%' }}
                placeholder="Select a traveller"
                value={participantId}
                onChange={setParticipantId}
                options={participants.map((p) => ({
                  value: p.id,
                  label: [p.name, p.cardNo].filter(Boolean).join(' · '),
                  disabled: settledParticipantIds.has(p.id),
                }))}
                optionRender={(option) => (
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <span>{option.label}</span>
                    {settledParticipantIds.has(option.value as number) ? (
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        Already prepared
                      </Text>
                    ) : null}
                  </div>
                )}
              />
            </div>
            <Button
              type="primary"
              icon={<HandCoins size={14} />}
              disabled={participantId == null || settledParticipantIds.has(participantId)}
              loading={prepareMutation.isPending}
              onClick={() => participantId != null && prepareMutation.mutate(participantId)}
            >
              Prepare settlement
            </Button>
          </div>

          <Table<TravelSettlementResponse>
            rowKey={(r) => r.id}
            columns={cols}
            dataSource={rows}
            loading={listQuery.isFetching}
            size="small"
            pagination={rows.length > 10 ? { pageSize: 10, size: 'small' } : false}
            scroll={{ x: 840 }}
            expandable={{
              expandRowByClick: true,
              rowExpandable: (row) => row.participantId != null,
              expandedRowRender: (row) =>
                row.participantId != null ? (
                  <SettlementExpenses
                    participantId={row.participantId}
                    currency={row.settlementCurrencyCode ?? currency}
                  />
                ) : null,
            }}
            locale={{
              emptyText: (
                <EmptyState
                  size="sm"
                  icon={HandCoins}
                  title="No settlements yet"
                  description="Pick a traveller above and prepare their final settlement."
                />
              ),
            }}
          />
        </div>
      ) : null}
    </Modal>
  );
}
