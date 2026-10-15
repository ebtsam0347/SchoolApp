// Gradient hero on /time/today. Owns the "Punch in / out" mutation and
// drives all the contextual messaging (weekend vs holiday vs leave vs
// working). Renders the gradient ourselves rather than hijacking
// StatCard hero — this card has interactive content (button + chips)
// that doesn't map cleanly to the stat-card prop surface.

import { useEffect, useState, type CSSProperties } from 'react';
import { Alert, App as AntdApp, Button, Card, Typography, theme as antdTheme } from 'antd';
import { Link } from '@tanstack/react-router';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FileEdit, LogIn, LogOut, Plane, Sparkles, Sun } from 'lucide-react';
import {
  attendanceApi,
  formatClock,
  formatMinutes,
  type PunchDto,
  type TodayRecord,
} from '@/attendance';
import { getErrorMessage, isApiError } from '@/lib';
import { ClockTicker } from './ClockTicker';

const { Text } = Typography;
const { useToken } = antdTheme;

export type PunchHeroCardProps = {
  record: TodayRecord | null;
  punches: PunchDto[];
};

export function PunchHeroCard({ record, punches }: PunchHeroCardProps) {
  const { token } = useToken();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(t);
  }, []);

  // Even count = next punch is check-in, odd count = next is check-out.
  const isCheckIn = punches.length % 2 === 0;
  const lastPunch = punches[punches.length - 1] ?? null;
  const lastPunchTime = lastPunch ? new Date(lastPunch.punchedAt).getTime() : null;
  const elapsedSinceLast =
    !isCheckIn && lastPunchTime
      ? Math.max(0, Math.floor((now - lastPunchTime) / 60_000))
      : null;

  const webPunchCount = punches.filter((p) => p.source === 'WEB').length;
  const webExhausted = webPunchCount >= 2;

  const punchMutation = useMutation({
    mutationFn: () => attendanceApi.punch({}),
    onSuccess: (punch) => {
      message.success(
        `${isCheckIn ? 'Punched in' : 'Punched out'} at ${formatClock(punch.punchedAt)}`,
      );
      queryClient.invalidateQueries({ queryKey: ['attendance'] });
    },
    onError: (err) => {
      if (isApiError(err) && err.code === 'PUNCH_LIMIT') {
        message.warning("You've already used your two web punches for today.");
        return;
      }
      message.error(getErrorMessage(err));
    },
  });

  return (
    <Card
      variant="borderless"
      styles={{ body: { padding: 0, height: '100%' } }}
      style={{
        borderRadius: 'var(--app-radius-lg)',
        overflow: 'hidden',
        boxShadow: `0 8px 28px color-mix(in srgb, var(--app-accent-grad-from, ${token.colorPrimary}) 22%, transparent)`,
      }}
    >
      <div
        style={{
          background: `linear-gradient(135deg, var(--app-accent-grad-from, ${token.colorPrimary}), var(--app-accent-grad-to, ${token.colorPrimary}))`,
          color: token.colorTextLightSolid,
          padding: '28px 32px',
          display: 'flex',
          flexDirection: 'column',
          gap: 20,
          minHeight: 220,
          height: '100%',
        }}
      >
        <header
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-start',
            gap: 12,
          }}
        >
          <div>
            <div
              style={{
                fontSize: 11,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                opacity: 0.85,
                fontWeight: 600,
              }}
            >
              {record?.status === 'WEEKEND'
                ? 'Weekend'
                : record?.status === 'HOLIDAY'
                  ? 'Public holiday'
                  : record?.status === 'LEAVE'
                    ? 'On leave today'
                    : isCheckIn
                      ? 'Ready to punch in'
                      : 'Currently working'}
            </div>
            <h2
              style={{
                margin: '6px 0 0',
                fontSize: 28,
                fontWeight: 600,
                lineHeight: 1.2,
              }}
            >
              {isCheckIn
                ? 'Start your day'
                : elapsedSinceLast != null
                  ? `Working ${formatMinutes(elapsedSinceLast)}`
                  : 'In progress'}
            </h2>
          </div>
          <div
            style={{
              fontSize: 36,
              fontWeight: 700,
              fontVariantNumeric: 'tabular-nums',
              lineHeight: 1,
              opacity: 0.95,
            }}
          >
            <ClockTicker />
          </div>
        </header>

        {record?.status === 'WEEKEND' || record?.status === 'HOLIDAY' ? (
          <Alert
            type="info"
            showIcon
            icon={<Sun size={16} />}
            title={
              record.status === 'WEEKEND'
                ? 'Weekend (Sunday) — no scheduled shift.'
                : 'Public holiday — no scheduled shift.'
            }
            description="Any work today counts as Special OT. Apply for OT from the quick actions below before you punch in."
            style={{
              background: 'rgba(255,255,255,0.12)',
              border: '1px solid rgba(255,255,255,0.18)',
            }}
          />
        ) : null}

        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'center' }}>
          <Button
            type="primary"
            size="large"
            loading={punchMutation.isPending}
            disabled={
              webExhausted ||
              record?.status === 'LEAVE' ||
              record?.status === 'WEEKEND' ||
              record?.status === 'HOLIDAY'
            }
            icon={isCheckIn ? <LogIn size={16} /> : <LogOut size={16} />}
            onClick={() => punchMutation.mutate()}
            style={{
              height: 48,
              fontSize: 15,
              fontWeight: 600,
              minWidth: 180,
              background: 'rgba(255,255,255,0.96)',
              color: token.colorPrimary,
              borderColor: 'transparent',
              boxShadow: '0 8px 22px rgba(0,0,0,0.18)',
            }}
          >
            {isCheckIn ? 'Punch in' : 'Punch out'}
          </Button>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 12 }}>
              {lastPunch
                ? isCheckIn
                  ? `Last out · ${formatClock(lastPunch.punchedAt)}`
                  : `In since · ${formatClock(lastPunch.punchedAt)}`
                : 'No punches yet today'}
            </Text>
            <Text style={{ color: 'rgba(255,255,255,0.7)', fontSize: 11 }}>
              Web portal punches: {webPunchCount}/2
              {webExhausted ? ' — limit reached, ask operator to log next punch' : ''}
            </Text>
          </div>
        </div>

        <div style={{ marginTop: 'auto', display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Link to="/time/leaves" style={heroChipStyle}>
            <Plane size={13} /> Apply leave
          </Link>
          <Link to="/time/overtime" style={heroChipStyle}>
            <Sparkles size={13} /> Request OT
          </Link>
          <Link to="/time/attendance" style={heroChipStyle}>
            <FileEdit size={13} /> Regularize
          </Link>
        </div>
      </div>
    </Card>
  );
}

const heroChipStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 6,
  padding: '6px 12px',
  fontSize: 12,
  fontWeight: 500,
  borderRadius: 'var(--app-radius-full)',
  color: 'rgba(255,255,255,0.95)',
  background: 'rgba(255,255,255,0.14)',
  border: '1px solid rgba(255,255,255,0.22)',
  textDecoration: 'none',
};
