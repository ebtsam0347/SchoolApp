// /admin/notifications — announcement broadcaster.
//
// notification-service does not resolve recipients; the caller hands it a
// flat user-id list. v1 takes a comma/whitespace-separated list of ids
// (operator paste from a spreadsheet, or copied from the user-list page).
// The proper department/role picker is a BFF wishlist item — see
// `BFF_WISHLIST.md` § Notifications.
//
// Required permission: `notification.announce`. The route shell already
// guards entry; this page also disables submit if the caller can't write.

import { createFileRoute, Link } from '@tanstack/react-router';
import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Form,
  Input,
  Radio,
  Tabs,
  Typography,
  theme as antdTheme,
} from 'antd';
import { Megaphone, Send, FileText } from 'lucide-react';
import {
  adminNotificationsApi,
  type AnnounceRequest,
  type NotificationPriority,
} from '@/notifications';
import { PageHeader } from '@/components';
import { applyApiErrorToForm, getErrorMessage } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;
const { TextArea } = Input;

export const Route = createFileRoute('/_app/admin/notifications/')({
  component: AnnouncePage,
});

type AnnounceForm = {
  recipientText: string;
  title: string;
  body?: string;
  link?: string;
  priority: NotificationPriority;
};

function AnnouncePage() {
  const { token } = useToken();
  const [form] = Form.useForm<AnnounceForm>();
  const { message } = App.useApp();
  const [topError, setTopError] = useState<string | null>(null);
  const [delivered, setDelivered] = useState<number | null>(null);

  const announce = useMutation({
    mutationFn: (body: AnnounceRequest) => adminNotificationsApi.announce(body),
    onSuccess: (res) => {
      setDelivered(res.delivered);
      message.success(
        `Sent to ${res.delivered} recipient${res.delivered === 1 ? '' : 's'}.`,
      );
      form.resetFields(['title', 'body', 'link', 'recipientText']);
      form.setFieldValue('priority', 'NORMAL');
    },
    onError: (e) => {
      applyApiErrorToForm(form, e);
      setTopError(getErrorMessage(e));
    },
  });

  function onFinish(values: AnnounceForm) {
    setTopError(null);
    setDelivered(null);
    const ids = parseUserIds(values.recipientText);
    if (ids.length === 0) {
      setTopError('Add at least one recipient user id.');
      return;
    }
    announce.mutate({
      userIds: ids,
      title: values.title.trim(),
      body: values.body?.trim() || undefined,
      link: values.link?.trim() || undefined,
      priority: values.priority,
    });
  }

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 20,
        width: '100%',
      }}
    >
      <PageHeader
        eyebrow="Notifications"
        title="Announce a broadcast"
        description="Send a one-off notification to a list of users. Recipient resolution is on you for now — paste user ids from People or a query result."
      />

      <NotificationsTabs current="announce" />

      <Card styles={{ body: { padding: 24 } }}>
        <Form<AnnounceForm>
          form={form}
          layout="vertical"
          initialValues={{ priority: 'NORMAL' }}
          onFinish={onFinish}
          requiredMark="optional"
        >
          {topError && (
            <Alert
              type="error"
              showIcon
              title={topError}
              style={{ marginBottom: 16 }}
              closable
              onClose={() => setTopError(null)}
            />
          )}
          {delivered !== null && (
            <Alert
              type="success"
              showIcon
              title={`Sent to ${delivered} ${delivered === 1 ? 'person' : 'people'}.`}
              style={{ marginBottom: 16 }}
              closable
              onClose={() => setDelivered(null)}
            />
          )}

          <Form.Item
            label="Recipient user ids"
            name="recipientText"
            rules={[{ required: true, message: 'Enter at least one user id.' }]}
            extra="Comma- or space-separated user ids. A picker by department or role is coming soon."
          >
            <TextArea
              autoSize={{ minRows: 2, maxRows: 6 }}
              placeholder="42, 43, 44 …"
            />
          </Form.Item>

          <Form.Item
            label="Title"
            name="title"
            rules={[
              { required: true, message: 'Title is required.' },
              { max: 255, message: 'Title is too long (max 255).' },
            ]}
          >
            <Input placeholder="e.g. Office closed Friday for staff offsite" />
          </Form.Item>

          <Form.Item label="Body" name="body" extra="Plain text. No formatting yet.">
            <TextArea
              autoSize={{ minRows: 3, maxRows: 8 }}
              placeholder="Optional details — what, when, where, who to contact."
            />
          </Form.Item>

          <Form.Item
            label="Deep link"
            name="link"
            extra="Optional. Path inside the app (e.g. /announcements/staff-offsite-2026)."
            rules={[{ max: 512, message: 'Link is too long (max 512).' }]}
          >
            <Input placeholder="/announcements/…" />
          </Form.Item>

          <Form.Item label="Priority" name="priority">
            <Radio.Group optionType="button" buttonStyle="solid">
              <Radio.Button value="LOW">Low</Radio.Button>
              <Radio.Button value="NORMAL">Normal</Radio.Button>
              <Radio.Button value="HIGH">High</Radio.Button>
            </Radio.Group>
          </Form.Item>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button
              type="primary"
              htmlType="submit"
              icon={<Send size={14} strokeWidth={1.75} />}
              loading={announce.isPending}
            >
              Send broadcast
            </Button>
          </div>
        </Form>
      </Card>

      <Alert
        type="info"
        showIcon
        icon={<Megaphone size={14} strokeWidth={1.75} />}
        title="Announcements reach everyone"
        description={
          <span style={{ fontSize: 13, color: token.colorTextSecondary }}>
            Recipients get the announcement in their bell and inbox even if they
            have notification preferences turned off — broadcasts are
            intentionally louder than routine approval pings. Use sparingly.
          </span>
        }
      />

      <Text type="secondary" style={{ fontSize: 12 }}>
        Need to manage rendering templates instead?{' '}
        <Link to="/admin/notifications/templates" style={{ color: token.colorPrimary }}>
          Open Templates
        </Link>
        .
      </Text>
    </div>
  );
}

function parseUserIds(text: string): number[] {
  if (!text) return [];
  const out: number[] = [];
  const seen = new Set<number>();
  for (const tok of text.split(/[\s,]+/)) {
    if (!tok) continue;
    const n = Number(tok);
    if (!Number.isFinite(n) || !Number.isInteger(n) || n <= 0) continue;
    if (seen.has(n)) continue;
    seen.add(n);
    out.push(n);
  }
  return out;
}

export function NotificationsTabs({ current }: { current: 'announce' | 'templates' }) {
  return (
    <Tabs
      activeKey={current}
      items={[
        {
          key: 'announce',
          label: (
            <Link to="/admin/notifications" style={{ color: 'inherit' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <Megaphone size={14} strokeWidth={1.75} />
                Announce
              </span>
            </Link>
          ),
        },
        {
          key: 'templates',
          label: (
            <Link to="/admin/notifications/templates" style={{ color: 'inherit' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                <FileText size={14} strokeWidth={1.75} />
                Templates
              </span>
            </Link>
          ),
        },
      ]}
    />
  );
}

