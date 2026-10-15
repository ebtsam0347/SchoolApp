// Edits the placement identifiers on a single requisition position row —
// New Employee Code and Temporary Employee Reference — for both NEW_HIRE
// and REPLACEMENT requisitions. Opened from the "Action" column in the
// RequisitionDetailDrawer's Position Details table.
//
// PATCH /requisitions/{requisitionId}/details/{detailId} with whatever the
// user typed; on success the details query is invalidated so the table
// reflects the new values.

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { App as AntdApp, Form, Input, Modal } from 'antd';

import { getErrorMessage } from '@/lib';
import { requisitionApi } from '../api';
import type { RequisitionEmployeeDetailDto } from '../types';

export type RequisitionDetailEditModalProps = {
  requisitionId: number;
  /** The position row being edited — `null` keeps the modal closed. */
  detail: RequisitionEmployeeDetailDto | null;
  onClose: () => void;
};

type FormValues = {
  newEmployeeCode?: string;
  temporaryEmployeeReference?: string;
};

export function RequisitionDetailEditModal({
  requisitionId,
  detail,
  onClose,
}: RequisitionDetailEditModalProps) {
  const [form] = Form.useForm<FormValues>();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const open = detail != null;

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      requisitionApi.updateDetail(requisitionId, detail?.id ?? 0, {
        newEmployeeCode: values.newEmployeeCode?.trim() || undefined,
        temporaryEmployeeReference: values.temporaryEmployeeReference?.trim() || undefined,
      }),
    onSuccess: () => {
      message.success('Position details updated');
      void queryClient.invalidateQueries({
        queryKey: ['requisitions', requisitionId, 'details'],
      });
      void queryClient.invalidateQueries({ queryKey: ['requisitions'] });
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  return (
    <Modal
      title="Update position details"
      open={open}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText="Save"
      confirmLoading={mutation.isPending}
      destroyOnHidden
    >
      <Form
        // Remount per row (and on every open, thanks to destroyOnHidden) so
        // `initialValues` re-applies — this is what fills the inputs with the
        // detail's existing New Employee Code / Temporary Employee Reference,
        // or leaves them blank when the row has none.
        key={detail?.id ?? 'none'}
        form={form}
        layout="vertical"
        preserve={false}
        initialValues={{
          newEmployeeCode: detail?.newEmployeeCode ?? '',
          temporaryEmployeeReference: detail?.temporaryEmployeeReference ?? '',
        }}
        onFinish={(values) => mutation.mutate(values)}
      >
        <Form.Item label="New Employee Code" name="newEmployeeCode">
          <Input placeholder="e.g. 1122" allowClear autoFocus />
        </Form.Item>
        <Form.Item
          label="Temporary Employee Reference"
          name="temporaryEmployeeReference"
          style={{ marginBottom: 0 }}
        >
          <Input placeholder="e.g. Temporary Name" allowClear />
        </Form.Item>
      </Form>
    </Modal>
  );
}
