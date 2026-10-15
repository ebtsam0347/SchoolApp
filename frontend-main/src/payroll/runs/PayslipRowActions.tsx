// Inline hold / unhold / release / undo actions on each payslip row.
// Visibility is gated on payslip status + run status; hold / unhold /
// release open a modal asking for a required reason (audit trail). Undo
// drops the payslip and moves the employee to the run's skipped list.

import { useContext, useState } from 'react';
import {
  App as AntdApp,
  Button,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Typography,
} from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Hand, Receipt, RotateCcw, Undo2 } from 'lucide-react';
import { AbilityContext, useAuth } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import {
  payrollRunsApi,
  payslipsApi,
  type PayrollRunDto,
  type PayslipStatus,
} from '@/payroll';

const { Text } = Typography;

export type PayslipRowActionsProps = {
  runId: number;
  payslipId: number;
  payslipStatus: PayslipStatus;
  runStatus: PayrollRunDto['status'];
  onChanged: () => void;
};

export function PayslipRowActions({
  runId,
  payslipId,
  payslipStatus,
  runStatus,
  onChanged,
}: PayslipRowActionsProps) {
  const auth = useAuth();
  const canHold = !!auth.user?.permissions.some((p) =>
    p.startsWith('payroll.payslips'),
  );
  // Undo hits a `payroll.admin` endpoint; CASL maps admin to `manage`.
  const canUndo = useContext(AbilityContext).can('manage', 'payroll');
  const { message } = AntdApp.useApp();
  const [openAction, setOpenAction] = useState<
    'hold' | 'unhold' | 'release' | null
  >(null);
  const [form] = Form.useForm<{ reason: string }>();

  const hold = useMutation({
    mutationFn: (reason: string) => payslipsApi.hold(payslipId, { reason }),
    onSuccess: () => {
      message.success('Payslip held.');
      setOpenAction(null);
      onChanged();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });
  const unhold = useMutation({
    mutationFn: (reason: string) => payslipsApi.unhold(payslipId, { reason }),
    onSuccess: () => {
      message.success('Payslip unheld.');
      setOpenAction(null);
      onChanged();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });
  const release = useMutation({
    mutationFn: (reason: string) => payslipsApi.release(payslipId, { reason }),
    onSuccess: () => {
      message.success('Release queued.');
      setOpenAction(null);
      onChanged();
    },
    onError: (e) => {
      if (isApiError(e)) applyApiErrorToForm(form, e);
      message.error(getErrorMessage(e));
    },
  });

  const undo = useMutation({
    mutationFn: () => payrollRunsApi.undoPayslip(runId, payslipId),
    onSuccess: () => {
      message.success('Payslip undone. The employee is now under Skipped employees.');
      onChanged();
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  // Undo: DRAFT payslip on a COMPUTED (not yet approved) run.
  const canShowUndo =
    canUndo && payslipStatus === 'DRAFT' && runStatus === 'COMPUTED';
  if (!canHold && !canShowUndo) return null;

  // Hold: DRAFT (pre-post) or PAID (post). Hidden on RELEASE_PENDING / RELEASED.
  // Unhold: HELD only AND run not yet POSTED — returns the payslip to DRAFT.
  // Release: HELD only AND run POSTED.
  const canShowHold =
    canHold && (payslipStatus === 'DRAFT' || payslipStatus === 'PAID');
  const canShowUnhold =
    canHold && payslipStatus === 'HELD' && runStatus !== 'POSTED';
  const canShowRelease =
    canHold && payslipStatus === 'HELD' && runStatus === 'POSTED';

  return (
    <>
      <Space size={4} wrap>
        {canShowHold && (
          <Button
            size="small"
            icon={<Hand size={12} strokeWidth={1.75} />}
            onClick={() => setOpenAction('hold')}
          >
            Hold
          </Button>
        )}
        {canShowUnhold && (
          <Button
            size="small"
            icon={<Undo2 size={12} strokeWidth={1.75} />}
            onClick={() => setOpenAction('unhold')}
          >
            Unhold
          </Button>
        )}
        {canShowRelease && (
          <Button
            size="small"
            type="primary"
            icon={<Receipt size={12} strokeWidth={1.75} />}
            onClick={() => setOpenAction('release')}
          >
            Release
          </Button>
        )}
        {canShowUndo && (
          <Popconfirm
            title="Undo this payslip?"
            description="Deletes the payslip with its permanent advance deduction and RFD entry. The employee moves to Skipped employees; retry from there to compute again."
            okText="Undo"
            okButtonProps={{ danger: true }}
            onConfirm={() => undo.mutateAsync()}
          >
            <Button
              size="small"
              danger
              icon={<RotateCcw size={12} strokeWidth={1.75} />}
              loading={undo.isPending}
            >
              Undo
            </Button>
          </Popconfirm>
        )}
      </Space>

      <Modal
        title={
          openAction === 'hold'
            ? 'Hold payslip'
            : openAction === 'unhold'
              ? 'Unhold payslip'
              : 'Release held payslip'
        }
        open={openAction !== null}
        onCancel={() => {
          setOpenAction(null);
          form.resetFields();
        }}
        okText={
          openAction === 'hold'
            ? 'Hold'
            : openAction === 'unhold'
              ? 'Unhold'
              : 'Queue release'
        }
        confirmLoading={hold.isPending || unhold.isPending || release.isPending}
        onOk={() =>
          form.validateFields().then(({ reason }) => {
            if (openAction === 'hold') return hold.mutateAsync(reason);
            if (openAction === 'unhold') return unhold.mutateAsync(reason);
            if (openAction === 'release') return release.mutateAsync(reason);
          })
        }
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="reason"
            label="Reason"
            rules={[{ required: true, message: 'A reason is required.' }]}
          >
            <Input.TextArea rows={3} placeholder="Why are you taking this action?" />
          </Form.Item>
          {openAction === 'release' && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              The held net will appear as a HELD_SALARY_RELEASE earning line on
              this employee's next payslip.
            </Text>
          )}
        </Form>
      </Modal>
    </>
  );
}
