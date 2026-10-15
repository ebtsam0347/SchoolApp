// Salary certificate: asks for the optional addressee and purpose, renders
// the PDF, and hands the file to the caller's preview drawer. Failures
// (422 EMPLOYEE_INACTIVE / NO_BASIC_SALARY, …) stay in the modal so the
// user can correct and resubmit.

import { useState } from 'react';
import { Alert, Form, Input, Modal } from 'antd';

import { getErrorMessage } from '@/lib';

import type { ReportFile } from '../download';
import { employeeReportsApi, type SalaryCertificateOptions } from './api';
import { reportProblemAlert } from './ReportProblemAlert';

export interface SalaryCertificateModalProps {
  open: boolean;
  employeeId: number;
  onClose: () => void;
  onRendered: (file: ReportFile) => void;
}

export function SalaryCertificateModal({
  open,
  employeeId,
  onClose,
  onRendered,
}: SalaryCertificateModalProps) {
  const [form] = Form.useForm<SalaryCertificateOptions>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  function handleClose() {
    setError(null);
    onClose();
  }

  async function submit(values: SalaryCertificateOptions) {
    setLoading(true);
    setError(null);
    try {
      const file = await employeeReportsApi.salaryCertificate(employeeId, values);
      form.resetFields();
      onRendered(file);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }

  return (
    <Modal
      open={open}
      title="Salary Certificate"
      okText="Preview"
      onOk={() => form.submit()}
      confirmLoading={loading}
      onCancel={handleClose}
      destroyOnHidden
    >
      {error ? (
        <div style={{ marginBottom: 16 }}>
          {reportProblemAlert('salaryCertificate', error, employeeId, handleClose) ?? (
            <Alert type="error" showIcon title={getErrorMessage(error)} />
          )}
        </div>
      ) : null}
      <Form<SalaryCertificateOptions>
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={(v) => void submit(v)}
      >
        <Form.Item
          name="addressee"
          label="Addressee"
          extra="Leave empty for 'To Whom It May Concern'"
          rules={[{ max: 200, message: 'At most 200 characters.' }]}
        >
          <Input
            allowClear
            maxLength={200}
            placeholder="The Branch Manager, Meezan Bank, Sialkot"
          />
        </Form.Item>
        <Form.Item
          name="purpose"
          label="Purpose"
          rules={[{ max: 200, message: 'At most 200 characters.' }]}
        >
          <Input allowClear maxLength={200} placeholder="car loan application" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
