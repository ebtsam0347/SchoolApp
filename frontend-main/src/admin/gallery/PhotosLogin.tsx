// Sign-in card for /photos. Calls POST /media/login (see photo-session.ts).

import { useState } from 'react';
import { Alert, Button, Card, Form, Input } from 'antd';
import { Lock } from 'lucide-react';
import { getErrorMessage } from '@/lib';
import { photoLogin } from './photo-session';

export function PhotosLogin() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async ({ password }: { password: string }) => {
    setBusy(true);
    setError(null);
    try {
      await photoLogin(password);
    } catch (e) {
      setError(getErrorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card style={{ maxWidth: 420, margin: '64px auto 0' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <Lock size={18} aria-hidden="true" />
        <h1 style={{ margin: 0, fontSize: 20 }}>Website photos</h1>
      </div>
      <p style={{ marginTop: 0, color: 'var(--ant-color-text-secondary, #666)' }}>
        Enter the photo password to change the pictures shown on the school website.
      </p>
      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      <Form layout="vertical" onFinish={submit} requiredMark={false}>
        <Form.Item name="password" label="Password" rules={[{ required: true, message: 'Enter the password.' }]}>
          <Input.Password autoFocus autoComplete="current-password" disabled={busy} />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={busy}>
          Sign in
        </Button>
      </Form>
    </Card>
  );
}
