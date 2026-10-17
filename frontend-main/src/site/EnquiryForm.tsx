// Admissions / contact enquiry form. Posts to the Pages Function at
// ENQUIRY_ENDPOINT, which forwards to the school's configured form service.
// A success message is shown ONLY when the endpoint answers { ok: true } —
// if no service is configured, the visitor sees an honest error instead.

import { Link } from '@tanstack/react-router';
import { Alert, Button, Checkbox, Form, Input, Radio, Select } from 'antd';
import { CheckCircle2, Send } from 'lucide-react';
import { useState } from 'react';
import { ENQUIRY_ENDPOINT, SCHOOL } from './config';

import { isUrdu, tr, trf } from '@/site/i18n';
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
  { value: 'admissions', label: SCHOOL.admissionsOpen ? tr('Admissions enquiry') : tr('Register for admissions') },
  { value: 'visit', label: tr('Book a school visit') },
  { value: 'general', label: tr('General question') },
];

const AGE_OPTIONS = [tr('Around 2–3 years'), tr('Around 3–4 years'), tr('Around 4–5 years'), tr('Around 5–6 years'), tr('Not sure yet')].map(
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
            ? tr('Online enquiries are not connected yet, so your message was not sent. Please try again later.')
            : data?.error === 'invalid'
              ? tr('Some details could not be accepted. Please check the form and try again.')
              : tr('Your enquiry could not be sent. Please try again in a few minutes.'),
      });
    } catch {
      setStatus({
        kind: 'error',
        message: tr('Your enquiry could not be sent — please check your connection and try again.'),
      });
    }
  }

  if (status.kind === 'sent') {
    return (
      <div className="s-form-success" role="status">
        <CheckCircle2 size={40} strokeWidth={1.5} aria-hidden="true" />
        <h3 className="s-h3">{tr('Thank you — your enquiry has been sent.')}</h3>
        <p>{tr('A member of our team will be in touch using your preferred contact method.')}</p>
        <button type="button" className="s-btn s-btn--ghost" onClick={() => setStatus({ kind: 'idle' })}>
          {tr('Send another enquiry')}</button>
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
      initialValues={{ enquiryType: defaultType, contactMethod: 'whatsapp', consent: false }}
      scrollToFirstError
    >
      {status.kind === 'error' && (
        <Alert type="error" showIcon className="s-form__alert" title={status.message} />
      )}

      <Form.Item label={tr('What would you like to do?')} name="enquiryType" rules={[{ required: true }]}>
        <Select options={TYPE_OPTIONS} />
      </Form.Item>

      <div className="s-form__grid">
        <Form.Item
          label={tr('Parent or guardian name')}
          name="parentName"
          rules={[
            { required: true, whitespace: true, message: tr('Please enter your name.') },
            { max: 100, message: tr('Please keep this under 100 characters.') },
          ]}
        >
          <Input autoComplete="name" />
        </Form.Item>
        <Form.Item
          label={tr('Phone / WhatsApp number')}
          name="phone"
          rules={[
            {
              required: contactMethod === 'phone' || contactMethod === 'whatsapp',
              message: tr('Please add a phone number so we can reach you.'),
            },
            { pattern: /^[+\d][\d\s-]{6,18}$/, message: tr('Please enter a valid phone number.') },
          ]}
        >
          <Input type="tel" autoComplete="tel" inputMode="tel" />
        </Form.Item>
        <Form.Item
          label={tr('Email address')}
          name="email"
          rules={[
            { required: contactMethod === 'email', message: tr('Please enter your email address.') },
            { type: 'email', message: tr('Please enter a valid email address.') },
          ]}
        >
          <Input type="email" autoComplete="email" inputMode="email" />
        </Form.Item>
        {enquiryType !== 'general' && (
          <Form.Item label={tr('Child’s age')} name="ageGroup">
            <Select options={AGE_OPTIONS} placeholder={tr('Select')} allowClear />
          </Form.Item>
        )}
      </div>

      <Form.Item label={tr('How should we reply?')} name="contactMethod" rules={[{ required: true }]}>
        <Radio.Group
          options={[
            { value: 'whatsapp', label: tr('WhatsApp') },
            { value: 'phone', label: tr('Phone call') },
            { value: 'email', label: tr('Email') },
          ]}
        />
      </Form.Item>

      <Form.Item
        label={tr('Message')}
        name="message"
        rules={[{ max: 1000, message: tr('Please keep your message under 1000 characters.') }]}
      >
        <Input.TextArea
          rows={4}
          showCount
          maxLength={1000}
          placeholder={
            enquiryType === 'visit'
              ? tr('Let us know which days or times usually suit you.')
              : tr('Anything you would like to ask or tell us.')
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
              v ? Promise.resolve() : Promise.reject(new Error(tr('Please confirm so we can contact you.'))),
          },
        ]}
      >
        <Checkbox>
          {tr('I agree to be contacted about this enquiry and have read the')}{' '}
          <Link to="/privacy">{tr('privacy notice')}</Link>.
        </Checkbox>
      </Form.Item>

      {SCHOOL.replyTime && (
        <p className="s-form__reply" role="note">
          {trf('We usually reply {time}.', { time: isUrdu ? SCHOOL.replyTime.ur : SCHOOL.replyTime.en })}
        </p>
      )}

      <p className="s-form__fine">
        {tr('Please don’t share sensitive details about your child.')}</p>

      <Button
        type="primary"
        htmlType="submit"
        loading={status.kind === 'sending'}
        icon={<Send size={16} aria-hidden="true" />}
        className="s-form__submit"
      >
        {status.kind === 'sending' ? tr('Sending…') : tr('Send enquiry')}
      </Button>
    </Form>
  );
}
