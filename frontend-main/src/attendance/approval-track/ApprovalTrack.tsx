// Where a request is in its approval chain, at a glance — the "Approval
// progress" column and History drawer of the Generated team requests page,
// shared with the short-leave lists so both read the same way.
//
// Works on any request type: rows carry the type's own status as
// `rawStatus`, plus the chain (`steps`) and the stage it's waiting on.

import { Drawer, Space, Tooltip, Typography, theme as antdTheme } from 'antd';
import { Check, Circle, Clock, Minus, X, type LucideIcon } from 'lucide-react';

import { PersonCell, Section, StatusPill } from '@/components';
import { APPROVAL_STEP_STATUS_LABEL, formatDateTime } from '../format';
import type { ApprovalStepDto } from '../types';
import { ApprovalStepsTimeline } from '../leaves';
import type { RequestTimelineKind } from '../bff-api';
import { RequestTimeline } from '../RequestTimeline';
import { phaseOf, roleLabel, whereLine, type PhaseInput, type TrackedRequestRow } from './phase';

const { Text } = Typography;
const { useToken } = antdTheme;

/* ------------------------------ tracker ------------------------------ */

const STEP_ICON: Record<ApprovalStepDto['status'], LucideIcon> = {
  APPROVED: Check,
  REJECTED: X,
  PENDING: Clock,
  NOT_YET: Circle,
  SKIPPED: Minus,
};

/**
 * The chain at a glance: one chip per stage, joined left to right, with the
 * stage it's sitting at ringed — and a line saying where it is or how it
 * ended. Just the line when a row has no steps.
 */
export function StageTrack({ row }: { row: PhaseInput }) {
  const { token } = useToken();
  const phase = phaseOf(row);
  const colorFor = (st: ApprovalStepDto['status']) =>
    st === 'APPROVED'
      ? token.colorSuccess
      : st === 'REJECTED'
        ? token.colorError
        : st === 'PENDING'
          ? token.colorWarning
          : token.colorTextQuaternary;
  const lineColor =
    phase === 'rejected'
      ? token.colorError
      : phase === 'in-progress'
        ? token.colorWarning
        : phase === 'approved'
          ? token.colorSuccess
          : token.colorTextTertiary;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      {row.steps.length > 0 ? (
        <div
          role="list"
          aria-label="Approval stages"
          style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', rowGap: 4 }}
        >
          {row.steps.map((st, i) => {
            const Icon = STEP_ICON[st.status];
            const color = colorFor(st.status);
            const current = row.currentStage?.stageNo === st.stageNo;
            const who = st.decidedByName
              ? ` by ${st.decidedByName}`
              : st.assignedToName
                ? ` · ${st.assignedToName}`
                : '';
            return (
              <div
                key={st.stageNo}
                role="listitem"
                style={{ display: 'flex', alignItems: 'center' }}
              >
                {i > 0 ? (
                  <span
                    aria-hidden
                    style={{
                      width: 12,
                      height: 2,
                      margin: '0 4px',
                      borderRadius: 1,
                      background: st.status === 'NOT_YET' ? token.colorBorderSecondary : color,
                    }}
                  />
                ) : null}
                <Tooltip
                  title={`${st.stageNo}. ${roleLabel(st.stageRole)} — ${
                    APPROVAL_STEP_STATUS_LABEL[st.status]
                  }${who}`}
                >
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      padding: '2px 8px',
                      borderRadius: 999,
                      fontSize: 12,
                      fontWeight: current ? 600 : 500,
                      whiteSpace: 'nowrap',
                      color: st.status === 'NOT_YET' ? token.colorTextTertiary : color,
                      background: `color-mix(in srgb, ${color} ${current ? 16 : 9}%, transparent)`,
                      boxShadow: current ? `0 0 0 1.5px ${color}` : 'none',
                    }}
                  >
                    <Icon size={12} strokeWidth={2.25} aria-hidden />
                    {roleLabel(st.stageRole)}
                  </span>
                </Tooltip>
              </div>
            );
          })}
        </div>
      ) : null}
      <Text style={{ fontSize: 12, color: lineColor }}>{whereLine(row)}</Text>
    </div>
  );
}

/* ------------------------------ drawer ------------------------------ */

/** A request's full approval history: who, what, where it is, every stage. */
export function RequestHistoryDrawer({
  row,
  typeLabel,
  onClose,
  activity,
}: {
  row: TrackedRequestRow | null;
  typeLabel: string;
  onClose: () => void;
  /** Adds the request's Activity feed (`/bff/timeline/{kind}/{id}`). */
  activity?: { kind: RequestTimelineKind; requestId: number } | null;
}) {
  return (
    <Drawer
      open={row != null}
      onClose={onClose}
      size={480}
      destroyOnHidden
      title={row ? `${typeLabel} · ${row.code}` : null}
    >
      {row ? (
        <Space orientation="vertical" size={20} style={{ width: '100%' }}>
          {row.person ? (
            <PersonCell
              person={{
                fullName: row.person.fullName,
                employeeCode: row.person.employeeCode ?? null,
                profilePictureUrl: row.person.profilePictureUrl ?? undefined,
              }}
            />
          ) : row.people ? (
            <Text>{row.people}</Text>
          ) : null}

          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            <Text>{row.detail}</Text>
            {row.note ? <Text type="secondary">{row.note}</Text> : null}
            <Space size={8} wrap>
              <StatusPill tone={row.statusTone}>{row.statusLabel}</StatusPill>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {whereLine(row)}
              </Text>
            </Space>
            {row.createdAt ? (
              <Text type="secondary" style={{ fontSize: 12 }}>
                Filed {formatDateTime(row.createdAt)}
              </Text>
            ) : null}
          </div>

          <div>
            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              Approval history
            </Text>
            <div style={{ marginBottom: 16 }}>
              <StageTrack row={row} />
            </div>
            {row.steps.length > 0 ? (
              <ApprovalStepsTimeline
                steps={row.steps}
                currentStageNo={row.currentStage?.stageNo ?? null}
              />
            ) : (
              <Text type="secondary">No approval steps recorded for this request.</Text>
            )}
          </div>

          {activity ? (
            <Section title="Activity" level={5}>
              <RequestTimeline kind={activity.kind} requestId={activity.requestId} />
            </Section>
          ) : null}
        </Space>
      ) : null}
    </Drawer>
  );
}
