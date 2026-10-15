// Admissions / contact enquiry form. Posts to the Pages Function at
// ENQUIRY_ENDPOINT, which forwards to the school's configured form service.
// A success message is shown ONLY when the endpoint answers { ok: true } —
// if no service is configured, the visitor sees an honest error instead.

import { Link } from '@tanstack/react-router';
import { Alert, Button, Checkbox, Form, Input, Radio, Select } from 'antd';
import { CheckCircle2, Send } from 'lucide-react';
import { useState } from 'react';
import { ENQUIRY_ENDPOINT, SCHOOL } from './config';

export type EnquiryType = 'admissions' | 'visit' | 'general';

type Values = {
  enquiryType: EnquiryType;
  parentName: string;
  email: string;
  phone?: string;
  ageGroup?: string;
  contactMethod: 'email' | 'phone' | 'whatsapp';
  message?: string;
  consent: boolean;
  /** Honeypot — real visitors never see or fill this. */
  website?: string;
};

const TYPE_OPTIONS = [
  { value: 'admissions', label: SCHOOL.admissionsOpen ? 'Admissions enquiry' : 'Register interest in admissions' },
  { value: 'visit', label: 'Arrange a school visit' },
  { value: 'general', label: 'General question' },
];

const AGE_OPTIONS = ['Around 2–3 years', 'Around 3–4 years', 'Around 4–5 years', 'Around 5–6 years', 'Not sure yet'].map(
  (v) => ({ value: v, label: v }),
);

type Status = { kind: 'idle' } | { kind: 'sending' } | { kind: 'sent' } | { kind: 'error'; message: string };

export function EnquiryForm({ defaultType = 'admissions' }: { defaultType?: EnquiryType }) {
  const [form] = Form.useForm<Values>();
  const [status, setStatus] = useState<Status>({ kind: 'idle' });
  const enquiryType = Form.useWatch('enquiryType', form) ?? defaultType;
  const contactMethod = Form.useWatch('contactMethod', form);

  async function onFinish(values: Values) {
    setStatus({ kind: 'sending' });
    try {
      const res = await fetch(ENQUIRY_ENDPOINT, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(values),
      });
      const data = (await res.json().catch(() => null)) as { ok?: boolean; error?: string } | null;
      if (res.ok && data?.ok === true) {
        setStatus({ kind: 'sent' });
        form.resetFields();
        return;
      }
      setStatus({
        kind: 'error',
        message:
          data?.error === 'not_configured' || res.status === 404 || res.status === 405
            ? 'Online enquiries are not connected yet, so your message was not sent. Please try again later.'
            : data?.error === 'invalid'
              ? 'Some details could not be accepted. Please check the form and try again.'
              : 'Your enquiry could not be sent. Please try again in a few minutes.',
      });
    } catch {
      setStatus({
        kind: 'error',
        message: 'Your enquiry could not be sent — please check your connection and try again.',
      });
    }
  }

  if (status.kind === 'sent') {
    return (
      <div className="s-form-success" role="status">
        <CheckCircle2 size={40} strokeWidth={1.5} aria-hidden="true" />
        <h3 className="s-h3">Thank you — your enquiry has been sent.</h3>
        <p>A member of our team will be in touch using your preferred contact method.</p>
        <button type="button" className="s-btn s-btn--ghost" onClick={() => setStatus({ kind: 'idle' })}>
          Send another enquiry
        </button>
      </div>
    );
  }

  return (
    <Form<Values>
      form={form}
      layout="vertical"
      requiredMark="optional"
      size="large"
      className="s-form"
      onFinish={onFinish}
      disabled={status.kind === 'sending'}
      initialValues={{ enquiryType: defaultType, contactMethod: 'email', consent: false }}
      scrollToFirstError
    >
      {status.kind === 'error' && (
        <Alert type="error" showIcon className="s-form__alert" title={status.message} />
      )}

      <Form.Item label="What can we help with?" name="enquiryType" rules={[{ required: true }]}>
        <Select options={TYPE_OPTIONS} />
      </Form.Item>

      <div className="s-form__grid">
        <Form.Item
          label="Parent or guardian name"
          name="parentName"
          rules={[
            { required: true, whitespace: true, message: 'Please enter your name.' },
            { max: 100, message: 'Please keep this under 100 characters.' },
          ]}
        >
          <Input autoComplete="name" />
        </Form.Item>
        <Form.Item
          label="Email address"
          name="email"
          rules={[
            { required: true, message: 'Please enter your email address.' },
            { type: 'email', message: 'Please enter a valid email address.' },
          ]}
        >
          <Input type="email" autoComplete="email" inputMode="email" />
        </Form.Item>
        <Form.Item
          label="Phone number"
          name="phone"
          rules={[
            {
              required: contactMethod === 'phone' || contactMethod === 'whatsapp',
              message: 'Please add a phone number so we can reach you.',
            },
            { pattern: /^[+\d][\d\s-]{6,18}$/, message: 'Please enter a valid phone number.' },
          ]}
        >
          <Input type="tel" autoComplete="tel" inputMode="tel" />
        </Form.Item>
        {enquiryType !== 'general' && (
          <Form.Item label="Child’s age or intended programme" name="ageGroup">
            <Select options={AGE_OPTIONS} placeholder="Select" allowClear />
          </Form.Item>
        )}
      </div>

      <Form.Item label="Preferred contact method" name="contactMethod" rules={[{ required: true }]}>
        <Radio.Group
          options={[
            { value: 'email', label: 'Email' },
            { value: 'phone', label: 'Phone call' },
            { value: 'whatsapp', label: 'WhatsApp' },
          ]}
        />
      </Form.Item>

      <Form.Item
        label="Message"
        name="message"
        rules={[{ max: 1000, message: 'Please keep your message under 1000 characters.' }]}
      >
        <Input.TextArea
          rows={4}
          showCount
          maxLength={1000}
          placeholder={
            enquiryType === 'visit'
              ? 'Let us know which days or times usually suit you.'
              : 'Anything you would like to ask or tell us.'
          }
        />
      </Form.Item>

      {/* Honeypot: hidden from people and assistive tech. */}
      <Form.Item name="website" className="s-hp" aria-hidden="true">
        <Input tabIndex={-1} autoComplete="off" />
      </Form.Item>

      <Form.Item
        name="consent"
        valuePropName="checked"
        rules={[
          {
            validator: (_, v) =>
              v ? Promise.resolve() : Promise.reject(new Error('Please confirm so we can contact you.')),
          },
        ]}
      >
        <Checkbox>
          I agree to be contacted about this enquiry and have read the{' '}
          <Link to="/privacy">privacy notice</Link>.
        </Checkbox>
      </Form.Item>

      <p className="s-form__fine">
        Please don’t include sensitive information about your child. We only use these details to reply to your
        enquiry.
      </p>

      <Button
        type="primary"
        htmlType="submit"
        loading={status.kind === 'sending'}
        icon={<Send size={16} aria-hidden="true" />}
        className="s-form__submit"
      >
        {status.kind === 'sending' ? 'Sending…' : 'Send enquiry'}
      </Button>
    </Form>
  );
}
