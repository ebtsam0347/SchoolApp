import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Modal, Skeleton, theme as antdTheme, Typography } from 'antd';
import { CalendarDays, Package, Percent, ShieldCheck } from 'lucide-react';

import { KvList, MoneyCell, StatusPill } from '@/components';
import { getErrorMessage } from '@/lib';
import { formatDateShort } from '@/lib/dates';
import { catalogsApi } from '@/people';

import {
  ACTIVATION_STATUS_LABEL,
  ACTIVATION_STATUS_TONE,
  CLEARANCE_STATUS_LABEL,
  CLEARANCE_STATUS_TONE,
} from '../format';
import type { BelongingCondition, BelongingIssuanceDto } from '../types';

const { Text } = Typography;

const CONDITION_LABEL: Record<BelongingCondition, string> = {
  NEW: 'New',
  USED: 'Used',
  OLD: 'Old',
};

export type AssetDetailModalProps = {
  asset: BelongingIssuanceDto | null;
  open: boolean;
  onClose: () => void;
};

/** Small titled block so each group of fields reads as its own section. */
function DetailSection({
  icon,
  title,
  children,
}: {
  icon: ReactNode;
  title: string;
  children: ReactNode;
}) {
  const { token } = antdTheme.useToken();
  return (
    <div
      style={{
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: token.borderRadiusLG,
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 14px',
          background: token.colorFillAlter,
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <span style={{ display: 'inline-flex', color: token.colorPrimary }}>{icon}</span>
        <Text strong style={{ fontSize: 13 }}>
          {title}
        </Text>
      </div>
      <div style={{ padding: '12px 14px' }}>{children}</div>
    </div>
  );
}

export function AssetDetailModal({ asset, open, onClose }: AssetDetailModalProps) {
  const { token } = antdTheme.useToken();
  const mastersQuery = useQuery({
    queryKey: ['catalog', 'belonging-master-all'],
    queryFn: ({ signal }) => catalogsApi.belongingMasters(signal),
    enabled: open && !!asset,
    staleTime: 5 * 60_000,
  });

  const master =
    asset?.belongingMasterId != null
      ? (mastersQuery.data ?? []).find((item) => item.id === asset.belongingMasterId)
      : undefined;

  const date = (iso: string | undefined) => (iso ? formatDateShort(iso) : '—');
  const pct = (v: number | undefined) => (v != null ? `${v}%` : '—');

  return (
    <Modal
      open={open && !!asset}
      title={
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          <Package size={18} color={token.colorPrimary} />
          Asset details
        </span>
      }
      onCancel={onClose}
      footer={null}
      destroyOnHidden
      width={640}
    >
      {mastersQuery.isLoading ? <Skeleton active paragraph={{ rows: 6 }} /> : null}
      {mastersQuery.isError ? (
        <Alert
          type="error"
          showIcon
          title="Couldn't load asset details"
          description={getErrorMessage(mastersQuery.error)}
        />
      ) : null}
      {!mastersQuery.isLoading && !mastersQuery.isError && asset ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {/* Identity: what the asset is, its price and where it stands */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: 12,
              padding: '14px 16px',
              borderRadius: token.borderRadiusLG,
              background: token.colorFillAlter,
              border: `1px solid ${token.colorBorderSecondary}`,
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  width: 44,
                  height: 44,
                  flexShrink: 0,
                  borderRadius: token.borderRadiusLG,
                  background: token.colorPrimaryBg,
                  color: token.colorPrimary,
                }}
              >
                <Package size={22} />
              </span>
              <div style={{ minWidth: 0 }}>
                <Text strong style={{ fontSize: token.fontSizeLG, display: 'block', lineHeight: 1.3 }}>
                  {asset.belongingName ?? master?.belongingName ?? '—'}
                </Text>
                <Text type="secondary" style={{ fontSize: 13 }}>
                  {[asset.assetModel, asset.assetYear].filter((v) => v != null && v !== '').join(' · ') ||
                    'No model recorded'}
                </Text>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 6 }}>
              {asset.assetPrice != null ? (
                <MoneyCell amount={asset.assetPrice} style={{ fontWeight: 700, fontSize: token.fontSizeLG }} />
              ) : null}
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                {asset.activationStatus ? (
                  <StatusPill tone={ACTIVATION_STATUS_TONE[asset.activationStatus]} size="sm">
                    {ACTIVATION_STATUS_LABEL[asset.activationStatus]}
                  </StatusPill>
                ) : null}
                {asset.clearanceStatus ? (
                  <StatusPill tone={CLEARANCE_STATUS_TONE[asset.clearanceStatus]} size="sm">
                    {CLEARANCE_STATUS_LABEL[asset.clearanceStatus]}
                  </StatusPill>
                ) : null}
              </div>
            </div>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
              gap: 12,
            }}
          >
            <DetailSection icon={<Package size={14} />} title="Asset">
              <KvList
                minColumnWidth={110}
                items={[
                  { label: 'Belonging master', value: master?.belongingName ?? '—' },
                  { label: 'Model', value: asset.assetModel || '—' },
                  { label: 'Year', value: asset.assetYear ?? '—' },
                  {
                    label: 'Condition',
                    value: asset.condition ? CONDITION_LABEL[asset.condition] : '—',
                  },
                ]}
              />
            </DetailSection>
            <DetailSection icon={<CalendarDays size={14} />} title="Issuance">
              <KvList
                minColumnWidth={110}
                items={[
                  { label: 'Issued on', value: date(asset.issuedDate) },
                  { label: 'Expires on', value: date(asset.expiryDate) },
                  {
                    label: 'Activation',
                    value: asset.activationStatus ? ACTIVATION_STATUS_LABEL[asset.activationStatus] : '—',
                  },
                  { label: 'Activated on', value: date(asset.activationDate) },
                ]}
              />
            </DetailSection>
            <DetailSection icon={<Percent size={14} />} title="Buy-back">
              <KvList
                minColumnWidth={110}
                items={[
                  { label: 'Employee share', value: pct(asset.buyBackPercEmployee) },
                  { label: 'Employer share', value: pct(asset.buyBackPercEmployer) },
                ]}
              />
            </DetailSection>
            <DetailSection icon={<ShieldCheck size={14} />} title="Clearance">
              <KvList
                minColumnWidth={110}
                items={[
                  {
                    label: 'Status',
                    value: asset.clearanceStatus ? (
                      <StatusPill tone={CLEARANCE_STATUS_TONE[asset.clearanceStatus]} size="sm">
                        {CLEARANCE_STATUS_LABEL[asset.clearanceStatus]}
                      </StatusPill>
                    ) : (
                      'Not started'
                    ),
                  },
                  { label: 'Cleared on', value: date(asset.clearanceDate) },
                  { label: 'Clearance ID', value: asset.clearanceId || '—', span: 2 },
                ]}
              />
            </DetailSection>
          </div>

          {asset.description ? (
            <DetailSection icon={<Package size={14} />} title="Description">
              <Text style={{ whiteSpace: 'pre-wrap' }}>{asset.description}</Text>
            </DetailSection>
          ) : null}
        </div>
      ) : null}
    </Modal>
  );
}
