// Modal that claims actual expenses against one approved travel request —
// the replacement for the legacy "Add / Update Factory Duty Tour Expenses"
// popup. Wired to POST/GET /travel/requests/{id}/expenses with
// POST /travel/expenses/{id}/cancel behind the row action; the trip must be
// APPROVED or later, which is why the table only offers this on approved rows.
//
// The claimant dropdown lists the trip's participants because the service
// resolves `employeeCode` to a participant and 404s on anyone else.
//
// What may be claimed comes from the policy: the trip's policy version has a
// grade limit per expense type, which the service snapshots onto each
// participant as an entitlement when the request is approved. Those
// entitlements aren't exposed over HTTP, so the picker and the amount cap are
// built from GET /travel/policies/{versionId}/grade-limits, applying the same
// quantity rule the service uses (PER_DAY × travel days, PER_NIGHT × nights,
// PER_KM × distance, otherwise once). The server still has the last word — it
// recomputes the eligible amount on every claim.

import { useState, type Key } from 'react';
import {
  App as AntdApp,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Table,
  Typography,
  type TableProps,
} from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { Plus, ReceiptText, XCircle } from 'lucide-react';

import { EmptyState, FilterBar, StatusPill, type StatusPillTone } from '@/components';
import {
  formatDateTime,
  getErrorMessage,
  handleFormError,
  toLocalDateTime,
} from '@/lib';
import {
  travelExpenseApi,
  travelPolicyApi,
  travelRequestApi,
  type GradeLimitResponse,
  type TravelExpenseResponse,
} from '@/travel';
import type { FactoryTourDto } from '../types';

const { Text } = Typography;

/**
 * The service's TravelExpenseType vocabulary, split the way the API stores a
 * claim: `expenseCategoryCode` is documented as one of ACCOMMODATION / MEAL /
 * TRANSPORTATION / TOLL / PARKING / OTHER, with the specific type in
 * `expenseSubcategoryCode`. `expenseTypeCode` is the key both a policy grade
 * limit and the entitlement it snapshots are filed under — it is what the
 * claim's `entitlementTypeCode` has to match to consume the right limit.
 */
const CATEGORIES = [
  {
    code: 'MEAL',
    label: 'Meal',
    subcategories: [
      { code: 'BREAKFAST', label: 'Breakfast', expenseTypeCode: 'BREAKFAST' },
      { code: 'LUNCH', label: 'Lunch', expenseTypeCode: 'LUNCH' },
      { code: 'DINNER', label: 'Dinner', expenseTypeCode: 'DINNER' },
      { code: 'MEAL_TOTAL', label: 'Meal total', expenseTypeCode: 'MEAL_TOTAL' },
    ],
  },
  {
    code: 'ACCOMMODATION',
    label: 'Accommodation',
    subcategories: [{ code: 'HOTEL', label: 'Hotel', expenseTypeCode: 'ACCOMMODATION' }],
  },
  {
    code: 'TRANSPORTATION',
    label: 'Transportation',
    subcategories: [
      { code: 'FUEL', label: 'Fuel (petrol)', expenseTypeCode: 'FUEL' },
      { code: 'TAXI', label: 'Taxi', expenseTypeCode: 'TAXI' },
      { code: 'TRAIN_TICKET', label: 'Train ticket', expenseTypeCode: 'TRAIN_TICKET' },
      { code: 'AIR_TICKET', label: 'Air ticket', expenseTypeCode: 'AIR_TICKET' },
    ],
  },
  {
    code: 'TOLL',
    label: 'Toll',
    subcategories: [{ code: 'TOLL', label: 'Toll', expenseTypeCode: 'TOLL' }],
  },
  {
    code: 'PARKING',
    label: 'Parking',
    subcategories: [{ code: 'PARKING', label: 'Parking', expenseTypeCode: 'PARKING' }],
  },
  {
    code: 'OTHER',
    label: 'Other',
    subcategories: [
      { code: 'TA_TD', label: 'TA / TD', expenseTypeCode: 'TA_TD' },
      { code: 'OTHER', label: 'Other', expenseTypeCode: 'OTHER' },
    ],
  },
] as const;

const CATEGORY_LABEL = new Map(CATEGORIES.map((c) => [c.code as string, c.label as string]));
const SUBCATEGORY_LABEL = new Map(
  CATEGORIES.flatMap((c) =>
    c.subcategories.map((s) => [s.code as string, s.label as string] as const),
  ),
);
const TYPE_LABEL = new Map(
  CATEGORIES.flatMap((c) =>
    c.subcategories.map((s) => [s.expenseTypeCode as string, s.label as string] as const),
  ),
);

function expenseTypeCodeFor(categoryCode?: string, subcategoryCode?: string) {
  return CATEGORIES.find((c) => c.code === categoryCode)?.subcategories.find(
    (s) => s.code === subcategoryCode,
  )?.expenseTypeCode;
}

/**
 * Fleet mileage the legacy screen used to turn a trip's distance into the
 * litres of petrol it shows. Nothing on the wire carries it — no field on the
 * trip, the policy or the claim — so the figure lives here; change this one
 * number if the fleet average moves.
 */
const KM_PER_LITRE = 10;

const STATUS_TONE: Record<string, StatusPillTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  APPROVED: 'success',
  PARTIALLY_APPROVED: 'warning',
  REJECTED: 'error',
  PAID: 'success',
  CANCELLED: 'neutral',
};

/** Mirrors TravelRequestService#entitlementQuantity. */
function limitQuantity(limitType: string, travelDays: number, distanceKm: number) {
  switch (limitType) {
    case 'PER_DAY':
      return travelDays;
    case 'PER_NIGHT':
      return Math.max(travelDays - 1, 0);
    case 'PER_KM':
      return distanceKm;
    default:
      return 1;
  }
}

/** How a cap was arrived at, e.g. "12 × 200 km" for a fuel rate. */
function limitBasis(limitType: string, unitAmount: number, quantity: number) {
  const unit =
    limitType === 'PER_DAY'
      ? 'day(s)'
      : limitType === 'PER_NIGHT'
        ? 'night(s)'
        : limitType === 'PER_KM'
          ? 'km'
          : undefined;
  return unit
    ? `${unitAmount.toLocaleString('en-PK')} × ${quantity.toLocaleString('en-PK')} ${unit}`
    : undefined;
}

type ExpenseFormValues = {
  employeeCode: string;
  expenseCategoryCode: string;
  expenseSubcategoryCode?: string;
  claimedAmount: number;
  expenseAt: dayjs.Dayjs;
  description?: string;
};

export type FactoryTourExpensesModalProps = {
  /** The trip being claimed against; null keeps the modal closed. */
  tour: FactoryTourDto | null;
  onClose: () => void;
};

export function FactoryTourExpensesModal({ tour, onClose }: FactoryTourExpensesModalProps) {
  const [form] = Form.useForm<ExpenseFormValues>();
  const [search, setSearch] = useState('');
  // Mirrored from the table so the footer total counts what the column
  // filters actually left on screen.
  const [columnFilters, setColumnFilters] = useState<
    Record<string, (Key | boolean)[] | null>
  >({});
  const { message, modal } = AntdApp.useApp();
  const qc = useQueryClient();

  const claimantCode = Form.useWatch('employeeCode', form);
  const categoryCode = Form.useWatch('expenseCategoryCode', form);
  const subcategoryCode = Form.useWatch('expenseSubcategoryCode', form);
  const currency = tour?.currency ?? 'PKR';

  const travellers = tour
    ? [{ cardNo: tour.cardNo, name: tour.name }, ...tour.additionalTravelPersons].filter(
        (p) => p.cardNo,
      )
    : [];

  const listQuery = useQuery({
    queryKey: ['travel-request-expenses', tour?.id],
    queryFn: ({ signal }) => travelExpenseApi.listForRequest(tour?.id ?? 0, signal),
    enabled: tour != null,
  });

  // The list endpoint drops the policy version and each participant's grade,
  // both of which the limits below are keyed on.
  const requestQuery = useQuery({
    queryKey: ['travel-request', tour?.id],
    queryFn: ({ signal }) => travelRequestApi.get(tour?.id ?? 0, signal),
    enabled: tour != null,
  });

  const policyVersionId = requestQuery.data?.policyVersionId;
  const limitsQuery = useQuery({
    queryKey: ['travel-policy-grade-limits', policyVersionId],
    queryFn: ({ signal }) => travelPolicyApi.gradeLimits(policyVersionId!, signal),
    enabled: policyVersionId != null,
    staleTime: 5 * 60 * 1000,
  });

  const createMutation = useMutation({
    mutationFn: (values: ExpenseFormValues) =>
      travelExpenseApi.create(tour?.id ?? 0, {
        employeeCode: values.employeeCode,
        entitlementTypeCode: expenseTypeCodeFor(
          values.expenseCategoryCode,
          values.expenseSubcategoryCode,
        ),
        expenseCategoryCode: values.expenseCategoryCode,
        expenseSubcategoryCode: values.expenseSubcategoryCode,
        description: values.description?.trim() || undefined,
        expenseAt: toLocalDateTime(values.expenseAt),
        claimedAmount: values.claimedAmount,
        claimedCurrencyCode: currency,
      }),
    onSuccess: () => {
      message.success('Expense added');
      void qc.invalidateQueries({ queryKey: ['travel-request-expenses', tour?.id] });
      // Keep the claimant and date — the next line is usually the same trip day.
      form.resetFields([
        'expenseCategoryCode',
        'expenseSubcategoryCode',
        'claimedAmount',
        'description',
      ]);
    },
    onError: (err) => handleFormError(form, err, message),
  });

  const cancelMutation = useMutation({
    mutationFn: (row: TravelExpenseResponse) => travelExpenseApi.cancel(row.id),
    onSuccess: () => {
      message.success('Expense cancelled');
      void qc.invalidateQueries({ queryKey: ['travel-request-expenses', tour?.id] });
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  // Cancelled claims are dead weight in the list — the service keeps them, the
  // claimant doesn't need to look at them.
  const rows = (listQuery.data ?? []).filter((r) => r.status !== 'CANCELLED');

  const travelDays = tour
    ? Math.max(
        dayjs(tour.tourEnd).startOf('day').diff(dayjs(tour.tourStart).startOf('day'), 'day') + 1,
        1,
      )
    : 1;

  const claimantGradeId = requestQuery.data?.participants?.find(
    (p) => p.employeeCode === claimantCode,
  )?.gradeId;

  /** The claimant's active limits, capped the way the trip's entitlements were. */
  const limits: (GradeLimitResponse & { totalLimit: number; basis?: string })[] =
    claimantGradeId == null
      ? []
      : (limitsQuery.data ?? [])
          .filter((l) => l.gradeId === claimantGradeId && l.isActive !== false)
          .map((l) => {
            const quantity = limitQuantity(l.limitType, travelDays, tour?.totalDistanceKm ?? 0);
            return {
              ...l,
              totalLimit: l.limitAmount * quantity,
              basis: limitBasis(l.limitType, l.limitAmount, quantity),
            };
          });

  /** What the same claimant already put against a limit on this trip. */
  const consumedFor = (expenseTypeCode: string) =>
    rows
      .filter(
        (r) =>
          r.employeeCode === claimantCode &&
          r.status !== 'REJECTED' &&
          r.status !== 'CANCELLED' &&
          expenseTypeCodeFor(
            r.expenseCategoryCode ?? undefined,
            r.expenseSubcategoryCode ?? undefined,
          ) === expenseTypeCode,
      )
      .reduce((sum, r) => sum + (r.approvedAmount ?? r.eligibleAmount ?? r.claimedAmount ?? 0), 0);

  const remainingFor = (expenseTypeCode: string) => {
    const limit = limits.find((l) => l.expenseTypeCode === expenseTypeCode);
    if (!limit) return undefined;
    return Math.max(limit.totalLimit - consumedFor(expenseTypeCode), 0);
  };

  // Every category/subcategory is always selectable — a type with no grade
  // limit just claims uncapped, same as when the grade has no limits at all.
  const categoryOptions = CATEGORIES;
  const subcategoryOptions = CATEGORIES.find((c) => c.code === categoryCode)?.subcategories ?? [];

  const selectedTypeCode = expenseTypeCodeFor(categoryCode, subcategoryCode);
  const selectedLimit = limits.find((l) => l.expenseTypeCode === selectedTypeCode);
  const selectedRemaining =
    selectedTypeCode == null ? undefined : remainingFor(selectedTypeCode);

  const money = (amount?: number | null, ccy = currency) =>
    amount == null ? '—' : `${amount.toLocaleString('en-PK')} ${ccy}`;

  const distanceKm = tour?.totalDistanceKm ?? undefined;
  const petrolLitres =
    distanceKm == null ? undefined : Math.round((distanceKm / KM_PER_LITRE) * 10) / 10;
  const fuelNote =
    petrolLitres == null ? undefined : `Petrol ${petrolLitres} L for ${distanceKm} km`;

  /** Fuel claims carry the litres in their description — the API has no field for it. */
  const prefillFuelDescription = (category?: string, subcategory?: string) => {
    if (!fuelNote) return;
    if (expenseTypeCodeFor(category, subcategory) !== 'FUEL') return;
    if (form.getFieldValue('description')) return;
    form.setFieldValue('description', fuelNote);
  };

  const categoryLabel = (row: TravelExpenseResponse) =>
    CATEGORY_LABEL.get(row.expenseCategoryCode ?? '') ?? row.expenseCategoryCode ?? '';
  const subcategoryLabel = (row: TravelExpenseResponse) =>
    SUBCATEGORY_LABEL.get(row.expenseSubcategoryCode ?? '') ?? row.expenseSubcategoryCode ?? '';

  const query = search.trim().toLowerCase();
  const visibleRows = !query
    ? rows
    : rows.filter((r) =>
        [
          categoryLabel(r),
          subcategoryLabel(r),
          r.employeeName,
          r.employeeCode,
          r.description,
          r.remarks,
          r.status,
          r.claimedAmount?.toString(),
          formatDateTime(r.expenseAt),
        ].some((field) => field?.toLowerCase().includes(query)),
      );

  const passesColumnFilters = (row: TravelExpenseResponse) => {
    const checks: [(Key | boolean)[] | null | undefined, string | null | undefined][] = [
      [columnFilters.category, row.expenseCategoryCode],
      [columnFilters.claimant, row.employeeCode],
      [columnFilters.status, row.status],
    ];
    return checks.every(([selected, value]) => !selected?.length || selected.includes(value ?? ''));
  };

  // The footer follows what's on screen, the way a filtered data table does.
  const shownRows = visibleRows.filter(passesColumnFilters);
  const claimed = shownRows
    .filter((r) => r.status !== 'REJECTED')
    .reduce((sum, r) => sum + (r.claimedAmount ?? 0), 0);
  const filterActive = Boolean(query) || Object.values(columnFilters).some((v) => v?.length);

  const distinct = <T,>(values: (T | null | undefined)[]) =>
    [...new Set(values.filter((v): v is T => v != null))];

  const cols: TableProps<TravelExpenseResponse>['columns'] = [
    {
      title: 'Category',
      key: 'category',
      filters: distinct(rows.map((r) => r.expenseCategoryCode)).map((code) => ({
        value: code,
        text: CATEGORY_LABEL.get(code) ?? code,
      })),
      onFilter: (value, row) => row.expenseCategoryCode === value,
      render: (_v, row) => (
        <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
          <Text style={{ fontSize: 13 }}>
            {CATEGORY_LABEL.get(row.expenseCategoryCode ?? '') ?? row.expenseCategoryCode ?? '—'}
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {SUBCATEGORY_LABEL.get(row.expenseSubcategoryCode ?? '') ??
              row.expenseSubcategoryCode ??
              '—'}
          </Text>
        </div>
      ),
    },
    {
      title: 'Claimant',
      key: 'claimant',
      filters: distinct(rows.map((r) => r.employeeCode)).map((code) => ({
        value: code,
        text: rows.find((r) => r.employeeCode === code)?.employeeName ?? code,
      })),
      onFilter: (value, row) => row.employeeCode === value,
      render: (_v, row) => (
        <Text style={{ fontSize: 13 }}>{row.employeeName ?? row.employeeCode ?? '—'}</Text>
      ),
    },
    {
      title: 'Amount',
      key: 'amount',
      align: 'right',
      width: 130,
      sorter: (a, b) => (a.claimedAmount ?? 0) - (b.claimedAmount ?? 0),
      render: (_v, row) => (
        <Text style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
          {money(row.claimedAmount)}
        </Text>
      ),
    },
    {
      title: 'Eligible',
      key: 'eligible',
      align: 'right',
      width: 120,
      render: (_v, row) => (
        <Text
          type={
            row.eligibleAmount != null && row.eligibleAmount < (row.claimedAmount ?? 0)
              ? 'warning'
              : 'secondary'
          }
          style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}
        >
          {money(row.eligibleAmount)}
        </Text>
      ),
    },
    {
      title: 'Date',
      key: 'expenseAt',
      width: 150,
      sorter: (a, b) => (a.expenseAt ?? '').localeCompare(b.expenseAt ?? ''),
      defaultSortOrder: 'descend',
      render: (_v, row) => (
        <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
          {formatDateTime(row.expenseAt)}
        </Text>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 130,
      filters: distinct(rows.map((r) => r.status)).map((status) => ({
        value: status,
        text: status,
      })),
      onFilter: (value, row) => row.status === value,
      render: (_v, row) => (
        <StatusPill tone={STATUS_TONE[row.status ?? ''] ?? 'neutral'}>
          {row.status ?? '—'}
        </StatusPill>
      ),
    },
    {
      title: '',
      key: 'rowActions',
      width: 90,
      align: 'right',
      render: (_v, row) =>
        row.status === 'SUBMITTED' ? (
          <Button
            size="small"
            type="text"
            danger
            icon={<XCircle size={12} />}
            loading={cancelMutation.isPending && cancelMutation.variables?.id === row.id}
            onClick={() =>
              modal.confirm({
                title: 'Cancel this expense claim?',
                content: 'It stays on the trip as a cancelled line.',
                okText: 'Cancel claim',
                okButtonProps: { danger: true },
                cancelText: 'Keep',
                onOk: () => cancelMutation.mutate(row),
              })
            }
          >
            Cancel
          </Button>
        ) : null,
    },
  ];

  return (
    <Modal
      title={tour ? `Tour expenses · ${tour.requestId}` : 'Tour expenses'}
      open={tour != null}
      onCancel={onClose}
      footer={<Button onClick={onClose}>Close</Button>}
      width={980}
      destroyOnHidden
    >
      {tour ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {tour.origin} → {tour.destination} · {formatDateTime(tour.tourStart)} to{' '}
            {formatDateTime(tour.tourEnd)} · {travelDays} day(s)
            {tour.totalDistanceKm != null ? ` · ${tour.totalDistanceKm} km` : ''}
          </Text>

          <Form<ExpenseFormValues>
            form={form}
            layout="vertical"
            initialValues={{
              employeeCode: tour.cardNo,
              expenseAt: dayjs(tour.tourStart || undefined),
            }}
            onFinish={(values) => createMutation.mutate(values)}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
                gap: 12,
              }}
            >
              {petrolLitres != null ? (
                <Form.Item
                  label="Petrol (in liters)"
                  tooltip={`${distanceKm} km ÷ ${KM_PER_LITRE} km per litre`}
                >
                  <Input disabled value={`${petrolLitres} L`} />
                </Form.Item>
              ) : null}
              <Form.Item
                name="employeeCode"
                label="Claimant"
                rules={[{ required: true, message: 'Pick who incurred it' }]}
              >
                <Select
                  options={travellers.map((p) => ({
                    value: p.cardNo,
                    label: [p.name, p.cardNo].filter(Boolean).join(' · '),
                  }))}
                  onChange={() =>
                    form.resetFields(['expenseCategoryCode', 'expenseSubcategoryCode'])
                  }
                />
              </Form.Item>
              <Form.Item
                name="expenseCategoryCode"
                label="Expense category"
                rules={[{ required: true, message: 'Pick a category' }]}
              >
                <Select
                  placeholder="Select category"
                  loading={limitsQuery.isLoading}
                  options={categoryOptions.map((c) => ({ value: c.code, label: c.label }))}
                  onChange={(code) => {
                    // Single-option categories (Toll, Parking) have nothing to choose.
                    const subs = CATEGORIES.find((c) => c.code === code)?.subcategories ?? [];
                    const sub = subs.length === 1 ? subs[0]!.code : undefined;
                    form.setFieldValue('expenseSubcategoryCode', sub);
                    prefillFuelDescription(code, sub);
                  }}
                />
              </Form.Item>
              <Form.Item name="expenseSubcategoryCode" label="Expense sub category">
                <Select
                  allowClear
                  placeholder={categoryCode ? 'Select sub category' : 'Pick a category first'}
                  disabled={!categoryCode}
                  options={subcategoryOptions.map((s) => ({ value: s.code, label: s.label }))}
                  onChange={(code) => prefillFuelDescription(categoryCode, code)}
                />
              </Form.Item>
              <Form.Item
                name="claimedAmount"
                label="Amount"
                extra={
                  selectedLimit == null ? undefined : (
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {money(selectedRemaining, selectedLimit.currencyCode)} left of{' '}
                      {money(selectedLimit.totalLimit, selectedLimit.currencyCode)}
                      {selectedLimit.basis ? ` · ${selectedLimit.basis}` : ''}
                    </Text>
                  )
                }
                rules={[
                  { required: true, message: 'Enter the amount' },
                  {
                    validator: (_rule, value: number | null) =>
                      value != null && selectedRemaining != null && value > selectedRemaining
                        ? Promise.reject(
                            new Error(`Grade limit allows only ${money(selectedRemaining)} more`),
                          )
                        : Promise.resolve(),
                  },
                ]}
              >
                <InputNumber style={{ width: '100%' }} min={0.01} addonBefore={currency} />
              </Form.Item>
              <Form.Item
                name="expenseAt"
                label="Date"
                rules={[{ required: true, message: 'Pick the date' }]}
              >
                <DatePicker showTime style={{ width: '100%' }} format="DD MMM YYYY, HH:mm" />
              </Form.Item>
              <Form.Item
                name="description"
                label="Description"
                dependencies={['expenseCategoryCode', 'expenseSubcategoryCode']}
                rules={[
                  {
                    required: selectedLimit == null,
                    message: 'Describe this expense — it has no grade limit to justify it',
                  },
                ]}
              >
                <Input placeholder={selectedLimit == null ? 'Required — no grade limit set' : 'Optional'} />
              </Form.Item>
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                gap: 12,
                flexWrap: 'wrap',
              }}
            >
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {limits.length > 0 ? (
                  limits.map((l) => (
                    <StatusPill
                      key={l.id}
                      tone={remainingFor(l.expenseTypeCode) === 0 ? 'neutral' : 'info'}
                    >
                      {TYPE_LABEL.get(l.expenseTypeCode) ?? l.expenseTypeCode}:{' '}
                      {money(remainingFor(l.expenseTypeCode), l.currencyCode)} left
                    </StatusPill>
                  ))
                ) : (
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {limitsQuery.isLoading || requestQuery.isLoading
                      ? 'Loading grade limits…'
                      : 'No grade limits configured for this grade — nothing is capped here.'}
                  </Text>
                )}
              </div>
              <Button
                type="primary"
                htmlType="submit"
                icon={<Plus size={14} />}
                loading={createMutation.isPending}
              >
                Add expense
              </Button>
            </div>
          </Form>

          <FilterBar
            search={{
              value: search,
              onChange: setSearch,
              placeholder: 'Search category, claimant, amount…',
            }}
            onReset={search ? () => setSearch('') : undefined}
          />

          <Table<TravelExpenseResponse>
            rowKey={(r) => r.id}
            columns={cols}
            dataSource={visibleRows}
            loading={listQuery.isFetching}
            size="small"
            pagination={rows.length > 10 ? { pageSize: 10, size: 'small' } : false}
            scroll={{ x: 840 }}
            onChange={(_pagination, filters) => setColumnFilters(filters)}
            locale={{
              emptyText: filterActive ? (
                <EmptyState
                  size="sm"
                  icon={ReceiptText}
                  title="Nothing matches these filters"
                  description="Clear them to see every expense on this trip."
                />
              ) : (
                <EmptyState
                  size="sm"
                  icon={ReceiptText}
                  title="No expenses yet"
                  description="Add the meals, fuel and other costs incurred on this trip."
                />
              ),
            }}
            summary={() =>
              shownRows.length > 0 ? (
                <Table.Summary.Row>
                  <Table.Summary.Cell index={0} colSpan={2}>
                    <Text strong style={{ fontSize: 13 }}>
                      {filterActive ? 'Total shown' : 'Total claimed'}
                    </Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={2} align="right">
                    <Text strong style={{ fontSize: 13, fontVariantNumeric: 'tabular-nums' }}>
                      {money(claimed)}
                    </Text>
                  </Table.Summary.Cell>
                  <Table.Summary.Cell index={3} colSpan={4} />
                </Table.Summary.Row>
              ) : null
            }
          />
        </div>
      ) : null}
    </Modal>
  );
}
