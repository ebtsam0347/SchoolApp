// Drawer that opens when a row is clicked in the Travel Request table —
// mirrors LeaveDetailDrawer's structure (facts card + approval timeline +
// row-scoped actions pinned to the footer) so the two request types read as
// one system. Approve/Reject/Cancel/Submit are all gated on the server's
// `actions[]` — nothing here re-derives who may act from `status`.

import {
  App as AntdApp,
  Avatar,
  Button,
  Card,
  Popconfirm,
  Space,
  Tag,
  theme as antdTheme,
  Typography,
} from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Globe, MapPin, Pencil, Users, XCircle } from 'lucide-react';

import { ApprovalStepsTimeline } from '@/attendance/leaves';
import { ApproveRejectActions, DetailDrawer, KvList, Section, StatusPill } from '@/components';
import type { DepartmentDto, DesignationDto, GradeDto, SectionDto } from '@/people';
import { formatDateTime, getErrorMessage } from '@/lib';
import { travelRequestApi } from '@/travel';
import {
  FACTORY_TOUR_STATUS_LABEL,
  factoryTourStatusTone,
  formatRawStatus,
  VEHICLE_SOURCE_LABEL,
} from '../format';
import type { FactoryTourDto } from '../types';

const { Text } = Typography;
const { useToken } = antdTheme;

export type FactoryTourDetailDrawerProps = {
  row: FactoryTourDto | null;
  onClose: () => void;
  departments?: DepartmentDto[];
  sections?: SectionDto[];
  designations?: DesignationDto[];
  grades?: GradeDto[];
  /** Omit to hide Edit — e.g. in the approvals inbox, an approver's view. */
  onEdit?: (row: FactoryTourDto) => void;
};

export function FactoryTourDetailDrawer({
  row,
  onClose,
  departments,
  sections,
  designations,
  grades,
  onEdit,
}: FactoryTourDetailDrawerProps) {
  const { token } = useToken();
  const { message } = AntdApp.useApp();
  const queryClient = useQueryClient();
  const open = row != null;

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['travel-requests'] });
    void queryClient.invalidateQueries({ queryKey: ['bff', 'approvals', 'inbox'] });
  };

  const approveMutation = useMutation({
    mutationFn: () => travelRequestApi.approve(row?.id ?? 0),
    onSuccess: () => {
      message.success('Travel request approved');
      invalidate();
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const rejectMutation = useMutation({
    mutationFn: (reason: string) => travelRequestApi.reject(row?.id ?? 0, { reason }),
    onSuccess: () => {
      message.success('Travel request rejected');
      invalidate();
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  const cancelMutation = useMutation({
    mutationFn: () => travelRequestApi.cancel(row?.id ?? 0),
    onSuccess: () => {
      message.success('Travel request cancelled');
      invalidate();
      onClose();
    },
    onError: (err) => message.error(getErrorMessage(err)),
  });

  if (!row) {
    return (
      <DetailDrawer title="Travel request" open={open} onClose={onClose}>
        {null}
      </DetailDrawer>
    );
  }

  const deptName = departments?.find((d) => d.id === row.departmentId)?.name;
  const sectionName = sections?.find((s) => s.id === row.sectionId)?.name;
  const designationTitle = designations?.find((d) => d.id === row.designationId)?.title;
  const gradeName = grades?.find((g) => g.id === row.gradeId)?.name;
  const isLocal = row.travelType === 'LOCAL';
  const RouteIcon = isLocal ? MapPin : Globe;
  const multiLeg = row.legs.length > 1;

  const canCancel = row.actions.includes('CANCEL');
  const canEdit = row.actions.includes('EDIT');
  const approveRejectActions = row.actions.filter(
    (a): a is 'APPROVE' | 'REJECT' => a === 'APPROVE' || a === 'REJECT',
  );

  const hasFooterActions = canEdit || canCancel || approveRejectActions.length > 0;

  return (
    <DetailDrawer
      title={`Travel request ${row.requestId}`}
      open={open}
      onClose={onClose}
      size={560}
      extra={
        <StatusPill tone={factoryTourStatusTone(row)} size="md">
          {row.rawStatus && row.rawStatus !== row.status
            ? formatRawStatus(row.rawStatus)
            : FACTORY_TOUR_STATUS_LABEL[row.status]}
        </StatusPill>
      }
      footer={
        hasFooterActions ? (
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Space size={8} wrap>
              {canEdit && onEdit ? (
                <Button
                  icon={<Pencil size={14} />}
                  onClick={() => {
                    onEdit(row);
                    onClose();
                  }}
                >
                  Edit
                </Button>
              ) : null}
              {canCancel ? (
                <Popconfirm
                  title="Cancel this travel request?"
                  description="This cannot be undone."
                  okText="Yes, cancel"
                  okButtonProps={{ danger: true }}
                  onConfirm={() => cancelMutation.mutate()}
                >
                  <Button danger icon={<XCircle size={14} />} loading={cancelMutation.isPending}>
                    Cancel request
                  </Button>
                </Popconfirm>
              ) : null}
              {approveRejectActions.length > 0 ? (
                <ApproveRejectActions
                  actions={approveRejectActions}
                  size="middle"
                  onApprove={async () => {
                    await approveMutation.mutateAsync();
                  }}
                  onReject={async (reason) => {
                    await rejectMutation.mutateAsync(reason);
                  }}
                  rejectTitle="Reject this travel request?"
                  hideReadOnlyHint
                />
              ) : null}
            </Space>
          </div>
        ) : undefined
      }
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
        <Card size="small" styles={{ body: { padding: 14 } }}>
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: 10,
              marginBottom: 'var(--app-space-sm)',
            }}
          >
            <span
              aria-hidden
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 32,
                height: 32,
                borderRadius: 8,
                flexShrink: 0,
                color: token.colorPrimary,
                background: `color-mix(in srgb, ${token.colorPrimary} 12%, transparent)`,
              }}
            >
              <RouteIcon size={16} strokeWidth={1.75} />
            </span>
            <div style={{ minWidth: 0, flex: 1 }}>
              <strong
                style={{
                  fontSize: 14,
                  display: 'block',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
                title={`${row.origin} → ${row.destination}`}
              >
                {row.origin} <span style={{ color: token.colorTextTertiary }}>→</span>{' '}
                {row.destination}
              </strong>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {isLocal ? 'Local' : 'International'} tour · {row.name}
              </Text>
            </div>
          </div>

          <KvList
            items={[
              { label: 'Tour start', value: formatDateTime(row.tourStart) },
              { label: 'Tour end', value: formatDateTime(row.tourEnd) },
              { label: 'Card No', value: row.cardNo },
              { label: 'Department', value: deptName ?? '—' },
              { label: 'Section', value: sectionName ?? '—' },
              { label: 'Designation', value: designationTitle ?? '—' },
              { label: 'Grade', value: gradeName ?? '—' },
              // With several hops the vehicle/distance facts differ per leg, so
              // they move into the Routes section below rather than showing
              // leg 1's values as if they covered the whole trip.
              ...(isLocal && !multiLeg
                ? [
                    { label: 'City', value: row.city || '—' },
                    {
                      label: 'Vehicle source',
                      value: row.vehicleSource ? VEHICLE_SOURCE_LABEL[row.vehicleSource] : '—',
                    },
                    { label: 'Transportation type', value: row.transportationType || '—' },
                    { label: 'Vehicle No', value: row.vehicleNo || '—' },
                    ...(row.totalDistanceKm != null
                      ? [{ label: 'Distance', value: `${row.totalDistanceKm} km` }]
                      : []),
                  ]
                : []),
              ...(isLocal && multiLeg && row.totalDistanceKm != null
                ? [{ label: 'Total distance', value: `${row.totalDistanceKm} km` }]
                : []),
              ...(!isLocal
                ? [
                    { label: 'Country', value: row.country || '—' },
                    { label: 'Currency', value: row.currency || '—' },
                  ]
                : []),
              { label: 'Requested', value: formatDateTime(row.requestDate) },
            ]}
            minColumnWidth={150}
            gap={14}
            size="sm"
          />

          {row.purpose ? (
            <div style={{ marginTop: 'var(--app-space-sm)', fontSize: 13 }}>
              <Text
                type="secondary"
                style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}
              >
                Purpose
              </Text>
              <div>{row.purpose}</div>
            </div>
          ) : null}
        </Card>

        {row.status === 'REJECTED' && row.rejectReason ? (
          <Card
            size="small"
            styles={{ body: { padding: 14 } }}
            style={{
              borderColor: `color-mix(in srgb, ${token.colorError} 35%, transparent)`,
              background: `color-mix(in srgb, ${token.colorError} 6%, transparent)`,
            }}
          >
            <Text
              type="secondary"
              style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}
            >
              Rejection reason
            </Text>
            <div style={{ fontSize: 13, marginTop: 4 }}>{row.rejectReason}</div>
          </Card>
        ) : null}

        {multiLeg ? (
          <Section title={`Routes (${row.legs.length})`} level={5}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {row.legs.map((leg) => (
                <Card key={leg.sequence} size="small" styles={{ body: { padding: 12 } }}>
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'baseline',
                      gap: 8,
                      marginBottom: 8,
                    }}
                  >
                    <Tag style={{ margin: 0 }}>{leg.sequence}</Tag>
                    <strong style={{ fontSize: 13 }}>
                      {leg.origin} <span style={{ color: token.colorTextTertiary }}>→</span>{' '}
                      {leg.destination}
                    </strong>
                  </div>
                  <KvList
                    items={[
                      {
                        label: 'Vehicle source',
                        value: leg.vehicleSource ? VEHICLE_SOURCE_LABEL[leg.vehicleSource] : '—',
                      },
                      { label: 'Transportation type', value: leg.transportationType || '—' },
                      { label: 'Vehicle No', value: leg.vehicleNo || '—' },
                      {
                        label: 'Distance',
                        value: leg.distanceKm != null ? `${leg.distanceKm} km` : '—',
                      },
                      ...(leg.remarks ? [{ label: 'Remarks', value: leg.remarks }] : []),
                    ]}
                    minColumnWidth={150}
                    gap={12}
                    size="sm"
                  />
                </Card>
              ))}
            </div>
          </Section>
        ) : null}

        {row.additionalTravelPersons.length > 0 ? (
          <Section title="Co-travellers" level={5}>
            <Space size={[8, 8]} wrap>
              {row.additionalTravelPersons.map((p) => (
                <Tag key={p.cardNo} style={{ margin: 0, padding: '4px 10px' }}>
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <Avatar size={16} icon={<Users size={10} />} />
                    {[p.name, p.cardNo].filter(Boolean).join(' · ')}
                  </span>
                </Tag>
              ))}
            </Space>
          </Section>
        ) : null}

        {row.steps.length > 0 ? (
          <Section title="Approval" level={5}>
            <ApprovalStepsTimeline steps={row.steps} />
          </Section>
        ) : null}
      </div>
    </DetailDrawer>
  );
}
