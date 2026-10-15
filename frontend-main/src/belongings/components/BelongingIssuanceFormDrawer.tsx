// Create / edit drawer for a Belonging Issuance — the "hand this belonging
// to this employee" transaction. Activation and clearance are managed
// through their own dedicated actions/endpoints (see
// BelongingIssuanceRowActions + ClearanceUpdateModal), not this form — on
// create they default to ACTIVE / PENDING; on edit the current values are
// carried through unchanged since PUT replaces the whole record.
//
// A Belonging Master row is authorized to exactly one employee (see
// `/admin/lookups/belongings`), so the employee field isn't a free pick —
// it's locked to whoever that belonging is authorized to (admin mode) or
// the signed-in user (self mode, from the My Belongings self-service page).

import { useEffect } from 'react';
import { App as AntdApp, DatePicker, Form, Input, InputNumber, Select } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';

import { DrawerForm } from '@/components';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { catalogsApi, employeesApi } from '@/people';
import { belongingIssuanceApi } from '../api';
import type { BelongingCondition, BelongingIssuanceDto } from '../types';

const CONDITION_OPTIONS: { value: BelongingCondition; label: string }[] = [
  { value: 'NEW', label: 'NEW' },
  { value: 'USED', label: 'USED' },
  { value: 'OLD', label: 'OLD' },
];

export type BelongingIssuanceFormDrawerProps = {
  open: boolean;
  onClose: () => void;
  /** Pass an existing issuance to edit it in place; omit to issue a new one. */
  issuance?: BelongingIssuanceDto | null;
  /**
   * 'self' restricts the belonging picker to items authorized to the
   * signed-in user and locks the employee to them — used on the
   * self-service My Belongings page. 'employee' restricts the picker to
   * items authorized to `presetEmployeeId` and locks the employee to
   * them — used from the employee detail page's Actions menu. Defaults to
   * 'admin' (any active belonging, employee locked to whoever it's
   * authorized to).
   */
  mode?: 'admin' | 'self' | 'employee';
  /** Self-service: preset + lock the belonging when issuing from a specific row. */
  presetBelongingMasterId?: number;
  /** Required when `mode` is 'employee' — who the issuance is for. */
  presetEmployeeId?: number;
};

type Values = {
  belongingMasterId: number;
  employeeId: number;
  assetModel?: string;
  assetYear?: number;
  assetPrice?: number;
  issuedDate: dayjs.Dayjs;
  expiryDate?: dayjs.Dayjs;
  buyBackPercEmployee?: number;
  buyBackPercEmployer?: number;
  condition?: BelongingCondition;
  description?: string;
};

export function BelongingIssuanceFormDrawer({
  open,
  onClose,
  issuance,
  mode = 'admin',
  presetBelongingMasterId,
  presetEmployeeId,
}: BelongingIssuanceFormDrawerProps) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();
  const editing = issuance ?? null;

  // 'employee' mode reuses the unfiltered admin list (there's no
  // belongings-authorized-to/{employeeId} endpoint) and filters client-side
  // — same as 'self' does server-side for the signed-in user.
  const belongingsQuery = useQuery({
    queryKey:
      mode === 'self'
        ? ['catalog', 'belonging-master-authorized-to-me']
        : ['catalog', 'belonging-master-all'],
    queryFn: ({ signal }) =>
      mode === 'self'
        ? catalogsApi.belongingsAuthorizedToMe(signal)
        : catalogsApi.belongingMasters(signal),
    staleTime: 5 * 60_000,
  });
  // Asset Type shows the entire master list regardless of which employee a
  // row happens to be authorized to — 'employee' mode used to filter this
  // down to only rows authorized to presetEmployeeId, which meant "No data"
  // whenever this employee had no asset type authorized specifically to
  // them. The employee this issuance is for still comes from presetEmployeeId
  // (mode 'employee') or the picked row's authorizedEmployeeId (mode
  // 'admin') below — this list is just what's pickable.
  const employeeBelongings = belongingsQuery.data ?? [];

  const meQuery = useQuery({
    queryKey: ['employees', 'me'],
    queryFn: ({ signal }) => employeesApi.me(signal),
    enabled: mode === 'self',
    staleTime: 5 * 60_000,
  });

  const belongingMasterId = Form.useWatch('belongingMasterId', form);
  const resolvedEmployeeId =
    mode === 'self'
      ? meQuery.data?.id
      : mode === 'employee'
        ? presetEmployeeId
        : belongingsQuery.data?.find((b) => b.id === belongingMasterId)?.authorizedEmployeeId;

  const employeeDetailQuery = useQuery({
    queryKey: ['employees', 'detail', resolvedEmployeeId],
    queryFn: ({ signal }) => employeesApi.get(resolvedEmployeeId!, signal),
    enabled: resolvedEmployeeId != null && (mode === 'admin' || mode === 'employee'),
    staleTime: 5 * 60_000,
  });
  const employeeDisplay = mode === 'self' ? meQuery.data : employeeDetailQuery.data;
  const employeeLoading = mode === 'self' ? meQuery.isLoading : employeeDetailQuery.isLoading;

  // Keep the hidden employeeId field in sync with whoever the belonging is
  // authorized to (admin) or the signed-in user (self) — nothing to pick.
  useEffect(() => {
    form.setFieldValue('employeeId', resolvedEmployeeId);
  }, [resolvedEmployeeId, form]);

  const mutation = useMutation({
    mutationFn: (v: Values) => {
      const body = {
        belongingMasterId: v.belongingMasterId,
        employeeId: v.employeeId,
        assetModel: v.assetModel,
        assetYear: v.assetYear,
        assetPrice: v.assetPrice,
        issuedDate: v.issuedDate.format('YYYY-MM-DD'),
        expiryDate: v.expiryDate ? v.expiryDate.format('YYYY-MM-DD') : undefined,
        buyBackPercEmployee: v.buyBackPercEmployee,
        buyBackPercEmployer: v.buyBackPercEmployer,
        condition: v.condition,
        description: v.description,
      };
      if (editing) {
        return belongingIssuanceApi.update(editing.id!, {
          ...body,
          activationStatus: editing.activationStatus,
          activationDate: editing.activationDate,
          clearanceStatus: editing.clearanceStatus,
          clearanceId: editing.clearanceId,
          clearanceDate: editing.clearanceDate,
        });
      }
      return belongingIssuanceApi.create({
        ...body,
        activationStatus: 'ACTIVE',
        activationDate: v.issuedDate.format('YYYY-MM-DD'),
        clearanceStatus: 'PENDING',
      });
    },
    onSuccess: () => {
      message.success(editing ? 'Issuance updated' : 'Asset allocated');
      queryClient.invalidateQueries({ queryKey: ['belonging-issuance'] });
      form.resetFields();
      onClose();
    },
    onError: (err) => {
      if (isApiError(err)) applyApiErrorToForm(form, err);
      message.error(getErrorMessage(err));
    },
  });

  const initialValues: Partial<Values> = editing
    ? {
        belongingMasterId: editing.belongingMasterId,
        employeeId: editing.employeeId,
        assetModel: editing.assetModel,
        assetYear: editing.assetYear,
        assetPrice: editing.assetPrice,
        issuedDate: editing.issuedDate ? dayjs(editing.issuedDate) : undefined,
        expiryDate: editing.expiryDate ? dayjs(editing.expiryDate) : undefined,
        buyBackPercEmployee: editing.buyBackPercEmployee,
        buyBackPercEmployer: editing.buyBackPercEmployer,
        condition: editing.condition,
        description: editing.description,
      }
    : { issuedDate: dayjs(), belongingMasterId: presetBelongingMasterId };

  return (
    <DrawerForm<Values>
      title={editing ? 'Edit issuance' : mode === 'self' ? 'Allocate to yourself' : 'Allocate an asset'}
      open={open}
      onClose={onClose}
      form={form}
      initialValues={initialValues}
      submitText={editing ? 'Save' : 'Allocate'}
      submitting={mutation.isPending}
      onFinish={(v) => mutation.mutate(v)}
    >
      <Form.Item
        label="Asset Type"
        name="belongingMasterId"
        rules={[{ required: true, message: 'Pick an asset type' }]}
        extra={
          mode === 'self' && (belongingsQuery.data ?? []).length === 0 && !belongingsQuery.isLoading
            ? "You don't have any asset types authorized to you yet — ask HR."
            : (mode === 'employee' || mode === 'admin') && employeeBelongings.length === 0 && !belongingsQuery.isLoading
              ? 'No asset types in the master list yet — add one under Lookups → Asset Types.'
              : undefined
        }
      >
        <Select
          showSearch
          optionFilterProp="label"
          disabled={presetBelongingMasterId != null}
          loading={belongingsQuery.isLoading}
          placeholder={mode === 'self' ? 'Pick from what you’re authorized for' : 'Pick an asset type'}
          options={employeeBelongings.map((b) => ({
            value: b.id!,
            label: b.belongingName,
          }))}
        />
      </Form.Item>

      <Form.Item
        label="Employee"
        name="employeeId"
        // Already implied by the page we opened this drawer from — showing
        // a disabled, pre-filled picker back at the operator is just noise.
        // Kept mounted (not removed) so the field stays registered and its
        // value still submits to the backend.
        style={mode === 'employee' ? { display: 'none' } : undefined}
        tooltip={
          mode === 'self'
            ? 'This issuance is always for you.'
            : 'Locked to whoever this asset type is authorized to in the Asset Types master list.'
        }
        rules={[
          {
            required: true,
            message:
              mode === 'admin' && belongingMasterId
                ? 'This asset type has no authorized employee set — authorize one in Asset Types first'
                : 'Pick an asset type',
          },
        ]}
      >
        <Select
          disabled
          loading={employeeLoading}
          placeholder={belongingMasterId || mode === 'self' ? 'No authorized employee set' : 'Pick an asset type first'}
          options={
            resolvedEmployeeId != null
              ? [
                  {
                    value: resolvedEmployeeId,
                    label: employeeDisplay
                      ? [employeeDisplay.fullName, employeeDisplay.employeeCode]
                          .filter(Boolean)
                          .join(' · ')
                      : `#${resolvedEmployeeId}`,
                  },
                ]
              : []
          }
        />
      </Form.Item>

      <Form.Item label="Asset model" name="assetModel" extra="e.g. MacBook Pro 14&quot;, Honda CD 70.">
        <Input placeholder="Model / make" />
      </Form.Item>

      <Form.Item label="Asset year" name="assetYear">
        <InputNumber min={1990} max={2100} style={{ width: '100%' }} />
      </Form.Item>

      <Form.Item label="Asset price" name="assetPrice">
        <InputNumber min={0} style={{ width: '100%' }} prefix="Rs" />
      </Form.Item>

      <Form.Item
        label="Issued date"
        name="issuedDate"
        rules={[{ required: true, message: 'Pick the issue date' }]}
      >
        <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
      </Form.Item>

      <Form.Item label="Expiry date" name="expiryDate" extra="Optional — leave blank if it doesn't expire.">
        <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
      </Form.Item>

      <Form.Item
        label="Buy-back % (employee)"
        name="buyBackPercEmployee"
        extra="Share of the residual value the employee pays if they keep it."
      >
        <InputNumber min={0} max={100} style={{ width: '100%' }} suffix="%" />
      </Form.Item>

      <Form.Item label="Buy-back % (employer)" name="buyBackPercEmployer">
        <InputNumber min={0} max={100} style={{ width: '100%' }} suffix="%" />
      </Form.Item>

      <Form.Item label="Condition" name="condition">
        <Select placeholder="Condition on issue" options={CONDITION_OPTIONS} allowClear />
      </Form.Item>

      <Form.Item label="Description" name="description">
        <Input.TextArea
          autoSize={{ minRows: 2, maxRows: 4 }}
          placeholder="Optional notes about this issuance."
        />
      </Form.Item>
    </DrawerForm>
  );
}