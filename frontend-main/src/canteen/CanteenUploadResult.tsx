// Import outcome for a spreadsheet upload.
//
// A full-day sheet can carry two or three thousand employees, so the raw
// `{code: message}` maps are unreadable as a list. Three things make that
// tractable, in the order an operator actually needs them:
//
//   1. Headline counts — did this mostly work?
//   2. Reasons grouped by message — "Employee not found ×412" is one fact,
//      not 412. This is what tells you whether the sheet is wrong or the
//      roster is.
//   3. The full per-code list, filtered and paginated, for chasing the
//      individual rows — plus a CSV export, because reconciling thousands
//      of codes happens in a spreadsheet, not in a drawer.

import { useMemo, useState } from 'react';
import { Button, Input, Segmented, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckCircle2, Download, XCircle } from 'lucide-react';

import { downloadCsv } from '@/lib';
import type { CanteenExcelUploadResponse } from './types';

const { Text } = Typography;
const { useToken } = antdTheme;

type Outcome = 'success' | 'error';
type ResultRow = { code: string; outcome: Outcome; message: string };

export type CanteenUploadResultProps = {
  result: CanteenExcelUploadResponse;
  /** Stamped into the exported filename so saved reports stay distinguishable. */
  attendanceDate: string;
};

export function CanteenUploadResult({ result, attendanceDate }: CanteenUploadResultProps) {
  const { token } = useToken();
  const [filter, setFilter] = useState<'ALL' | Outcome>('ALL');
  const [q, setQ] = useState('');

  const rows = useMemo<ResultRow[]>(() => {
    const out: ResultRow[] = [];
    for (const [code, message] of Object.entries(result.success ?? {})) {
      out.push({ code, outcome: 'success', message });
    }
    for (const [code, message] of Object.entries(result.error ?? {})) {
      out.push({ code, outcome: 'error', message });
    }
    return out;
  }, [result]);

  const successCount = Object.keys(result.success ?? {}).length;
  const errorCount = Object.keys(result.error ?? {}).length;

  // Distinct failure reasons, most common first — the fastest read on what
  // actually went wrong across thousands of rows.
  const reasons = useMemo(() => {
    const counts = new Map<string, number>();
    for (const message of Object.values(result.error ?? {})) {
      counts.set(message, (counts.get(message) ?? 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]);
  }, [result]);

  const visible = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return rows.filter((r) => {
      if (filter !== 'ALL' && r.outcome !== filter) return false;
      if (!needle) return true;
      return (
        r.code.toLowerCase().includes(needle) ||
        r.message.toLowerCase().includes(needle)
      );
    });
  }, [rows, filter, q]);

  const exportCsv = () =>
    downloadCsv(
      `canteen-upload-${attendanceDate}.csv`,
      ['Employee code', 'Outcome', 'Message'],
      rows.map((r) => [r.code, r.outcome === 'success' ? 'Success' : 'Error', r.message]),
    );

  const columns: ColumnsType<ResultRow> = [
    {
      title: 'Code',
      dataIndex: 'code',
      width: 110,
      render: (code: string) => (
        <span style={{ fontSize: 12, fontVariantNumeric: 'tabular-nums' }}>{code}</span>
      ),
    },
    {
      title: 'Outcome',
      dataIndex: 'outcome',
      width: 110,
      render: (outcome: Outcome) => (
        <Tag color={outcome === 'success' ? 'green' : 'red'} style={{ margin: 0 }}>
          {outcome === 'success' ? 'Recorded' : 'Failed'}
        </Tag>
      ),
    },
    {
      title: 'Message',
      dataIndex: 'message',
      render: (message: string) => <Text style={{ fontSize: 12 }}>{message}</Text>,
    },
  ];

  return (
    <div
      style={{
        marginTop: 16,
        border: `1px solid ${token.colorBorderSecondary}`,
        borderRadius: 12,
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 14,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <Text strong style={{ fontSize: 13 }}>
          Upload result
        </Text>
        <Tag
          icon={<CheckCircle2 size={12} />}
          color="green"
          style={{ margin: 0, display: 'inline-flex', alignItems: 'center', gap: 4 }}
        >
          {successCount.toLocaleString('en-PK')} recorded
        </Tag>
        <Tag
          icon={<XCircle size={12} />}
          color={errorCount > 0 ? 'red' : 'default'}
          style={{ margin: 0, display: 'inline-flex', alignItems: 'center', gap: 4 }}
        >
          {errorCount.toLocaleString('en-PK')} failed
        </Tag>
        <Button
          size="small"
          icon={<Download size={14} />}
          onClick={exportCsv}
          style={{ marginLeft: 'auto' }}
          disabled={rows.length === 0}
        >
          Export CSV
        </Button>
      </div>

      {reasons.length > 0 ? (
        <div>
          <Text type="secondary" style={{ fontSize: 11 }}>
            Why rows failed
          </Text>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 6 }}>
            {reasons.map(([message, count]) => (
              <button
                key={message}
                type="button"
                onClick={() => {
                  setFilter('error');
                  setQ(message);
                }}
                title="Filter the list to these rows"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  width: '100%',
                  textAlign: 'left',
                  border: 'none',
                  background: 'transparent',
                  padding: '4px 6px',
                  borderRadius: 6,
                  cursor: 'pointer',
                }}
              >
                <Tag color="red" style={{ margin: 0, minWidth: 46, textAlign: 'center' }}>
                  {count.toLocaleString('en-PK')}
                </Tag>
                <Text style={{ fontSize: 12 }}>{message}</Text>
              </button>
            ))}
          </div>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        <Segmented
          size="small"
          value={filter}
          onChange={(v) => setFilter(v as 'ALL' | Outcome)}
          options={[
            { value: 'ALL', label: `All ${rows.length.toLocaleString('en-PK')}` },
            { value: 'success', label: `Recorded ${successCount.toLocaleString('en-PK')}` },
            { value: 'error', label: `Failed ${errorCount.toLocaleString('en-PK')}` },
          ]}
        />
        <Input
          allowClear
          size="small"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Find a code or message…"
          style={{ width: 220 }}
        />
      </div>

      <Table<ResultRow>
        rowKey={(r) => `${r.outcome}-${r.code}`}
        size="small"
        columns={columns}
        dataSource={visible}
        pagination={{
          pageSize: 25,
          size: 'small',
          showSizeChanger: true,
          pageSizeOptions: ['25', '50', '100'],
          showTotal: (n, [start, end]) =>
            `${start}–${end} of ${n.toLocaleString('en-PK')}`,
        }}
      />
    </div>
  );
}
