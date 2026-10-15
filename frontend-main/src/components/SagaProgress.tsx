import { theme as antdTheme, Typography } from 'antd';
import {
  CheckCircle2,
  CircleAlert,
  CircleDashed,
  Loader2,
  type LucideIcon,
} from 'lucide-react';
import type { CSSProperties, ReactNode } from 'react';

import { StatusPill, type StatusPillTone } from './StatusPill';

const { Text } = Typography;
const { useToken } = antdTheme;

export type SagaProgressStatus = 'RUNNING' | 'COMPLETED' | 'FAILED';

export type SagaProgressStep = {
  /** Server-side step name (e.g. "PROVISION_AUTH_USER"). */
  key: string;
  label: string;
  /** Optional explainer line. */
  description?: string;
};

export type SagaProgressProps = {
  status: SagaProgressStatus;
  /** Server's `currentStep` value — used to highlight the active row. */
  currentStep?: string | null;
  /** Optional ordered step list. When omitted, just the status pill + lastError row render. */
  steps?: SagaProgressStep[];
  /** Server's `attemptCount`. */
  attemptCount?: number;
  /** Server's `lastError` — shown when status === FAILED. */
  lastError?: string | null;
  /** Right-aligned slot — usually a Retry button when FAILED. */
  actions?: ReactNode;
  className?: string;
  styles?: { root?: CSSProperties };
};

const STATUS_TONE: Record<SagaProgressStatus, StatusPillTone> = {
  RUNNING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
};

const STATUS_LABEL: Record<SagaProgressStatus, string> = {
  RUNNING: 'Running',
  COMPLETED: 'Completed',
  FAILED: 'Failed',
};

export function SagaProgress({
  status,
  currentStep,
  steps,
  attemptCount,
  lastError,
  actions,
  className,
  styles,
}: SagaProgressProps) {
  const { token } = useToken();

  const currentIdx = steps?.findIndex((s) => s.key === currentStep) ?? -1;

  return (
    <div
      className={className}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
        padding: 18,
        background: token.colorBgContainer,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        ...styles?.root,
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <StatusPill tone={STATUS_TONE[status]}>
            {STATUS_LABEL[status]}
          </StatusPill>
          {attemptCount !== undefined && attemptCount > 1 ? (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Attempt {attemptCount}
            </Text>
          ) : null}
        </div>
        {actions ? (
          <div style={{ display: 'flex', gap: 6 }}>{actions}</div>
        ) : null}
      </div>

      {steps && steps.length > 0 ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {steps.map((step, i) => {
            const isCompleted =
              status === 'COMPLETED'
                ? true
                : currentIdx >= 0
                  ? i < currentIdx
                  : false;
            const isActive = step.key === currentStep && status === 'RUNNING';
            const isFailed = step.key === currentStep && status === 'FAILED';
            const isPending = !isCompleted && !isActive && !isFailed;

            const Icon: LucideIcon = isCompleted
              ? CheckCircle2
              : isFailed
                ? CircleAlert
                : isActive
                  ? Loader2
                  : CircleDashed;
            const iconColor = isCompleted
              ? token.colorSuccess
              : isFailed
                ? token.colorError
                : isActive
                  ? token.colorInfo
                  : token.colorTextTertiary;

            return (
              <div
                key={step.key}
                style={{
                  display: 'flex',
                  alignItems: 'flex-start',
                  gap: 10,
                  padding: '6px 10px',
                  borderRadius: 8,
                  background: isActive
                    ? `color-mix(in srgb, ${token.colorInfo} 8%, transparent)`
                    : 'transparent',
                }}
              >
                <Icon
                  size={16}
                  strokeWidth={1.75}
                  color={iconColor}
                  style={
                    isActive
                      ? { animation: 'saga-spin 1s linear infinite' }
                      : undefined
                  }
                  aria-hidden
                />
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div
                    style={{
                      fontSize: 13,
                      fontWeight: isActive || isFailed ? 600 : 500,
                      color: isPending
                        ? token.colorTextTertiary
                        : token.colorTextHeading,
                    }}
                  >
                    {step.label}
                  </div>
                  {step.description ? (
                    <Text
                      type="secondary"
                      style={{ fontSize: 11, display: 'block' }}
                    >
                      {step.description}
                    </Text>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      ) : null}

      {status === 'FAILED' && lastError ? (
        <div
          style={{
            padding: 10,
            background: `color-mix(in srgb, ${token.colorError} 8%, transparent)`,
            border: `1px solid color-mix(in srgb, ${token.colorError} 24%, transparent)`,
            borderRadius: 8,
            fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
            fontSize: 12,
            color: token.colorError,
            wordBreak: 'break-word',
          }}
        >
          {lastError}
        </div>
      ) : null}

      <style>{`
        @keyframes saga-spin {
          from { transform: rotate(0deg); }
          to   { transform: rotate(360deg); }
        }
        @media (prefers-reduced-motion: reduce) {
          [style*="saga-spin"] { animation: none !important; }
        }
      `}</style>
    </div>
  );
}
