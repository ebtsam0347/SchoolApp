// Small standalone modal for PUT /belonging-issuance/{id}/clearance — kept
// separate from the main edit drawer since clearance is its own workflow
// step (asset return / write-off), not a general field edit.

import { App as AntdApp, DatePicker, Form, Input, Modal, Select } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';

import { getErrorMessage } from '@/lib';
import { belongingIssuanceApi } from '../api';
import { CLEARANCE_STATUS_LABEL } from '../format';
import type { BelongingIssuanceDto, ClearanceStatus } from '../types';

const CLEARANCE_STATUS_VALUES: ClearanceStatus[] = ['PENDING', 'APPROVED', 'REJECTED'];

type Values = {
  clearanceStatus: ClearanceStatus;
  clearanceId?: string;
  clearanceDate?: dayjs.Dayjs;
};

export function ClearanceUpdateModal({
  open,
  onClose,
  issuance,
}: {
  open: boolean;
  onClose: () => void;
  issuance: BelongingIssuanceDto | null;
}) {
  const [form] = Form.useForm<Values>();
  const queryClient = useQueryClient();
  const { message } = AntdApp.useApp();

  const mutation = useMutation({
    mutationFn: (v: Values) =>
      belongingIssuanceApi.updateClearance(issuance?.id ?? 0, {
        clearanceStatus: v.clearanceStatus,
        clearanceId: v.clearanceId,
        clearanceDate: v.clearanceDate ? v.clearanceDate.format('YYYY-MM-DD') : undefined,
      }),
    onSuccess: () => {
      message.success('Clearance updated');
      queryClient.invalidateQueries({ queryKey: ['belonging-issuance'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <Modal
      open={open}
      title={`Update clearance${issuance?.belongingName ? ` — ${issuance.belongingName}` : ''}`}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Save"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Form<Values>
        form={form}
        layout="vertical"
        initialValues={{
          clearanceStatus: issuance?.clearanceStatus ?? 'PENDING',
          clearanceId: issuance?.clearanceId,
          clearanceDate: issuance?.clearanceDate ? dayjs(issuance.clearanceDate) : undefined,
        }}
        onFinish={(v) => mutation.mutate(v)}
      >
        <Form.Item
          label="Clearance status"
          name="clearanceStatus"
          rules={[{ required: true }]}
        >
          <Select
            options={CLEARANCE_STATUS_VALUES.map((s) => ({
              value: s,
              label: CLEARANCE_STATUS_LABEL[s],
            }))}
          />
        </Form.Item>
        <Form.Item label="Clearance reference" name="clearanceId" extra="e.g. clearance form / ticket number.">
          <Input placeholder="Reference ID" />
        </Form.Item>
        <Form.Item label="Clearance date" name="clearanceDate">
          <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
