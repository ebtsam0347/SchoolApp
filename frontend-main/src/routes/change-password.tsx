import { createFileRoute, redirect, useNavigate } from '@tanstack/react-router';
import { useState } from 'react';
import { Alert, Button, Form, Input, Typography, theme as antdTheme } from 'antd';
import { KeyRound } from 'lucide-react';
import { useAuth } from '@/auth';
import { ThemeSwitcher } from '@/theme/ThemeSwitcher';
import { applyApiErrorToForm } from '@/lib/form-violations';
import { getErrorMessage } from '@/lib/errors';
import { isApiError } from '@/lib/api-error';
import { readSnapshot } from '@/lib';

const { useToken } = antdTheme;
const { Title, Text } = Typography;

type ChangePasswordValues = {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
};

export const Route = createFileRoute('/change-password')({
  beforeLoad: () => {
    if (!readSnapshot()) throw redirect({ to: '/login' });
  },
  component: ChangePasswordScreen,
});

function ChangePasswordScreen() {
  const auth = useAuth();
  const navigate = useNavigate();
  const { token } = useToken();
  const [form] = Form.useForm<ChangePasswordValues>();
  const [topError, setTopError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const forced = auth.status === 'must-change-password';

  async function onFinish(values: ChangePasswordValues) {
    setSubmitting(true);
    setTopError(null);
    try {
      await auth.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      void navigate({ to: '/', replace: true });
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
    <div
      style={{
        minHeight: '100dvh',
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr)',
        background: token.colorBgLayout,
        color: token.colorText,
      }}
    >
      <div
        style={{
          position: 'absolute',
          top: 16,
          right: 16,
          zIndex: 1,
        }}
      >
        <ThemeSwitcher />
      </div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '32px 24px',
        }}
      >
        <div
          style={{
            width: '100%',
            maxWidth: 420,
            display: 'flex',
            flexDirection: 'column',
            gap: 24,
          }}
        >
          <div>
            <Title level={3} style={{ margin: 0, marginBottom: 4 }}>
              {forced ? 'Set a new password' : 'Change your password'}
            </Title>
            <Text type="secondary" style={{ fontSize: 14 }}>
              {forced
                ? 'Your administrator set a temporary password — choose a permanent one to continue.'
                : 'Choose a new password. Other active sessions will be signed out.'}
            </Text>
          </div>

          {topError && (
            <Alert
              type="error"
              showIcon
              title={topError}
              closable
              onClose={() => setTopError(null)}
            />
          )}

          <Form<ChangePasswordValues>
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={onFinish}
            disabled={submitting}
            size="large"
          >
            <Form.Item
              label="Current password"
              name="oldPassword"
              rules={[{ required: true, message: 'Current password is required.' }]}
            >
              <Input.Password autoComplete="current-password" autoFocus />
            </Form.Item>
            <Form.Item
              label="New password"
              name="newPassword"
              rules={[
                { required: true, message: 'New password is required.' },
                { min: 8, message: 'Use at least 8 characters.' },
              ]}
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
                block
                loading={submitting}
                icon={<KeyRound size={16} strokeWidth={1.75} />}
              >
                Update password
              </Button>
            </Form.Item>
          </Form>

          {!forced && (
            <Text
              type="secondary"
              style={{ fontSize: 12, textAlign: 'center', lineHeight: 1.6 }}
            >
              Changed your mind?{' '}
              <a
                onClick={() => void navigate({ to: '/' })}
                style={{ cursor: 'pointer' }}
              >
                Back to dashboard
              </a>
            </Text>
          )}
        </div>
      </div>
    </div>
  );
}
