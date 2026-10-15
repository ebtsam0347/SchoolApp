// Service-token panel — only renders when the user holds at least one
// service-account role. Service-account users don't sign in; they carry a
// long-lived JWT minted here. Auth doesn't store the token, so the issue
// flow returns it once in a copy panel and there's no list of past tokens.

import { useState } from 'react';
import {
  Alert,
  App as AntdApp,
  Button,
  Form,
  InputNumber,
  Input,
  Modal,
  Space,
  theme as antdTheme,
  Tooltip,
  Typography,
} from 'antd';
import { useMutation } from '@tanstack/react-query';
import { Bot, Copy, KeyRound, ShieldCheck } from 'lucide-react';

import { Section } from '@/components';
import {
  adminUsersApi,
  type IssueServiceTokenRequest,
  type RoleDto,
  type ServiceTokenResponse,
  type UserDto,
} from '@/auth';
import { handleFormError } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export type ServiceTokensPanelProps = {
  userId: number;
  user: UserDto;
  /** Full role catalog. Used to check whether the user holds a service-account role. */
  catalog: RoleDto[];
};

export function ServiceTokensPanel({ userId, user, catalog }: ServiceTokensPanelProps) {
  const { token } = useToken();
  const { message } = AntdApp.useApp();
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [issued, setIssued] = useState<ServiceTokenResponse | null>(null);
  const [form] = Form.useForm<IssueServiceTokenRequest>();

  const serviceRoles = catalog.filter(
    (r) => r.serviceAccount && user.roles.includes(r.code),
  );
  if (serviceRoles.length === 0) return null;

  const issueMutation = useMutation({
    mutationFn: (body: IssueServiceTokenRequest) =>
      adminUsersApi.issueServiceToken(userId, body),
    onSuccess: (resp) => {
      setIssued(resp);
      setDrawerOpen(false);
      form.resetFields();
    },
    onError: (err) => handleFormError(form, err, message),
  });

  return (
    <>
      <Section
        title={
          <Space size={8}>
            <Bot size={16} strokeWidth={1.75} />
            Service tokens
          </Space>
        }
        subtitle="This account is service-only — it doesn't sign in. Mint a token and seal it into the consumer's secret."
        actions={
          <Button
            type="primary"
            icon={<KeyRound size={14} strokeWidth={1.75} />}
            onClick={() => setDrawerOpen(true)}
          >
            Issue token
          </Button>
        }
      >
        <Space wrap size={[8, 8]}>
          {serviceRoles.map((r) => (
            <span
              key={r.code}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 6,
                padding: '4px 10px',
                fontSize: 12,
                borderRadius: 999,
                color: token.colorTextSecondary,
                border: `1px solid ${token.colorBorderSecondary}`,
                background: `color-mix(in srgb, ${token.colorPrimary} 6%, transparent)`,
              }}
            >
              <ShieldCheck size={12} strokeWidth={2} />
              <code style={{ fontSize: 11 }}>{r.code}</code>
              <Text type="secondary" style={{ fontSize: 11 }}>
                · {r.permissions.length} permission
                {r.permissions.length === 1 ? '' : 's'}
              </Text>
            </span>
          ))}
        </Space>
        <Alert
          type="info"
          showIcon
          style={{ marginTop: 12 }}
          title="Tokens are surfaced once."
          description="Auth does not store the issued JWT. If you lose it, mint a new one and rotate the consumer's secret. Existing tokens remain valid until their expiry — there is no revocation list."
        />
      </Section>

      <Modal
        title="Issue service token"
        open={drawerOpen}
        onCancel={() => {
          if (!issueMutation.isPending) {
            setDrawerOpen(false);
            form.resetFields();
          }
        }}
        okText="Issue"
        confirmLoading={issueMutation.isPending}
        onOk={() => form.submit()}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{ ttlDays: 365 }}
          onFinish={(v) => issueMutation.mutate(v)}
        >
          <Form.Item
            label="Lifetime (days)"
            name="ttlDays"
            rules={[
              { required: true, message: 'Required' },
              { type: 'number', min: 1, max: 730, message: '1–730 days' },
            ]}
            extra="Cap is 730 (~2 years). Expiry is the only revocation lever — pick a length you're comfortable rotating."
          >
            <InputNumber min={1} max={730} style={{ width: '100%' }} autoFocus />
          </Form.Item>
        </Form>
      </Modal>

      <IssuedTokenModal
        token={issued}
        onClose={() => setIssued(null)}
      />
    </>
  );
}

function IssuedTokenModal({
  token: tok,
  onClose,
}: {
  token: ServiceTokenResponse | null;
  onClose: () => void;
}) {
  const { token } = useToken();
  const { message } = AntdApp.useApp();
  const [copied, setCopied] = useState(false);

  return (
    <Modal
      open={tok !== null}
      title="Token issued"
      onCancel={onClose}
      footer={[
        <Button key="close" onClick={onClose} type="primary">
          I have it — close
        </Button>,
      ]}
      destroyOnHidden
      width={640}
    >
      {tok ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type="warning"
            showIcon
            title="Capture this now."
            description="The token is shown once and then forgotten. Closing this dialog without copying means a fresh issue."
          />

          <div>
            <Text type="secondary" style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Bearer token
            </Text>
            <Input.TextArea
              readOnly
              autoSize={{ minRows: 4, maxRows: 8 }}
              value={tok.token}
              style={{
                marginTop: 4,
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                fontSize: 11,
                background: token.colorFillQuaternary,
              }}
            />
            <Tooltip title={copied ? 'Copied!' : 'Copy to clipboard'}>
              <Button
                style={{ marginTop: 8 }}
                icon={<Copy size={13} strokeWidth={1.75} />}
                onClick={async () => {
                  await navigator.clipboard.writeText(tok.token);
                  setCopied(true);
                  message.success('Token copied.');
                  setTimeout(() => setCopied(false), 2000);
                }}
              >
                Copy token
              </Button>
            </Tooltip>
          </div>

          <dl
            style={{
              display: 'grid',
              gridTemplateColumns: 'auto 1fr',
              gap: '6px 16px',
              margin: 0,
              fontSize: 12,
            }}
          >
            <dt
              style={{
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                opacity: 0.6,
              }}
            >
              Expires
            </dt>
            <dd style={{ margin: 0, fontVariantNumeric: 'tabular-nums' }}>
              {new Date(tok.expiresAt).toLocaleString()}
            </dd>
            <dt
              style={{
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                opacity: 0.6,
              }}
            >
              JWT id (jti)
            </dt>
            <dd style={{ margin: 0 }}>
              <code style={{ fontSize: 11 }}>{tok.jti}</code>
            </dd>
          </dl>
        </Space>
      ) : null}
    </Modal>
  );
}
