// Drawer that opens when an employee clicks an asset row on /me/belongings
// — mirrors RequisitionDetailDrawer's pattern (facts up top, no extra
// fetch since the list query already returns the full row).

import { Typography } from 'antd';
import type { ReactNode } from 'react';

import { DetailDrawer, KvList, MoneyCell, StatusPill } from '@/components';
import { formatDateShort } from '@/lib/dates';
import {
  ACTIVATION_STATUS_LABEL,
  ACTIVATION_STATUS_TONE,
  CLEARANCE_STATUS_LABEL,
  CLEARANCE_STATUS_TONE,
} from '../format';
import type { BelongingIssuanceDto } from '../types';

const { Text } = Typography;

export type BelongingIssuanceDetailDrawerProps = {
  row: BelongingIssuanceDto | null;
  onClose: () => void;
};

const CONDITION_LABEL: Record<string, string> = {
  NEW: 'New',
  USED: 'Used',
  OLD: 'Old',
};

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <Text
        type="secondary"
        style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: '0.06em' }}
      >
        {label}
      </Text>
      <div style={{ fontSize: 13, marginTop: 4 }}>{children}</div>
    </div>
  );
}

export function BelongingIssuanceDetailDrawer({
  row,
  onClose,
}: BelongingIssuanceDetailDrawerProps) {
  const open = row != null;

  return (
    <DetailDrawer
      title={row?.belongingName ?? 'Asset'}
      open={open}
      onClose={onClose}
      extra={
        row?.clearanceStatus ? (
          <StatusPill tone={CLEARANCE_STATUS_TONE[row.clearanceStatus]} size="md">
            {CLEARANCE_STATUS_LABEL[row.clearanceStatus]}
          </StatusPill>
        ) : undefined
      }
    >
      {row ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
          <KvList
            items={[
              { label: 'Asset type', value: row.belongingName ?? '—' },
              { label: 'Model', value: row.assetModel ?? '—' },
              { label: 'Year', value: row.assetYear ?? '—' },
              {
                label: 'Condition',
                value: row.condition ? CONDITION_LABEL[row.condition] : '—',
              },
              {
                label: 'Price',
                value: row.assetPrice != null ? <MoneyCell amount={row.assetPrice} /> : '—',
              },
              {
                label: 'Issued',
                value: row.issuedDate ? formatDateShort(row.issuedDate) : '—',
              },
              {
                label: 'Expiry',
                value: row.expiryDate ? formatDateShort(row.expiryDate) : '—',
              },
              {
                label: 'Buy-back — employee',
                value: row.buyBackPercEmployee != null ? `${row.buyBackPercEmployee}%` : '—',
              },
              {
                label: 'Buy-back — employer',
                value: row.buyBackPercEmployer != null ? `${row.buyBackPercEmployer}%` : '—',
              },
              {
                label: 'Activation',
                value: row.activationStatus ? (
                  <StatusPill tone={ACTIVATION_STATUS_TONE[row.activationStatus]}>
                    {ACTIVATION_STATUS_LABEL[row.activationStatus]}
                  </StatusPill>
                ) : (
                  '—'
                ),
              },
              {
                label: 'Activation date',
                value: row.activationDate ? formatDateShort(row.activationDate) : '—',
              },
              {
                label: 'Clearance id',
                value: row.clearanceId ?? '—',
              },
              {
                label: 'Clearance date',
                value: row.clearanceDate ? formatDateShort(row.clearanceDate) : '—',
              },
            ]}
            minColumnWidth={150}
            gap={14}
          />

          {row.description ? <Fact label="Description">{row.description}</Fact> : null}
        </div>
      ) : null}
    </DetailDrawer>
  );
}
