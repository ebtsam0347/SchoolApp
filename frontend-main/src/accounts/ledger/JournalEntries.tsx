import { useState } from 'react';
import { Button, Modal, Space, Table, Tag, Typography, theme as antdTheme } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { BookOpen, Filter, Plus, RotateCcw } from 'lucide-react';

import { FilterBar, MoneyCell, PageHeader, StatCard, KpiGrid, Section } from '@/components';
import { formatDateShort } from '@/lib/dates';

import { PreviewNotice } from '../PreviewNotice';
import { recentJournals, type JournalEntry } from '../mock/data';

const { Text } = Typography;
const { useToken } = antdTheme;

const sourceColors: Record<JournalEntry['source'], string> = {
  AR: 'green',
  AP: 'blue',
  Payroll: 'gold',
  Bank: 'cyan',
  Inventory: 'purple',
  Depreciation: 'magenta',
  Manual: 'default',
};

export function JournalEntries() {
  const { token } = useToken();
  const [search, setSearch] = useState('');
  const [composing, setComposing] = useState(false);

  const filtered = recentJournals.filter((j) => {
    if (!search.trim()) return true;
    const q = search.toLowerCase();
    return (
      j.id.toLowerCase().includes(q) ||
      j.reference.toLowerCase().includes(q) ||
      j.description.toLowerCase().includes(q) ||
      j.source.toLowerCase().includes(q)
    );
  });

  const totalDebit = filtered.reduce((sum, j) => sum + j.debit, 0);
  const drafts = recentJournals.filter((j) => j.status === 'DRAFT').length;
  const today = recentJournals.filter((j) => j.postedOn === '2026-05-03').length;

  const columns: ColumnsType<JournalEntry> = [
    {
      title: 'Entry',
      dataIndex: 'id',
      key: 'id',
      width: 160,
      render: (id: string, row) => (
        <Space orientation="vertical" size={0}>
          <Text strong style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 13 }}>
            {id}
          </Text>
          <Text type="secondary" style={{ fontSize: 11 }}>
            {formatDateShort(row.postedOn)}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Reference',
      dataIndex: 'reference',
      key: 'reference',
      width: 180,
      render: (ref: string) => (
        <Text style={{ fontFamily: 'var(--font-mono, ui-monospace)', fontSize: 12 }}>{ref}</Text>
      ),
    },
    {
      title: 'Description',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: 'Source',
      dataIndex: 'source',
      key: 'source',
      width: 120,
      render: (s: JournalEntry['source']) => <Tag color={sourceColors[s]}>{s}</Tag>,
    },
    {
      title: 'Debit',
      dataIndex: 'debit',
      key: 'debit',
      width: 140,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Credit',
      dataIndex: 'credit',
      key: 'credit',
      width: 140,
      align: 'right',
      render: (v: number) => <MoneyCell amount={v} compact />,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: JournalEntry['status']) => {
        const color = s === 'POSTED' ? 'green' : s === 'DRAFT' ? 'gold' : 'red';
        return <Tag color={color}>{s}</Tag>;
      },
    },
    {
      title: 'By',
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 110,
      render: (v: string) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {v}
        </Text>
      ),
    },
  ];

  return (
    <Space orientation="vertical" size={20} style={{ width: '100%' }}>
      <PageHeader
        eyebrow="LEDGER"
        title="Journal entries"
        description="Every posting that hits the general ledger — manual, automated, and system-generated."
        actions={
          <Space>
            <Button icon={<RotateCcw size={14} strokeWidth={1.75} />}>Recurring</Button>
            <Button
              type="primary"
              icon={<Plus size={14} strokeWidth={1.75} />}
              onClick={() => setComposing(true)}
            >
              New entry
            </Button>
          </Space>
        }
      />

      <PreviewNotice />

      <KpiGrid minColumnWidth={220}>
        <StatCard
          variant="accent-border"
          tone="primary"
          icon={BookOpen}
          label="Posted today"
          value={today.toString()}
        />
        <StatCard
          variant="accent-border"
          tone="warning"
          icon={Filter}
          label="Awaiting review"
          value={drafts.toString()}
          delta="drafts pending posting"
        />
        <StatCard
          variant="accent-border"
          tone="info"
          icon={BookOpen}
          label="Total debit (visible)"
          value={<MoneyCell amount={totalDebit} compact />}
        />
        <StatCard
          variant="accent-border"
          tone="success"
          icon={BookOpen}
          label="Balanced"
          value="100%"
          delta="Dr = Cr across all entries"
        />
      </KpiGrid>

      <Section title="Recent postings" subtitle="Latest 10 GL entries — click to expand">
        <FilterBar
          search={{
            value: search,
            onChange: setSearch,
            placeholder: 'Search entry, reference, description…',
          }}
        />
        <Table
          rowKey="id"
          columns={columns}
          dataSource={filtered}
          pagination={false}
          size="middle"
          style={{ marginTop: 12 }}
          rowClassName={(record) =>
            record.status === 'DRAFT' ? 'je-draft-row' : ''
          }
        />
      </Section>

      <Modal
        open={composing}
        onCancel={() => setComposing(false)}
        title="New journal entry"
        footer={null}
        width={680}
      >
        <Text type="secondary">
          A composition surface for new postings will land here — debit / credit
          line builder, reference, attachment, posting date, and an automatic
          balance check before submit.
        </Text>
        <div
          style={{
            marginTop: 16,
            padding: 16,
            background: token.colorFillTertiary,
            borderRadius: token.borderRadiusSM,
          }}
        >
          <Text style={{ fontSize: 12 }}>
            <strong>Coming next:</strong> account picker with chart-of-accounts
            tree search, side-by-side debit / credit grid with live balance,
            keyboard shortcut (<kbd>↵</kbd> to add line, <kbd>⌘↵</kbd> to post),
            attachment and recurring-template binding.
          </Text>
        </div>
      </Modal>
    </Space>
  );
}
