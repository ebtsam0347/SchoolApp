// AntD Drawer + Form scaffold for create / edit dialogs.
//
// What it owns:
//   - Drawer chrome (title, width, footer with Cancel + Submit)
//   - The Form instance (unless caller passes one)
//   - destroyOnHidden so stale field state doesn't leak between opens
//   - Submit button loading state via `submitting` prop
//   - Mobile-responsive sizing (full-width on small viewports)
//   - Dirty-state guard: confirms before discarding unsaved input
//
// What it does NOT own:
//   - The mutation. Callers stay in charge of useMutation, success toasts,
//     query invalidation, and applyApiErrorToForm — those are too varied to
//     hide behind a single component.
//
// Why a slot-based footer is exposed too: some flows want a "Save & add
// another" button or a "Delete" button. Override `footer` for those.
//
// `renderActions` goes further: it moves the whole action row into the form
// body and drops the drawer footer, while still handing back the guarded
// close so Cancel keeps its dirty-check.

import { App, Button, Drawer, Form } from 'antd';
import type { DrawerProps, FormInstance, FormProps } from 'antd';
import type { ReactNode } from 'react';
import { useIsDesktop } from '@/lib';

export type DrawerFormProps<Values = Record<string, unknown>> = {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  /**
   * Drawer size. Forwards to AntD `size` (default | large | number | string).
   * Defaults to 480 on desktop and `'100%'` on mobile so create flows aren't
   * cramped on a phone.
   */
  size?: DrawerProps['size'];
  width?: DrawerProps['width'];
  /** Caller-managed Form instance. If omitted, an internal one is created. */
  form?: FormInstance<Values>;
  /** Pass-through to the underlying Form. */
  layout?: FormProps<Values>['layout'];
  initialValues?: FormProps<Values>['initialValues'];
  onFinish?: FormProps<Values>['onFinish'];
  /** Submit button label. Defaults to "Save". */
  submitText?: ReactNode;
  /** Cancel button label. Defaults to "Cancel". */
  cancelText?: ReactNode;
  /** Pass `mutation.isPending` to wire the submit button spinner. */
  submitting?: boolean;
  /** Disable the submit button (e.g. while form is invalid). */
  submitDisabled?: boolean;
  /** Hide the submit button entirely (read-only drawer). */
  hideSubmit?: boolean;
  /** Render extra footer content on the left (e.g. a "Delete" button). */
  footerExtra?: ReactNode;
  /** Replace the entire footer. Overrides everything above. */
  footer?: ReactNode;
  /**
   * Render the action row at the end of the form body instead of in the
   * drawer footer, and drop the footer entirely. Use when the primary action
   * reads better beside the content it acts on, or when the fixed
   * bottom-right corner is occupied by something else.
   *
   * Receives the same guarded close the drawer chrome uses, so a Cancel
   * rendered here still honours `confirmCloseWhenDirty`. When set, the
   * submit/cancel props above are ignored — you are drawing both buttons.
   */
  renderActions?: (api: { requestClose: () => void }) => ReactNode;
  /**
   * Confirm before closing when the form has unsaved input. Defaults to
   * `true`. Set to `false` for read-only or non-destructive drawers.
   */
  confirmCloseWhenDirty?: boolean;
  /** Other Drawer pass-throughs. */
  placement?: DrawerProps['placement'];
  className?: string;
  children: ReactNode;
};

export function DrawerForm<Values = Record<string, unknown>>({
  open,
  onClose,
  title,
  size,
  width,
  form: externalForm,
  layout = 'vertical',
  initialValues,
  onFinish,
  submitText = 'Save',
  cancelText = 'Cancel',
  submitting,
  submitDisabled,
  hideSubmit,
  footerExtra,
  footer,
  renderActions,
  confirmCloseWhenDirty = true,
  placement = 'right',
  className,
  children,
}: DrawerFormProps<Values>) {
  const [internalForm] = Form.useForm<Values>();
  const form = externalForm ?? internalForm;
  const isDesktop = useIsDesktop();
  const { modal } = App.useApp();

  const resolvedSize: DrawerProps['size'] =
    size ?? width ?? (isDesktop ? 480 : '100%');

  const requestClose = () => {
    if (submitting) return;
    if (!confirmCloseWhenDirty || !form.isFieldsTouched(true)) {
      onClose();
      return;
    }
    modal.confirm({
      title: 'Discard changes?',
      content:
        'Your unsaved input will be lost. You can keep editing or discard and close.',
      okText: 'Discard',
      okButtonProps: { danger: true },
      cancelText: 'Keep editing',
      onOk: onClose,
    });
  };

  const defaultFooter = (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--app-space-sm)',
      }}
    >
      {footerExtra}
      <div style={{ marginLeft: 'auto', display: 'flex', gap: 'var(--app-space-sm)' }}>
        <Button onClick={requestClose}>{cancelText}</Button>
        {hideSubmit ? null : (
          <Button
            type="primary"
            loading={submitting}
            disabled={submitDisabled}
            onClick={() => form.submit()}
          >
            {submitText}
          </Button>
        )}
      </div>
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={requestClose}
      title={title}
      size={resolvedSize}
      placement={placement}
      destroyOnHidden
      className={className}
      footer={renderActions ? undefined : (footer ?? defaultFooter)}
    >
      <Form
        form={form}
        layout={layout}
        initialValues={initialValues}
        onFinish={onFinish}
      >
        {children}
        {renderActions?.({ requestClose })}
      </Form>
    </Drawer>
  );
}
