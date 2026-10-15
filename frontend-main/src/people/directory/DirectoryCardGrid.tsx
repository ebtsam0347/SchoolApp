import { Skeleton, theme as antdTheme, Tooltip, Typography } from 'antd';
import { Link } from '@tanstack/react-router';
import { Briefcase, Building2, Mail, Phone, Users } from 'lucide-react';

import { EmptyState, PersonCell, StatusPill } from '@/components';
import {
  EMPLOYMENT_STATUS_LABEL,
  EMPLOYMENT_STATUS_TONE,
  type EmployeeDirectoryRow,
} from '../types';
import './DirectoryCardGrid.css';

const { useToken } = antdTheme;
const { Text } = Typography;

export type DirectoryCardGridProps = {
  rows: EmployeeDirectoryRow[];
  loading: boolean;
  /** Number of skeleton placeholders while first page loads. */
  skeletonCount?: number;
  /**
   * Open a card in place instead of linking to `/people/:id` — for callers
   * without `employee.read`, where that page would 403.
   */
  onOpen?: (row: EmployeeDirectoryRow) => void;
};

export function DirectoryCardGrid({
  rows,
  loading,
  skeletonCount = 8,
  onOpen,
}: DirectoryCardGridProps) {
  const { token } = useToken();

  if (loading && rows.length === 0) {
    return (
      <div style={gridStyle}>
        {Array.from({ length: skeletonCount }).map((_, i) => (
          <div key={i} style={cardStyle(token)}>
            <Skeleton avatar active paragraph={{ rows: 3 }} />
          </div>
        ))}
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <EmptyState
        title="No employees match these filters"
        description="Try clearing a filter or broadening your search."
      />
    );
  }

  return (
    <div style={gridStyle}>
      {rows.map((row) => {
        const e = row.employee;
        if (!e?.id) return null;
        const r = row.resolved;
        const status = e.employmentStatus;
        const directReports = e.directReportsCount ?? 0;

        const body = (
          <>
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                justifyContent: 'space-between',
                gap: 8,
              }}
            >
              <PersonCell
                size="default"
                person={{
                  fullName: e.fullName ?? '—',
                  employeeCode: e.employeeCode,
                  designation: r?.designation?.title ?? null,
                  profilePictureUrl: e.profilePictureUrl,
                }}
              />
              {status ? (
                <StatusPill tone={EMPLOYMENT_STATUS_TONE[status]} size="sm">
                  {EMPLOYMENT_STATUS_LABEL[status]}
                </StatusPill>
              ) : null}
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
              {r?.grade?.name ? (
                <Row icon={<Briefcase size={14} strokeWidth={1.75} />}>
                  {r.grade.name}
                </Row>
              ) : null}
              <Row icon={<Building2 size={14} strokeWidth={1.75} />}>
                {[r?.department?.name, r?.section?.name]
                  .filter(Boolean)
                  .join(' › ') || '—'}
              </Row>
              {e.workEmail ? (
                <Row icon={<Mail size={14} strokeWidth={1.75} />}>
                  <Text style={{ fontSize: 12 }} ellipsis={{ tooltip: e.workEmail }}>
                    {e.workEmail}
                  </Text>
                </Row>
              ) : null}
              {e.phone ? (
                <Row icon={<Phone size={14} strokeWidth={1.75} />}>
                  <Text style={{ fontSize: 12 }}>{e.phone}</Text>
                </Row>
              ) : null}
            </div>

            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                paddingTop: 8,
                borderTop: `1px solid ${token.colorBorderSecondary}`,
                gap: 8,
              }}
            >
              <div style={{ minWidth: 0, flex: 1 }}>
                {r?.manager?.fullName ? (
                  <Text type="secondary" style={{ fontSize: 11 }} ellipsis>
                    Reports to{' '}
                    <Text style={{ fontSize: 11, color: token.colorText }}>
                      {r.manager.fullName}
                    </Text>
                  </Text>
                ) : e.managerId == null ? (
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    No manager
                  </Text>
                ) : null}
              </div>
              {directReports > 0 ? (
                <Tooltip title="Direct reports">
                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      fontSize: 11,
                      color: token.colorTextSecondary,
                      fontVariantNumeric: 'tabular-nums',
                    }}
                  >
                    <Users size={12} strokeWidth={1.75} />
                    {directReports}
                  </span>
                </Tooltip>
              ) : null}
            </div>
          </>
        );

        return onOpen ? (
          <div
            key={e.id}
            role="button"
            tabIndex={0}
            onClick={() => onOpen(row)}
            onKeyDown={(ev) => {
              if (ev.key === 'Enter' || ev.key === ' ') {
                ev.preventDefault();
                onOpen(row);
              }
            }}
            style={cardStyle(token)}
            className="directory-card"
          >
            {body}
          </div>
        ) : (
          <Link
            key={e.id}
            to="/people/$id"
            params={{ id: String(e.id) }}
            style={{ ...cardStyle(token), textDecoration: 'none' }}
            className="directory-card"
          >
            {body}
          </Link>
        );
      })}
    </div>
  );
}

const gridStyle: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
  gap: 12,
};

function cardStyle(token: ReturnType<typeof useToken>['token']): React.CSSProperties {
  return {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
    padding: 14,
    background: token.colorBgContainer,
    border: `1px solid ${token.colorBorderSecondary}`,
    borderRadius: token.borderRadiusLG,
    color: token.colorText,
    transition: `transform 200ms ${token.motionEaseOut}, box-shadow 200ms ${token.motionEaseOut}, border-color 200ms ${token.motionEaseOut}`,
    cursor: 'pointer',
  };
}

function Row({
  icon,
  children,
}: {
  icon: React.ReactNode;
  children: React.ReactNode;
}) {
  const { token } = useToken();
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        minWidth: 0,
        color: token.colorTextSecondary,
        fontSize: 12,
      }}
    >
      <span style={{ display: 'inline-flex', flexShrink: 0 }}>{icon}</span>
      <span
        style={{
          minWidth: 0,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
          color: token.colorText,
        }}
      >
        {children}
      </span>
    </div>
  );
}
