// Modal that asks for a rejection reason and calls back when confirmed.
//
// Replaces the hand-rolled `modal.confirm({ content: <Input.TextArea/>, ... })`
// pattern that lived in inbox / leaves / roles / replay / delegations. The
// pattern there:
//   - leaks `let reason = ''` outside the JSX,
//   - silently swallows the validation by `throw`-ing inside `onOk`,
//   - duplicates the toast/invalidate dance.
//
// Usage:
//   const [target, setTarget] = useState<Row | null>(null);
//   <RejectReasonModal
//     open={!!target}
//     title="Reject this leave?"
//     description="The whole request is rejected at your stage."
//     onCancel={() => setTarget(null)}
//     onConfirm={async (reason) => {
//       await api.reject(target!.id, { reason });
//       setTarget(null);
//     }}
//   />
//
// `onConfirm` is awaited; rejecting it leaves the modal open. Errors are
// surfaced with `message.error(getErrorMessage(err))` automatically.

import { App as AntdApp, Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import type { ReactNode } from 'react';
import { getErrorMessage } from '@/lib';

export type RejectReasonModalProps = {
  open: boolean;
  title?: ReactNode;
  description?: ReactNode;
  /** Confirm-button label. Defaults to "Reject". */
  okText?: ReactNode;
  /** Render the confirm button as `danger`. Defaults to true. */
  danger?: boolean;
  /** Reason TextArea placeholder. */
  placeholder?: string;
  /** Minimum reason length. Defaults to 4. */
  minLength?: number;
  /** Whether the reason is mandatory. Defaults to true. */
  required?: boolean;
  /** Called when the user clicks Cancel or dismisses the modal. */
  onCancel: () => void;
  /**
   * Called when the user confirms with a non-empty reason. If it throws or
   * rejects, the modal stays open and the error is surfaced as a toast.
   */
  onConfirm: (reason: string) => Promise<void> | void;
  /** Optional success toast — set null to suppress. Defaults to "Rejected". */
  successMessage?: string | null;
};

export function RejectReasonModal({
  open,
  title = 'Reject?',
  description,
  okText = 'Reject',
  danger = true,
  placeholder = 'Reason (required)',
  minLength = 4,
  required = true,
  onCancel,
  onConfirm,
  successMessage = 'Rejected',
}: RejectReasonModalProps) {
  const [form] = Form.useForm<{ reason: string }>();
  const { message } = AntdApp.useApp();

  // Reset the form whenever the modal re-opens — otherwise a stale reason
  // from the previous target would carry through.
  useEffect(() => {
    if (open) form.resetFields();
  }, [open, form]);

  const handleOk = async () => {
    const reason = form.getFieldValue('reason')?.trim() ?? '';
    if (required && reason.length < minLength) {
      form.setFields([
        {
          name: 'reason',
          errors: [
            minLength > 1
              ? `At least ${minLength} characters required.`
              : 'Reason is required.',
          ],
        },
      ]);
      return;
    }
    try {
      await onConfirm(reason);
      if (successMessage) message.success(successMessage);
    } catch (err) {
      message.error(getErrorMessage(err));
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      onCancel={onCancel}
      onOk={handleOk}
      okText={okText}
      okButtonProps={{ danger }}
      destroyOnHidden
    >
      {description ? (
        <p style={{ margin: '0 0 var(--app-space-sm)', fontSize: 13 }}>
          {description}
        </p>
      ) : null}
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="reason" style={{ marginBottom: 0 }}>
          <Input.TextArea
            autoFocus
            autoSize={{ minRows: 2, maxRows: 5 }}
            placeholder={placeholder}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
