import { useAuth } from '@/auth';
import { readSnapshot, useIsDesktop } from '@/lib';
import { isApiError } from '@/lib/api-error';
import { getErrorMessage } from '@/lib/errors';
import { applyApiErrorToForm } from '@/lib/form-violations';
import { ThemeSwitcher } from '@/theme/ThemeSwitcher';
import { createFileRoute, Link, redirect, useNavigate } from '@tanstack/react-router';
import { Alert, Button, Form, Input, Typography, theme as antdTheme } from 'antd';
import {
  LogIn,
  ShieldCheck,
  Sparkles,
  Workflow,
} from 'lucide-react';
import { useState } from 'react';
import './login.css';

const { useToken } = antdTheme;
const { Title, Text } = Typography;

type LoginRequest = {
  username: string;
  password: string;
};

export const Route = createFileRoute('/login')({
  beforeLoad: () => {
    const snap = readSnapshot();
    if (!snap) return;
    if (snap.user?.mustChangePassword) {
      throw redirect({ to: '/change-password' });
    }
    throw redirect({ to: '/' });
  },
  component: LoginScreen,
});

function LoginScreen() {
  const isDesktop = useIsDesktop();
  const { token } = useToken();

  return (
    <div
      className="login-shell"
      style={{
        background: token.colorBgLayout,
        color: token.colorText,
      }}
    >
      {isDesktop && <BrandPanel />}
      <FormPanel />
    </div>
  );
}

function FormPanel() {
  const auth = useAuth();
  const navigate = useNavigate();
  const { token } = useToken();
  const [form] = Form.useForm<LoginRequest>();
  const [topError, setTopError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onFinish(values: LoginRequest) {
    setSubmitting(true);
    setTopError(null);
    try {
      const tokens = await auth.login(values);
      void navigate({
        to: tokens.mustChangePassword ? '/change-password' : '/',
        replace: true,
      });
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
    <section className="login-form">
      <div className="login-form-actions">
        <ThemeSwitcher />
      </div>
      <div className="login-form-inner">
        <div>
          <span
            className="login-eyebrow"
            style={{ color: token.colorTextSecondary }}
          >
            Sign in
          </span>
          <Title level={2} style={{ margin: 0, marginTop: 6 }}>
            Welcome back
          </Title>
          <Text
            type="secondary"
            style={{ fontSize: 14, lineHeight: 1.6 }}
          >
            Use your school staff account to continue.
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

        <Form<LoginRequest>
          form={form}
          layout="vertical"
          requiredMark={false}
          onFinish={onFinish}
          initialValues={{ username: '', password: '' }}
          disabled={submitting}
          size="large"
        >
          <Form.Item
            label="Username"
            name="username"
            rules={[{ required: true, message: 'Username is required.' }]}
          >
            <Input
              autoComplete="username"
              autoFocus
              placeholder="employee.code or admin"
            />
          </Form.Item>
          <Form.Item
            label="Password"
            name="password"
            rules={[{ required: true, message: 'Password is required.' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginTop: 8, marginBottom: 0 }}>
            <Button
              htmlType="submit"
              type="primary"
              block
              loading={submitting}
              icon={<LogIn size={16} strokeWidth={1.75} />}
            >
              Sign in
            </Button>
          </Form.Item>
        </Form>

        <Text
          type="secondary"
          style={{
            fontSize: 12,
            textAlign: 'center',
            lineHeight: 1.6,
            marginTop: 4,
          }}
        >
          Forgot your password? Ask the school admin office — the portal does not support
          self-service password reset.
        </Text>
        <Link to="/visit" style={{ fontSize: 13, textAlign: 'center' }}>
          Visiting the school? Check in here →
        </Link>
      </div>
    </section>
  );
}

function BrandPanel() {
  return (
    <aside className="login-brand" aria-hidden="false">
      <BrandWatermark />

      <div className="login-brand-hero">
        <img
          className="login-brand-crest"
          src="/icons/school-logo.png"
          alt="The Spirit of Islam School crest"
        />
        <div className="login-brand-name">
          <span className="login-brand-name-primary">The Spirit of Islam School</span>
          <span className="login-brand-name-sub">Staff Portal</span>
        </div>
        <span className="login-brand-rule" aria-hidden="true" />
        <h1 className="login-brand-headline">
          One workspace.<br />For the whole school.
        </h1>
        <p className="login-brand-tagline">
          Staff, attendance, leave and payroll — in one place.
        </p>
      </div>

      <ul className="login-brand-features" aria-label="Highlights">
        <li>
          <span className="login-brand-feat-icon" aria-hidden="true">
            <Workflow size={16} strokeWidth={1.75} />
          </span>
          <span>Built for our campus</span>
        </li>
        <li>
          <span className="login-brand-feat-icon" aria-hidden="true">
            <ShieldCheck size={16} strokeWidth={1.75} />
          </span>
          <span>Audit‑grade trail</span>
        </li>
        <li>
          <span className="login-brand-feat-icon" aria-hidden="true">
            <Sparkles size={16} strokeWidth={1.75} />
          </span>
          <span>Six themes, one feel</span>
        </li>
      </ul>

      <footer className="login-brand-foot">
        <span>© The Spirit of Islam School — Thinking is our curriculum</span>
      </footer>
    </aside>
  );
}

function BrandWatermark() {
  // Outlined "SOI" set behind the content — reads as editorial decoration
  // rather than a filled block (which obscures the hero text).
  return (
    <svg
      className="login-brand-watermark"
      viewBox="0 0 400 400"
      preserveAspectRatio="xMaxYMid meet"
      aria-hidden="true"
    >
      <text
        className="login-brand-watermark-text"
        x="100%"
        y="78%"
        textAnchor="end"
        fontSize="220"
        fontWeight="800"
        letterSpacing="-12"
        fontFamily="Inter, system-ui, sans-serif"
        fill="currentColor"
        stroke="currentColor"
        strokeWidth="2.25"
        strokeLinecap="round"
        strokeLinejoin="round"
        vectorEffect="non-scaling-stroke"
      >
        SOI
      </text>
    </svg>
  );
}
