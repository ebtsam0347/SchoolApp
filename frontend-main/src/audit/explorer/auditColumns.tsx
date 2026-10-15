import { Tag, Tooltip, Typography, type TableProps } from 'antd';

import type { AuditEventDto } from '../api';

const { Text } = Typography;

export const auditColumns: TableProps<AuditEventDto>['columns'] = [
  {
    title: 'When',
    dataIndex: 'occurredAt',
    width: 180,
    render: (v: string) => (
      <span
        style={{
          fontSize: 12,
          fontFamily:
            'ui-monospace, SFMono-Regular, Menlo, monospace',
        }}
      >
        {new Date(v).toLocaleString()}
      </span>
    ),
  },
  {
    title: 'Service',
    dataIndex: 'sourceService',
    width: 110,
    render: (v: string) => <Tag style={{ margin: 0 }}>{v}</Tag>,
  },
  {
    title: 'Action',
    dataIndex: 'action',
    width: 160,
    render: (a: string) => <code style={{ fontSize: 12 }}>{a}</code>,
  },
  {
    title: 'Entity',
    key: 'entity',
    width: 220,
    render: (_, evt) => (
      <Tooltip title={evt.entityId}>
        <span style={{ fontSize: 12 }}>
          <code>{evt.entityType}</code>
          <span style={{ marginLeft: 6, opacity: 0.6 }}>{evt.entityId}</span>
        </span>
      </Tooltip>
    ),
  },
  {
    title: 'Actor',
    key: 'actor',
    width: 200,
    render: (_, evt) =>
      evt.actorLabel ? (
        <span style={{ fontSize: 12 }}>
          {evt.actorLabel}{' '}
          <Text type="secondary" style={{ fontSize: 11 }}>
            @{evt.actorUsername}
          </Text>
        </span>
      ) : (
        <Text type="secondary" style={{ fontSize: 12 }}>
          system
        </Text>
      ),
  },
  {
    title: 'Message',
    dataIndex: 'message',
    ellipsis: true,
    render: (v: string | null) => v ?? <Text type="secondary">—</Text>,
  },
  {
    title: '',
    key: 'visibility',
    width: 110,
    render: (_, evt) =>
      evt.userVisible ? (
        <Tag color="success" style={{ margin: 0, fontSize: 10 }}>
          User-visible
        </Tag>
      ) : (
        <Tag style={{ margin: 0, fontSize: 10 }}>Internal</Tag>
      ),
  },
];
