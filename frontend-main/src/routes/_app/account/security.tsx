// /account/security — voluntary password change.
//
// Voluntary password changes succeed → backend revokes all OTHER sessions
// (keeps the current one) and we re-fetch /auth/me. Forced changes still go
// through `/change-password` which is what the auth gate redirects to.
//
// The full sign-in feed (successes + failures + locks + unlocks) and other
// account events live on /account/activity — this page is just the password
// form so the security action is unmistakable.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Typography,
  theme as antdTheme,
} from 'antd';
import { CheckCircle2, History, Info, KeyRound } from 'lucide-react';
import { useAuth } from '@/auth';
import { applyApiErrorToForm, getErrorMessage, isApiError } from '@/lib';
import { Section } from '@/components';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/security')({
  component: SecurityPage,
});

type ChangePasswordValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

function SecurityPage() {
  const { token } = useToken();
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <ChangePasswordSection />
      <Text type="secondary" style={{ fontSize: 12 }}>
        Looking for sign‑in history?{' '}
        <Link
          to="/account/activity"
          style={{ color: token.colorPrimary, display: 'inline-flex', alignItems: 'center', gap: 4 }}
        >
          <History size={12} strokeWidth={1.75} />
          View account activity
        </Link>{' '}
        — every sign‑in attempt, lock, and account change in one feed.
      </Text>
    </div>
  );
}

function ChangePasswordSection() {
  const auth = useAuth();
  const [form] = Form.useForm<ChangePasswordValues>();
  const { message } = App.useApp();
  const [submitting, setSubmitting] = useState(false);
  const [topError, setTopError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function onFinish(values: ChangePasswordValues) {
    setSubmitting(true);
    setTopError(null);
    setDone(false);
    try {
      await auth.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      form.resetFields();
      setDone(true);
      message.success('Password changed. Other sessions were signed out.');
    } catch (e) {
      const { applied, unmapped } = applyApiErrorToForm(form, e);
      if (applied.length === 0) setTopError(getErrorMessage(e));
      else if (unmapped.length > 0) setTopError(unmapped[0].message);
      else if (isApiError(e) && e.code !== 'VALIDATION_FAILED') {
        setTopError(getErrorMessage(e));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Section
      title="Password"
      subtitle="Change your password. Every other active session will be signed out automatically."
    >
      <Card styles={{ body: { padding: 24, maxWidth: 480 } }}>
        {done && (
          <Alert
            type="success"
            showIcon
            icon={<CheckCircle2 size={16} strokeWidth={1.75} />}
            title="Password updated"
            description="Other devices were signed out. Your current session stays active."
            closable
            onClose={() => setDone(false)}
            style={{ marginBottom: 16 }}
          />
        )}
        {topError && (
          <Alert
            type="error"
            showIcon
            title={topError}
            closable
            onClose={() => setTopError(null)}
            style={{ marginBottom: 16 }}
          />
        )}
        <Form<ChangePasswordValues>
          form={form}
          layout="vertical"
          requiredMark={false}
          onFinish={onFinish}
          disabled={submitting}
        >
          <Form.Item
            label="Current password"
            name="oldPassword"
            rules={[{ required: true, message: 'Current password is required.' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item
            label="New password"
            name="newPassword"
            rules={[
              { required: true, message: 'New password is required.' },
              { min: 10, message: 'Use at least 10 characters.' },
            ]}
            extra="At least 10 characters."
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            label="Confirm new password"
            name="confirmPassword"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: 'Please confirm the new password.' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('Passwords do not match.'));
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item style={{ marginTop: 8, marginBottom: 0 }}>
            <Button
              htmlType="submit"
              type="primary"
              loading={submitting}
              icon={<KeyRound size={14} strokeWidth={1.75} />}
            >
              Update password
            </Button>
          </Form.Item>
        </Form>
        <PolicyNote />
      </Card>
    </Section>
  );
}

function PolicyNote() {
  const { token } = useToken();
  return (
    <div
      style={{
        marginTop: 16,
        display: 'flex',
        alignItems: 'flex-start',
        gap: 10,
        padding: '10px 12px',
        borderRadius: 8,
        background: token.colorFillQuaternary,
        color: token.colorTextSecondary,
      }}
    >
      <Info size={14} strokeWidth={1.75} style={{ marginTop: 2, flexShrink: 0 }} />
      <Text type="secondary" style={{ fontSize: 12, lineHeight: 1.55 }}>
        Passwords must be at least <strong style={{ color: token.colorText }}>10 characters</strong>.
        After saving, every other active session will be signed out automatically.
      </Text>
    </div>
  );
}
