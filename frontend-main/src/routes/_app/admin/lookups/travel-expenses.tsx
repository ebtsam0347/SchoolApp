// /admin/lookups/travel-expenses — "Travel Expenses" tab.
//
// One row per travel request, not per claim: the service has no global expense
// search, only GET /travel/requests/{id}/expenses, so this loads the requests
// and fans out over them, then rolls the claims up per request. Opening a row
// breaks it back down by participant — every traveller on the trip, including
// the ones who haven't claimed anything. Editing goes through
// PATCH /travel/expenses/{id} and is only offered while a claim is SUBMITTED.
// Reads require travel.read; editing requires travel.write.

import { useState, type ReactNode } from 'react';
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Select,
  Table,
  Tooltip,
  Typography,
  type TableProps,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';
import dayjs from 'dayjs';
import { ChevronLeft, ChevronRight, Pencil, ReceiptText, RefreshCcw } from 'lucide-react';

import {
  DetailDrawer,
  DrawerForm,
  EmptyState,
  FilterBar,
  PageHeader,
  PersonCell,
  RequestState,
  Section,
  StatusPill,
  type StatusPillTone,
} from '@/components';
import { formatDateTime, handleFormError, toLocalDateTime } from '@/lib';
import {
  travelExpenseApi,
  travelRequestApi,
  type TravelExpenseResponse,
  type TravelRequestResponse,
  type UpdateTravelExpenseRequest,
} from '@/travel';

const { Text } = Typography;

const STATUS_TONE: Record<string, StatusPillTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  APPROVED: 'success',
  PARTIALLY_APPROVED: 'warning',
  REJECTED: 'error',
  PAID: 'success',
  CANCELLED: 'neutral',
};

const STATUS_VALUES = [
  'SUBMITTED',
  'APPROVED',
  'PARTIALLY_APPROVED',
  'REJECTED',
  'PAID',
  'CANCELLED',
];

/** A travel request with every claim filed against it. */
type RequestExpenses = {
  request: TravelRequestResponse;
  requestNo: string;
  expenses: TravelExpenseResponse[];
};

const PARTICIPANTS_PER_PAGE = 2;

/** Cancelled and rejected claims cost nothing — they stay out of the totals. */
const counts = (expenses: TravelExpenseResponse[]) =>
  expenses.filter((e) => e.status !== 'CANCELLED' && e.status !== 'REJECTED');

const sumClaimed = (expenses: TravelExpenseResponse[]) =>
  counts(expenses).reduce((sum, e) => sum + (e.claimedAmount ?? 0), 0);

const sumApproved = (expenses: TravelExpenseResponse[]) =>
  counts(expenses).reduce((sum, e) => sum + (e.approvedAmount ?? e.eligibleAmount ?? 0), 0);

type ExpenseFormValues = {
  employeeCode: string;
  entitlementTypeCode?: string;
  expenseCategoryCode: string;
  expenseSubcategoryCode?: string;
  description?: string;
  expenseAt: dayjs.Dayjs;
  claimedAmount: number;
  claimedCurrencyCode: string;
  receiptReference?: string;
  receiptDocumentId?: string;
  remarks?: string;
};

export const Route = createFileRoute('/_app/admin/lookups/travel-expenses')({
  component: TravelExpensesTab,
});

/** Arrow that flanks the traveller list; keeps its slot when there's one page. */
function PagerButton({
  label,
  icon,
  disabled,
  hidden,
  onClick,
}: {
  label: string;
  icon: ReactNode;
  disabled: boolean;
  hidden: boolean;
  onClick: () => void;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', flexShrink: 0 }}>
      <Button
        shape="circle"
        aria-label={label}
        icon={icon}
        disabled={disabled}
        onClick={onClick}
        style={{ visibility: hidden ? 'hidden' : 'visible' }}
      />
    </div>
  );
}

function TravelExpensesTab() {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string | undefined>();
  const [viewingId, setViewingId] = useState<number | null>(null);
  // A trip with many travellers would be a very long drawer, so they come two
  // at a time with each one's full claim list.
  const [participantPage, setParticipantPage] = useState(0);
  const [editing, setEditing] = useState<TravelExpenseResponse | null>(null);
  const [form] = Form.useForm<ExpenseFormValues>();
  const { message } = AntdApp.useApp();
  const qc = useQueryClient();

  const listQuery = useQuery({
    queryKey: ['travel-expenses', 'by-request'],
    queryFn: async ({ signal }): Promise<RequestExpenses[]> => {
      const page = await travelRequestApi.search({}, signal);
      const requests = page.content ?? [];
      const rolled = await Promise.all(
        requests.map(async (request) => ({
          request,
          requestNo: request.requestNo ?? `#${request.id}`,
          expenses: await travelExpenseApi.listForRequest(request.id, signal),
        })),
      );
      return rolled.filter((r) => r.expenses.length > 0);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (body: UpdateTravelExpenseRequest) => travelExpenseApi.update(editing?.id ?? 0, body),
    onSuccess: () => {
      message.success('Expense updated');
      void qc.invalidateQueries({ queryKey: ['travel-expenses', 'by-request'] });
      setEditing(null);
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const rows = listQuery.data ?? [];
  const viewing = rows.find((r) => r.request.id === viewingId) ?? null;

  const participants = viewing?.request.participants ?? [];
  const participantPageCount = Math.max(
    Math.ceil(participants.length / PARTICIPANTS_PER_PAGE),
    1,
  );
  const participantStart =
    Math.min(participantPage, participantPageCount - 1) * PARTICIPANTS_PER_PAGE;
  const shownParticipants = participants.slice(
    participantStart,
    participantStart + PARTICIPANTS_PER_PAGE,
  );

  const query = search.trim().toLowerCase();
  const filteredRows = rows.filter((row) => {
    if (statusFilter && !row.expenses.some((e) => e.status === statusFilter)) return false;
    if (!query) return true;
    return [
      row.requestNo,
      row.request.requesterEmployeeName,
      row.request.requesterEmployeeCode,
      row.request.purpose,
      ...row.expenses.flatMap((e) => [
        e.employeeName,
        e.employeeCode,
        e.expenseCategoryCode,
        e.expenseSubcategoryCode,
        e.description,
      ]),
    ].some((field) => field?.toLowerCase().includes(query));
  });

  const hasActiveFilters = Boolean(query) || Boolean(statusFilter);
  const clearFilters = () => {
    setSearch('');
    setStatusFilter(undefined);
  };

  const totalClaims = rows.reduce((sum, r) => sum + r.expenses.length, 0);

  const money = (amount?: number | null, ccy?: string | null) =>
    amount == null ? '—' : `${amount.toLocaleString('en-PK')} ${ccy ?? ''}`.trim();

  const currencyOf = (row: RequestExpenses) =>
    row.request.currencyCode ?? row.expenses[0]?.claimedCurrencyCode ?? '';

  const openEdit = (expense: TravelExpenseResponse) => {
    form.setFieldsValue({
      employeeCode: expense.employeeCode ?? undefined,
      expenseCategoryCode: expense.expenseCategoryCode ?? undefined,
      expenseSubcategoryCode: expense.expenseSubcategoryCode ?? undefined,
      description: expense.description ?? undefined,
      expenseAt: expense.expenseAt ? dayjs(expense.expenseAt) : undefined,
      claimedAmount: expense.claimedAmount ?? undefined,
      claimedCurrencyCode: expense.claimedCurrencyCode ?? undefined,
      receiptReference: expense.receiptReference ?? undefined,
      receiptDocumentId: expense.receiptDocumentId ?? undefined,
      remarks: expense.remarks ?? undefined,
    } as Partial<ExpenseFormValues>);
    setEditing(expense);
  };

  const statusSummary = (expenses: TravelExpenseResponse[]) => {
    const tally = new Map<string, number>();
    expenses.forEach((e) => {
      const key = e.status ?? '—';
      tally.set(key, (tally.get(key) ?? 0) + 1);
    });
    return [...tally.entries()];
  };

  const cols: TableProps<RequestExpenses>['columns'] = [
    {
      title: 'Request',
      key: 'request',
      width: 180,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <Text style={{ fontWeight: 500, fontSize: 13 }}>{row.requestNo}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {row.request.requesterEmployeeName ?? row.request.requesterEmployeeCode ?? '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Travellers',
      key: 'travellers',
      width: 110,
      render: (_v, row) => (
        <Text style={{ fontSize: 13 }}>{(row.request.participants ?? []).length}</Text>
      ),
    },
    {
      title: 'Claims',
      key: 'claims',
      width: 90,
      sorter: (a, b) => a.expenses.length - b.expenses.length,
      render: (_v, row) => <Text style={{ fontSize: 13 }}>{row.expenses.length}</Text>,
    },
    {
      title: 'Claimed',
      key: 'claimed',
      align: 'right',
      width: 140,
      sorter: (a, b) => sumClaimed(a.expenses) - sumClaimed(b.expenses),
      render: (_v, row) => (
        <Text style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
          {money(sumClaimed(row.expenses), currencyOf(row))}
        </Text>
      ),
    },
    {
      title: 'Approved',
      key: 'approved',
      align: 'right',
      width: 140,
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {money(sumApproved(row.expenses), currencyOf(row))}
        </Text>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_v, row) => (
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
          {statusSummary(row.expenses).map(([status, count]) => (
            <StatusPill key={status} tone={STATUS_TONE[status] ?? 'neutral'}>
              {count} {status}
            </StatusPill>
          ))}
        </div>
      ),
    },
    {
      title: 'Actions',
      key: 'rowActions',
      width: 110,
      fixed: 'right',
      align: 'right',
      render: (_v, row) => (
        <Button
          size="small"
          onClick={() => {
            setParticipantPage(0);
            setViewingId(row.request.id);
          }}
        >
          Details
        </Button>
      ),
    },
  ];

  const claimColumns = (row: RequestExpenses): TableProps<TravelExpenseResponse>['columns'] => [
    {
      title: 'Category',
      key: 'category',
      width: 190,
      render: (_v, e) => (
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            lineHeight: 1.3,
            whiteSpace: 'nowrap',
          }}
        >
          <Text style={{ fontSize: 12 }}>{e.expenseCategoryCode ?? '—'}</Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {e.expenseSubcategoryCode ?? '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Claimed',
      key: 'claimed',
      align: 'right',
      width: 110,
      render: (_v, e) => (
        <Text style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {money(e.claimedAmount, e.claimedCurrencyCode ?? currencyOf(row))}
        </Text>
      ),
    },
    {
      title: 'Approved',
      key: 'approved',
      align: 'right',
      width: 110,
      render: (_v, e) => (
        <Text type="secondary" style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>
          {money(e.approvedAmount ?? e.eligibleAmount, e.claimedCurrencyCode ?? currencyOf(row))}
        </Text>
      ),
    },
    {
      title: 'Date',
      key: 'expenseAt',
      width: 130,
      render: (_v, e) => (
        <Text type="secondary" style={{ fontSize: 11, whiteSpace: 'nowrap' }}>
          {formatDateTime(e.expenseAt)}
        </Text>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      render: (_v, e) => (
        <StatusPill tone={STATUS_TONE[e.status ?? ''] ?? 'neutral'}>{e.status ?? '—'}</StatusPill>
      ),
    },
    {
      title: '',
      key: 'rowActions',
      width: 70,
      align: 'right',
      render: (_v, e) =>
        e.status === 'SUBMITTED' ? (
          <Button size="small" icon={<Pencil size={12} />} onClick={() => openEdit(e)}>
            Edit
          </Button>
        ) : null,
    },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 24, paddingBottom: 32 }}>
      <PageHeader
        eyebrow=""
        title="Travel Expenses"
        description="Claim and review actual travel expenses. Flow: Submitted → Approved / Partially approved → Paid (or Rejected / Cancelled)."
        actions={
          <Tooltip title="Refresh">
            <Button
              icon={<RefreshCcw size={14} strokeWidth={1.75} />}
              onClick={() => listQuery.refetch()}
              loading={listQuery.isFetching}
              aria-label="Refresh"
            />
          </Tooltip>
        }
      />

      <Section
        title="Expenses by travel request"
        subtitle={`${rows.length.toLocaleString('en-PK')} request(s) · ${totalClaims.toLocaleString('en-PK')} claim(s)`}
        actions={
          <Select<string | undefined>
            allowClear
            placeholder="Status"
            style={{ width: 'clamp(140px, 45vw, 200px)' }}
            value={statusFilter}
            onChange={setStatusFilter}
            options={STATUS_VALUES.map((s) => ({ value: s, label: s }))}
          />
        }
      >
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search request, claimant, category…',
          }}
          style={{ marginBottom: 12 }}
        />

        <RequestState
          loading={listQuery.isLoading}
          variant="table"
          error={listQuery.isError ? listQuery.error : undefined}
          errorTitle="Couldn't load travel expenses"
          onRetry={() => listQuery.refetch()}
          empty={!listQuery.isLoading && filteredRows.length === 0}
          filterActive={hasActiveFilters}
          onClearFilters={clearFilters}
          emptyFallback={
            <EmptyState
              icon={ReceiptText}
              title="No travel expenses yet"
              description="Claims appear here once they're added to an approved travel request."
            />
          }
        >
          <Table<RequestExpenses>
            rowKey={(r) => r.request.id}
            columns={cols}
            dataSource={filteredRows}
            loading={listQuery.isFetching}
            size="middle"
            pagination={filteredRows.length > 20 ? { pageSize: 20, size: 'small' } : false}
            scroll={{ x: 1000 }}
          />
        </RequestState>
      </Section>

      <DetailDrawer
        open={viewing != null}
        onClose={() => setViewingId(null)}
        title={viewing ? `Expenses · ${viewing.requestNo}` : 'Expenses'}
        extra={
          viewing ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {money(sumClaimed(viewing.expenses), currencyOf(viewing))} claimed
            </Text>
          ) : null
        }
        width="clamp(320px, 94vw, 760px)"
      >
        {viewing ? (
          <div style={{ display: 'flex', alignItems: 'stretch', gap: 8, height: '100%' }}>
            <PagerButton
              label="Previous travellers"
              icon={<ChevronLeft size={16} />}
              disabled={participantPage === 0}
              hidden={participantPageCount <= 1}
              onClick={() => setParticipantPage((p) => Math.max(p - 1, 0))}
            />

            <div
              style={{
                flex: 1,
                minWidth: 0,
                display: 'flex',
                flexDirection: 'column',
                gap: 16,
              }}
            >
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                }}
              >
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {participants.length} traveller(s)
                </Text>
                {participantPageCount > 1 ? (
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {participantStart + 1}–{participantStart + shownParticipants.length} of{' '}
                    {participants.length} · page {participantPage + 1}/{participantPageCount}
                  </Text>
                ) : null}
              </div>

              {shownParticipants.map((participant) => {
                const claims = viewing.expenses.filter(
                  (e) => e.employeeCode === participant.employeeCode,
                );
                return (
                  <div
                    key={participant.id}
                    style={{
                      flex: 1,
                      minHeight: 0,
                      display: 'flex',
                      flexDirection: 'column',
                      border: '1px solid var(--ant-color-border-secondary)',
                      borderRadius: 10,
                      padding: 14,
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12,
                        flexWrap: 'wrap',
                        marginBottom: claims.length ? 12 : 8,
                      }}
                    >
                      <PersonCell
                        person={{
                          fullName: participant.employeeName ?? participant.employeeCode ?? '—',
                          employeeCode: participant.employeeCode,
                          subtitle: [
                            participant.employeeCode,
                            participant.participantRole,
                            participant.gradeName ?? participant.gradeCode,
                          ]
                            .filter(Boolean)
                            .join(' · '),
                        }}
                      />
                      <StatusPill tone={claims.length ? 'info' : 'neutral'}>
                        {claims.length} claim(s) · {money(sumClaimed(claims), currencyOf(viewing))}
                      </StatusPill>
                    </div>

                    {claims.length === 0 ? (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        No claims from this traveller yet.
                      </Text>
                    ) : (
                      <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
                        <Table<TravelExpenseResponse>
                          rowKey={(e) => e.id}
                          columns={claimColumns(viewing)}
                          dataSource={claims}
                          size="small"
                          pagination={false}
                          scroll={{ x: 560 }}
                        />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <PagerButton
              label="Next travellers"
              icon={<ChevronRight size={16} />}
              disabled={participantPage >= participantPageCount - 1}
              hidden={participantPageCount <= 1}
              onClick={() =>
                setParticipantPage((p) => Math.min(p + 1, participantPageCount - 1))
              }
            />
          </div>
        ) : null}
      </DetailDrawer>

      <DrawerForm<ExpenseFormValues>
        title={`Edit expense #${editing?.id ?? ''}`}
        open={editing != null}
        onClose={() => setEditing(null)}
        form={form}
        submitting={updateMutation.isPending}
        onFinish={(values) =>
          updateMutation.mutate({
            employeeCode: values.employeeCode,
            entitlementTypeCode: values.entitlementTypeCode,
            expenseCategoryCode: values.expenseCategoryCode,
            expenseSubcategoryCode: values.expenseSubcategoryCode,
            description: values.description,
            expenseAt: toLocalDateTime(values.expenseAt),
            claimedAmount: values.claimedAmount,
            claimedCurrencyCode: values.claimedCurrencyCode,
            receiptReference: values.receiptReference,
            receiptDocumentId: values.receiptDocumentId,
            remarks: values.remarks,
          })
        }
      >
        <Form.Item
          name="employeeCode"
          label="Employee code"
          rules={[{ required: true, message: 'Enter the employee code' }]}
        >
          <Input />
        </Form.Item>
        <Form.Item name="entitlementTypeCode" label="Entitlement type code">
          <Input placeholder="Optional" />
        </Form.Item>
        <Form.Item
          name="expenseCategoryCode"
          label="Expense category code"
          rules={[{ required: true, message: 'Enter an expense category code' }]}
        >
          <Input placeholder="e.g. MEAL" />
        </Form.Item>
        <Form.Item name="expenseSubcategoryCode" label="Expense subcategory code">
          <Input placeholder="Optional" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea rows={2} />
        </Form.Item>
        <Form.Item
          name="expenseAt"
          label="Expense date"
          rules={[{ required: true, message: 'Pick the expense date' }]}
        >
          <DatePicker showTime style={{ width: '100%' }} format="DD MMM YYYY, HH:mm" />
        </Form.Item>
        <Form.Item
          name="claimedAmount"
          label="Claimed amount"
          rules={[{ required: true, message: 'Enter the claimed amount' }]}
        >
          <InputNumber style={{ width: '100%' }} min={0.01} />
        </Form.Item>
        <Form.Item
          name="claimedCurrencyCode"
          label="Claimed currency code"
          rules={[{ required: true, message: 'Enter a currency code' }]}
        >
          <Input placeholder="e.g. PKR" />
        </Form.Item>
        <Form.Item name="receiptReference" label="Receipt reference">
          <Input />
        </Form.Item>
        <Form.Item name="receiptDocumentId" label="Receipt document id">
          <Input />
        </Form.Item>
        <Form.Item name="remarks" label="Remarks">
          <Input.TextArea rows={2} />
        </Form.Item>
      </DrawerForm>
    </div>
  );
}
