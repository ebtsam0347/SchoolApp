// /account/notifications — per-user opt-out toggles.
//
// notification-service stores only explicit overrides; absence = enabled.
// The toggle catalog comes from `/notifications/types` (an active row per
// type in `notification_templates`), so adding a new type backend-side
// surfaces here without a code change. PATCH is sparse — we only ship the
// keys whose toggle was actually flipped.

import { createFileRoute } from '@tanstack/react-router';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  App,
  Button,
  Card,
  Skeleton,
  Switch,
  Tooltip,
  Typography,
  theme as antdTheme,
} from 'antd';
import { RefreshCcw, RotateCcw, Save } from 'lucide-react';
import {
  notificationsApi,
  notificationMeta,
  PushSettingsCard,
  categoryLabel,
  humanizeType,
  ALL_CATEGORIES,
  type NotificationCategory,
  type NotificationTypeDto,
} from '@/notifications';
import { Section } from '@/components';
import { getErrorMessage, isForbidden } from '@/lib';

const { Text } = Typography;
const { useToken } = antdTheme;

export const Route = createFileRoute('/_app/account/notifications')({
  component: NotificationPreferencesPage,
});

function NotificationPreferencesPage() {
  const { token } = useToken();
  const qc = useQueryClient();
  const { message } = App.useApp();

  const typesQuery = useQuery({
    queryKey: ['notifications', 'types'],
    queryFn: ({ signal }) => notificationsApi.listTypes(signal),
    staleTime: 5 * 60_000,
  });

  const prefsQuery = useQuery({
    queryKey: ['notifications', 'preferences'],
    queryFn: ({ signal }) => notificationsApi.listPreferences(signal),
  });

  // Local edit buffer: { type → enabled }. Only types the user has actually
  // touched land here; saving collapses to a sparse PATCH body.
  const [pending, setPending] = useState<Record<string, boolean>>({});

  // Group the live type catalog by category for the section layout.
  // Anything missing a category lands under SYSTEM.
  const grouped = (() => {
    const out: Record<NotificationCategory, NotificationTypeDto[]> = {
      APPROVAL: [],
      ANNOUNCEMENT: [],
      PAYROLL: [],
      SYSTEM: [],
    };
    for (const t of typesQuery.data ?? []) {
      const bucket = (t.category ?? 'SYSTEM') as NotificationCategory;
      out[bucket].push(t);
    }
    for (const k of ALL_CATEGORIES) {
      out[k].sort((a, b) => a.label.localeCompare(b.label));
    }
    return out;
  })();

  // Effective state for rendering = server defaults (enabled) + server
  // overrides + any pending local toggles.
  const effective = (() => {
    const map: Record<string, boolean> = {};
    for (const t of typesQuery.data ?? []) map[t.type] = true;
    for (const p of prefsQuery.data ?? []) map[p.type] = p.enabled;
    for (const [t, v] of Object.entries(pending)) map[t] = v;
    return map;
  })();

  const save = useMutation({
    mutationFn: () => notificationsApi.updatePreferences({ preferences: pending }),
    onSuccess: (data) => {
      qc.setQueryData(['notifications', 'preferences'], data);
      setPending({});
      message.success('Preferences saved.');
    },
    onError: (e) => message.error(getErrorMessage(e)),
  });

  function toggle(type: string, next: boolean) {
    // If the toggle now matches the server snapshot, drop it from `pending`
    // so a no-op edit doesn't stay in the buffer.
    const server = (prefsQuery.data ?? []).find((p) => p.type === type);
    const serverValue = server?.enabled ?? true;
    setPending((prev) => {
      const copy = { ...prev };
      if (next === serverValue) delete copy[type];
      else copy[type] = next;
      return copy;
    });
  }

  const dirty = Object.keys(pending).length > 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
      <PushSettingsCard />
      <Alert
        type="info"
        showIcon
        title="Choose what we ping you about"
        description="Approval-related types stay on by default — you don't want to miss something waiting on you. Turn off the ones you don't need; you can re-enable any of them later."
      />

      {typesQuery.isLoading || prefsQuery.isLoading ? (
        <Card styles={{ body: { padding: 16 } }}>
          <Skeleton active title={false} paragraph={{ rows: 6 }} />
        </Card>
      ) : typesQuery.isError || prefsQuery.isError ? (
        <Alert
          type={
            isForbidden(typesQuery.error) || isForbidden(prefsQuery.error)
              ? 'info'
              : 'error'
          }
          showIcon
          title="Couldn't load preferences"
          description={getErrorMessage(typesQuery.error ?? prefsQuery.error)}
        />
      ) : (
        <>
          {ALL_CATEGORIES.map((cat) => {
            const types = grouped[cat];
            if (types.length === 0) return null;
            return (
              <Section
                key={cat}
                level={5}
                title={categoryLabel(cat)}
                subtitle={subtitleFor(cat)}
              >
                <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
                  {types.map((t, i) => (
                    <PrefRow
                      key={t.type}
                      type={t.type}
                      label={t.label}
                      description={t.description}
                      enabled={effective[t.type] ?? true}
                      onChange={(v) => toggle(t.type, v)}
                      isLast={i === types.length - 1}
                    />
                  ))}
                </ul>
              </Section>
            );
          })}

          <Card
            style={{
              borderColor: dirty
                ? `color-mix(in srgb, ${token.colorPrimary} 35%, ${token.colorBorderSecondary})`
                : token.colorBorderSecondary,
            }}
            styles={{ body: { padding: 16, display: 'flex', alignItems: 'center', gap: 12 } }}
          >
            <Text type="secondary" style={{ flex: 1, fontSize: 13 }}>
              {dirty
                ? `${Object.keys(pending).length} change${Object.keys(pending).length === 1 ? '' : 's'} pending.`
                : 'No pending changes.'}
            </Text>
            <Button
              icon={<RotateCcw size={14} strokeWidth={1.75} />}
              disabled={!dirty || save.isPending}
              onClick={() => setPending({})}
            >
              Discard
            </Button>
            <Tooltip title="Refresh">
              <Button
                icon={<RefreshCcw size={14} strokeWidth={1.75} />}
                onClick={() => prefsQuery.refetch()}
                loading={prefsQuery.isFetching && !prefsQuery.isLoading}
                aria-label="Refresh"
              />
            </Tooltip>
            <Button
              type="primary"
              icon={<Save size={14} strokeWidth={1.75} />}
              disabled={!dirty}
              loading={save.isPending}
              onClick={() => save.mutate()}
            >
              Save
            </Button>
          </Card>
        </>
      )}
    </div>
  );
}

function subtitleFor(cat: NotificationCategory): string {
  switch (cat) {
    case 'APPROVAL':
      return 'Things waiting on you, and outcomes of requests you submitted.';
    case 'PAYROLL':
      return 'Payslip availability and salary-related events.';
    case 'SYSTEM':
      return 'Schedule overrides, reporting changes, security events.';
    case 'ANNOUNCEMENT':
      return 'Org-wide broadcasts.';
  }
}

function PrefRow({
  type,
  label,
  description,
  enabled,
  onChange,
  isLast,
}: {
  type: string;
  label: string;
  description: string | null;
  enabled: boolean;
  onChange: (v: boolean) => void;
  isLast: boolean;
}) {
  const { token } = useToken();
  const meta = notificationMeta(type, null);
  const Icon = meta.icon;
  const tint = toneColor(token, meta.tone);

  return (
    <li
      style={{
        display: 'grid',
        gridTemplateColumns: 'auto minmax(0, 1fr) auto',
        gap: 14,
        alignItems: 'center',
        padding: '12px 0',
        borderBottom: isLast ? 'none' : `1px solid ${token.colorBorderSecondary}`,
      }}
    >
      <span
        aria-hidden="true"
        style={{
          width: 32,
          height: 32,
          flexShrink: 0,
          borderRadius: 8,
          display: 'inline-flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: `color-mix(in srgb, ${tint} 12%, transparent)`,
          color: tint,
        }}
      >
        <Icon size={15} strokeWidth={1.75} />
      </span>
      <div style={{ minWidth: 0 }}>
        <div style={{ fontSize: 13, fontWeight: 500, color: token.colorText }}>
          {label || humanizeType(type)}
        </div>
        {description ? (
          <div style={{ fontSize: 12, color: token.colorTextSecondary, lineHeight: 1.4 }}>
            {description}
          </div>
        ) : (
          <code style={{ fontSize: 11, color: token.colorTextTertiary }}>{type}</code>
        )}
      </div>
      <Switch checked={enabled} onChange={onChange} />
    </li>
  );
}

type Token = ReturnType<typeof useToken>['token'];
function toneColor(t: Token, tone: ReturnType<typeof notificationMeta>['tone']): string {
  switch (tone) {
    case 'info':
      return t.colorInfo;
    case 'success':
      return t.colorSuccess;
    case 'warning':
      return t.colorWarning;
    case 'error':
      return t.colorError;
    case 'neutral':
      return t.colorTextSecondary;
  }
}

